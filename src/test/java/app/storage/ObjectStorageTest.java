package app.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.Blob;
import app.model.Repository;
import app.service.HashService;
import app.service.RepositoryService;

/**
 * Integration tests for {@link ObjectStorage} against a real, temporary
 * repository: round-trip persistence, the sharded on-disk layout, idempotent
 * writes, and absent-object handling.
 */
class ObjectStorageTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final HashService hashService = new HashService();

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    private Blob blobOf(String text) {
        return hashService.createBlob(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a written blob reads back with identical content")
    void writeThenReadRoundTrips(@TempDir Path root) {
        Repository repo = initRepo(root);
        ObjectStorage storage = new ObjectStorage(repo.getMetadataPath());
        Blob blob = blobOf("data");

        storage.writeBlob(blob);
        Optional<Blob> loaded = storage.readBlob(blob.getId());

        assertTrue(loaded.isPresent());
        assertEquals(blob, loaded.get());
        assertArrayEquals("data".getBytes(StandardCharsets.UTF_8), loaded.get().getContent());
    }

    @Test
    @DisplayName("a blob is stored at objects/<first2>/<remaining>")
    void storesAtShardedPath(@TempDir Path root) {
        Repository repo = initRepo(root);
        ObjectStorage storage = new ObjectStorage(repo.getMetadataPath());
        Blob blob = blobOf("shard me");

        storage.writeBlob(blob);

        String id = blob.getId();
        Path expected = repo.getMetadataPath()
                .resolve(FileStorage.OBJECTS_DIR)
                .resolve(id.substring(0, 2))
                .resolve(id.substring(2));
        assertTrue(Files.isRegularFile(expected), "object must exist at sharded path");
    }

    @Test
    @DisplayName("writing the same blob twice is idempotent")
    void writeIsIdempotent(@TempDir Path root) {
        Repository repo = initRepo(root);
        ObjectStorage storage = new ObjectStorage(repo.getMetadataPath());
        Blob blob = blobOf("once");

        storage.writeBlob(blob);
        storage.writeBlob(blob); // must not throw or duplicate

        assertTrue(storage.exists(blob.getId()));
    }

    @Test
    @DisplayName("reading an unknown id returns empty")
    void readMissingReturnsEmpty(@TempDir Path root) {
        Repository repo = initRepo(root);
        ObjectStorage storage = new ObjectStorage(repo.getMetadataPath());

        assertFalse(storage.readBlob("deadbeef".repeat(8)).isPresent());
    }
}
