package io.effects.core;

import io.effects.core.F;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IOTest {

    @Test
    void testEitherLeftAndRight() {
        Either<String, Integer> right = Either.right(42);
        assertTrue(right.isRight());
        assertFalse(right.isLeft());
        assertEquals(42, right.getRight());
        assertThrows(Exception.class, right::getLeft);

        Either<String, Integer> left = Either.left("Error");
        assertTrue(left.isLeft());
        assertFalse(left.isRight());
        assertEquals("Error", left.getLeft());
        assertThrows(Exception.class, left::getRight);
    }

    @Test
    void testEitherMonadicOps() {
        Either<String, Integer> right = Either.right(42);
        Either<String, String> mapped = right.map(Object::toString);
        assertEquals("42", mapped.getRight());

        Either<String, Integer> flatMapped = right.flatMap(v -> Either.right(v + 8));
        assertEquals(50, flatMapped.getRight());

        Either<String, Integer> left = Either.left("Fail");
        Either<String, Integer> mapLeft = left.map(v -> v + 10);
        assertTrue(mapLeft.isLeft());
        assertEquals("Fail", mapLeft.getLeft());

        Either<String, Integer> flatMapLeft = left.flatMap(v -> Either.right(v + 10));
        assertTrue(flatMapLeft.isLeft());
        assertEquals("Fail", flatMapLeft.getLeft());
    }

    @Test
    void testIOPure() {
        IO<Integer> io = IO.of(42);
        Integer result = io.unsafeRunSync();
        assertEquals(42, result);
    }

    @Test
    void testIODelay() {
        AtomicInteger counter = new AtomicInteger(0);
        IO<Integer> io = IO.delay(counter::incrementAndGet);
        
        // Ensure delay is lazy and does not run immediately
        assertEquals(0, counter.get());

        Integer result1 = io.unsafeRunSync();
        assertEquals(1, result1);
        assertEquals(1, counter.get());

        Integer result2 = io.unsafeRunSync();
        assertEquals(2, result2);
        assertEquals(2, counter.get());
    }

    @Test
    void testIOMapAndFlatMap() {
        IO<Integer> io = IO.of(10)
                .map(x -> x * 2)
                .flatMap(x -> IO.delay(() -> x + 5));

        assertEquals(25, io.unsafeRunSync());
    }

    @Test
    void testIOErrorHandling() {
        IO<Integer> failing = IO.delay(() -> {
            throw new RuntimeException("boom");
        });

        assertThrows(RuntimeException.class, failing::unsafeRunSync);

        IO<Integer> recovered = failing.handleErrorWith(t -> {
            assertEquals("boom", t.getMessage());
            return IO.of(100);
        });

        assertEquals(100, recovered.unsafeRunSync());
    }

    @Test
    void testIOAttempt() {
        IO<Integer> failing = IO.delay(() -> {
            throw new RuntimeException("boom");
        });

        IO<Either<Throwable, Integer>> attempted = failing.attempt();
        Either<Throwable, Integer> result = attempted.unsafeRunSync();

        assertTrue(result.isLeft());
        assertEquals("boom", result.getLeft().getMessage());

        IO<Either<Throwable, Integer>> success = IO.of(42).attempt();
        Either<Throwable, Integer> successResult = success.unsafeRunSync();

        assertTrue(successResult.isRight());
        assertEquals(42, successResult.getRight());
    }

    @Test
    void testStackSafety() {
        int iterations = 50000;
        IO<Integer> io = IO.of(0);
        
        for (int i = 0; i < iterations; i++) {
            io = io.flatMap(x -> IO.of(x + 1));
        }

        // This would cause StackOverflowError if flatMap evaluation was recursive
        Integer result = io.unsafeRunSync();
        assertEquals(iterations, result);
    }

    @Test
    void testIOShift() {
        Thread mainThread = Thread.currentThread();
        AtomicReference<Thread> evaluatedThread = new AtomicReference<>();

        IO<Void> io = IO.shift()
                .flatMap(v -> IO.delay(() -> {
                    evaluatedThread.set(Thread.currentThread());
                    return null;
                }));

        io.unsafeRunSync();

        assertNotNull(evaluatedThread.get());
        assertNotEquals(mainThread, evaluatedThread.get());
        assertTrue(evaluatedThread.get().isVirtual());
    }

    @Test
    void testIOAsync() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        IO<String> asyncIO = IO.async(cb -> {
            // Run registration asynchronously on a manual platform thread
            Thread.ofPlatform().start(() -> {
                try {
                    Thread.sleep(50);
                    cb.accept(Either.right("Hello from Async"));
                } catch (InterruptedException e) {
                    cb.accept(Either.left(e));
                }
            });
        });

        asyncIO.unsafeRunAsync(either -> {
            either.fold(
                err -> {
                    errorRef.set(err);
                    return null;
                },
                val -> {
                    resultRef.set(val);
                    latch.countDown();
                    return null;
                }
            );
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("Hello from Async", resultRef.get());
        assertNull(errorRef.get());
    }

    @Test
    void testTypeclassFunctorHelpers() {
        IO<String> io = IO.of("hello").as("world");
        assertEquals("world", io.unsafeRunSync());

        IO<Void> voided = IO.of("hello").voided();
        assertNull(voided.unsafeRunSync());
    }

    @Test
    void testTypeclassApplicativeHelpers() {
        IO<Integer> io1 = IO.of(10);
        IO<Integer> io2 = IO.of(20);

        IO<Integer> combined = io1.map2(io2, Integer::sum);
        assertEquals(30, combined.unsafeRunSync());

        IO<Integer> prodL = io1.productL(io2);
        assertEquals(10, prodL.unsafeRunSync());

        IO<Integer> prodR = io1.productR(io2);
        assertEquals(20, prodR.unsafeRunSync());
    }

    @Test
    void testTypeclassMonadHelpers() {
        IO<IO<Integer>> nested = IO.of(IO.of(42));
        IO<Integer> flattened = nested.flatten();
        assertEquals(42, flattened.unsafeRunSync());

        IO<Integer> io1 = IO.of(10);
        IO<Integer> io2 = IO.of(20);
        IO<Integer> sequential = io1.andThen(io2);
        assertEquals(20, sequential.unsafeRunSync());
    }

    @Test
    void testBifunctorEither() {
        Either<String, Integer> right = Either.right(42);
        Either<String, Integer> left = Either.left("Error");

        Either<String, String> rightBimapped = right.bimap(s -> s + "!", Object::toString);
        assertEquals("42", rightBimapped.getRight());

        Either<String, String> leftBimapped = left.bimap(s -> s + "!", Object::toString);
        assertEquals("Error!", leftBimapped.getLeft());

        Either<String, Integer> mappedFirst = left.mapFirst(s -> s + "?");
        assertEquals("Error?", mappedFirst.getLeft());

        Either<String, String> mappedSecond = right.mapSecond(Object::toString);
        assertEquals("42", mappedSecond.getRight());
    }

    @Test
    void testMonadErrorIO() {
        io.effects.core.MonadError<Integer, Throwable> me = IO.of(0);

        F<Integer, ? extends io.effects.core.Functor<Integer>> failed = me.raiseError(new RuntimeException("typeclass boom"));
        IO<Integer> failedIO = (IO<Integer>) failed;
        assertThrows(RuntimeException.class, failedIO::unsafeRunSync);

        F<Integer, ? extends io.effects.core.Functor<Integer>> recovered = me.handleErrorWith(failed, err -> me.pure(100));
        IO<Integer> recoveredIO = (IO<Integer>) recovered;
        assertEquals(100, recoveredIO.unsafeRunSync());
    }

    @Test
    void testSemigroupAndMonoid() {
        io.effects.core.Monoid<String> stringMonoid = new io.effects.core.Monoid<>() {
            @Override
            public String combine(String x, String y) {
                return x + y;
            }

            @Override
            public String empty() {
                return "";
            }
        };

        assertEquals("HelloWorld", stringMonoid.combine("Hello", "World"));
        assertEquals("Hello", stringMonoid.combine("Hello", stringMonoid.empty()));
    }

    @Test
    void testFunctorLift() {
        var lifted = IO.of(42).lift(Object::toString);

        IO<String> io = (IO<String>) lifted.apply(IO.of(42));
        assertEquals("42", io.unsafeRunSync());
    }

    @Test
    void testApplicativeWhen() {
        AtomicInteger runCount = new AtomicInteger(0);
        IO<Void> effect = IO.delay(() -> {
            runCount.incrementAndGet();
            return null;
        });

        IO<Void> whenTrue = (IO<Void>) IO.of(null).when(true, effect);
        whenTrue.unsafeRunSync();
        assertEquals(1, runCount.get());

        IO<Void> whenFalse = (IO<Void>) IO.of(null).when(false, effect);
        whenFalse.unsafeRunSync();
        assertEquals(1, runCount.get());
    }

    @Test
    void testMonadIfM() {
        IO<String> condTrue = IO.of("True branch");
        IO<String> condFalse = IO.of("False branch");

        IO<String> resultTrue = condTrue.ifM(IO.of(true), condTrue, condFalse);
        assertEquals("True branch", resultTrue.unsafeRunSync());

        IO<String> resultFalse = condTrue.ifM(IO.of(false), condTrue, condFalse);
        assertEquals("False branch", resultFalse.unsafeRunSync());
    }

    @Test
    void testMonadErrorEnsure() {
        IO<Integer> io = IO.of(42);

        IO<Integer> success = io.ensure(x -> x > 40, new RuntimeException("fail"));
        assertEquals(42, success.unsafeRunSync());

        IO<Integer> failure = io.ensure(x -> x > 50, new RuntimeException("fail"));
        assertThrows(RuntimeException.class, failure::unsafeRunSync);
    }

    @Test
    void testParallelValidationRecipe() {
        Either<List<String>, String> validName = Either.right("Senthil");
        Either<List<String>, Integer> invalidAge = Either.left(List.of("Age must be positive"));
        Either<List<String>, String> invalidEmail = Either.left(List.of("Email is invalid"));

        io.effects.core.Semigroup<List<String>> listSemigroup = (x, y) -> {
            List<String> combined = new ArrayList<>(x);
            combined.addAll(y);
            return combined;
        };

        Either<List<String>, String> combinedSingleFail = Either.validateParallel(
            validName, invalidAge, listSemigroup, (name, age) -> name + age
        );
        assertTrue(combinedSingleFail.isLeft());
        assertEquals(1, combinedSingleFail.getLeft().size());
        assertEquals("Age must be positive", combinedSingleFail.getLeft().get(0));

        Either<List<String>, String> combinedBothFail = Either.validateParallel(
            invalidEmail, invalidAge, listSemigroup, (email, age) -> email + age
        );
        assertTrue(combinedBothFail.isLeft());
        assertEquals(2, combinedBothFail.getLeft().size());
        assertEquals("Email is invalid", combinedBothFail.getLeft().get(0));
        assertEquals("Age must be positive", combinedBothFail.getLeft().get(1));
    }

    @Test
    void testIOConcurrencyRace() {
        IO<String> slow = IO.delay(() -> {
            try { Thread.sleep(80); } catch (InterruptedException ignored) {}
            return "slow";
        });
        IO<String> fast = IO.delay(() -> {
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            return "fast";
        });

        Either<String, String> winner = IO.race(slow, fast).unsafeRunSync();
        assertTrue(winner.isRight());
        assertEquals("fast", winner.getRight());
    }

    @Test
    void testIOConcurrencyParallelMap() {
        IO<Integer> ioA = IO.delay(() -> {
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            return 10;
        });
        IO<Integer> ioB = IO.delay(() -> {
            try { Thread.sleep(20); } catch (InterruptedException ignored) {}
            return 20;
        });

        Integer sum = IO.parMap2(ioA, ioB, Integer::sum).unsafeRunSync();
        assertEquals(30, sum);

        IO.Pair<Integer, Integer> tuple = IO.parTuple(ioA, ioB).unsafeRunSync();
        assertEquals(10, tuple.first());
        assertEquals(20, tuple.second());
    }

    @Test
    void testIOConcurrencyParallelMapFailsFast() {
        IO<Integer> failing = IO.delay(() -> {
            throw new RuntimeException("fail fast");
        });
        IO<Integer> slow = IO.delay(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            return 100;
        });

        long start = System.currentTimeMillis();
        assertThrows(RuntimeException.class, () -> IO.parMap2(failing, slow, Integer::sum).unsafeRunSync());
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 200); // Fails fast, does not wait for the 1000ms delay
    }
}
