package app.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import app.model.Branch;
import app.model.Commit;
import app.model.FileSnapshot;
import app.model.Index;
import app.model.MergeResult;
import app.model.Repository;
import app.model.StatusReport;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.IndexStorage;
import app.storage.ObjectStorage;
import app.storage.StorageException;
import app.storage.StorageFactory;
import app.util.FileUtil;

/**
 * Merges one branch into the current branch — the equivalent of
 * {@code git merge <branch>}.
 *
 * <p>This service supports the following merge strategies:
 * <ul>
 *   <li><strong>Already up-to-date:</strong> The target is an ancestor of the
 *       current branch. Nothing to merge.</li>
 *   <li><strong>Fast-forward:</strong> The current branch tip is an ancestor of
 *       the target branch tip. The branch pointer is simply advanced and the
 *       working tree is rewritten to match the target commit.</li>
 * </ul>
 *
 * <p>Three-way merge and conflict resolution are future milestones.
 *
 * <p>Like {@link CheckoutService}, the merge rewrites the working tree. It
 * <strong>refuses</strong> to proceed if the tree has uncommitted changes.
 *
 * <p>It is application-scoped and injected with a {@link StorageFactory} and
 * {@link HashService}, from which it builds collaborating services.
 */
public final class MergeService {

    private static final Logger LOGGER = Logger.getLogger(MergeService.class.getName());

    private final StorageFactory storageFactory;
    private final BranchService branchService;
    private final HistoryService historyService;
    private final StatusService statusService;

    /** Creates a service wired with default collaborators. */
    public MergeService() {
        this(new DefaultStorageFactory(), new HashService());
    }

