package app.model;

/**
 * A single staging-area entry: the mapping of one working-tree path to the blob
 * currently staged for it.
 *
 * <p>A {@code FileSnapshot} records that, as of staging, the file at {@link #path()}
 * holds the content identified by {@link #blobId()}. It carries the location; the
 * {@link Blob} carries the content. A collection of these forms the {@code Index}.
 *
 * <p>It is a {@code record} because it is <strong>persisted</strong> as part of
 * the index JSON: this gives immutable value semantics and clean two-way Jackson
 * binding. Equality is by both components, which is correct — a snapshot is fully
 * described by which path maps to which blob. Enforcing a single active entry per
 * path is the responsibility of the {@code Index}, not this type.
 *
 * <p><strong>Path contract.</strong> The path is expected to be
 * <em>repository-root-relative</em> — producing that relative path is the
 * service layer's responsibility, since only it knows the repository root. This
 * type guarantees the complementary invariant: the stored path is normalized to
 * forward slashes, so an index written on one platform reads identically on
 * another.
 *
 * @param path   the repository-relative path, normalized to forward slashes.
 * @param blobId the content-addressed id of the staged blob.
 */
public record FileSnapshot(String path, String blobId) {

    public FileSnapshot {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("FileSnapshot path is required and must not be blank");
        }
        if (blobId == null || blobId.isBlank()) {
            throw new IllegalArgumentException("FileSnapshot blobId is required and must not be blank");
        }
        // Normalize to POSIX-style separators for a platform-independent index.
        path = normalizePath(path);
    }

    /**
     * Normalizes a path to the forward-slash form used for index keys, making
     * lookups consistent across platforms. This is the single normalization
     * function shared by this record's constructor and {@code Index}'s lookups.
     *
     * @param path the path to normalize (non-null).
     * @return the path with backslashes replaced by forward slashes.
     */
    public static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
}
