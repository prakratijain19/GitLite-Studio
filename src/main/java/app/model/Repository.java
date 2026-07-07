package app.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * In-memory representation of a GitLite repository.
 *
 * <p>This class is intentionally storage-agnostic: it holds no logic for
 * reading or writing files. Its only job is to represent the identity and
 * metadata of a repository once it has been located or initialized on disk.
 * Persistence is the responsibility of the storage layer (see
 * {@code storage.FileStorage} and {@code storage.JsonStorage}), orchestrated
 * by {@code service.RepositoryService}.
 *
 * <p>Instances are immutable and constructed via {@link Builder}, since the
 * number of fields is expected to grow (e.g. active branch, remote info)
 * without wanting a multi-argument constructor.
 */
public final class Repository {

    /** Name of the metadata directory GitLite creates inside a project folder. */
    public static final String METADATA_DIR_NAME = ".gitlite";

    private final Path rootPath;
    private final String defaultBranch;
    private final String userName;
    private final LocalDateTime createdAt;

    private Repository(Builder builder) {
        this.rootPath = builder.rootPath;
        this.defaultBranch = builder.defaultBranch;
        this.userName = builder.userName;
        this.createdAt = builder.createdAt;
    }

    /**
     * @return the absolute path to the project folder that contains
     *         {@code .gitlite} (this is the project root, not the metadata
     *         directory itself).
     */
    public Path getRootPath() {
        return rootPath;
    }

    /** @return path to this repository's {@code .gitlite} metadata directory. */
    public Path getMetadataPath() {
        return rootPath.resolve(METADATA_DIR_NAME);
    }

    /** @return the name of the branch this repository was initialized with. */
    public String getDefaultBranch() {
        return defaultBranch;
    }

    /** @return the identity recorded as the repository's user (used in commits later). */
    public String getUserName() {
        return userName;
    }

    /** @return the timestamp this repository was initialized. */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return a new {@link Builder} for constructing a {@link Repository}. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Repository)) return false;
        Repository that = (Repository) o;
        return rootPath.equals(that.rootPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootPath);
    }

    @Override
    public String toString() {
        return "Repository{rootPath=" + rootPath + ", defaultBranch=" + defaultBranch + "}";
    }

    /**
     * Builder for {@link Repository}. Validates required fields in
     * {@link #build()} rather than in the constructor, so invalid states
     * are impossible to construct.
     */
    public static final class Builder {
        private Path rootPath;
        private String defaultBranch = "master";
        private String userName;
        private LocalDateTime createdAt;

        private Builder() {
        }

        public Builder rootPath(Path rootPath) {
            this.rootPath = rootPath;
            return this;
        }

        public Builder defaultBranch(String defaultBranch) {
            this.defaultBranch = defaultBranch;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * @return a validated, immutable {@link Repository}.
         * @throws IllegalStateException if required fields are missing.
         */
        public Repository build() {
            if (rootPath == null) {
                throw new IllegalStateException("rootPath is required to build a Repository");
            }
            if (userName == null || userName.isBlank()) {
                throw new IllegalStateException("userName is required to build a Repository");
            }
            if (createdAt == null) {
                createdAt = LocalDateTime.now();
            }
            return new Repository(this);
        }
    }
}