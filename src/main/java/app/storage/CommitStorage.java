package app.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;

import app.model.Commit;
import app.util.FileUtil;
import app.util.JsonUtil;

/**
 * Repository-scoped persistence for commits, stored as JSON under
 * {@code .gitlite/commits/}.
 *
 * <p>Each commit is written to its own file named by its content-addressed id
 * ({@code commits/<id>}). Unlike blob objects, commits are <strong>not</strong>
 * sharded — the on-disk contract keeps one flat file per commit. Like
 * {@link JsonStorage} and {@link IndexStorage}, this class combines
 * {@link JsonUtil} with {@link FileUtil} and translates low-level failures into
 * {@link StorageException}. Because {@link Commit} is a record, it binds directly
 * to and from JSON with no intermediate DTO.
 */
public final class CommitStorage {

    private final Path commitsDir;

    /**
     * @param metadataDir the {@code .gitlite} directory this instance manages
     *                    (typically {@code Repository.getMetadataPath()}).
     */
    public CommitStorage(Path metadataDir) {
        Objects.requireNonNull(metadataDir, "metadataDir must not be null");
        this.commitsDir = metadataDir.resolve(FileStorage.COMMITS_DIR);
    }

    /**
     * Serializes and writes a commit to {@code commits/<id>}.
     *
     * @param commit the commit to persist (non-null).
     * @throws StorageException if the commit cannot be serialized or written.
     */
    public void writeCommit(Commit commit) {
        Objects.requireNonNull(commit, "commit must not be null");
        Path path = commitPath(commit.id());
        try {
            FileUtil.ensureDirectory(commitsDir);
            String json = JsonUtil.toJson(commit);
            FileUtil.writeString(path, json);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize commit " + commit.id(), e);
        } catch (IOException e) {
            throw new StorageException("Failed to write commit at " + path, e);
        }
    }

    /**
     * Loads the commit with the given id, if present.
     *
     * @param id the commit id (non-blank).
     * @return the commit, or an empty optional if no such commit exists.
     * @throws StorageException if the commit exists but cannot be read or parsed.
     */
    public Optional<Commit> readCommit(String id) {
        Path path = commitPath(id);
        if (!FileUtil.exists(path)) {
            return Optional.empty();
        }
        try {
            String json = FileUtil.readString(path);
            return Optional.of(JsonUtil.fromJson(json, Commit.class));
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to parse commit at " + path, e);
        } catch (IOException e) {
            throw new StorageException("Failed to read commit at " + path, e);
        }
    }

    /**
     * @param id the commit id (non-blank).
     * @return {@code true} if a commit with this id is stored.
     */
    public boolean exists(String id) {
        return FileUtil.exists(commitPath(id));
    }

    private Path commitPath(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("commit id is required and must not be blank");
        }
        return commitsDir.resolve(id);
    }
}
