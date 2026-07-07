package app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.Repository;
import app.storage.FileStorage;

/**
 * Integration tests for {@link BranchService}: HEAD resolution, the unborn-branch
 * state, tip advancement (birth), and rejection of a non-symbolic HEAD.
 */
class BranchServiceTest {

    private final RepositoryService repositoryService = new RepositoryService();
    private final BranchService branchService = new BranchService();

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
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
}
