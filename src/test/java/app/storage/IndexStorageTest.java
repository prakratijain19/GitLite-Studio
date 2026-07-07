package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.model.FileSnapshot;
import app.model.Index;
import app.model.Repository;
import app.service.RepositoryService;

/**
 * Integration tests for {@link IndexStorage}: the empty-file-at-init case and a
 * full write/read round-trip.
 */
class IndexStorageTest {

    private final RepositoryService repositoryService = new RepositoryService();

    private Repository initRepo(Path root) {
        return repositoryService.initialize(root, "Tester", "master");
    }

    @Test
    @DisplayName("the empty index file produced at init yields an empty index")
    void emptyFileYieldsEmptyIndex(@TempDir Path root) {
        Repository repo = initRepo(root);
        Index index = new IndexStorage(repo.getMetadataPath()).readIndex();

        assertTrue(index.isEmpty());
    }

    @Test
    @DisplayName("an index round-trips through disk unchanged")
    void indexRoundTrips(@TempDir Path root) {
        Repository repo = initRepo(root);
        IndexStorage storage = new IndexStorage(repo.getMetadataPath());

        Index original = new Index();
        original.stage(new FileSnapshot("a.txt", "id1"));
        original.stage(new FileSnapshot("dir/b.txt", "id2"));
        storage.writeIndex(original);

        Index loaded = storage.readIndex();
        assertEquals(original, loaded);
    }
}
