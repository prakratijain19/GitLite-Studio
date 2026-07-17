package app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Also tests ancestry queries ({@link HistoryService#isAncestor}) and
 * {@link HistoryService#historyFrom}.
 */
class HistoryServiceTest {

    private final DefaultStorageFactory storageFactory = new DefaultStorageFactory();
    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();
    private final BranchService branchService = new BranchService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(storageFactory, new HashService(), clock);
    private final CheckoutService checkoutService =
            new CheckoutService(storageFactory, new HashService());
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

    // ── isAncestor ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a commit is its own ancestor")
    void commitIsOwnAncestor(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit commit = commitService.commit(repo, "first");

        assertTrue(historyService.isAncestor(repo, commit.id(), commit.id()));
    }

    @Test
    @DisplayName("parent is an ancestor of its child")
    void parentIsAncestorOfChild(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit first = commitService.commit(repo, "first");
        stageFile(repo, "b.txt", "two");
        Commit second = commitService.commit(repo, "second");

        assertTrue(historyService.isAncestor(repo, first.id(), second.id()));
        assertFalse(historyService.isAncestor(repo, second.id(), first.id()));
    }

    @Test
    @DisplayName("grandparent is an ancestor of grandchild (transitive)")
    void ancestorIsTransitive(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit first = commitService.commit(repo, "first");
        stageFile(repo, "b.txt", "two");
        commitService.commit(repo, "second");
        stageFile(repo, "c.txt", "three");
        Commit third = commitService.commit(repo, "third");

        assertTrue(historyService.isAncestor(repo, first.id(), third.id()));
    }

    @Test
    @DisplayName("commits on different branches are not ancestors of each other")
    void divergedCommitsNotAncestors(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        commitService.commit(repo, "base");

        branchService.createBranch(repo, "feature");

        stageFile(repo, "b.txt", "master-only");
        Commit masterCommit = commitService.commit(repo, "master commit");

        checkoutService.checkout(repo, "feature");
        stageFile(repo, "c.txt", "feature-only");
        Commit featureCommit = commitService.commit(repo, "feature commit");

        assertFalse(historyService.isAncestor(repo, masterCommit.id(), featureCommit.id()));
        assertFalse(historyService.isAncestor(repo, featureCommit.id(), masterCommit.id()));
    }

    // ── historyFrom ─────────────────────────────────────────────────────

    @Test
    @DisplayName("historyFrom returns history starting from the given commit")
    void historyFromStartsAtGivenCommit(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit first = commitService.commit(repo, "first");
        stageFile(repo, "b.txt", "two");
        Commit second = commitService.commit(repo, "second");
        stageFile(repo, "c.txt", "three");
        commitService.commit(repo, "third");

        // Start from second — should get [second, first].
        List<Commit> history = historyService.historyFrom(repo, second.id());
        assertEquals(2, history.size());
        assertEquals(second, history.get(0));
        assertEquals(first, history.get(1));
    }

    @Test
    @DisplayName("historyFrom with blank startId is rejected")
    void historyFromBlankStartIdRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertThrows(IllegalArgumentException.class,
                () -> historyService.historyFrom(repo, " "));
    }
}

