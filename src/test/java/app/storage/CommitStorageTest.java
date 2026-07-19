package app.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import app.model.Commit;
import app.model.FileSnapshot;
import app.model.Repository;
import app.service.RepositoryService;

class CommitStorageTest {
    private final RepositoryService repositoryService = new RepositoryService();
    private Repository initRepo(Path root) { return repositoryService.initialize(root, "Tester", "master"); }

    @Test @DisplayName("a written commit reads back equal, timestamp included")
    void commitRoundTrips(@TempDir Path root) {
        Repository repo = initRepo(root);
        CommitStorage storage = new CommitStorage(repo.getMetadataPath());
        Commit commit = new Commit("cid", "initial commit", "Tester", Instant.parse("2026-01-01T00:00:00Z"), null, null, List.of(new FileSnapshot("a.txt", "id1")));
        storage.writeCommit(commit);
        Optional<Commit> loaded = storage.readCommit("cid");
        assertTrue(loaded.isPresent());
        assertEquals(commit, loaded.get());
    }

    @Test @DisplayName("reading an unknown commit id returns empty")
    void readMissingReturnsEmpty(@TempDir Path root) {
        Repository repo = initRepo(root);
        assertFalse(new CommitStorage(repo.getMetadataPath()).readCommit("nope").isPresent());
    }
}