    /**
     * Creates a service with explicit collaborators. Intended for tests.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     * @param hashService    the service used by the status dirty-check.
     */
    public MergeService(StorageFactory storageFactory, HashService hashService) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        Objects.requireNonNull(hashService, "hashService must not be null");
        this.branchService = new BranchService(storageFactory);
        this.historyService = new HistoryService(storageFactory);
        this.statusService = new StatusService(storageFactory, hashService);
    }

    /**
     * Merges the given branch into the current branch.
     *
     * <p>If the current branch tip is an ancestor of the source branch tip, a
     * <strong>fast-forward</strong> merge is performed: the branch pointer is
     * advanced and the working tree is rewritten to match the target. If the
     * source is already an ancestor of the current branch, the merge is
     * <strong>already up-to-date</strong> and nothing changes.
     *
     * @param repository  the repository to merge in.
     * @param branchName  the name of the branch to merge into the current branch.
     * @return a {@link MergeResult} describing what happened.
     * @throws IllegalArgumentException if the branch does not exist or has no commits.
     * @throws IllegalStateException    if the working tree has uncommitted changes,
     *                                  or the current branch has no commits yet.
     * @throws StorageException         if repository data cannot be read or written.
     */
    public MergeResult merge(Repository repository, String branchName) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branch name must not be null or blank");
        }

        String currentBranch = branchService.currentBranch(repository);

        if (currentBranch.equals(branchName)) {
            throw new IllegalArgumentException("Cannot merge a branch into itself: " + branchName);
        }

        // Resolve both branch tips.
        String currentTip = branchService.currentTip(repository).orElseThrow(() ->
                new IllegalStateException("Cannot merge: current branch has no commits yet"));
        String sourceTip = branchService.tipOf(repository, branchName).orElseThrow(() ->
                new IllegalArgumentException(
                        "Cannot merge branch (missing or no commits): " + branchName));

        // Trivial: already at the same commit.
        if (currentTip.equals(sourceTip)) {
            return new MergeResult(MergeResult.Type.ALREADY_UP_TO_DATE, currentTip,
                    "Already up to date.");
        }

        // Check if the source is already reachable from the current branch.
        if (historyService.isAncestor(repository, sourceTip, currentTip)) {
            return new MergeResult(MergeResult.Type.ALREADY_UP_TO_DATE, currentTip,
                    "Already up to date.");
        }

        // Check if fast-forward is possible: current tip is ancestor of source tip.
        if (historyService.isAncestor(repository, currentTip, sourceTip)) {
            return fastForward(repository, currentBranch, currentTip, sourceTip, branchName);
        }

        // Diverged branches — three-way merge needed (future milestone).
        throw new UnsupportedOperationException(
                "Three-way merge is not yet supported. "
                        + "Branches '" + currentBranch + "' and '" + branchName
                        + "' have diverged.");
    }

    /**
     * Performs a fast-forward merge by advancing the current branch to the
     * source tip and rewriting the working tree and index to match.
     */
    private MergeResult fastForward(Repository repository, String currentBranch,
                                    String currentTip, String sourceTip,
                                    String sourceBranch) {
        // Guard: refuse if working tree is dirty.
        StatusReport status = statusService.status(repository);
        if (!status.stagedChanges().isEmpty() || !status.unstagedChanges().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot merge with uncommitted changes; commit or discard them first");
        }

        Path metadataDir = repository.getMetadataPath();
        Path root = repository.getRootPath();

        // Read the target commit's manifest.
        CommitStorage commitStorage = storageFactory.createCommitStorage(metadataDir);
        Commit targetCommit = commitStorage.readCommit(sourceTip)
                .orElseThrow(() -> new StorageException(
                        "Source branch tip commit not found: " + sourceTip));
        List<FileSnapshot> targetManifest = targetCommit.manifest();
        Set<String> targetPaths = targetManifest.stream()
                .map(FileSnapshot::path)
                .collect(Collectors.toSet());

        // Read the current index to determine which tracked files to remove.
        IndexStorage indexStorage = storageFactory.createIndexStorage(metadataDir);
        for (FileSnapshot tracked : indexStorage.readIndex().getSnapshots()) {
            if (!targetPaths.contains(tracked.path())) {
                deleteWorkingFile(root, tracked.path());
            }
        }

        // Write the target's files from their blobs.
        ObjectStorage objectStorage = storageFactory.createObjectStorage(metadataDir);
        for (FileSnapshot entry : targetManifest) {
            byte[] content = objectStorage.readBlob(entry.blobId())
                    .orElseThrow(() -> new StorageException(
                            "Blob missing for " + entry.path() + ": " + entry.blobId()))
                    .getContent();
            writeWorkingFile(root, entry.path(), content);
        }

        // Update the index to the target snapshot.
        indexStorage.writeIndex(Index.fromSnapshots(targetManifest));

        // Advance the branch tip.
        branchService.advanceTip(repository, currentBranch, sourceTip);

        String message = "Fast-forward merge: " + currentBranch + " -> " + sourceBranch
                + " (" + shortId(sourceTip) + ")";
        LOGGER.info(() -> message);

        return new MergeResult(MergeResult.Type.FAST_FORWARD, sourceTip, message);
    }

    /**
     * Returns the first 7 characters of a commit id for display, mirroring
     * Git's short-hash convention.
     */
    private static String shortId(String commitId) {
        return commitId.length() > 7 ? commitId.substring(0, 7) : commitId;
    }

    private static void writeWorkingFile(Path root, String relativePath, byte[] content) {
        Path file = root.resolve(relativePath);
        try {
            FileUtil.ensureDirectory(file.getParent());
            FileUtil.writeBytes(file, content);
        } catch (IOException e) {
            throw new StorageException("Failed to write working file " + file, e);
        }
    }

    private static void deleteWorkingFile(Path root, String relativePath) {
        Path file = root.resolve(relativePath);
        if (!FileUtil.exists(file)) {
            return;
        }
        try {
            FileUtil.delete(file);
        } catch (IOException e) {
            throw new StorageException("Failed to delete working file " + file, e);
        }
    }
}
