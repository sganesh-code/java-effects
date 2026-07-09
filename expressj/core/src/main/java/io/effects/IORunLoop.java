package io.effects;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The internal execution engine for IO computations.
 * Implements a stack-safe trampolined loop and error propagation.
 */
final class IORunLoop {
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Consumer<Either<Throwable, Object>> finalCallback;
    private final IORuntime runtime;

    IORunLoop(IORuntime runtime, Consumer<Either<Throwable, Object>> finalCallback) {
        this.runtime = runtime;
        this.finalCallback = finalCallback;
    }

    // Stack frames representing monadic continuations
    private sealed interface Frame permits FlatMapFrame, ErrorFrame {
        IO<?> apply(Object value);
        IO<?> handleError(Throwable throwable);
    }

    private record FlatMapFrame(Function<Object, IO<?>> f) implements Frame {
        @Override
        public IO<?> apply(Object value) {
            return f.apply(value);
        }
        @Override
        public IO<?> handleError(Throwable throwable) {
            return null; // Not an error handler frame, bubble up
        }
    }

    private record ErrorFrame(Function<Throwable, IO<?>> handler) implements Frame {
        @Override
        public IO<?> apply(Object value) {
            return IO.of(value); // Success value, bypass error handler
        }
        @Override
        public IO<?> handleError(Throwable throwable) {
            return handler.apply(throwable);
        }
    }

    @SuppressWarnings("unchecked")
    void execute(IO<?> startIO) {
        IO<?> current = startIO;
        boolean suspended = false;

        while (current != null && !suspended) {
            try {
                switch (current) {
                    case IO.Pure<?> pure -> {
                        Object val = pure.value();
                        current = popAndApply(val);
                    }
                    case IO.Delay<?> delay -> {
                        try {
                            Object val = delay.thunk().get();
                            current = popAndApply(val);
                        } catch (Throwable t) {
                            current = popAndHandleError(t);
                        }
                    }
                    case IO.Error<?> err -> {
                        current = popAndHandleError(err.error());
                    }
                    case IO.FlatMap<?, ?> fm -> {
                        stack.push(new FlatMapFrame((Function<Object, IO<?>>) fm.f()));
                        current = fm.source();
                    }
                    case IO.HandleError<?> he -> {
                        stack.push(new ErrorFrame((Function<Throwable, IO<?>>) he.handler()));
                        current = he.source();
                    }
                    case IO.Async<?> async -> {
                        suspended = true;
                        try {
                            async.register().accept(result -> {
                                // Resume execution asynchronously on the virtual thread pool
                                runtime.execute(() -> {
                                    result.fold(
                                        err -> {
                                            execute(popAndHandleError(err));
                                            return null;
                                        },
                                        val -> {
                                            execute(popAndApply(val));
                                            return null;
                                        }
                                    );
                                });
                            });
                        } catch (Throwable t) {
                            execute(popAndHandleError(t));
                        }
                    }
                }
            } catch (Throwable fatal) {
                current = popAndHandleError(fatal);
            }
        }
    }

    private IO<?> popAndApply(Object value) {
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            if (frame instanceof FlatMapFrame fFrame) {
                try {
                    return fFrame.apply(value);
                } catch (Throwable t) {
                    return popAndHandleError(t);
                }
            }
        }
        // Completed successfully
        finalCallback.accept(Either.right(value));
        return null;
    }

    private IO<?> popAndHandleError(Throwable error) {
        while (!stack.isEmpty()) {
            Frame frame = stack.pop();
            if (frame instanceof ErrorFrame errFrame) {
                try {
                    return errFrame.handleError(error);
                } catch (Throwable t) {
                    error = t; // Handler failed, propagate the new error
                }
            }
        }
        // Completed with uncaught error
        finalCallback.accept(Either.left(error));
        return null;
    }
}
