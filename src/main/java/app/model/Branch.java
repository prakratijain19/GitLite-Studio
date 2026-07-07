package app.model;

import java.util.Objects;
import java.util.Optional;

/**
 * In-memory representation of a GitLite branch: a named pointer to a commit.
 *
 * <p>A branch is one of the simplest yet most important concepts in the model.
 * In Git terms it is a mutable reference (e.g. {@code refs/heads/master}) whose
 * value is the ID of the commit at the branch's tip. This class captures that
 * association and nothing more: it performs no file I/O and knows nothing about
 * the {@code .gitlite} directory. Persistence is the responsibility of the
 * storage layer, orchestrated by the service layer.
 *
 * <p><strong>Unborn branches.</strong> A freshly initialized repository has a
 * default branch but no commits, so the branch has no tip to point at. Such a
 * branch is <em>unborn</em>. This state is represented by an absent tip, created
 * via {@link #unborn(String)}. Callers must handle it explicitly: the tip is
 * exposed as an {@link Optional} and never as a raw {@code null}.
 *
 * <p><strong>Immutability.</strong> Although a real branch tip advances on every
 * commit, instances of this class are immutable. Advancing the tip produces a
 * <em>new</em> {@code Branch} via {@link #withTip(String)} rather than mutating
 * the existing one. The authoritative, changing state lives on disk and in the
 * service layer; the model object is a snapshot.
 *
 * <p><strong>Equality.</strong> Two branches are equal when they share the same
 * {@link #getName() name}, irrespective of their tips. A branch's identity is
 * its name; the tip is mutable state, not identity. This means
 * {@code master} pointing at commit A equals {@code master} pointing at commit B,
 * which is intentional and lets a branch be located by name in a {@code Set} or
 * {@code Map} regardless of where its tip currently sits.
 */
public final class Branch {

    private final String name;

    /** The tip commit ID, or {@code null} for an unborn branch (no commits yet). */
    private final String tipCommitId;

    private Branch(String name, String tipCommitId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Branch name is required and must not be blank");
        }
        this.name = name;
        this.tipCommitId = tipCommitId;
    }

    /**
     * Creates an unborn branch: a named branch that does not yet point at any
     * commit. This is the state produced by initializing a repository.
     *
     * @param name the branch name (non-blank).
     * @return a branch with no tip.
     * @throws IllegalArgumentException if {@code name} is null or blank.
     */
    public static Branch unborn(String name) {
        return new Branch(name, null);
    }

    /**
     * Creates a branch pointing at a known commit.
     *
     * @param name         the branch name (non-blank).
     * @param tipCommitId  the ID of the commit at the branch's tip (non-blank).
     * @return a branch whose tip is the given commit.
     * @throws IllegalArgumentException if {@code name} is null/blank or
     *                                  {@code tipCommitId} is null/blank.
     */
    public static Branch at(String name, String tipCommitId) {
        if (tipCommitId == null || tipCommitId.isBlank()) {
            throw new IllegalArgumentException("tipCommitId is required and must not be blank");
        }
        return new Branch(name, tipCommitId);
    }

    /**
     * Returns a new branch with the same name pointing at the given commit,
     * leaving this instance unchanged.
     *
     * @param tipCommitId the ID of the new tip commit (non-blank).
     * @return a new {@code Branch} advanced to the given commit.
     * @throws IllegalArgumentException if {@code tipCommitId} is null or blank.
     */
    public Branch withTip(String tipCommitId) {
        return at(name, tipCommitId);
    }

    /** @return the branch name (e.g. {@code "master"}). */
    public String getName() {
        return name;
    }

    /**
     * @return the tip commit ID if this branch has one, or an empty
     *         {@link Optional} if the branch is unborn.
     */
    public Optional<String> getTipCommitId() {
        return Optional.ofNullable(tipCommitId);
    }

    /** @return {@code true} if this branch points at a commit; {@code false} if unborn. */
    public boolean hasCommits() {
        return tipCommitId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Branch)) return false;
        Branch that = (Branch) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Branch{name=" + name + ", tipCommitId=" + tipCommitId + "}";
    }
}
