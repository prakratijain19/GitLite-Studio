package app.service;

import java.nio.file.Path;

/**
 * Thrown when initialization is requested for a location that already contains
 * an initialized GitLite repository.
 *
 * <p>This is a <strong>service-level precondition failure</strong>, intentionally
 * distinct from {@code storage.StorageException}. A {@code StorageException}
 * signals that a disk read or write failed; this exception signals that the
 * requested operation is not valid in the current state (a repository is already
 * present). Keeping the two separate lets callers — notably the future UI — catch
 * this specific case and present a friendly "a repository already exists here"
 * message rather than treating it as an I/O error.
 *
 * <p>It is unchecked, consistent with the project's approach of not forcing
 * pervasive {@code throws} clauses for conditions handled at well-defined
 * boundaries.
 */
public class RepositoryAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param rootPath the project root at which an existing repository was found.
     */
    public RepositoryAlreadyExistsException(Path rootPath) {
        super("A GitLite repository already exists at " + rootPath);
    }
}
