package app.model;

import java.util.Objects;

/**
 * In-memory representation of a blob: the content of a single file together with
 * its content-addressed identity.
 *
 * <p>A blob captures <em>content only</em> — it has no filename or path. Two
 * files with identical bytes are represented by the same blob, which is the
 * essence of content-addressed storage. The {@link #getId() id} is the SHA-256
 * that identifies this content.
 *
 * <p><strong>The blob does not compute its own id.</strong> Deriving the id
 * requires knowledge of the canonical object format ({@code "blob <size>\0" +
 * content}) and the hashing primitive, both of which belong to the service layer
 * ({@code service.HashService}). That service acts as this model's factory: it
 * computes the id and supplies it here. The blob trusts that the id corresponds
 * to its content; guaranteeing that relationship is the service's responsibility.
 *
 * <p><strong>Immutability.</strong> The content is stored and returned as a
 * defensive copy, so the byte array held internally can never be observed or
 * mutated by callers. Instances are therefore safely immutable.
 *
 * <p><strong>Equality</strong> is by {@link #getId() id} alone. Because the id is
 * the hash of the content, equal ids imply equal content, so identity comparison
 * is both correct and inexpensive.
 */
public final class Blob {

    private final String id;
    private final byte[] content;

    private Blob(String id, byte[] content) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Blob id is required and must not be blank");
        }
        Objects.requireNonNull(content, "Blob content must not be null");
        this.id = id;
        this.content = content.clone();
    }

    /**
     * Creates a blob from a precomputed id and its content.
     *
     * @param id      the content-addressed identity (typically produced by
     *                {@code HashService}); non-blank.
     * @param content the file content; non-null. Defensively copied.
     * @return an immutable {@link Blob}.
     * @throws IllegalArgumentException if {@code id} is null or blank.
     * @throws NullPointerException     if {@code content} is null.
     */
    public static Blob of(String id, byte[] content) {
        return new Blob(id, content);
    }

    /** @return the content-addressed SHA-256 identity of this blob. */
    public String getId() {
        return id;
    }

    /** @return a copy of this blob's content bytes. */
    public byte[] getContent() {
        return content.clone();
    }

    /** @return the size of the content in bytes. */
    public int size() {
        return content.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Blob)) return false;
        Blob that = (Blob) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Blob{id=" + id + ", size=" + content.length + "}";
    }
}
