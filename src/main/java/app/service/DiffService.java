package app.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.model.DiffLine;
import app.model.DiffLineType;

/**
 * Computes line-level diffs between two versions of content — the core of
 * {@code git diff}.
 *
 * <p>The diff is derived from the <strong>longest common subsequence</strong>
 * (LCS) of the two line sequences: lines in the LCS are unchanged, lines only in
 * the old version are removed, and lines only in the new version are added. The
 * result is an ordered {@link DiffLine} list that interleaves context, removals,
 * and additions in their natural positions.
 *
 * <p>Content is split into lines with {@link String#lines()}, which handles
 * {@code \n}, {@code \r\n}, and {@code \r} terminators and treats a trailing
 * newline as a terminator rather than an extra empty line. As a result, content
 * that differs only by a trailing newline is treated as identical.
 *
 * <p>This is a pure algorithm with no collaborators or storage dependencies.
 */
public final class DiffService {

    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Computes the line-level diff from {@code oldContent} to {@code newContent}.
     *
     * @param oldContent the original content (non-null).
     * @param newContent the new content (non-null).
     * @return the ordered diff lines transforming old into new.
     */
    public List<DiffLine> diff(byte[] oldContent, byte[] newContent) {
        Objects.requireNonNull(oldContent, "oldContent must not be null");
        Objects.requireNonNull(newContent, "newContent must not be null");
        return diffLines(splitLines(oldContent), splitLines(newContent));
    }

    private static List<String> splitLines(byte[] content) {
        return new String(content, CHARSET).lines().toList();
    }

    private static List<DiffLine> diffLines(List<String> oldLines, List<String> newLines) {
        int m = oldLines.size();
        int n = newLines.size();

        // lcs[i][j] = length of the LCS of oldLines[i..] and newLines[j..].
        int[][] lcs = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<DiffLine> diff = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < m && j < n) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                diff.add(new DiffLine(DiffLineType.CONTEXT, oldLines.get(i)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                diff.add(new DiffLine(DiffLineType.REMOVED, oldLines.get(i)));
                i++;
            } else {
                diff.add(new DiffLine(DiffLineType.ADDED, newLines.get(j)));
                j++;
            }
        }
        while (i < m) {
            diff.add(new DiffLine(DiffLineType.REMOVED, oldLines.get(i)));
            i++;
        }
        while (j < n) {
            diff.add(new DiffLine(DiffLineType.ADDED, newLines.get(j)));
            j++;
        }
        return List.copyOf(diff);
    }
}
