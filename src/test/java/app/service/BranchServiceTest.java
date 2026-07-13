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

import app.model.Branch;
import app.model.Repository;
import app.storage.DefaultStorageFactory;
import app.storage.FileStorage;

/**
 * Integration tests for {@link BranchService}: HEAD resolution, the unborn-branch
 * state, tip advancement (birth), and rejection of a non-symbolic HEAD.
 */
class BranchServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final BranchService branchService = new BranchService();
    private final StagingService stagingService = new StagingService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final CommitService commitService =
            new CommitService(new DefaultStorageFactory(), new HashService(), clock);

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    /** Stages a file and commits it, returning the new commit id. */
    private String commit(Repository repo, String relative, String content, String message)
            throws IOException {
        Path file = repo.getRootPath().resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        stagingService.stage(repo, file);
        return commitService.commit(repo, message).id();
    }

    @Test
    @DisplayName("toHeadRef builds the git-style symbolic ref")
    void toHeadRefFormat() {
        assertEquals("ref: refs/heads/master", BranchService.toHeadRef("master"));
    }

    @Test
    @DisplayName("currentBranch resolves the branch HEAD points at")
    void currentBranchResolvesFromHead(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertEquals("master", branchService.currentBranch(repo));
    }

    @Test
    @DisplayName("a freshly initialized branch is unborn (no tip)")
    void unbornBranchHasNoTip(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertTrue(branchService.currentTip(repo).isEmpty());
    }

    @Test
    @DisplayName("advanceTip births the branch and sets its tip")
    void advanceTipBirthsBranch(@TempDir Path root) {
        Repository repo = initRepo(root);
        branchService.advanceTip(repo, "master", "commit123");

        assertTrue(new FileStorage(repo.getMetadataPath()).branchExists("master"));
        assertEquals(Optional.of("commit123"), branchService.currentTip(repo));
    }

    @Test
    @DisplayName("a non-symbolic HEAD is rejected")
    void nonSymbolicHeadRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        new FileStorage(repo.getMetadataPath()).writeHead("abc123");
        assertThrows(IllegalStateException.class, () -> branchService.currentBranch(repo));
    }

    // --- branch management ---

    @Test
    @DisplayName("createBranch points the new branch at the current commit")
    void createBranchPointsAtCurrentCommit(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        String tip = commit(repo, "a.txt", "hello", "initial");

        Branch feature = branchService.createBranch(repo, "feature");

        assertEquals(Optional.of(tip), feature.getTipCommitId());
        assertEquals(Optional.of(tip), branchService.tipOf(repo, "feature"));
    }

    @Test
    @DisplayName("listBranches includes created branches with their tips")
    void listBranchesReflectsCreated(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        String tip = commit(repo, "a.txt", "hello", "initial");
        branchService.createBranch(repo, "feature");

        List<Branch> branches = branchService.listBranches(repo);
        assertEquals(List.of(Branch.at("feature", tip), Branch.at("master", tip)), branches);
    }

    @Test
    @DisplayName("creating a duplicate branch is rejected")
    void duplicateBranchRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "hello", "initial");
        branchService.createBranch(repo, "feature");

        assertThrows(IllegalArgumentException.class,
                () -> branchService.createBranch(repo, "feature"));
    }

    @Test
    @DisplayName("creating a branch before the first commit is rejected")
    void createBeforeFirstCommitRejected(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertThrows(IllegalStateException.class,
                () -> branchService.createBranch(repo, "feature"));
    }

    @Test
    @DisplayName("deleting the current branch is rejected")
    void deleteCurrentBranchRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "hello", "initial");
        assertThrows(IllegalStateException.class,
                () -> branchService.deleteBranch(repo, "master"));
    }

    @Test
    @DisplayName("deleting a missing branch is rejected")
    void deleteMissingBranchRejected(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "hello", "initial");
        assertThrows(IllegalArgumentException.class,
                () -> branchService.deleteBranch(repo, "nope"));
    }

    @Test
    @DisplayName("deleting a branch removes it from the listing")
    void deleteRemovesFromListing(@TempDir Path root) throws IOException {
        Repository repo = initRepo(root);
        commit(repo, "a.txt", "hello", "initial");
        branchService.createBranch(repo, "feature");

        branchService.deleteBranch(repo, "feature");

        assertFalse(branchService.listBranches(repo).stream()
                .anyMatch(branch -> branch.getName().equals("feature")));
    }
}
