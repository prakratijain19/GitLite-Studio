package app.service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import app.model.Repository;
import app.model.RepositoryConfig;
import app.storage.DefaultStorageFactory;
import app.storage.FileStorage;
import app.storage.JsonStorage;
import app.storage.StorageFactory;

/**
 * Orchestrates repository-level operations, beginning with initialization.
 *
 * <p>This service owns the <em>policy</em> and <em>sequence</em> of creating a
 * repository; it delegates every actual disk operation to the storage layer. It
 * is application-scoped (one instance may initialize many repositories) and
 * therefore obtains repository-scoped storage through an injected
 * {@link StorageFactory} rather than constructing storage directly. This keeps
 * the service unit-testable: a test can inject a factory returning fakes to
 * verify orchestration without touching the filesystem.
 */
public final class RepositoryService {

    private static final Logger LOGGER = Logger.getLogger(RepositoryService.class.getName());

    private final StorageFactory storageFactory;

    /**
     * Creates a service wired with the default, filesystem-backed storage
     * factory. Intended for production use.
     */
    public RepositoryService() {
        this(new DefaultStorageFactory());
    }

    /**
     * Creates a service with an explicit storage factory. Intended for tests,
     * which may supply fakes.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     */
    public RepositoryService(StorageFactory storageFactory) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
    }

    /**
     * Initializes a new GitLite repository at {@code rootPath}, creating the
     * {@code .gitlite} skeleton, {@code HEAD}, and {@code config} on disk.
     *
     * <p>The operation refuses to run if a repository already exists at the
     * location, guarding against accidental re-initialization before any files
     * are written.
     *
     * @param rootPath      the project folder that will contain {@code .gitlite}.
     * @param userName      the identity to record for the repository (non-blank).
     * @param defaultBranch the name of the initial branch (non-blank).
     * @return the in-memory {@link Repository} describing what was created.
     * @throws IllegalArgumentException            if any argument is null or blank.
     * @throws RepositoryAlreadyExistsException    if a repository already exists
     *                                             at {@code rootPath}.
     * @throws app.storage.StorageException        if the skeleton cannot be
     *                                             written to disk.
     */
    public Repository initialize(Path rootPath, String userName, String defaultBranch) {
        validate(rootPath, userName, defaultBranch);

        Repository repository = Repository.builder()
                .rootPath(rootPath)
                .userName(userName)
                .defaultBranch(defaultBranch)
                .build();

        Path metadataDir = repository.getMetadataPath();
        FileStorage fileStorage = storageFactory.createFileStorage(metadataDir);

        if (fileStorage.isInitialized()) {
            throw new RepositoryAlreadyExistsException(rootPath);
        }

        // Structure first, then pointers, then config — nothing points at data
        // that does not yet exist.
        fileStorage.createSkeleton();
        fileStorage.createIndex();
        fileStorage.writeHead(BranchService.toHeadRef(defaultBranch));

        RepositoryConfig config = new RepositoryConfig(
                defaultBranch, repository.getCreatedAt(), userName);
        JsonStorage jsonStorage = storageFactory.createJsonStorage(metadataDir);
        jsonStorage.writeConfig(config);

        LOGGER.info(() -> "Initialized GitLite repository at " + rootPath
                + " (branch '" + defaultBranch + "', user '" + userName + "')");
        return repository;
    }

    /**
     * @param rootPath the project folder to test.
     * @return {@code true} if {@code rootPath} contains an initialized GitLite
     *         repository.
     */
    public boolean isRepository(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath must not be null");
        return storageFactory.createFileStorage(metadataDir(rootPath)).isInitialized();
    }

    /**
     * Opens an existing repository at {@code rootPath}, reconstructing its
     * in-memory {@link Repository} from the stored {@code config}.
     *
     * @param rootPath the project folder containing {@code .gitlite}.
     * @return the loaded {@link Repository}.
     * @throws IllegalArgumentException if {@code rootPath} is not an initialized
     *                                  repository.
     * @throws app.storage.StorageException if the config cannot be read.
     */
    public Repository open(Path rootPath) {
        Objects.requireNonNull(rootPath, "rootPath must not be null");
        Path metadataDir = metadataDir(rootPath);
        FileStorage fileStorage = storageFactory.createFileStorage(metadataDir);
        if (!fileStorage.isInitialized()) {
            throw new IllegalArgumentException("Not a GitLite repository: " + rootPath);
        }
        RepositoryConfig config = storageFactory.createJsonStorage(metadataDir).readConfig();
        return Repository.builder()
                .rootPath(rootPath)
                .defaultBranch(config.defaultBranch())
                .userName(config.userName())
                .createdAt(config.createdAt())
                .build();
    }

    private static Path metadataDir(Path rootPath) {
        return rootPath.resolve(Repository.METADATA_DIR_NAME);
    }

    private static void validate(Path rootPath, String userName, String defaultBranch) {
        if (rootPath == null) {
            throw new IllegalArgumentException("rootPath must not be null");
        }
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName must not be null or blank");
        }
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new IllegalArgumentException("defaultBranch must not be null or blank");
        }
    }
}
