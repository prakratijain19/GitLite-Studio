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

import app.model.Commit;
import app.model.Index;
import app.model.MergeResult;
import app.model.Repository;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.FileStorage;
import app.storage.IndexStorage;
import app.util.FileUtil;

/**
 * Integration tests for {@link MergeService}: fast-forward merge, three-way
 * merge (non-conflicting), conflict detection, already-up-to-date, dirty
 * tree guard, and various error cases.
 */
class MergeServiceTest {

    private final DefaultStorageFactory storageFactory = new DefaultStorageFactory();
    private final RepositoryService repositoryService = new RepositoryService();
    private final StagingService stagingService = new StagingService();
    private final BranchService branchService = new BranchService();
    private final HashService hashService = new HashService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(storageFactory, hashService, clock);
    private final CheckoutService checkoutService =
            new CheckoutService(storageFactory, hashService);
    private final MergeService mergeService =
            new MergeService(storageFactory, hashService, clock);

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

    private String readFile(Repository repo, String relative) throws IOException {
        return Files.readString(repo.getRootPath().resolve(relative), StandardCharsets.UTF_8);
    }

    // =================================================================
    // Fast-forward merge
    // =================================================================

    @Test
    @DisplayName("fast-forward merge advances master to feature's tip and rewrites working tree")
    void fastForwardMergeAdvancesBranchAndRewritesTree(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a on master");

        branchService.createBranch(repo, "feature");
        checkoutService.checkout(repo, "feature");
        commit(repo, "b.txt", "B", "add b on feature");
        String featureTip = branchService.tipOf(repo, "feature").orElseThrow();

        checkoutService.checkout(repo, "master");
        assertFalse(exists(repo, "b.txt"));

        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.FAST_FORWARD, result.type());
        assertEquals(featureTip, result.commitId());
        assertTrue(result.isSuccess());
        assertTrue(result.didMerge());
        assertTrue(exists(repo, "b.txt"));
        assertEquals("B", readFile(repo, "b.txt"));
        assertEquals(featureTip, branchService.tipOf(repo, "master").orElseThrow());
    }

    @Test
    @DisplayName("fast-forward merge updates the index to match the target commit")
    void fastForwardUpdatesIndex(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        checkoutService.checkout(repo, "feature");
        commit(repo, "b.txt", "B", "add b");

        checkoutService.checkout(repo, "master");
        mergeService.merge(repo, "feature");

        Index index = new IndexStorage(repo.getMetadataPath()).readIndex();
        assertEquals(2, index.size());
        assertTrue(index.contains("a.txt"));
        assertTrue(index.contains("b.txt"));
    }

    @Test
    @DisplayName("fast-forward merge across multiple commits works correctly")
    void fastForwardAcrossMultipleCommits(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        checkoutService.checkout(repo, "feature");
        commit(repo, "b.txt", "B1", "add b");
        commit(repo, "b.txt", "B2", "update b");
        commit(repo, "c.txt", "C", "add c");
        String featureTip = branchService.tipOf(repo, "feature").orElseThrow();

        checkoutService.checkout(repo, "master");
        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.FAST_FORWARD, result.type());
        assertEquals(featureTip, result.commitId());
        assertEquals("B2", readFile(repo, "b.txt"));
        assertTrue(exists(repo, "c.txt"));
    }

    @Test
    @DisplayName("untracked files are preserved during fast-forward merge")
    void untrackedFilesSurviveFastForward(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        checkoutService.checkout(repo, "feature");
        commit(repo, "b.txt", "B", "add b");

        checkoutService.checkout(repo, "master");
        writeFile(repo, "scratch.txt", "keep me");
        mergeService.merge(repo, "feature");

        assertTrue(exists(repo, "scratch.txt"));
    }

    // =================================================================
    // Already up-to-date
    // =================================================================

    @Test
    @DisplayName("merging an ancestor branch returns ALREADY_UP_TO_DATE")
    void mergingAncestorIsNoOp(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        commit(repo, "b.txt", "B", "add b on master");
        String masterTip = branchService.tipOf(repo, "master").orElseThrow();

        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.ALREADY_UP_TO_DATE, result.type());
        assertEquals(masterTip, result.commitId());
        assertFalse(result.didMerge());
    }

    @Test
    @DisplayName("merging a branch that is at the same commit returns ALREADY_UP_TO_DATE")
    void mergingSameCommitIsUpToDate(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");

        MergeResult result = mergeService.merge(repo, "feature");
        assertEquals(MergeResult.Type.ALREADY_UP_TO_DATE, result.type());
    }

    // =================================================================
    // Three-way merge (non-conflicting)
    // =================================================================

    @Test
    @DisplayName("three-way merge: each side adds a different file")
    void threeWayMergeBothAddDifferentFiles(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "base.txt", "BASE", "initial commit");
        branchService.createBranch(repo, "feature");

        // Master adds b.txt
        commit(repo, "b.txt", "B", "add b on master");

        // Feature adds c.txt
        checkoutService.checkout(repo, "feature");
        commit(repo, "c.txt", "C", "add c on feature");

        // Merge feature into master
        checkoutService.checkout(repo, "master");
        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.MERGED, result.type());
        assertTrue(result.isSuccess());
        assertTrue(result.didMerge());

        // Working tree should have all three files.
        assertTrue(exists(repo, "base.txt"));
        assertTrue(exists(repo, "b.txt"));
        assertTrue(exists(repo, "c.txt"));
        assertEquals("BASE", readFile(repo, "base.txt"));
        assertEquals("B", readFile(repo, "b.txt"));
        assertEquals("C", readFile(repo, "c.txt"));
    }

    @Test
    @DisplayName("three-way merge creates a merge commit with two parents")
    void threeWayMergeCreatesMergeCommit(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "base.txt", "BASE", "initial commit");
        branchService.createBranch(repo, "feature");

        commit(repo, "b.txt", "B", "add b on master");
        String masterTip = branchService.tipOf(repo, "master").orElseThrow();

        checkoutService.checkout(repo, "feature");
        commit(repo, "c.txt", "C", "add c on feature");
        String featureTip = branchService.tipOf(repo, "feature").orElseThrow();

        checkoutService.checkout(repo, "master");
        MergeResult result = mergeService.merge(repo, "feature");

        // Read the merge commit.
        CommitStorage commitStorage = new CommitStorage(repo.getMetadataPath());
        Commit mergeCommit = commitStorage.readCommit(result.commitId()).orElseThrow();

        assertTrue(mergeCommit.isMerge());
        assertEquals(masterTip, mergeCommit.parentId());
        assertEquals(featureTip, mergeCommit.secondParentId());
        assertEquals(2, mergeCommit.parents().size());
        assertEquals("Tester", mergeCommit.author());
    }

    @Test
    @DisplayName("three-way merge: one side modifies a file, the other doesn't")
    void threeWayMergeOneSideModifies(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "original", "initial commit");
        branchService.createBranch(repo, "feature");

        // Feature modifies a.txt
        checkoutService.checkout(repo, "feature");
        commit(repo, "a.txt", "modified by feature", "modify a on feature");

        // Master adds b.txt but does NOT touch a.txt
        checkoutService.checkout(repo, "master");
        commit(repo, "b.txt", "B", "add b on master");

        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.MERGED, result.type());
        // a.txt should have feature's version (they modified, we didn't).
        assertEquals("modified by feature", readFile(repo, "a.txt"));
        assertTrue(exists(repo, "b.txt"));
    }

    @Test
    @DisplayName("three-way merge: both sides make the same change")
    void threeWayMergeSameChange(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "original", "initial commit");
        branchService.createBranch(repo, "feature");

        // Both sides modify a.txt identically.
        commit(repo, "a.txt", "same change", "modify a on master");
        checkoutService.checkout(repo, "feature");
        commit(repo, "a.txt", "same change", "modify a on feature");

        checkoutService.checkout(repo, "master");
        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.MERGED, result.type());
        assertEquals("same change", readFile(repo, "a.txt"));
    }

    @Test
    @DisplayName("three-way merge: one side modifies, other adds new file — all files present")
    void threeWayMergeModifyAndAdd(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        commit(repo, "b.txt", "B", "add b");
        branchService.createBranch(repo, "feature");

        // Feature modifies a, master adds c
        checkoutService.checkout(repo, "feature");
        commit(repo, "a.txt", "A-mod", "modify a on feature");

        checkoutService.checkout(repo, "master");
        commit(repo, "c.txt", "C", "add c on master");

        MergeResult result = mergeService.merge(repo, "feature");

        assertEquals(MergeResult.Type.MERGED, result.type());
        assertTrue(exists(repo, "a.txt"));
        assertTrue(exists(repo, "b.txt"));
        assertTrue(exists(repo, "c.txt"));
        assertEquals("A-mod", readFile(repo, "a.txt"));
    }

    @Test
    @DisplayName("three-way merge updates the index to reflect the merged state")
    void threeWayMergeUpdatesIndex(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "base.txt", "BASE", "initial commit");
        branchService.createBranch(repo, "feature");

        commit(repo, "master-only.txt", "M", "master file");
        checkoutService.checkout(repo, "feature");
        commit(repo, "feature-only.txt", "F", "feature file");

        checkoutService.checkout(repo, "master");
        mergeService.merge(repo, "feature");

        Index index = new IndexStorage(repo.getMetadataPath()).readIndex();
        assertTrue(index.contains("base.txt"));
        assertTrue(index.contains("master-only.txt"));
        assertTrue(index.contains("feature-only.txt"));
    }

    @Test
    @DisplayName("untracked files are preserved during three-way merge")
    void untrackedFilesSurviveThreeWayMerge(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "base.txt", "BASE", "initial commit");
        branchService.createBranch(repo, "feature");

        commit(repo, "b.txt", "B", "master commit");
        checkoutService.checkout(repo, "feature");
        commit(repo, "c.txt", "C", "feature commit");

        checkoutService.checkout(repo, "master");
        writeFile(repo, "scratch.txt", "keep me");

        mergeService.merge(repo, "feature");
        assertTrue(exists(repo, "scratch.txt"));
    }

    // =================================================================
    // Conflict detection
    // =================================================================

    @Test
    @DisplayName("three-way merge with conflicts throws MergeConflictException, writes markers, and sets MERGE_HEAD")
    void conflictingChangesThrow(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "original\n", "initial commit");
        branchService.createBranch(repo, "feature");

        // Both sides modify a.txt differently.
        commit(repo, "a.txt", "master version\n", "modify a on master");
        checkoutService.checkout(repo, "feature");
        commit(repo, "a.txt", "feature version\n", "modify a on feature");

        checkoutService.checkout(repo, "master");
        
        MergeConflictException ex = assertThrows(MergeConflictException.class,
                () -> mergeService.merge(repo, "feature"));
        
        assertEquals(MergeResult.Type.CONFLICTED, ex.getMergeResult().type());
        assertEquals(1, ex.getConflicts().size());
        assertEquals("a.txt", ex.getConflicts().get(0).path());

        // Verify conflict markers are in the working tree file
        String content = FileUtil.readString(root.resolve("a.txt"));
        assertTrue(content.contains("<<<<<<< master"));
        assertTrue(content.contains("master version"));
        assertTrue(content.contains("======="));
        assertTrue(content.contains("feature version"));
        assertTrue(content.contains(">>>>>>> feature"));
        
        // Verify MERGE_HEAD exists
        FileStorage fileStorage = new app.storage.DefaultStorageFactory().createFileStorage(repo.getMetadataPath());
        assertTrue(fileStorage.hasMergeHead());
    }

    @Test
    @DisplayName("aborting a merge clears MERGE_HEAD and restores working tree")
    void abortMerge(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "original\n", "initial commit");
        branchService.createBranch(repo, "feature");
        commit(repo, "a.txt", "master version\n", "modify a on master");
        checkoutService.checkout(repo, "feature");
        commit(repo, "a.txt", "feature version\n", "modify a on feature");
        checkoutService.checkout(repo, "master");

        // Cause conflict
        assertThrows(MergeConflictException.class, () -> mergeService.merge(repo, "feature"));
        
        // Now abort
        mergeService.abort(repo);
        
        // Verify MERGE_HEAD is gone
        FileStorage fileStorage = new app.storage.DefaultStorageFactory().createFileStorage(repo.getMetadataPath());
        assertFalse(fileStorage.hasMergeHead());
        
        // Verify a.txt is restored to master version
        assertEquals("master version\n", FileUtil.readString(root.resolve("a.txt")));
    }

    // =================================================================
    // Error cases
    // =================================================================

    @Test
    @DisplayName("merging a branch into itself is rejected")
    void mergeSelfRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        assertThrows(IllegalArgumentException.class,
                () -> mergeService.merge(repo, "master"));
    }

    @Test
    @DisplayName("merging a non-existent branch is rejected")
    void missingBranchRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        assertThrows(IllegalArgumentException.class,
                () -> mergeService.merge(repo, "nonexistent"));
    }

    @Test
    @DisplayName("merging before any commits is rejected")
    void mergeBeforeCommitsRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertThrows(IllegalStateException.class,
                () -> mergeService.merge(repo, "feature"));
    }

    @Test
    @DisplayName("merging with uncommitted changes is refused")
    void dirtyTreeRefused(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        branchService.createBranch(repo, "feature");
        checkoutService.checkout(repo, "feature");
        commit(repo, "b.txt", "B", "add b");

        checkoutService.checkout(repo, "master");
        writeFile(repo, "a.txt", "modified but not staged");

        assertThrows(IllegalStateException.class,
                () -> mergeService.merge(repo, "feature"));
        assertEquals("master", branchService.currentBranch(repo));
    }

    @Test
    @DisplayName("merging with blank branch name is rejected")
    void blankBranchNameRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "A", "add a");
        assertThrows(IllegalArgumentException.class,
                () -> mergeService.merge(repo, "  "));
    }

    @Test
    @DisplayName("dirty tree is also refused for diverged branches (three-way)")
    void dirtyTreeRefusedForThreeWay(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "base.txt", "BASE", "initial");
        branchService.createBranch(repo, "feature");

        commit(repo, "b.txt", "B", "master commit");
        checkoutService.checkout(repo, "feature");
        commit(repo, "c.txt", "C", "feature commit");

        checkoutService.checkout(repo, "master");
        writeFile(repo, "base.txt", "dirty");

        assertThrows(IllegalStateException.class,
                () -> mergeService.merge(repo, "feature"));
    }
}
