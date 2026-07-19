package app.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import app.model.Commit;
import app.model.FileSnapshot;
import app.model.Index;
import app.model.MergeConflict;
import app.model.MergeResult;
import app.model.Repository;
import app.model.StatusReport;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.FileStorage;
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
 *   <li><strong>Three-way merge:</strong> Both branches have diverged from a
 *       common ancestor. Auto-resolvable changes are merged. If conflicts
 *       exist, conflict markers are written and a {@link MergeConflictException}
 *       is thrown, leaving the repository in a MERGE_HEAD state.</li>
 * </ul>
 */
public final class MergeService {

    private static final Logger LOGGER = Logger.getLogger(MergeService.class.getName());

    private final StorageFactory storageFactory;
    private final BranchService branchService;
    private final HistoryService historyService;
    private final StatusService statusService;
    private final CommitService commitService;

    public MergeService() {
        this(new DefaultStorageFactory(), new HashService(), Clock.systemUTC());
    }

    public MergeService(StorageFactory storageFactory, HashService hashService, Clock clock) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        Objects.requireNonNull(hashService, "hashService must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        this.branchService = new BranchService(storageFactory);
        this.historyService = new HistoryService(storageFactory);
        this.statusService = new StatusService(storageFactory, hashService);
        this.commitService = new CommitService(storageFactory, hashService, clock);
    }

