package app.service;

import java.util.List;
import java.util.Objects;

import app.model.MergeConflict;
import app.model.MergeResult;

/**
 * Thrown when a three-way merge cannot be completed automatically due to
 * file-level conflicts.
 *
 * <p>This exception carries both the overall {@link MergeResult} (which will have
 * type {@link MergeResult.Type#CONFLICTED}) and a detailed list of the paths
 * that are in conflict.
 */
public final class MergeConflictException extends RuntimeException {

    private final MergeResult mergeResult;
    private final List<MergeConflict> conflicts;

    public MergeConflictException(MergeResult mergeResult, List<MergeConflict> conflicts) {
        super(mergeResult.message());
        this.mergeResult = Objects.requireNonNull(mergeResult, "mergeResult must not be null");
        if (mergeResult.type() != MergeResult.Type.CONFLICTED) {
            throw new IllegalArgumentException(
                    "MergeConflictException must be initialized with a CONFLICTED result type");
        }
        this.conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts must not be null"));
        if (this.conflicts.isEmpty()) {
            throw new IllegalArgumentException("conflicts list must not be empty");
        }
    }

    public MergeResult getMergeResult() {
        return mergeResult;
    }

    public List<MergeConflict> getConflicts() {
        return conflicts;
    }
}
