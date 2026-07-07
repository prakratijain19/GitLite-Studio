package app.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import app.model.Commit;
import app.model.FileSnapshot;
import app.model.Index;
import app.model.Repository;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.IndexStorage;
import app.storage.StorageException;
import app.storage.StorageFactory;

/**
 * Creates commits — freezing the staging index into permanent history.
 *
 * <p>Committing resolves the current branch and its tip (the parent), snapshots
 * the staged index, writes the commit object, and advances the branch tip — which
 * births a previously unborn branch on its first commit. The staging index is
 * left untouched afterwards, so it continues to mirror the new commit.
 *
 * <p>The service owns commit <em>policy</em> and delegates disk work to the
 * storage layer. It is application-scoped and receives a {@link Repository} per
 * call. Its collaborators are injected: a {@link HashService} for commit
 * identity, a {@link StorageFactory} for repository-scoped storage, and a
 * {@link Clock} so that commit timestamps — and therefore the content-addressed
 * commit ids that depend on them — are deterministic under test.
 */
public final class CommitService {

    private static final Logger LOGGER = Logger.getLogger(CommitService.class.getName());

    private final StorageFactory storageFactory;
    private final HashService hashService;
    private final BranchService branchService;
    private final Clock clock;

    /** Creates a service wired with default collaborators and the system UTC clock. */
    public CommitService() {
        this(new DefaultStorageFactory(), new HashService(), Clock.systemUTC());
    }

    /**
     * Creates a service with explicit collaborators. Intended for tests, which may
     * supply a fixed {@link Clock} for deterministic commit ids.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     * @param hashService    the service used to compute commit ids.
     * @param clock          the clock used to timestamp commits.
     */
    public CommitService(StorageFactory storageFactory, HashService hashService, Clock clock) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.hashService = Objects.requireNonNull(hashService, "hashService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.branchService = new BranchService(storageFactory);
    }

    /**
     * Commits the current staging index on the branch that {@code HEAD} points at.
     *
     * @param repository the repository to commit in.
     * @param message    the commit message (non-blank).
     * @return the created {@link Commit}.
     * @throws IllegalArgumentException   if the message is null or blank.
     * @throws NothingToCommitException   if the index is empty or unchanged from
     *                                    the current branch tip.
     * @throws StorageException           if repository data cannot be read or written.
     */
    public Commit commit(Repository repository, String message) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("commit message must not be null or blank");
        }

        String branch = branchService.currentBranch(repository);
        String parentId = branchService.tipOf(repository, branch).orElse(null);

        IndexStorage indexStorage = storageFactory.createIndexStorage(repository.getMetadataPath());
        Index index = indexStorage.readIndex();
        if (index.isEmpty()) {
            throw new NothingToCommitException("Nothing to commit: the staging area is empty");
        }
        List<FileSnapshot> manifest = index.getSnapshots();

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        if (parentId != null && isUnchanged(commitStorage, parentId, manifest)) {
            throw new NothingToCommitException(
                    "Nothing to commit: no changes since the last commit");
        }

        Instant timestamp = Instant.now(clock);
        String author = repository.getUserName();
        String id = hashService.createCommitId(message, author, timestamp, parentId, manifest);
        Commit commit = new Commit(id, message, author, timestamp, parentId, manifest);

        commitStorage.writeCommit(commit);
        branchService.advanceTip(repository, branch, id);

        LOGGER.info(() -> "Committed " + id + " on branch '" + branch + "' ("
                + manifest.size() + " file(s))");
        return commit;
    }

    /**
     * @return {@code true} if the staged manifest is identical to the parent
     *         commit's manifest, meaning there is nothing new to commit.
     */
    private static boolean isUnchanged(CommitStorage commitStorage, String parentId,
                                       List<FileSnapshot> manifest) {
        Commit parent = commitStorage.readCommit(parentId).orElseThrow(() ->
                new StorageException("Parent commit not found: " + parentId));
        return parent.manifest().equals(manifest);
    }
}
