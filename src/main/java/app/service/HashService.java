package app.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import app.model.Blob;
import app.util.SHAUtil;

/**
 * Computes content-addressed identities for repository objects and constructs
 * the corresponding model objects.
 *
 * <p>This service owns knowledge of GitLite's <strong>object format</strong>. A
 * blob's identity is not the hash of its raw bytes but the hash of a canonical
 * form: the ASCII header {@code "blob " + <size> + "\0"} followed by the content.
 * The header tags the object's type and length, which prevents collisions across
 * object types and makes the encoding unambiguous. This mirrors the way Git
 * derives object ids.
 *
 * <p>It sits between the format-agnostic {@link SHAUtil} primitive and the
 * format-aware {@link Blob} model: {@code SHAUtil} performs the raw hashing,
 * this service supplies the canonical bytes, and {@code Blob} holds the result.
 * The produced {@code Blob} stores the <em>raw</em> content; the header is used
 * only to derive the identity.
 *
 * <p>Unlike the static utility classes, this is an instance service: it encodes
 * domain knowledge and is intended to be injected into higher-level services
 * (for example {@code StagingService}).
 */
public final class HashService {

    /** Object-type word used in the canonical header for file content. */
    private static final String BLOB_TYPE = "blob";

    /** Charset for the ASCII object header. */
    private static final Charset HEADER_CHARSET = StandardCharsets.UTF_8;

    /**
     * Builds a {@link Blob} for the given content, computing its content-addressed
     * id from the canonical blob form.
     *
     * @param content the raw file content; non-null. Defensively copied by the
     *                resulting blob.
     * @return a blob whose id is the SHA-256 of {@code "blob <size>\0" + content}.
     * @throws NullPointerException if {@code content} is null.
     */
    public Blob createBlob(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        String id = SHAUtil.sha256Hex(canonicalBlobForm(content));
        return Blob.of(id, content);
    }

    /**
     * Produces the canonical byte form used to identify a blob:
     * the header {@code "blob " + <size> + "\0"} concatenated with the content.
     *
     * @param content the raw content.
     * @return the canonical bytes to be hashed.
     */
    private static byte[] canonicalBlobForm(byte[] content) {
        byte[] header = (BLOB_TYPE + " " + content.length + "\0").getBytes(HEADER_CHARSET);
        byte[] canonical = new byte[header.length + content.length];
        System.arraycopy(header, 0, canonical, 0, header.length);
        System.arraycopy(content, 0, canonical, header.length, content.length);
        return canonical;
    }
}
