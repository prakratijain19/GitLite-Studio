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
import app.storage.FileStorage;
import app.storage.IndexStorage;
import app.storage.StorageException;
import app.storage.StorageFactory;

/**
 * Creates commits — freezing the staging index into permanent history.
 */
public final class CommitService {

    private static final Logger LOGGER = Logger.getLogger(CommitService.class.getName());

    private final StorageFactory storageFactory;
    private final HashService hashService;
    private final BranchService branchService;
    private final Clock clock;

    public CommitService() {
        this(new DefaultStorageFactory(), new HashService(), Clock.systemUTC());
    }

    public CommitService(StorageFactory storageFactory, HashService hashService, Clock clock) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.hashService = Objects.requireNonNull(hashService, "hashService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.branchService = new BranchService(storageFactory);
    }

    /**
     * Commits the current staging index on the branch that HEAD points at.
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

        FileStorage fileStorage = storageFactory.createFileStorage(repository.getMetadataPath());
        String secondParentId = fileStorage.readMergeHead().orElse(null);
        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());

        if (secondParentId == null) {
            if (parentId != null && isUnchanged(commitStorage, parentId, manifest)) {
                throw new NothingToCommitException(
                        "Nothing to commit: no changes since the last commit");
            }
        } else {
            Commit commit = mergeCommit(repository, message, parentId, secondParentId, manifest);
            fileStorage.deleteMergeHead();
            return commit;
        }

        Instant timestamp = Instant.now(clock);
        String author = repository.getUserName();
        String id = hashService.createCommitId(message, author, timestamp, parentId, null, manifest);
        Commit commit = new Commit(id, message, author, timestamp, parentId, null, manifest);

        commitStorage.writeCommit(commit);
        branchService.advanceTip(repository, branch, id);

        LOGGER.info(() -> "Committed " + id + " on branch '" + branch + "' ("
                + manifest.size() + " file(s))");
        return commit;
    }

    /**
     * Creates a merge commit with two parents and writes it to the repository.
     */
    public Commit mergeCommit(Repository repository, String message,
                              String parentId, String secondParentId,
                              List<FileSnapshot> manifest) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("commit message must not be null or blank");
        }
        Objects.requireNonNull(parentId, "parentId must not be null");
        Objects.requireNonNull(secondParentId, "secondParentId must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");

        String branch = branchService.currentBranch(repository);
        Instant timestamp = Instant.now(clock);
        String author = repository.getUserName();
        String id = hashService.createCommitId(message, author, timestamp,
                parentId, secondParentId, manifest);
        Commit commit = new Commit(id, message, author, timestamp,
                parentId, secondParentId, manifest);

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        commitStorage.writeCommit(commit);
        branchService.advanceTip(repository, branch, id);

        LOGGER.info(() -> "Merge commit " + id + " on branch '" + branch + "' ("
                + manifest.size() + " file(s), parents: " + parentId.substring(0, 7)
                + " + " + secondParentId.substring(0, 7) + ")");
        return commit;
    }

    private static boolean isUnchanged(CommitStorage commitStorage, String parentId,
                                       List<FileSnapshot> manifest) {
        Commit parent = commitStorage.readCommit(parentId).orElseThrow(() ->
                new StorageException("Parent commit not found: " + parentId));
        return parent.manifest().equals(manifest);
    }
}
