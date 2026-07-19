package app.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Commit(
        String id,
        String message,
        String author,
        Instant timestamp,
        String parentId,
        String secondParentId,
        List<FileSnapshot> manifest) {

    public Commit {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Commit id is required and must not be blank");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Commit message is required and must not be blank");
        if (author == null || author.isBlank()) throw new IllegalArgumentException("Commit author is required and must not be blank");
        Objects.requireNonNull(timestamp, "Commit timestamp must not be null");
        Objects.requireNonNull(manifest, "Commit manifest must not be null");
        if (secondParentId != null && parentId == null) throw new IllegalArgumentException("A merge commit must have a primary parentId");
        manifest = List.copyOf(manifest);
    }

    public Optional<String> parent() { return Optional.ofNullable(parentId); }
    public boolean isRoot() { return parentId == null; }
    public boolean isMerge() { return secondParentId != null; }
    public List<String> parents() {
        if (parentId == null) return List.of();
        if (secondParentId == null) return List.of(parentId);
        return List.of(parentId, secondParentId);
    }
}
