package app.model;

/**
 * The kind of change a path represents when comparing two trees (HEAD, index, or
 * working tree) during a status computation.
 */
public enum ChangeType {

    /** The path exists in the newer tree but not the older one. */
    ADDED,

    /** The path exists in both trees but its content (blob id) differs. */
    MODIFIED,

    /** The path exists in the older tree but not the newer one. */
    DELETED
}
