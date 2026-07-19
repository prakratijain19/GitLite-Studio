package app.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import app.model.Blob;
import app.model.FileSnapshot;

class HashServiceTest {
    private final HashService hashService = new HashService();
    private Blob blobOf(String text) { return hashService.createBlob(text.getBytes(StandardCharsets.UTF_8)); }
    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<FileSnapshot> MANIFEST = List.of(new FileSnapshot("a.txt", "id1"));

    @Test @DisplayName("identical content always yields the same id")
    void sameContentSameId() { assertEquals(blobOf("Hello").getId(), blobOf("Hello").getId()); }

    @Test @DisplayName("different content yields different ids")
    void differentContentDifferentId() { assertNotEquals(blobOf("Hello").getId(), blobOf("World").getId()); }

    @Test @DisplayName("id is a 64-character lowercase hex SHA-256")
    void idIsSha256Hex() { String id = blobOf("anything").getId(); assertEquals(64, id.length()); assertTrue(id.matches("[0-9a-f]{64}")); }

    @Test @DisplayName("empty content is hashable")
    void emptyContentIsHashable() { Blob blob = hashService.createBlob(new byte[0]); assertEquals(0, blob.size()); assertEquals(64, blob.getId().length()); }

    @Test @DisplayName("the blob stores raw content")
    void blobStoresRawContent() { byte[] c = "Hello".getBytes(StandardCharsets.UTF_8); assertArrayEquals(c, hashService.createBlob(c).getContent()); }

    @Test @DisplayName("identical commit inputs always yield the same id")
    void sameCommitInputsSameId() { assertEquals(hashService.createCommitId("msg", "author", WHEN, null, null, MANIFEST), hashService.createCommitId("msg", "author", WHEN, null, null, MANIFEST)); }

    @Test @DisplayName("changing the parent changes the commit id")
    void parentChangesCommitId() { assertNotEquals(hashService.createCommitId("msg", "author", WHEN, null, null, MANIFEST), hashService.createCommitId("msg", "author", WHEN, "parent", null, MANIFEST)); }

    @Test @DisplayName("manifest ordering does not affect the commit id")
    void manifestOrderDoesNotAffectCommitId() {
        var ordered = List.of(new FileSnapshot("a", "1"), new FileSnapshot("b", "2"));
        var reversed = List.of(new FileSnapshot("b", "2"), new FileSnapshot("a", "1"));
        assertEquals(hashService.createCommitId("msg", "author", WHEN, null, null, ordered), hashService.createCommitId("msg", "author", WHEN, null, null, reversed));
    }

    @Test @DisplayName("commit id is a 64-character lowercase hex SHA-256")
    void commitIdIsSha256Hex() { String id = hashService.createCommitId("msg", "author", WHEN, null, null, MANIFEST); assertEquals(64, id.length()); assertTrue(id.matches("[0-9a-f]{64}")); }
}
