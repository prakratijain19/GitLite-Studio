package app.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.ArrayList;

/**
 * The staging area: an in-memory collection of {@link FileSnapshot} entries,
 * keyed by path, that will be frozen into the next commit.
 *
 * <p>Unlike the value-object models in this package, {@code Index} is
 * deliberately <strong>mutable</strong>. It represents evolving state — files are
 * repeatedly staged and unstaged — so it is manipulated in place through a
 * guarded API rather than by producing copies. Its internal map is never exposed;
 * reads return an unmodifiable, path-ordered view.
 *
 * <p>Entries are held in a {@link TreeMap} keyed by the entry's path. This map is
 * what enforces the "one active entry per path" rule: staging a path that is
 * already present replaces its entry. Keying by a sorted map also makes iteration
 * order deterministic (sorted by path), which yields stable, diff-friendly output
 * when the index is persisted.
 */
public final class Index {

    private final Map<String, FileSnapshot> entries = new TreeMap<>();

    /** Creates an empty index. */
    public Index() {
    }

    /**
     * Creates an index populated from the given snapshots. Where two snapshots
     * share a path, the last one wins.
     *
     * @param snapshots the snapshots to load (non-null).
     * @return a new index containing the snapshots.
     */
    public static Index fromSnapshots(Collection<FileSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots must not be null");
        Index index = new Index();
        for (FileSnapshot snapshot : snapshots) {
            index.stage(snapshot);
        }
        return index;
    }

    /**
     * Stages a snapshot, adding it or replacing any existing entry for the same
     * path.
     *
     * @param snapshot the snapshot to stage (non-null).
     */
    public void stage(FileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        entries.put(snapshot.path(), snapshot);
    }

    /**
     * Removes the entry for the given path, if present.
     *
     * @param path the path to unstage (non-null); normalized before lookup.
     * @return {@code true} if an entry was removed, {@code false} if none existed.
     */
    public boolean unstage(String path) {
        return entries.remove(key(path)) != null;
    }

    /**
     * @param path the path to look up (non-null); normalized before lookup.
     * @return the snapshot staged for {@code path}, if any.
     */
    public Optional<FileSnapshot> get(String path) {
        return Optional.ofNullable(entries.get(key(path)));
    }

    /**
     * @param path the path to test (non-null); normalized before lookup.
     * @return {@code true} if a snapshot is staged for {@code path}.
     */
    public boolean contains(String path) {
        return entries.containsKey(key(path));
    }

    /** @return an unmodifiable, path-ordered view of the staged snapshots. */
    public List<FileSnapshot> getSnapshots() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    /** @return the number of staged entries. */
    public int size() {
        return entries.size();
    }

    /** @return {@code true} if nothing is staged. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static String key(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return FileSnapshot.normalizePath(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Index)) return false;
        Index that = (Index) o;
        return entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return "Index{size=" + entries.size() + "}";
    }
}
