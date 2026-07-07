package app.storage;

import java.nio.file.Path;

/**
 * Creates repository-scoped storage objects for a given {@code .gitlite}
 * directory.
 *
 * <p>This factory exists to bridge a lifecycle mismatch. {@link FileStorage} and
 * {@link JsonStorage} are <em>repository-scoped</em>: each is bound at
 * construction to one metadata directory. The services that use them (for
 * example {@code service.RepositoryService}) are <em>application-scoped</em> and
 * operate on many repositories over their lifetime, so they cannot receive a
 * concrete storage instance at construction time. Injecting a
 * {@code StorageFactory} instead lets an application-scoped collaborator mint the
 * repository-scoped storage it needs, on demand, once a path is known.
 *
 * <p>Depending on this interface (rather than constructing storage with
 * {@code new}) keeps callers testable: a test can supply a factory that returns
 * fakes, exercising a service's policy without touching the real filesystem.
 */
public interface StorageFactory {

    /**
     * @param metadataDir the {@code .gitlite} directory the storage will manage.
     * @return a {@link FileStorage} bound to {@code metadataDir}.
     */
    FileStorage createFileStorage(Path metadataDir);

    /**
     * @param metadataDir the {@code .gitlite} directory the storage will manage.
     * @return a {@link JsonStorage} bound to {@code metadataDir}.
     */
    JsonStorage createJsonStorage(Path metadataDir);
}
