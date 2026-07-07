package app.service;

/**
 * Thrown when a commit is requested but there is nothing to commit — either the
 * staging index is empty, or its contents are identical to the current branch tip
 * (no changes since the last commit).
 *
 * <p>Like {@link RepositoryAlreadyExistsException}, this is a service-level
 * precondition failure rather than a storage error, so the UI can catch it and
 * present a friendly "nothing to commit" message. It is unchecked, consistent
 * with the project's handling of conditions resolved at well-defined boundaries.
 */
public class NothingToCommitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message a human-readable description of why there was nothing to commit.
     */
    public NothingToCommitException(String message) {
        super(message);
    }
}
