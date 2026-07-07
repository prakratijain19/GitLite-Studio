package app.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import app.model.Blob;

/**
 * Unit tests for {@link HashService}: the content-addressing guarantees that the
 * rest of the staging layer relies on.
 */
class HashServiceTest {

    private final HashService hashService = new HashService();

    private Blob blobOf(String text) {
        return hashService.createBlob(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("identical content always yields the same id (deduplication basis)")
    void sameContentSameId() {
        assertEquals(blobOf("Hello").getId(), blobOf("Hello").getId());
    }

    @Test
    @DisplayName("different content yields different ids")
    void differentContentDifferentId() {
        assertNotEquals(blobOf("Hello").getId(), blobOf("World").getId());
    }

    @Test
    @DisplayName("id is a 64-character lowercase hex SHA-256")
    void idIsSha256Hex() {
        String id = blobOf("anything").getId();
        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]{64}"), "id must be lowercase hex");
    }

    @Test
    @DisplayName("empty content is hashable and produces a valid id")
    void emptyContentIsHashable() {
        Blob blob = hashService.createBlob(new byte[0]);
        assertEquals(0, blob.size());
        assertEquals(64, blob.getId().length());
    }

    @Test
    @DisplayName("the blob stores the raw content, not the hashed canonical form")
    void blobStoresRawContent() {
        byte[] content = "Hello".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(content, hashService.createBlob(content).getContent());
    }
}
