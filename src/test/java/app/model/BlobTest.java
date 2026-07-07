package app.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Blob}, focused on its two guarantees: true immutability
 * of the content bytes, and identity-based equality.
 */
class BlobTest {

    @Test
    @DisplayName("mutating the source array after construction does not affect the blob")
    void defensiveCopyOnInput() {
        byte[] source = {1, 2, 3};
        Blob blob = Blob.of("id", source);
        source[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, blob.getContent());
    }

    @Test
    @DisplayName("mutating the returned array does not affect the blob")
    void defensiveCopyOnOutput() {
        Blob blob = Blob.of("id", new byte[] {1, 2, 3});
        blob.getContent()[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, blob.getContent());
    }

    @Test
    @DisplayName("equality is by id; content is irrelevant")
    void equalsById() {
        assertEquals(Blob.of("id", new byte[] {1}), Blob.of("id", new byte[] {2}));
        assertNotEquals(Blob.of("a", new byte[] {1}), Blob.of("b", new byte[] {1}));
    }

    @Test
    @DisplayName("a blank id is rejected")
    void blankIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> Blob.of("  ", new byte[0]));
    }
}
