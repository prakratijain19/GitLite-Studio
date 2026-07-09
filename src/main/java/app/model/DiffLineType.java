package app.model;

/**
 * The role of a line in a line-level diff between two versions of content.
 */
public enum DiffLineType {

    /** A line present unchanged in both versions. */
    CONTEXT,

    /** A line present only in the new version. */
    ADDED,

    /** A line present only in the old version. */
    REMOVED
}
