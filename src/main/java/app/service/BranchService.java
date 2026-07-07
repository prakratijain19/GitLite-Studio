package app.service;

import java.util.Objects;
import java.util.Optional;

import app.model.Repository;
import app.storage.DefaultStorageFactory;
import app.storage.FileStorage;
import app.storage.StorageFactory;

/**
 * Resolves the current branch from {@code HEAD} and manages branch tips.
 *
 * <p>This service is the authority on the <strong>HEAD symbolic-ref format</strong>.
 * {@code HEAD} holds a git-style reference such as {@code "ref: refs/heads/master"};
 * this class owns building that string ({@link #toHeadRef(String)}) and parsing it
 * back to a branch name, so the format is defined in exactly one place. It also
 * reads and advances branch tips, which is where a previously unborn branch is
 * born on its first commit.
 *
 * <p>Like the other services, it is application-scoped and obtains
 * repository-scoped {@link FileStorage} through an injected {@link StorageFactory}.
 * Detached HEAD (where {@code HEAD} holds a raw commit id rather than a branch
 * ref) is a future capability; for now a non-symbolic HEAD is rejected.
 */
public final class BranchService {

    /**
     * Prefix of the git-style symbolic ref stored in {@code HEAD}. Combined with a
     * branch name it yields, for example, {@code "ref: refs/heads/master"}.
     */
    public static final String HEAD_REF_PREFIX = "ref: refs/heads/";

    private final StorageFactory storageFactory;

    /** Creates a service wired with the default storage factory. */
    public BranchService() {
        this(new DefaultStorageFactory());
    }

    /**
     * Creates a service with an explicit storage factory. Intended for tests.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     */
    public BranchService(StorageFactory storageFactory) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
    }

    /**
     * Builds the {@code HEAD} symbolic-ref string for a branch.
     *
     * @param branchName the branch name.
     * @return the ref string, for example {@code "ref: refs/heads/master"}.
     */
    public static String toHeadRef(String branchName) {
        return HEAD_REF_PREFIX + branchName;
    }

    /**
     * Resolves the branch that {@code HEAD} currently points at.
     *
     * @param repository the repository to inspect.
     * @return the current branch name.
     * @throws IllegalStateException if {@code HEAD} is not a symbolic branch ref
     *                               (for example a detached HEAD, not yet supported).
     */
    public String currentBranch(Repository repository) {
        String head = fileStorage(repository).readHead().strip();
        if (!head.startsWith(HEAD_REF_PREFIX)) {
            throw new IllegalStateException("HEAD is not a symbolic branch ref: " + head);
        }
        return head.substring(HEAD_REF_PREFIX.length());
    }

    /**
     * Reads the tip commit id of the given branch.
     *
     * @param repository the repository to inspect.
     * @param branchName the branch name.
     * @return the tip commit id, or empty if the branch is unborn.
     */
    public Optional<String> tipOf(Repository repository, String branchName) {
        return fileStorage(repository).readBranchTip(branchName);
    }

    /**
     * Reads the tip commit id of the branch {@code HEAD} points at. This is the
     * parent for the next commit; it is empty when the current branch is unborn.
     *
     * @param repository the repository to inspect.
     * @return the current branch's tip commit id, or empty if unborn.
     */
    public Optional<String> currentTip(Repository repository) {
        return tipOf(repository, currentBranch(repository));
    }

    /**
     * Moves a branch's tip to the given commit, creating the branch if it did not
     * yet exist (birthing a previously unborn branch).
     *
     * @param repository the repository to update.
     * @param branchName the branch to advance.
     * @param commitId   the commit the branch should now point at.
     */
    public void advanceTip(Repository repository, String branchName, String commitId) {
        fileStorage(repository).writeBranchTip(branchName, commitId);
    }

    private FileStorage fileStorage(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");
        return storageFactory.createFileStorage(repository.getMetadataPath());
    }
}
