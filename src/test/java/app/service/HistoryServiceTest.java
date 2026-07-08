package app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.Commit;
import app.model.Repository;
import app.storage.DefaultStorageFactory;

/**
 * Integration tests for {@link HistoryService}: the empty (unborn) case, a single
 * commit, and correct newest-first ordering with parent linkage across a chain.
 */
class HistoryServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(new DefaultStorageFactory(), new HashService(), clock);
    private final HistoryService historyService = new HistoryService();

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    private void stageFile(Repository repo, String relative, String content) throws IOException {
        Path file = repo.getRootPath().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        stagingService.stage(repo, file);
    }

    @Test
    @DisplayName("a fresh repository has empty history")
    void emptyHistoryOnFreshRepo(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertTrue(historyService.history(repo).isEmpty());
    }

    @Test
    @DisplayName("a single commit yields a one-element history")
    void singleCommitHistory(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit commit = commitService.commit(repo, "first");

        List<Commit> history = historyService.history(repo);
        assertEquals(List.of(commit), history);
    }

    @Test
    @DisplayName("history is returned newest first with correct parent linkage")
    void threeCommitsNewestFirst(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit first = commitService.commit(repo, "first");
        stageFile(repo, "b.txt", "two");
        Commit second = commitService.commit(repo, "second");
        stageFile(repo, "c.txt", "three");
        Commit third = commitService.commit(repo, "third");

        List<Commit> history = historyService.history(repo);

        assertEquals(List.of(third, second, first), history);
        assertEquals(Optional.of(second.id()), third.parent());
        assertEquals(Optional.of(first.id()), second.parent());
        assertTrue(first.isRoot());
    }
}
