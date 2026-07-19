package app.model;

import java.util.Objects;

/**
 * The result of a merge operation, describing what happened and the commit
 * that the current branch now points at.
 *
 * <p>A merge can complete in several ways, each captured by the {@link Type}
 * enum:
 * <ul>
 *   <li>{@link Type#FAST_FORWARD} — the current branch was simply advanced to
 *       the target commit because it was a direct descendant.</li>
 *   <li>{@link Type#ALREADY_UP_TO_DATE} — the target is already an ancestor of
 *       the current branch; nothing to do.</li>
 *   <li>{@link Type#MERGED} — a true merge commit was created combining both
 *       branches (future milestone).</li>
 *   <li>{@link Type#CONFLICTED} — the merge could not be completed
 *       automatically due to conflicting changes (future milestone).</li>
 * </ul>
 *
 * <p>This is an immutable value object. It does not hold the full
 * {@link Commit} because the caller may or may not need it; it carries only
 * the resulting commit id and the merge type.
 *
 * @param type     the kind of merge that was performed.
 * @param commitId the commit id that the current branch now points at after
 *                 the merge. For {@link Type#ALREADY_UP_TO_DATE} this is the
 *                 unchanged current tip.
 * @param message  a human-readable summary of what happened.
 */
public record MergeResult(Type type, String commitId, String message) {

    /**
     * The kind of merge performed.
     */
    public enum Type {

        /** The branch was advanced to the target (no divergence). */
        FAST_FORWARD,

        /** The target was already reachable from the current branch. */
        ALREADY_UP_TO_DATE,

        /** A merge commit was created combining both histories. */
        MERGED,

        /** The merge has conflicts that must be resolved manually. */
        CONFLICTED
    }

    public MergeResult {
        Objects.requireNonNull(type, "MergeResult type must not be null");
        if (commitId == null || commitId.isBlank()) {
            throw new IllegalArgumentException(
                    "MergeResult commitId is required and must not be blank");
        }
        Objects.requireNonNull(message, "MergeResult message must not be null");
    }

    /** @return {@code true} if the merge completed without conflicts. */
    public boolean isSuccess() {
        return type != Type.CONFLICTED;
    }

    /** @return {@code true} if the branch was actually advanced or a commit was created. */
    public boolean didMerge() {
        return type == Type.FAST_FORWARD || type == Type.MERGED;
    }
}
