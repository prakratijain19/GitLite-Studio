package app.model;

import java.util.Objects;

/**
 * A single line of a line-level diff: its {@link DiffLineType role} and its text.
 *
 * <p>An ordered sequence of these describes the full diff between two versions of
 * content — {@link DiffLineType#CONTEXT} lines are unchanged, {@link DiffLineType#ADDED}
 * lines appear only in the new version, and {@link DiffLineType#REMOVED} lines
 * appear only in the old version.
 *
 * <p>The content may be an empty string, since a blank line is legitimate line
 * content; only {@code null} is rejected.
 *
 * @param type    the role of this line in the diff.
 * @param content the text of the line (without its line terminator).
 */
public record DiffLine(DiffLineType type, String content) {

    public DiffLine {
        Objects.requireNonNull(type, "DiffLine type must not be null");
        Objects.requireNonNull(content, "DiffLine content must not be null");
    }
}
