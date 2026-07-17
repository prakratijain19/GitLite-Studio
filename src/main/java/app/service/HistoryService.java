package app.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import app.model.Commit;
import app.model.Repository;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.StorageException;
import app.storage.StorageFactory;

/**
 * Reads commit history by walking the parent chain — the equivalent of
 * {@code git log}.
 *
 * <p>Starting from the tip of the branch {@code HEAD} points at, the service
 * emits each commit and follows its parent link until it reaches the root commit
 * (the one with no parent). The result is ordered newest first. This is a
 * read-only query: it never modifies the repository.
 *
 * <p>It also provides ancestry queries used by merge operations: determining
 * whether one commit is an ancestor of another is the basis for fast-forward
 * detection.
 *
 * <p>It is application-scoped and obtains repository-scoped storage through an
 * injected {@link StorageFactory}, from which it also builds the
 * {@link BranchService} used to resolve the current tip.
 */
public final class HistoryService {

    private final StorageFactory storageFactory;
    private final BranchService branchService;

    /** Creates a service wired with the default storage factory. */
    public HistoryService() {
        this(new DefaultStorageFactory());
    }

    /**
     * Creates a service with an explicit storage factory. Intended for tests.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     */
    public HistoryService(StorageFactory storageFactory) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.branchService = new BranchService(storageFactory);
    }

    /**
     * Returns the history of the current branch, newest commit first.
     *
     * @param repository the repository to read.
     * @return the commits from the current branch tip back to the root, ordered
     *         newest to oldest; empty if the branch is unborn (no commits yet).
     * @throws StorageException if a commit referenced as a parent cannot be found.
     */
    public List<Commit> history(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");

        Optional<String> tip = branchService.currentTip(repository);
        if (tip.isEmpty()) {
            return List.of();
        }

        return historyFrom(repository, tip.get());
    }

    /**
     * Returns the history starting from the given commit, newest first.
     *
     * <p>This method walks the parent chain from {@code startId} back to the
     * root commit, emitting each commit along the way. It is used by
     * {@link #history(Repository)} as well as by other services (for example
     * merge operations) that need the history of a specific branch tip.
     *
     * @param repository the repository to read.
     * @param startId    the commit to start from (non-blank).
     * @return the commits from {@code startId} back to the root, ordered newest
     *         to oldest.
     * @throws StorageException if a commit referenced as a parent cannot be found.
     */
    public List<Commit> historyFrom(Repository repository, String startId) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (startId == null || startId.isBlank()) {
            throw new IllegalArgumentException("startId must not be null or blank");
        }

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        List<Commit> history = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentId = startId;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new StorageException("Cycle detected in commit history at: " + currentId);
            }
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            history.add(commit);
            currentId = commit.parentId();
        }
        return List.copyOf(history);
    }

    /**
     * Determines whether {@code ancestorId} is an ancestor of
     * {@code descendantId} — that is, whether walking the parent chain from
     * {@code descendantId} eventually reaches {@code ancestorId}.
     *
     * <p>This is the key test for fast-forward merge eligibility: if the
     * current branch tip is an ancestor of the target branch tip, the merge
     * can be performed by simply advancing the branch pointer.
     *
     * <p>If both ids are equal, this returns {@code true} (a commit is
     * considered its own ancestor).
     *
     * @param repository   the repository to inspect.
     * @param ancestorId   the commit id suspected to be an ancestor (non-blank).
     * @param descendantId the commit id to walk backwards from (non-blank).
     * @return {@code true} if {@code ancestorId} is reachable by walking the
     *         parent chain from {@code descendantId}.
     * @throws StorageException if a commit in the chain cannot be found.
     */
    public boolean isAncestor(Repository repository, String ancestorId, String descendantId) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (ancestorId == null || ancestorId.isBlank()) {
            throw new IllegalArgumentException("ancestorId must not be null or blank");
        }
        if (descendantId == null || descendantId.isBlank()) {
            throw new IllegalArgumentException("descendantId must not be null or blank");
        }

        if (ancestorId.equals(descendantId)) {
            return true;
        }

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        Set<String> visited = new HashSet<>();
        String currentId = descendantId;
        while (currentId != null) {
            if (currentId.equals(ancestorId)) {
                return true;
            }
            if (!visited.add(currentId)) {
                return false; // cycle — ancestor not found
            }
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            currentId = commit.parentId();
        }
        return false;
    }
}
