package app.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The result of a status computation: how the working tree and index differ from
 * each other and from the current commit.
 *
 * <p>Two categories of change are kept separate, mirroring Git:
 * <ul>
 *   <li>{@link #stagedChanges()} — the index compared to the HEAD commit
 *       (changes that a commit would capture). Values are {@link ChangeType#ADDED},
 *       {@link ChangeType#MODIFIED}, or {@link ChangeType#DELETED}.</li>
 *   <li>{@link #unstagedChanges()} — the working tree compared to the index
 *       (changes not yet staged). Values are {@link ChangeType#MODIFIED} or
 *       {@link ChangeType#DELETED}.</li>
 * </ul>
 * A path can appear in both maps — for example a staged modification that has
 * since been edited again. {@link #untracked()} lists working-tree files that are
 * not in the index at all.
 *
 * <p>This is a transient query result rather than persisted data, but it is
 * modelled as an immutable {@code record} with defensive copies so it can be
 * passed around and compared safely.
 *
 * @param stagedChanges   index-vs-HEAD changes, keyed by repository-relative path.
 * @param unstagedChanges working-tree-vs-index changes, keyed by path.
 * @param untracked       working-tree paths not present in the index.
 */
public record StatusReport(
        Map<String, ChangeType> stagedChanges,
        Map<String, ChangeType> unstagedChanges,
        Set<String> untracked) {

    public StatusReport {
        Objects.requireNonNull(stagedChanges, "stagedChanges must not be null");
        Objects.requireNonNull(unstagedChanges, "unstagedChanges must not be null");
        Objects.requireNonNull(untracked, "untracked must not be null");
        stagedChanges = Map.copyOf(stagedChanges);
        unstagedChanges = Map.copyOf(unstagedChanges);
        untracked = Set.copyOf(untracked);
    }

    /**
     * @return {@code true} if the working tree is clean: nothing staged, nothing
     *         unstaged, and nothing untracked.
     */
    public boolean isClean() {
        return stagedChanges.isEmpty() && unstagedChanges.isEmpty() && untracked.isEmpty();
    }
}
