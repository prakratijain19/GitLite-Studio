package app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Commit}: root vs. parented state, manifest immutability,
 * and construction validation.
 */
class CommitTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<FileSnapshot> MANIFEST = List.of(new FileSnapshot("a.txt", "id1"));

    @Test
    @DisplayName("a null parent means the commit is the root")
    void rootHasNoParent() {
        Commit commit = new Commit("cid", "msg", "author", WHEN, null, MANIFEST);
        assertTrue(commit.isRoot());
        assertTrue(commit.parent().isEmpty());
    }

    @Test
    @DisplayName("a parented commit exposes its parent")
    void parentedCommit() {
        Commit commit = new Commit("cid", "msg", "author", WHEN, "parentId", MANIFEST);
        assertFalse(commit.isRoot());
        assertEquals(Optional.of("parentId"), commit.parent());
    }

    @Test
    @DisplayName("the manifest is an immutable copy")
    void manifestIsImmutable() {
        Commit commit = new Commit("cid", "msg", "author", WHEN, null, MANIFEST);
        assertThrows(UnsupportedOperationException.class,
                () -> commit.manifest().add(new FileSnapshot("x", "y")));
    }

    @Test
    @DisplayName("a blank message is rejected")
    void blankMessageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Commit("cid", "  ", "author", WHEN, null, MANIFEST));
    }

    @Test
    @DisplayName("a null timestamp is rejected")
    void nullTimestampRejected() {
        assertThrows(NullPointerException.class,
                () -> new Commit("cid", "msg", "author", null, null, MANIFEST));
    }
}
