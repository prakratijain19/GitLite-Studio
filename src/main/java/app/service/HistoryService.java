package app.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        List<Commit> history = new ArrayList<>();
        String currentId = tip.get();
        while (currentId != null) {
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            history.add(commit);
            currentId = commit.parentId();
        }
        return List.copyOf(history);
    }
}
