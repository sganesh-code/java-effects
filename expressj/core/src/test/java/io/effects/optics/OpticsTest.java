package io.effects.optics;

import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.core.F;
import io.effects.core.Kleisli;
import io.effects.optics.Iso;
import io.effects.optics.Lens;
import io.effects.optics.Prism;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OpticsTest {

    // 1. Domain structures for testing Lenses

    record Address(String city, String zip) {
        public Address withCity(String newCity) {
            return new Address(newCity, this.zip);
        }
    }

    record User(String name, Address address) {
        public User withAddress(Address newAddress) {
            return new User(this.name, newAddress);
        }
    }

    // Lenses defined purely as static instances
    static final Lens<User, Address> userAddressLens = new Lens<>() {
        @Override public Address get(User u) { return u.address(); }
        @Override public User set(User u, Address a) { return u.withAddress(a); }
    };

    static final Lens<Address, String> addressCityLens = new Lens<>() {
        @Override public String get(Address a) { return a.city(); }
        @Override public Address set(Address a, String c) { return a.withCity(c); }
    };

    @Test
    void testLensCompositionAndModification() {
        User user = new User("Senthil", new Address("San Francisco", "94103"));

        // Retrieve field
        assertEquals("San Francisco", userAddressLens.compose(addressCityLens).get(user));

        // Compose Lenses
        Lens<User, String> userCityLens = userAddressLens.compose(addressCityLens);

        // Immutable modification
        User updatedUser = userCityLens.set(user, "New York");

        // Verify referential transparency (original user is untouched)
        assertEquals("San Francisco", user.address().city());
        assertEquals("New York", updatedUser.address().city());
        assertEquals("Senthil", updatedUser.name()); // Other fields preserved

        // Verify modify HOF
        User modifiedUser = userCityLens.modify(user, String::toUpperCase);
        assertEquals("SAN FRANCISCO", modifiedUser.address().city());
    }

    // 2. Prism Verification

    @Test
    void testPrismEitherFocus() {
        Prism<Either<String, Integer>, Integer> rightPrism = Either.rightPrism();
        Prism<Either<String, Integer>, String> leftPrism = Either.leftPrism();

        Either<String, Integer> success = Either.right(42);
        Either<String, Integer> failure = Either.left("Fatal Error");

        // GetOption
        assertEquals(Optional.of(42), rightPrism.getOption(success));
        assertEquals(Optional.empty(), rightPrism.getOption(failure));
        assertEquals(Optional.of("Fatal Error"), leftPrism.getOption(failure));

        // ReverseGet (lifting back to container context)
        Either<String, Integer> reconstructed = rightPrism.reverseGet(100);
        assertTrue(reconstructed.isRight());
        assertEquals(100, reconstructed.getRight());

        // Modify (success modified, failure left alone)
        Either<String, Integer> modifiedSuccess = rightPrism.modify(success, x -> x * 2);
        assertEquals(84, modifiedSuccess.getRight());

        Either<String, Integer> modifiedFailure = rightPrism.modify(failure, x -> x * 2);
        assertTrue(modifiedFailure.isLeft());
        assertEquals("Fatal Error", modifiedFailure.getLeft());
    }

    // 3. Iso (Lossless isomorphism) Verification

    record WrappedString(String val) {}

    static final Iso<WrappedString, String> wrappedStringIso = new Iso<>() {
        @Override public String get(WrappedString ws) { return ws.val(); }
        @Override public WrappedString reverseGet(String s) { return new WrappedString(s); }
    };

    @Test
    void testIsoEquivalence() {
        WrappedString ws = new WrappedString("effects");

        // Iso can act as a getter/setter (Lens)
        assertEquals("effects", wrappedStringIso.get(ws));
        assertEquals("effects", wrappedStringIso.getOption(ws).get());

        WrappedString updated = wrappedStringIso.set(ws, "optics");
        assertEquals("optics", updated.val());

        // Iso can reverseGet
        WrappedString reversed = wrappedStringIso.reverseGet("isomorphism");
        assertEquals("isomorphism", reversed.val());
    }

    // 4. Kleisli (Monadic Function Pipelines) Verification

    @Test
    void testKleisliPipelines() {
        // Create Kleisli effectful arrows for IO
        Kleisli<String, Integer> parseLength = IO.kleisli(s -> IO.delay(s::length));
        Kleisli<Integer, Integer> multiplyBy10 = IO.kleisli(x -> IO.delay(() -> x * 10));

        // Compose Kleisli pipelines sequentially (andThen)
        Kleisli<String, Integer> pipeline = parseLength.andThen(multiplyBy10);

        F<Integer, ?> resultEffect = pipeline.run("functional");
        IO<Integer> ioResult = (IO<Integer>) (Object) resultEffect;

        // Run the composed pipeline
        assertEquals(100, ioResult.unsafeRunSync());

        // Compose Kleisli before (compose)
        Kleisli<String, Integer> composePipeline = multiplyBy10.compose(parseLength);
        F<Integer, ?> composeEffect = composePipeline.run("optics");
        IO<Integer> ioComposeResult = (IO<Integer>) (Object) composeEffect;

        assertEquals(60, ioComposeResult.unsafeRunSync());
    }
}
