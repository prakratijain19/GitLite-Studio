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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.Index;
import app.model.Repository;
import app.storage.DefaultStorageFactory;
import app.storage.IndexStorage;

/**
 * Integration tests for {@link CheckoutService}: switching rewrites the working
 * tree, index, and HEAD; refuses on a dirty tree; rejects missing branches; and
 * preserves untracked files.
 */
class CheckoutServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();
    private final BranchService branchService = new BranchService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(new DefaultStorageFactory(), new HashService(), clock);
    private final CheckoutService checkoutService =
            new CheckoutService(new DefaultStorageFactory(), new HashService());

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    private Path writeFile(Repository repo, String relative, String content) throws IOException {
        Path file = repo.getRootPath().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private void commit(Repository repo, String relative, String content, String message) throws IOException {
        stagingService.stage(repo, writeFile(repo, relative, content));
        commitService.commit(repo, message);
    }

    private boolean exists(Repository repo, String relative) {
        return Files.exists(repo.getRootPath().resolve(relative));
    }

    /** Builds: master has a.txt; feature has a.txt + b.txt. Leaves HEAD on feature. */
    private Repository twoBranchRepo(Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        checkoutService.checkout(repo, "feature");
        commit(repo, "b.txt", "B", "add b on feature");
        return repo;
    }

    @Test
    @DisplayName("switching branches adds and removes working-tree files to match the target")
    void switchRewritesWorkingTree(@TempDir Path root) throws IOException {
        Repository repo = twoBranchRepo(root);

        checkoutService.checkout(repo, "master");
        assertTrue(exists(repo, "a.txt"), "a.txt is on master");
        assertFalse(exists(repo, "b.txt"), "b.txt is not on master");

        checkoutService.checkout(repo, "feature");
        assertTrue(exists(repo, "a.txt"));
        assertTrue(exists(repo, "b.txt"), "b.txt returns on feature");
    }

    @Test
    @DisplayName("switching updates HEAD and the index")
    void switchUpdatesHeadAndIndex(@TempDir Path root) throws IOException {
        Repository repo = twoBranchRepo(root);

        checkoutService.checkout(repo, "master");
        assertEquals("master", branchService.currentBranch(repo));

        Index index = new IndexStorage(repo.getMetadataPath()).readIndex();
        assertEquals(1, index.size());
        assertTrue(index.contains("a.txt"));
        assertFalse(index.contains("b.txt"));
    }

    @Test
    @DisplayName("switching is refused when the working tree has uncommitted changes")
    void dirtyTreeRefused(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        writeFile(repo, "a.txt", "modified but not staged");

        assertThrows(IllegalStateException.class, () -> checkoutService.checkout(repo, "feature"));
        assertEquals("master", branchService.currentBranch(repo), "HEAD must be unchanged");
    }

    @Test
    @DisplayName("switching to a missing branch is rejected")
    void missingBranchRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        assertThrows(IllegalArgumentException.class, () -> checkoutService.checkout(repo, "nope"));
    }

    @Test
    @DisplayName("untracked files survive a switch")
    void untrackedFilesSurvive(@TempDir Path root) throws IOException {
        Repository repo = twoBranchRepo(root);
        writeFile(repo, "scratch.txt", "keep me");   // untracked

        checkoutService.checkout(repo, "master");

        assertTrue(exists(repo, "scratch.txt"), "untracked files must not be deleted by a switch");
    }
}
