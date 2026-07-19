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
 */
public final class HashService {

    private static final String BLOB_TYPE = "blob";
    private static final String COMMIT_TYPE = "commit";
    private static final Charset HEADER_CHARSET = StandardCharsets.UTF_8;

    public Blob createBlob(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        String id = SHAUtil.sha256Hex(withObjectHeader(BLOB_TYPE, content));
        return Blob.of(id, content);
    }

    /**
     * Computes the content-addressed id of a commit from its canonical form.
     *
     * @param message        the commit message; non-null.
     * @param author         the commit author; non-null.
     * @param timestamp      the commit time; non-null.
     * @param parentId       the parent commit id, or {@code null} for the root commit.
     * @param secondParentId the second parent commit id (merge source), or
     *                       {@code null} for non-merge commits.
     * @param manifest       the staged entries captured by the commit; non-null.
     * @return the 64-character hex SHA-256 commit id.
     */
    public String createCommitId(String message, String author, Instant timestamp,
                                 String parentId, String secondParentId,
                                 List<FileSnapshot> manifest) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
        byte[] content = canonicalCommitText(message, author, timestamp, parentId,
                secondParentId, manifest).getBytes(HEADER_CHARSET);
        return SHAUtil.sha256Hex(withObjectHeader(COMMIT_TYPE, content));
    }

    private static String canonicalCommitText(String message, String author, Instant timestamp,
                                              String parentId, String secondParentId,
                                              List<FileSnapshot> manifest) {
        StringBuilder text = new StringBuilder();
        text.append("tree\n");
        manifest.stream()
                .sorted(Comparator.comparing(FileSnapshot::path))
                .forEach(entry -> text.append(entry.blobId()).append(' ')
                        .append(entry.path()).append('\n'));
        if (parentId != null) {
            text.append("parent ").append(parentId).append('\n');
        }
        if (secondParentId != null) {
            text.append("merge ").append(secondParentId).append('\n');
        }
        text.append("author ").append(author).append('\n');
        text.append("time ").append(timestamp.getEpochSecond()).append('\n');
        text.append('\n');
        text.append(message);
        return text.toString();
    }

    private static byte[] withObjectHeader(String type, byte[] content) {
        byte[] header = (type + " " + content.length + "\0").getBytes(HEADER_CHARSET);
        byte[] canonical = new byte[header.length + content.length];
        System.arraycopy(header, 0, canonical, 0, header.length);
        System.arraycopy(content, 0, canonical, header.length, content.length);
        return canonical;
    }
}
