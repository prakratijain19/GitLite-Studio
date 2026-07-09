package app.service;

import static app.model.DiffLineType.ADDED;
import static app.model.DiffLineType.CONTEXT;
import static app.model.DiffLineType.REMOVED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import app.model.DiffLine;
import app.model.DiffLineType;

/**
 * Unit tests for {@link DiffService}: the LCS-based line diff across identity,
 * pure additions/removals, and interleaved edits.
 */
class DiffServiceTest {

    private final DiffService diffService = new DiffService();

    private List<DiffLine> diff(String oldText, String newText) {
        return diffService.diff(
                oldText.getBytes(StandardCharsets.UTF_8),
                newText.getBytes(StandardCharsets.UTF_8));
    }

    private static DiffLine line(DiffLineType type, String content) {
        return new DiffLine(type, content);
    }

    @Test
    @DisplayName("identical content is all context")
    void identicalContentAllContext() {
        assertEquals(
                List.of(line(CONTEXT, "a"), line(CONTEXT, "b"), line(CONTEXT, "c")),
                diff("a\nb\nc", "a\nb\nc"));
    }

    @Test
    @DisplayName("two empty inputs produce an empty diff")
    void bothEmpty() {
        assertTrue(diff("", "").isEmpty());
    }

    @Test
    @DisplayName("empty to content is all additions")
    void emptyToContentAllAdded() {
        assertEquals(
                List.of(line(ADDED, "a"), line(ADDED, "b")),
                diff("", "a\nb"));
    }

    @Test
    @DisplayName("content to empty is all removals")
    void contentToEmptyAllRemoved() {
        assertEquals(
                List.of(line(REMOVED, "a"), line(REMOVED, "b")),
                diff("a\nb", ""));
    }

    @Test
    @DisplayName("a changed middle line is a removal followed by an addition, framed by context")
    void singleMiddleLineChange() {
        assertEquals(
                List.of(line(CONTEXT, "a"), line(REMOVED, "b"), line(ADDED, "x"), line(CONTEXT, "c")),
                diff("a\nb\nc", "a\nx\nc"));
    }

    @Test
    @DisplayName("an inserted middle line is a single addition between context")
    void insertionInMiddle() {
        assertEquals(
                List.of(line(CONTEXT, "a"), line(ADDED, "b"), line(CONTEXT, "c")),
                diff("a\nc", "a\nb\nc"));
    }

    @Test
    @DisplayName("a deleted middle line is a single removal between context")
    void deletionInMiddle() {
        assertEquals(
                List.of(line(CONTEXT, "a"), line(REMOVED, "b"), line(CONTEXT, "c")),
                diff("a\nb\nc", "a\nc"));
    }
}
