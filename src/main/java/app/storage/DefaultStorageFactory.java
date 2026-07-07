package app.storage;

import java.nio.file.Path;

/**
 * Default {@link StorageFactory} that constructs the real, filesystem-backed
 * storage implementations.
 *
 * <p>This is the implementation used in production wiring. Tests may substitute
 * an alternative factory that returns fakes or stubs to exercise a service's
 * behaviour without touching the disk.
 */
public final class DefaultStorageFactory implements StorageFactory {

    @Override
    public FileStorage createFileStorage(Path metadataDir) {
        return new FileStorage(metadataDir);
    }

    @Override
    public JsonStorage createJsonStorage(Path metadataDir) {
        return new JsonStorage(metadataDir);
    }

    @Override
    public ObjectStorage createObjectStorage(Path metadataDir) {
        return new ObjectStorage(metadataDir);
    }

    @Override
    public IndexStorage createIndexStorage(Path metadataDir) {
        return new IndexStorage(metadataDir);
    }
}
