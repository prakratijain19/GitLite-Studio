package app.service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import app.model.Blob;
import app.model.FileSnapshot;
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

    /** Object-type word used in the canonical header for commits. */
    private static final String COMMIT_TYPE = "commit";

    /** Charset for the ASCII object header and canonical commit text. */
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
        String id = SHAUtil.sha256Hex(withObjectHeader(BLOB_TYPE, content));
        return Blob.of(id, content);
    }

    /**
     * Computes the content-addressed id of a commit from its canonical form.
     *
     * <p>The canonical form is a deterministic text document — sorted manifest
     * lines, an optional {@code parent} line (omitted for the root commit), the
     * author, the timestamp as epoch seconds, a blank line, and the message —
     * hashed with the {@code "commit <size>\0"} header. Because it is defined
     * independently of how commits are stored, the id is stable regardless of the
     * JSON storage format.
     *
     * @param message   the commit message; non-null.
     * @param author    the commit author; non-null.
     * @param timestamp the commit time; non-null.
     * @param parentId  the parent commit id, or {@code null} for the root commit.
     * @param manifest  the staged entries captured by the commit; non-null.
     * @return the 64-character hex SHA-256 commit id.
     */
    public String createCommitId(String message, String author, Instant timestamp,
                                 String parentId, List<FileSnapshot> manifest) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        byte[] content = canonicalCommitText(message, author, timestamp, parentId, manifest)
                .getBytes(HEADER_CHARSET);
        return SHAUtil.sha256Hex(withObjectHeader(COMMIT_TYPE, content));
    }

    /**
     * Builds the deterministic canonical text used to identify a commit. Manifest
     * entries are sorted by path so identity does not depend on their order, and
     * the {@code parent} line is omitted entirely for the root commit.
     */
    private static String canonicalCommitText(String message, String author, Instant timestamp,
                                              String parentId, List<FileSnapshot> manifest) {
        StringBuilder text = new StringBuilder();
        text.append("tree\n");
        manifest.stream()
                .sorted(Comparator.comparing(FileSnapshot::path))
                .forEach(entry -> text.append(entry.blobId()).append(' ')
                        .append(entry.path()).append('\n'));
        if (parentId != null) {
            text.append("parent ").append(parentId).append('\n');
        }
        text.append("author ").append(author).append('\n');
        text.append("time ").append(timestamp.getEpochSecond()).append('\n');
        text.append('\n');
        text.append(message);
        return text.toString();
    }

    /**
     * Prepends the canonical object header {@code "<type> <size>\0"} to the given
     * content. Shared by every object type (blobs, commits) so the header format
     * is defined in exactly one place.
     *
     * @param type    the object-type word (for example {@code "blob"}).
     * @param content the object content.
     * @return the header-prefixed canonical bytes to be hashed.
     */
    private static byte[] withObjectHeader(String type, byte[] content) {
        byte[] header = (type + " " + content.length + "\0").getBytes(HEADER_CHARSET);
        byte[] canonical = new byte[header.length + content.length];
        System.arraycopy(header, 0, canonical, 0, header.length);
        System.arraycopy(content, 0, canonical, header.length, content.length);
        return canonical;
    }
}
