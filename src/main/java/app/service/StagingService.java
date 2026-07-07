package app.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import app.model.Blob;
import app.model.FileSnapshot;
import app.model.Index;
import app.model.Repository;
import app.storage.DefaultStorageFactory;
import app.storage.IndexStorage;
import app.storage.ObjectStorage;
import app.storage.StorageException;
import app.storage.StorageFactory;
import app.util.FileUtil;

/**
 * Stages working-tree files into the index — the equivalent of {@code git add}.
 *
 * <p>Staging a file reads its content, stores it as a content-addressed blob,
 * and records a {@link FileSnapshot} mapping the file's repository-relative path
 * to that blob in the index. This service owns the <em>policy</em> of staging and
 * delegates all disk work to the storage layer.
 *
 * <p>It is application-scoped and receives a {@link Repository} per call. Its
 * collaborators are injected: {@link HashService} directly, and the
 * repository-scoped {@link ObjectStorage} / {@link IndexStorage} through an
 * injected {@link StorageFactory}, so the service can be unit-tested with fakes.
 *
 * <p>Only single-file staging is supported; recursive staging of directories is
 * a future enhancement.
 */
public final class StagingService {

    private static final Logger LOGGER = Logger.getLogger(StagingService.class.getName());

    private final StorageFactory storageFactory;
    private final HashService hashService;

    /** Creates a service wired with the default storage factory and hash service. */
    public StagingService() {
        this(new DefaultStorageFactory(), new HashService());
    }

    /**
     * Creates a service with explicit collaborators. Intended for tests.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     * @param hashService    the service used to hash content into blobs.
     */
    public StagingService(StorageFactory storageFactory, HashService hashService) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.hashService = Objects.requireNonNull(hashService, "hashService must not be null");
    }

    /**
     * Stages a single working-tree file into the index.
     *
     * @param repository the repository whose index is updated.
     * @param file       the working-tree file to stage; must exist, be a regular
     *                   file, and live inside the repository (outside
     *                   {@code .gitlite}).
     * @return the {@link FileSnapshot} recorded in the index.
     * @throws IllegalArgumentException if the file is missing, a directory, or
     *                                  outside the repository working tree.
     * @throws StorageException         if the file or repository data cannot be
     *                                  read or written.
     */
    public FileSnapshot stage(Repository repository, Path file) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(file, "file must not be null");

        Path target = file.toAbsolutePath().normalize();
        if (!FileUtil.exists(target) || FileUtil.isDirectory(target)) {
            throw new IllegalArgumentException("Not a stageable file: " + file);
        }

        String relativePath = relativizeIntoRepository(repository, target);

        byte[] content;
        try {
            content = FileUtil.readBytes(target);
        } catch (IOException e) {
            throw new StorageException("Failed to read file to stage: " + target, e);
        }

        Path metadataDir = repository.getMetadataPath();
        Blob blob = hashService.createBlob(content);
        storageFactory.createObjectStorage(metadataDir).writeBlob(blob);

        IndexStorage indexStorage = storageFactory.createIndexStorage(metadataDir);
        Index index = indexStorage.readIndex();
        FileSnapshot snapshot = new FileSnapshot(relativePath, blob.getId());
        index.stage(snapshot);
        indexStorage.writeIndex(index);

        LOGGER.info(() -> "Staged " + snapshot.path() + " -> " + snapshot.blobId());
        return snapshot;
    }

    /**
     * Computes the repository-root-relative path of a working-tree file, rejecting
     * files that escape the repository or live inside its metadata directory.
     */
    private static String relativizeIntoRepository(Repository repository, Path target) {
        Path root = repository.getRootPath().toAbsolutePath().normalize();
        Path relative = root.relativize(target);

        if (relative.toString().isEmpty() || relative.startsWith("..")) {
            throw new IllegalArgumentException("File is outside the repository: " + target);
        }
        if (relative.startsWith(Repository.METADATA_DIR_NAME)) {
            throw new IllegalArgumentException("Cannot stage files inside "
                    + Repository.METADATA_DIR_NAME + ": " + target);
        }
        return relative.toString();
    }
}
