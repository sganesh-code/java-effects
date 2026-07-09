package io.effects;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The execution runtime for IO computations, backed by a virtual thread pool.
 */
public final class IORuntime {
    private static final IORuntime GLOBAL = new IORuntime();

    private final ExecutorService executor;

    public IORuntime() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Retrieve the default global runtime.
     */
    public static IORuntime global() {
        return GLOBAL;
    }

    /**
     * Executes a task on the underlying virtual thread pool.
     */
    public void execute(Runnable task) {
        Objects.requireNonNull(task);
        executor.submit(task);
    }

    /**
     * Runs an IO asynchronously and triggers the callback when done.
     */
    @SuppressWarnings("unchecked")
    public <A> void runAsync(IO<A> io, Consumer<Either<Throwable, A>> cb) {
        Objects.requireNonNull(io);
        Objects.requireNonNull(cb);

        execute(() -> {
            IORunLoop loop = new IORunLoop(this, (Consumer<Either<Throwable, Object>>) (Consumer<?>) cb);
            loop.execute(io);
        });
    }

    /**
     * Runs an IO synchronously by blocking the calling thread until evaluation is complete.
     */
    public <A> A runSync(IO<A> io) {
        Objects.requireNonNull(io);
        CompletableFuture<Either<Throwable, A>> future = new CompletableFuture<>();
        runAsync(io, future::complete);

        try {
            Either<Throwable, A> result = future.join();
            return result.fold(
                err -> {
                    if (err instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(err);
                },
                val -> val
            );
        } catch (Throwable t) {
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
    }

    /**
     * Shuts down the runtime's executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Returns an IO representation of a virtual thread boundary shift.
     */
    static IO<Void> shift() {
        return IO.async(cb -> cb.accept(Either.right(null)));
    }
}
