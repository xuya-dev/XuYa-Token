package dev.xuya.token.core;

import dev.xuya.token.core.crypto.BCryptPasswordEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void encodeThenMatches() {
        String encoded = encoder.encode("secret");
        assertTrue(encoder.matches("secret", encoded));
        assertFalse(encoder.matches("wrong", encoded));
    }

    @Test
    void saltedHashesDifferEachTime() {
        assertNotEquals(encoder.encode("secret"), encoder.encode("secret"));
    }

    @Test
    void malformedHashDoesNotThrow() {
        assertFalse(encoder.matches("secret", "not-a-bcrypt-hash"));
    }

    @Test
    void nullInputsNeverMatch() {
        assertFalse(encoder.matches(null, encoder.encode("secret")));
        assertFalse(encoder.matches("secret", null));
        assertFalse(encoder.matches("secret", ""));
    }

    @Test
    void invalidStrengthRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordEncoder(3));
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordEncoder(32));
    }
}
