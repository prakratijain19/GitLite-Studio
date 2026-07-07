package app.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, content-addressed snapshot of the staging index at a moment in
 * time, together with its metadata — the equivalent of a Git commit.
 *
 * <p>A commit records the frozen {@link #manifest()} (the staged path → blob
 * mappings), a human {@link #message()}, the {@link #author()}, an
 * {@link #timestamp()}, and the {@link #parentId() parent} it descends from. The
 * <strong>root commit</strong> (the first in a repository) has no parent, which
 * is represented by a {@code null} parent id; prefer {@link #parent()} and
 * {@link #isRoot()} over reading {@link #parentId()} directly.
 *
 * <p>Like {@link FileSnapshot} and {@code RepositoryConfig}, this is a
 * {@code record}: it is immutable persisted data, which gives value semantics and
 * clean two-way JSON binding without a separate DTO. Its {@link #id()} is
 * <em>supplied</em>, not self-computed — {@code service.HashService} derives it
 * from the canonical commit form and {@code service.CommitService} constructs the
 * record with it. The model performs no hashing.
 *
 * <p>The manifest is defensively copied to an unmodifiable list, so the record is
 * immutable despite holding a collection.
 *
 * @param id        the content-addressed commit id.
 * @param message   the commit message.
 * @param author    the identity that created the commit.
 * @param timestamp the moment the commit was created (UTC instant).
 * @param parentId  the id of the parent commit, or {@code null} for the root.
 * @param manifest  the frozen staged entries captured by this commit.
 */
public record Commit(
        String id,
        String message,
        String author,
        Instant timestamp,
        String parentId,
        List<FileSnapshot> manifest) {

    public Commit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Commit id is required and must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Commit message is required and must not be blank");
        }
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("Commit author is required and must not be blank");
        }
        Objects.requireNonNull(timestamp, "Commit timestamp must not be null");
        Objects.requireNonNull(manifest, "Commit manifest must not be null");
        // Defensive, immutable copy so the record cannot be mutated via the list.
        manifest = List.copyOf(manifest);
    }

    /** @return the parent commit id if present, or empty for the root commit. */
    public Optional<String> parent() {
        return Optional.ofNullable(parentId);
    }

    /** @return {@code true} if this is the root commit (it has no parent). */
    public boolean isRoot() {
        return parentId == null;
    }
}
