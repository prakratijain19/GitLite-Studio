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
 * Switches the repository onto another branch — the equivalent of
 * {@code git switch} / {@code git checkout <branch>}.
 *
 * <p>Switching rewrites the working tree, index, and {@code HEAD} to match the
 * target branch's tip commit: files tracked now but absent from the target are
 * deleted, the target's files are written from their blobs, the index is set to
 * the target snapshot, and {@code HEAD} is pointed at the branch.
 *
 * <p>This is the first operation that writes user files, so it is deliberately
 * safe: it <strong>refuses</strong> if the working tree has staged or unstaged
 * changes, rather than overwriting them. Untracked files are left untouched. Only
 * branch targets are supported; detached HEAD is a future capability.
 *
 * <p>It is application-scoped and injected with a {@link StorageFactory} and
 * {@link HashService}, from which it builds a {@link StatusService} (for the
 * dirty check) and a {@link BranchService} (to resolve the tip and move HEAD).
 */
public final class CheckoutService {

    private static final Logger LOGGER = Logger.getLogger(CheckoutService.class.getName());

    private final StorageFactory storageFactory;
    private final StatusService statusService;
    private final BranchService branchService;

    /** Creates a service wired with default collaborators. */
    public CheckoutService() {
        this(new DefaultStorageFactory(), new HashService());
    }

    /**
     * Creates a service with explicit collaborators. Intended for tests.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     * @param hashService    the service used by the status dirty-check.
     */
    public CheckoutService(StorageFactory storageFactory, HashService hashService) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.statusService = new StatusService(storageFactory, hashService);
        this.branchService = new BranchService(storageFactory);
    }

    /**
     * Switches the working tree, index, and {@code HEAD} to the given branch.
     *
     * @param repository the repository to switch.
     * @param branchName the branch to switch to; must exist and have a commit.
     * @return the branch now checked out.
     * @throws IllegalArgumentException if the branch does not exist or has no commit.
     * @throws IllegalStateException    if the working tree has uncommitted changes.
     * @throws StorageException         if repository data or the working tree
     *                                  cannot be read or written.
     */
    public Branch checkout(Repository repository, String branchName) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branch name must not be null or blank");
        }

        String targetTip = branchService.tipOf(repository, branchName).orElseThrow(() ->
                new IllegalArgumentException("Cannot switch to branch (missing or no commits): " + branchName));

        StatusReport status = statusService.status(repository);
        if (!status.stagedChanges().isEmpty() || !status.unstagedChanges().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot switch branches with uncommitted changes; commit or discard them first");
        }

        Path metadataDir = repository.getMetadataPath();
        Commit target = storageFactory.createCommitStorage(metadataDir).readCommit(targetTip)
                .orElseThrow(() -> new StorageException("Branch tip commit not found: " + targetTip));
        List<FileSnapshot> targetManifest = target.manifest();
        Set<String> targetPaths = targetManifest.stream()
                .map(FileSnapshot::path)
                .collect(Collectors.toSet());

        Path root = repository.getRootPath();
        IndexStorage indexStorage = storageFactory.createIndexStorage(metadataDir);

        // Remove tracked files that the target no longer has.
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

        // Point the index and HEAD at the target.
        indexStorage.writeIndex(Index.fromSnapshots(targetManifest));
        branchService.setCurrentBranch(repository, branchName);

        LOGGER.info(() -> "Switched to branch '" + branchName + "' at " + targetTip);
        return Branch.at(branchName, targetTip);
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
