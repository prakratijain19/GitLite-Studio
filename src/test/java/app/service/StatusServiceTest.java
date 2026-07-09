package app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.ChangeType;
import app.model.Repository;
import app.model.StatusReport;
import app.storage.DefaultStorageFactory;

/**
 * Integration tests for {@link StatusService}: the staged / unstaged / untracked
 * categories across the HEAD, index, and working-tree trees, plus exclusion of
 * the {@code .gitlite} metadata directory.
 */
class StatusServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(new DefaultStorageFactory(), new HashService(), clock);
    private final StatusService statusService = new StatusService();

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    private Path writeFile(Repository repo, String relative, String content) throws IOException {
        Path file = repo.getRootPath().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private void stageFile(Repository repo, String relative, String content) throws IOException {
        stagingService.stage(repo, writeFile(repo, relative, content));
    }

    @Test
    @DisplayName("a committed, unchanged tree is clean")
    void cleanAfterCommit(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "hello");
        commitService.commit(repo, "initial");

        assertTrue(statusService.status(repo).isClean());
    }

    @Test
    @DisplayName("a new unstaged file is reported as untracked only")
    void untrackedFile(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        writeFile(repo, "note.txt", "scratch");

        StatusReport status = statusService.status(repo);
        assertEquals(Set.of("note.txt"), status.untracked());
        assertTrue(status.stagedChanges().isEmpty());
        assertTrue(status.unstagedChanges().isEmpty());
        assertFalse(status.isClean());
    }

    @Test
    @DisplayName("a staged new file is a staged addition")
    void stagedAddition(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "hi");

        StatusReport status = statusService.status(repo);
        assertEquals(Map.of("a.txt", ChangeType.ADDED), status.stagedChanges());
        assertTrue(status.unstagedChanges().isEmpty());
        assertTrue(status.untracked().isEmpty());
    }

    @Test
    @DisplayName("a file staged then edited again shows in both staged and unstaged")
    void stagedThenReEdited(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "v1");
        commitService.commit(repo, "c1");
        stageFile(repo, "a.txt", "v2");           // staged modification vs HEAD
        writeFile(repo, "a.txt", "v3");           // further edit, not staged

        StatusReport status = statusService.status(repo);
        assertEquals(Map.of("a.txt", ChangeType.MODIFIED), status.stagedChanges());
        assertEquals(Map.of("a.txt", ChangeType.MODIFIED), status.unstagedChanges());
        assertTrue(status.untracked().isEmpty());
    }

    @Test
    @DisplayName("a committed file deleted from disk is an unstaged deletion")
    void deletedFromWorkingTree(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        Path file = writeFile(repo, "a.txt", "hello");
        stagingService.stage(repo, file);
        commitService.commit(repo, "initial");

        Files.delete(file);

        StatusReport status = statusService.status(repo);
        assertEquals(Map.of("a.txt", ChangeType.DELETED), status.unstagedChanges());
        assertTrue(status.stagedChanges().isEmpty());
        assertTrue(status.untracked().isEmpty());
    }

    @Test
    @DisplayName("the .gitlite metadata directory never appears in status")
    void gitliteExcluded(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        writeFile(repo, "x.txt", "content");

        StatusReport status = statusService.status(repo);
        assertEquals(Set.of("x.txt"), status.untracked(),
                "only the real working file should be untracked, never .gitlite entries");
    }
}
