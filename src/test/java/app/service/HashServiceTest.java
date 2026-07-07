package app.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import app.model.Blob;
import app.model.FileSnapshot;

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

    // --- commit identity ---

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<FileSnapshot> MANIFEST = List.of(new FileSnapshot("a.txt", "id1"));

    @Test
    @DisplayName("identical commit inputs always yield the same id")
    void sameCommitInputsSameId() {
        assertEquals(
                hashService.createCommitId("msg", "author", WHEN, null, MANIFEST),
                hashService.createCommitId("msg", "author", WHEN, null, MANIFEST));
    }

    @Test
    @DisplayName("changing the parent changes the commit id")
    void parentChangesCommitId() {
        assertNotEquals(
                hashService.createCommitId("msg", "author", WHEN, null, MANIFEST),
                hashService.createCommitId("msg", "author", WHEN, "parent", MANIFEST));
    }

    @Test
    @DisplayName("manifest ordering does not affect the commit id")
    void manifestOrderDoesNotAffectCommitId() {
        List<FileSnapshot> ordered = List.of(new FileSnapshot("a", "1"), new FileSnapshot("b", "2"));
        List<FileSnapshot> reversed = List.of(new FileSnapshot("b", "2"), new FileSnapshot("a", "1"));
        assertEquals(
                hashService.createCommitId("msg", "author", WHEN, null, ordered),
                hashService.createCommitId("msg", "author", WHEN, null, reversed));
    }

    @Test
    @DisplayName("commit id is a 64-character lowercase hex SHA-256")
    void commitIdIsSha256Hex() {
        String id = hashService.createCommitId("msg", "author", WHEN, null, MANIFEST);
        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]{64}"));
    }
}
