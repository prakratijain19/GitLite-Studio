package app.storage;

/**
 * Thrown when the storage layer fails to read or write repository data on disk.
 *
 * <p>This exception is the storage layer's <strong>translation boundary</strong>.
 * The lower utility classes ({@code util.FileUtil}, {@code util.JsonUtil}) throw
 * raw, checked {@link java.io.IOException} and Jackson exceptions, which are the
 * right currency at that level but the wrong currency for services and
 * controllers. Storage classes catch those low-level failures and rethrow them
 * as a {@code StorageException} carrying a business-meaningful message (for
 * example, "Failed to write HEAD for repository at …") while preserving the
 * original {@linkplain #getCause() cause} for logging and debugging.
 *
 * <p>It is an <strong>unchecked</strong> exception because storage failures
 * (missing permissions, a full disk, corrupted metadata) are generally not
 * recoverable at the call site. Making it unchecked keeps the signatures of the
 * service and controller layers free of pervasive {@code throws} clauses, while
 * the deliberate translation at the storage boundary ensures failures are never
 * silently swallowed. Callers that can recover may still catch it explicitly.
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a storage exception with a descriptive message.
     *
     * @param message a human-readable description of what failed.
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Creates a storage exception with a descriptive message and the underlying
     * cause, which is typically a low-level {@link java.io.IOException}.
     *
     * @param message a human-readable description of what failed.
     * @param cause   the underlying exception that triggered this failure.
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
