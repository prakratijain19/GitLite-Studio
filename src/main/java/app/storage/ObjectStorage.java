package app.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import app.model.Blob;
import app.util.FileUtil;

/**
 * Content-addressed storage for blob objects under {@code .gitlite/objects/}.
 *
 * <p>Each instance is repository-scoped, constructed from the {@code .gitlite}
 * metadata directory. Objects are laid out in <strong>sharded</strong> form: an
 * object with id {@code abcdef...} is stored at {@code objects/ab/cdef...}, where
 * the first two hex characters name a subdirectory. Sharding mirrors Git and
 * keeps any single directory from accumulating a very large number of files.
 *
 * <p>Consistent with the storage-layer contract, this class holds <em>no object
 * format knowledge</em>: it stores a blob's raw content bytes at the path named
 * by the blob's id, and reads them back. Deriving that id from the canonical
 * object form is the concern of {@code service.HashService}; the header used for
 * hashing never reaches disk. Low-level {@link IOException}s are translated into
 * {@link StorageException}.
 */
public final class ObjectStorage {

    /** Number of leading hex characters used as the shard subdirectory name. */
    private static final int SHARD_PREFIX_LENGTH = 2;

    private final Path objectsDir;

    /**
     * @param metadataDir the {@code .gitlite} directory this instance manages
     *                    (typically {@code Repository.getMetadataPath()}).
     */
    public ObjectStorage(Path metadataDir) {
        Objects.requireNonNull(metadataDir, "metadataDir must not be null");
        this.objectsDir = metadataDir.resolve(FileStorage.OBJECTS_DIR);
    }

    /**
     * Persists a blob's content, addressed by its id. The operation is
     * idempotent: if an object with the same id already exists its content is,
     * by content-addressing, identical, so the write is skipped.
     *
     * @param blob the blob to store (non-null).
     * @throws StorageException if the object cannot be written.
     */
    public void writeBlob(Blob blob) {
        Objects.requireNonNull(blob, "blob must not be null");
        Path objectPath = objectPath(blob.getId());
        if (FileUtil.exists(objectPath)) {
            return;
        }
        try {
            FileUtil.ensureDirectory(objectPath.getParent());
            FileUtil.writeBytes(objectPath, blob.getContent());
        } catch (IOException e) {
            throw new StorageException("Failed to write object " + blob.getId(), e);
        }
    }

    /**
     * Loads the blob with the given id, if present.
     *
     * @param id the content-addressed id (non-blank).
     * @return the blob, or an empty optional if no such object exists.
     * @throws StorageException if the object exists but cannot be read.
     */
    public Optional<Blob> readBlob(String id) {
        Path objectPath = objectPath(id);
        if (!FileUtil.exists(objectPath)) {
            return Optional.empty();
        }
        try {
            byte[] content = FileUtil.readBytes(objectPath);
            return Optional.of(Blob.of(id, content));
        } catch (IOException e) {
            throw new StorageException("Failed to read object " + id, e);
        }
    }

    /**
     * @param id the content-addressed id (non-blank).
     * @return {@code true} if an object with this id is stored.
     */
    public boolean exists(String id) {
        return FileUtil.exists(objectPath(id));
    }

    /**
     * Resolves the sharded on-disk path for an object id:
     * {@code objects/<first2>/<remaining>}.
     *
     * @param id the object id (non-blank, longer than the shard prefix).
     * @return the path at which the object is (or would be) stored.
     */
    private Path objectPath(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("object id is required and must not be blank");
        }
        if (id.length() <= SHARD_PREFIX_LENGTH) {
            throw new IllegalArgumentException("object id is too short to shard: " + id);
        }
        String shard = id.substring(0, SHARD_PREFIX_LENGTH);
        String remainder = id.substring(SHARD_PREFIX_LENGTH);
        return objectsDir.resolve(shard).resolve(remainder);
    }
}
