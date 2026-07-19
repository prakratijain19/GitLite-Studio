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
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.Commit;
import app.model.Repository;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.IndexStorage;

/**
 * Integration tests for {@link CommitService}: the first (root) commit, chaining
 * to a parent, deterministic ids under a fixed clock, and the precondition guards.
 */
class CommitServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();
    private final BranchService branchService = new BranchService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(new DefaultStorageFactory(), new HashService(), clock);

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
    @DisplayName("the first commit is the root, births the branch, and leaves the index intact")
    void firstCommitIsRootAndBirthsBranch(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "src/Main.java", "hello");

        Commit commit = commitService.commit(repo, "initial commit");

        assertTrue(commit.isRoot());
        assertEquals("Tester", commit.author());
        assertEquals(Optional.of(commit.id()), branchService.tipOf(repo, "master"));
        assertTrue(new CommitStorage(repo.getMetadataPath()).exists(commit.id()));
        assertFalse(new IndexStorage(repo.getMetadataPath()).readIndex().isEmpty(),
                "the index must not be cleared by committing");
    }

    @Test
    @DisplayName("the second commit chains to the first as its parent")
    void secondCommitChainsToParent(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit first = commitService.commit(repo, "first");

        stageFile(repo, "b.txt", "two");
        Commit second = commitService.commit(repo, "second");

        assertEquals(Optional.of(first.id()), second.parent());
        assertEquals(Optional.of(second.id()), branchService.tipOf(repo, "master"));
    }

    @Test
    @DisplayName("identical staged content + fixed clock yield identical commit ids")
    void deterministicIdUnderFixedClock(@TempDir Path rootA, @TempDir Path rootB) throws IOException {
        Repository repoA = initRepo(rootA);
        Repository repoB = initRepo(rootB);
        stageFile(repoA, "f.txt", "same");
        stageFile(repoB, "f.txt", "same");

        Commit a = commitService.commit(repoA, "msg");
        Commit b = commitService.commit(repoB, "msg");

        assertEquals(a.id(), b.id());
    }

    @Test
    @DisplayName("committing with an empty index is rejected")
    void emptyIndexRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertThrows(NothingToCommitException.class, () -> commitService.commit(repo, "nope"));
    }

    @Test
    @DisplayName("committing with no changes since the last commit is rejected")
    void noChangesRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        commitService.commit(repo, "first");

        assertThrows(NothingToCommitException.class, () -> commitService.commit(repo, "again"));
    }

    @Test
    @DisplayName("a blank commit message is rejected")
    void blankMessageRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        assertThrows(IllegalArgumentException.class, () -> commitService.commit(repo, "  "));
    }

    @Test
    @DisplayName("commit() with MERGE_HEAD present creates a merge commit and clears MERGE_HEAD")
    void mergeHeadCreatesMergeCommit(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        stageFile(repo, "a.txt", "one");
        Commit parent = commitService.commit(repo, "first");

        // Simulate a merge in progress
        app.storage.FileStorage fileStorage = new DefaultStorageFactory().createFileStorage(repo.getMetadataPath());
        fileStorage.writeMergeHead("secondParent123");

        // We stage the resolved file. Even if it's identical to the parent,
        // it shouldn't be rejected because it's a merge commit.
        stageFile(repo, "a.txt", "one");
        Commit mergeCommit = commitService.commit(repo, "Merge branch 'feature'");

        assertTrue(mergeCommit.isMerge());
        assertEquals(Optional.of(parent.id()), mergeCommit.parent());
        assertEquals("secondParent123", mergeCommit.parents().get(1));
        
        // Ensure MERGE_HEAD was deleted
        assertFalse(fileStorage.hasMergeHead());
    }
}
