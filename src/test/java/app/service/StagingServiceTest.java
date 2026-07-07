package app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.FileSnapshot;
import app.model.Index;
import app.model.Repository;
import app.storage.IndexStorage;
import app.storage.ObjectStorage;

/**
 * Integration tests for {@link StagingService}: the full {@code git add} flow
 * (object + index entry), deduplication and replacement semantics, and the
 * precondition guards.
 */
class StagingServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    private Path writeFile(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private Index loadIndex(Repository repo) {
        return new IndexStorage(repo.getMetadataPath()).readIndex();
    }

    @Test
    @DisplayName("staging writes the object and records a normalized index entry")
    void stageWritesObjectAndIndexEntry(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        Path file = writeFile(root, "src/Main.java", "hello");

        FileSnapshot snapshot = stagingService.stage(repo, file);

        assertEquals("src/Main.java", snapshot.path());
        assertTrue(new ObjectStorage(repo.getMetadataPath()).exists(snapshot.blobId()));

        Index index = loadIndex(repo);
        assertEquals(1, index.size());
        assertEquals(snapshot, index.get("src/Main.java").orElseThrow());
    }

    @Test
    @DisplayName("re-staging edited content updates the entry without a duplicate index row")
    void restagingEditedContentUpdatesEntry(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        Path file = writeFile(root, "a.txt", "v1");

        FileSnapshot first = stagingService.stage(repo, file);
        Files.writeString(file, "v2", StandardCharsets.UTF_8);
        FileSnapshot second = stagingService.stage(repo, file);

        assertNotEquals(first.blobId(), second.blobId());

        Index index = loadIndex(repo);
        assertEquals(1, index.size());
        assertEquals(second.blobId(), index.get("a.txt").orElseThrow().blobId());

        // Content-addressing keeps both object versions on disk.
        ObjectStorage objects = new ObjectStorage(repo.getMetadataPath());
        assertTrue(objects.exists(first.blobId()));
        assertTrue(objects.exists(second.blobId()));
    }

    @Test
    @DisplayName("two files with identical content share one object but get two index entries")
    void identicalContentDeduplicatesObject(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        Path x = writeFile(root, "x.txt", "same");
        Path y = writeFile(root, "y.txt", "same");

        FileSnapshot sx = stagingService.stage(repo, x);
        FileSnapshot sy = stagingService.stage(repo, y);

        assertEquals(sx.blobId(), sy.blobId(), "identical content must share a blob id");
        assertEquals(2, loadIndex(repo).size());
    }

    @Test
    @DisplayName("staging a missing file is rejected")
    void missingFileRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertThrows(IllegalArgumentException.class,
                () -> stagingService.stage(repo, root.resolve("nope.txt")));
    }

    @Test
    @DisplayName("staging a directory is rejected")
    void directoryRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        Path dir = root.resolve("subdir");
        Files.createDirectories(dir);
        assertThrows(IllegalArgumentException.class, () -> stagingService.stage(repo, dir));
    }

    @Test
    @DisplayName("staging a file outside the repository is rejected")
    void outsideRepoRejected(@TempDir Path root, @TempDir Path elsewhere) throws IOException {
        Repository repo = initRepo(root);
        Path outside = writeFile(elsewhere, "f.txt", "x");
        assertThrows(IllegalArgumentException.class, () -> stagingService.stage(repo, outside));
    }

    @Test
    @DisplayName("staging a file inside .gitlite is rejected")
    void gitliteFileRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        Path head = repo.getMetadataPath().resolve("HEAD");
        assertThrows(IllegalArgumentException.class, () -> stagingService.stage(repo, head));
    }
}