    public MergeResult merge(Repository repository, String branchName) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branch name must not be null or blank");
        }

        String currentBranch = branchService.currentBranch(repository);
        if (currentBranch.equals(branchName)) {
            throw new IllegalArgumentException("Cannot merge a branch into itself: " + branchName);
        }

        String currentTip = branchService.currentTip(repository).orElseThrow(() ->
                new IllegalStateException("Cannot merge: current branch has no commits yet"));
        String sourceTip = branchService.tipOf(repository, branchName).orElseThrow(() ->
                new IllegalArgumentException(
                        "Cannot merge branch (missing or no commits): " + branchName));

        if (currentTip.equals(sourceTip) || historyService.isAncestor(repository, sourceTip, currentTip)) {
            return new MergeResult(MergeResult.Type.ALREADY_UP_TO_DATE, currentTip, "Already up to date.");
        }

        StatusReport status = statusService.status(repository);
        if (!status.stagedChanges().isEmpty() || !status.unstagedChanges().isEmpty()) {
            throw new IllegalStateException("Cannot merge with uncommitted changes; commit or discard them first");
        }

        if (historyService.isAncestor(repository, currentTip, sourceTip)) {
            return fastForward(repository, currentBranch, currentTip, sourceTip, branchName);
        }

        return threeWayMerge(repository, currentBranch, currentTip, sourceTip, branchName);
    }

    /**
     * Aborts a merge in progress, restoring the working tree and index to the
     * current branch tip and clearing the MERGE_HEAD state.
     *
     * @param repository the repository.
     * @throws IllegalStateException if there is no merge in progress.
     */
    public void abort(Repository repository) {
        Path metadataDir = repository.getMetadataPath();
        FileStorage fileStorage = storageFactory.createFileStorage(metadataDir);

        if (!fileStorage.hasMergeHead()) {
            throw new IllegalStateException("There is no merge to abort (MERGE_HEAD missing).");
        }

        String currentTip = branchService.currentTip(repository)
                .orElseThrow(() -> new StorageException("Current branch has no tip"));

        CommitStorage commitStorage = storageFactory.createCommitStorage(metadataDir);
        List<FileSnapshot> targetManifest = commitStorage.readCommit(currentTip)
                .orElseThrow(() -> new StorageException("Commit missing: " + currentTip))
                .manifest();

        rewriteWorkingTree(repository, targetManifest);
        fileStorage.deleteMergeHead();
        LOGGER.info(() -> "Merge aborted. Working tree and index restored to " + currentTip);
    }

    // ── Fast-forward merge ──────────────────────────────────────────────

    private MergeResult fastForward(Repository repository, String currentBranch,
                                    String currentTip, String sourceTip,
                                    String sourceBranch) {
        Path metadataDir = repository.getMetadataPath();
        CommitStorage commitStorage = storageFactory.createCommitStorage(metadataDir);
        Commit targetCommit = commitStorage.readCommit(sourceTip)
                .orElseThrow(() -> new StorageException("Source branch tip commit not found: " + sourceTip));
        List<FileSnapshot> targetManifest = targetCommit.manifest();

        rewriteWorkingTree(repository, targetManifest);
        branchService.advanceTip(repository, currentBranch, sourceTip);

        String message = "Fast-forward merge: " + currentBranch + " -> " + sourceBranch + " (" + shortId(sourceTip) + ")";
        LOGGER.info(() -> message);

        return new MergeResult(MergeResult.Type.FAST_FORWARD, sourceTip, message);
    }

    // ── Three-way merge ─────────────────────────────────────────────────

    private MergeResult threeWayMerge(Repository repository, String currentBranch,
                                      String currentTip, String sourceTip,
                                      String sourceBranch) {
        Path metadataDir = repository.getMetadataPath();
        CommitStorage commitStorage = storageFactory.createCommitStorage(metadataDir);

        String mergeBaseId = historyService.findMergeBase(repository, currentTip, sourceTip)
                .orElseThrow(() -> new StorageException("Cannot find common ancestor for '" + currentBranch + "' and '" + sourceBranch + "'"));

        Map<String, String> baseManifest = toManifestMap(commitStorage, mergeBaseId);
        Map<String, String> oursManifest = toManifestMap(commitStorage, currentTip);
        Map<String, String> theirsManifest = toManifestMap(commitStorage, sourceTip);

        ResolutionResult resolution = resolveThreeWay(baseManifest, oursManifest, theirsManifest);

        rewriteWorkingTree(repository, resolution.mergedManifest());

        if (!resolution.conflicts().isEmpty()) {
            writeConflictedFiles(repository, resolution.conflicts(), currentBranch, sourceBranch);
            
            FileStorage fileStorage = storageFactory.createFileStorage(metadataDir);
            fileStorage.writeMergeHead(sourceTip);
            
            MergeResult result = new MergeResult(MergeResult.Type.CONFLICTED, currentTip, "Merge failed due to conflicts.");
            LOGGER.info(() -> "Three-way merge resulted in conflicts.");
            throw new MergeConflictException(result, resolution.conflicts());
        }

        String message = "Merge branch '" + sourceBranch + "' into " + currentBranch;
        Commit mergeCommit = commitService.mergeCommit(repository, message, currentTip, sourceTip, resolution.mergedManifest());

        LOGGER.info(() -> "Three-way merge: " + currentBranch + " + " + sourceBranch + " -> " + shortId(mergeCommit.id()) + " (base: " + shortId(mergeBaseId) + ")");
        return new MergeResult(MergeResult.Type.MERGED, mergeCommit.id(), message);
    }

    private record ResolutionResult(List<FileSnapshot> mergedManifest, List<MergeConflict> conflicts) {}

    private static ResolutionResult resolveThreeWay(
            Map<String, String> base, Map<String, String> ours, Map<String, String> theirs) {

        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(base.keySet());
        allPaths.addAll(ours.keySet());
        allPaths.addAll(theirs.keySet());

        List<FileSnapshot> merged = new ArrayList<>();
        List<MergeConflict> conflicts = new ArrayList<>();

        for (String path : allPaths) {
            String baseBlobId = base.get(path);
            String ourBlobId = ours.get(path);
            String theirBlobId = theirs.get(path);

            if (Objects.equals(ourBlobId, theirBlobId)) {
                if (ourBlobId != null) merged.add(new FileSnapshot(path, ourBlobId));
                continue;
            }
            if (Objects.equals(baseBlobId, ourBlobId)) {
                if (theirBlobId != null) merged.add(new FileSnapshot(path, theirBlobId));
                continue;
            }
            if (Objects.equals(baseBlobId, theirBlobId)) {
                if (ourBlobId != null) merged.add(new FileSnapshot(path, ourBlobId));
                continue;
            }

            // Conflict. Keep our version in the index so it doesn't get completely deleted.
            if (ourBlobId != null) {
                merged.add(new FileSnapshot(path, ourBlobId));
            }
            conflicts.add(new MergeConflict(path, baseBlobId, ourBlobId, theirBlobId));
        }

        merged.sort(Comparator.comparing(FileSnapshot::path));
        return new ResolutionResult(merged, conflicts);
    }

    private void writeConflictedFiles(Repository repository, List<MergeConflict> conflicts,
                                      String ourBranch, String theirBranch) {
        Path root = repository.getRootPath();
        ObjectStorage objectStorage = storageFactory.createObjectStorage(repository.getMetadataPath());

        for (MergeConflict conflict : conflicts) {
            String oursText = readBlobText(objectStorage, conflict.ourBlobId());
            String theirsText = readBlobText(objectStorage, conflict.theirBlobId());

            String markers = "<<<<<<< " + ourBranch + "\n"
                    + oursText
                    + "=======\n"
                    + theirsText
                    + ">>>>>>> " + theirBranch + "\n";

            writeWorkingFile(root, conflict.path(), markers.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readBlobText(ObjectStorage objectStorage, String blobId) {
        if (blobId == null) {
            return "";
        }
        byte[] content = objectStorage.readBlob(blobId)
                .orElseThrow(() -> new StorageException("Missing blob: " + blobId))
                .getContent();
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && !text.endsWith("\n")) {
            text += "\n";
        }
        return text;
    }

    // ── Shared helpers ──────────────────────────────────────────────────

    private static Map<String, String> toManifestMap(CommitStorage commitStorage, String commitId) {
        Commit commit = commitStorage.readCommit(commitId)
                .orElseThrow(() -> new StorageException("Commit not found: " + commitId));
        Map<String, String> manifest = new LinkedHashMap<>();
        for (FileSnapshot entry : commit.manifest()) {
            manifest.put(entry.path(), entry.blobId());
        }
        return manifest;
    }

    private void rewriteWorkingTree(Repository repository, List<FileSnapshot> targetManifest) {
        Path metadataDir = repository.getMetadataPath();
        Path root = repository.getRootPath();

        Set<String> targetPaths = targetManifest.stream()
                .map(FileSnapshot::path)
                .collect(Collectors.toSet());

        IndexStorage indexStorage = storageFactory.createIndexStorage(metadataDir);
        for (FileSnapshot tracked : indexStorage.readIndex().getSnapshots()) {
            if (!targetPaths.contains(tracked.path())) {
                deleteWorkingFile(root, tracked.path());
            }
        }

        ObjectStorage objectStorage = storageFactory.createObjectStorage(metadataDir);
        for (FileSnapshot entry : targetManifest) {
            byte[] content = objectStorage.readBlob(entry.blobId())
                    .orElseThrow(() -> new StorageException("Blob missing for " + entry.path() + ": " + entry.blobId()))
                    .getContent();
            writeWorkingFile(root, entry.path(), content);
        }

        indexStorage.writeIndex(Index.fromSnapshots(targetManifest));
    }

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
