package app.model;

import java.util.Objects;

/**
 * Represents a conflict in a specific path during a three-way merge.
 *
 * <p>A conflict occurs when a file has been modified differently on the target
 * branch compared to the current branch, or modified on one side and deleted
 * on the other. This record captures the conflicting blob IDs (which can be
 * {@code null} if the file was deleted or absent on that side).
 *
 * @param path        the conflicted file path.
 * @param baseBlobId  the blob id in the merge base (common ancestor).
 * @param ourBlobId   the blob id on the current branch (HEAD).
 * @param theirBlobId the blob id on the target branch being merged.
 */
public record MergeConflict(
        String path,
        String baseBlobId,
        String ourBlobId,
        String theirBlobId) {

    public MergeConflict {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Conflicted path must not be null or blank");
        }
        if (Objects.equals(ourBlobId, theirBlobId)) {
            throw new IllegalArgumentException(
                    "A conflict cannot exist if ourBlobId and theirBlobId are identical");
        }
    }
}
