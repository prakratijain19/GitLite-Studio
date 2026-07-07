package app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the mutable {@link Index}: per-path uniqueness, replacement,
 * removal, deterministic ordering, and separator normalization on lookup.
 */
class IndexTest {

    @Test
    @DisplayName("staging a path adds an entry")
    void stageAddsEntry() {
        Index index = new Index();
        index.stage(new FileSnapshot("a.txt", "id1"));

        assertEquals(1, index.size());
        assertEquals(Optional.of(new FileSnapshot("a.txt", "id1")), index.get("a.txt"));
    }

    @Test
    @DisplayName("staging the same path again replaces the entry, not adds one")
    void stageSamePathReplaces() {
        Index index = new Index();
        index.stage(new FileSnapshot("a.txt", "id1"));
        index.stage(new FileSnapshot("a.txt", "id2"));

        assertEquals(1, index.size());
        assertEquals("id2", index.get("a.txt").orElseThrow().blobId());
    }

    @Test
    @DisplayName("unstage removes an entry and reports whether one existed")
    void unstageRemoves() {
        Index index = new Index();
        index.stage(new FileSnapshot("a.txt", "id1"));

        assertTrue(index.unstage("a.txt"));
        assertFalse(index.unstage("a.txt"));
        assertTrue(index.isEmpty());
    }

    @Test
    @DisplayName("snapshots are returned sorted by path")
    void snapshotsSortedByPath() {
        Index index = new Index();
        index.stage(new FileSnapshot("c.txt", "id"));
        index.stage(new FileSnapshot("a.txt", "id"));
        index.stage(new FileSnapshot("b.txt", "id"));

        List<String> paths = index.getSnapshots().stream().map(FileSnapshot::path).toList();
        assertEquals(List.of("a.txt", "b.txt", "c.txt"), paths);
    }

    @Test
    @DisplayName("lookups normalize path separators")
    void lookupNormalizesSeparators() {
        Index index = new Index();
        index.stage(new FileSnapshot("dir/f.txt", "id"));

        assertTrue(index.contains("dir\\f.txt"));
        assertTrue(index.get("dir\\f.txt").isPresent());
    }

    @Test
    @DisplayName("fromSnapshots loads entries, last-wins on duplicate paths")
    void fromSnapshotsLastWins() {
        Index index = Index.fromSnapshots(List.of(
                new FileSnapshot("a.txt", "id1"),
                new FileSnapshot("a.txt", "id2")));

        assertEquals(1, index.size());
        assertEquals("id2", index.get("a.txt").orElseThrow().blobId());
    }
}
