package app.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Thin, stateless wrapper over {@link MessageDigest} providing SHA-256 hashing.
 *
 * <p>This class is the <strong>single seam over the JDK's message-digest API</strong>,
 * mirroring the role {@code FileUtil} plays for NIO and {@code JsonUtil} for
 * Jackson. Its responsibility is intentionally narrow and generic: it turns raw
 * bytes into a lowercase hexadecimal SHA-256 string. It knows nothing about Git
 * object types or headers — composing an object's canonical byte form before
 * hashing is the responsibility of the service layer (see {@code HashService}),
 * so this class remains a reusable primitive for hashing any content.
 *
 * <p>The class is a utility holder: {@code final}, non-instantiable, static-only.
 */
public final class SHAUtil {

    /** The digest algorithm. Guaranteed to be available on every JVM. */
    private static final String ALGORITHM = "SHA-256";

    private SHAUtil() {
        throw new AssertionError("SHAUtil is a utility class and must not be instantiated");
    }

    /**
     * Computes the SHA-256 digest of the given bytes and returns it as a
     * lowercase hexadecimal string (64 characters).
     *
     * <p>A new {@link MessageDigest} instance is created per call because
     * {@code MessageDigest} is stateful and not thread-safe.
     *
     * @param data the bytes to hash (must not be null).
     * @return the 64-character lowercase hex SHA-256 of {@code data}.
     * @throws IllegalStateException if the SHA-256 algorithm is unavailable,
     *                               which indicates a fundamentally broken JVM
     *                               and should never occur in practice.
     */
    public static String sha256Hex(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " algorithm is required but unavailable", e);
        }
    }
}
