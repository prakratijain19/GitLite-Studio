package app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.Repository;
import app.model.RepositoryConfig;
import app.storage.FileStorage;
import app.storage.JsonStorage;

/**
 * Integration tests for {@link RepositoryService#initialize} that exercise the
 * full stack (service → factory → storage → disk) against a real, temporary
 * filesystem provided by JUnit's {@link TempDir}.
 *
 * <p>These tests are the acceptance criteria for Milestone 1: they assert that
 * initialization produces exactly the agreed on-disk contract for {@code .gitlite}.
 */
class RepositoryServiceTest {

    private static final String USER = "Ada Lovelace";
    private static final String BRANCH = "master";
    private static final String EXPECTED_HEAD = "ref: refs/heads/" + BRANCH;

    private final RepositoryService service = new RepositoryService();

    @Test
    @DisplayName("initialize() creates the .gitlite skeleton directories")
    void initializeCreatesSkeleton(@TempDir Path root) {
        Repository repo = service.initialize(root, USER, BRANCH);
        Path meta = repo.getMetadataPath();

        assertTrue(Files.isDirectory(meta), ".gitlite must exist");
        assertTrue(Files.isDirectory(meta.resolve(FileStorage.OBJECTS_DIR)), "objects/ must exist");
        assertTrue(Files.isDirectory(meta.resolve(FileStorage.COMMITS_DIR)), "commits/ must exist");
        assertTrue(Files.isDirectory(meta.resolve(FileStorage.BRANCHES_DIR)), "branches/ must exist");
    }

    @Test
    @DisplayName("initialize() leaves branches/ empty (unborn default branch)")
    void initializeLeavesBranchesEmpty(@TempDir Path root) throws IOException {
        Repository repo = service.initialize(root, USER, BRANCH);
        Path branches = repo.getMetadataPath().resolve(FileStorage.BRANCHES_DIR);

        try (Stream<Path> entries = Files.list(branches)) {
            assertEquals(0, entries.count(),
                    "no branch file should exist until the first commit");
        }
    }

    @Test
    @DisplayName("initialize() creates an empty index file")
    void initializeCreatesEmptyIndex(@TempDir Path root) throws IOException {
        Repository repo = service.initialize(root, USER, BRANCH);
        Path index = repo.getMetadataPath().resolve(FileStorage.INDEX_FILE);

        assertTrue(Files.isRegularFile(index), "index must exist");
        assertEquals(0L, Files.size(index), "index must be empty at init");
    }

    @Test
    @DisplayName("initialize() writes a git-style HEAD ref")
    void initializeWritesHeadRef(@TempDir Path root) {
        Repository repo = service.initialize(root, USER, BRANCH);

        String head = new FileStorage(repo.getMetadataPath()).readHead();
        assertEquals(EXPECTED_HEAD, head, "HEAD must point at the default branch ref");
    }

    @Test
    @DisplayName("initialize() writes a config that round-trips back to the same values")
    void initializeWritesRoundTrippableConfig(@TempDir Path root) {
        Repository repo = service.initialize(root, USER, BRANCH);

        RepositoryConfig config = new JsonStorage(repo.getMetadataPath()).readConfig();
        assertEquals(BRANCH, config.defaultBranch());
        assertEquals(USER, config.userName());
        assertEquals(repo.getCreatedAt(), config.createdAt(),
                "createdAt must survive JSON serialization exactly");
    }

    @Test
    @DisplayName("initialize() returns a Repository describing what was created")
    void initializeReturnsRepository(@TempDir Path root) {
        Repository repo = service.initialize(root, USER, BRANCH);

        assertEquals(root, repo.getRootPath());
        assertEquals(USER, repo.getUserName());
        assertEquals(BRANCH, repo.getDefaultBranch());
    }

    @Test
    @DisplayName("initialize() on an already-initialized location is rejected")
    void initializeRejectsReinit(@TempDir Path root) {
        service.initialize(root, USER, BRANCH);

        assertThrows(RepositoryAlreadyExistsException.class,
                () -> service.initialize(root, USER, BRANCH));
    }

    @Test
    @DisplayName("initialize() rejects a blank user name")
    void initializeRejectsBlankUser(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> service.initialize(root, "  ", BRANCH));
    }

    @Test
    @DisplayName("initialize() rejects a blank default branch")
    void initializeRejectsBlankBranch(@TempDir Path root) {
        assertThrows(IllegalArgumentException.class,
                () -> service.initialize(root, USER, ""));
    }

    @Test
    @DisplayName("initialize() rejects a null root path")
    void initializeRejectsNullRoot() {
        assertThrows(IllegalArgumentException.class,
                () -> service.initialize(null, USER, BRANCH));
    }
}
