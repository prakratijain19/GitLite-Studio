package app.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import app.model.ChangeType;
import app.model.Commit;
import app.model.FileSnapshot;
import app.model.Repository;
import app.model.StatusReport;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.IndexStorage;
import app.storage.StorageException;
import app.storage.StorageFactory;
import app.util.FileUtil;

/**
 * Computes the status of a repository by comparing three trees — the HEAD commit,
 * the staging index, and the working tree — the equivalent of {@code git status}.
 *
 * <p>The comparison is entirely content-addressed: each tree is reduced to a map
 * of repository-relative path to blob id, and the maps are compared. The working
 * tree is scanned by hashing each file's current content, using the same path
 * relativization and normalization as the staging layer so that keys align
 * exactly across the three trees. This is a read-only query; it re-hashes working
 * files on each call rather than relying on any modification-time cache.
 *
 * <p>It is application-scoped and injected with a {@link HashService}, a
 * {@link StorageFactory} for repository-scoped storage, and (built from that
 * factory) a {@link BranchService} to resolve the current commit.
 */
public final class StatusService {

    private final StorageFactory storageFactory;
    private final HashService hashService;
    private final BranchService branchService;

    /** Creates a service wired with default collaborators. */
    public StatusService() {
        this(new DefaultStorageFactory(), new HashService());
    }

    /**
     * Creates a service with explicit collaborators. Intended for tests.
     *
     * @param storageFactory the factory used to obtain repository-scoped storage.
     * @param hashService    the service used to hash working-tree files.
     */
    public StatusService(StorageFactory storageFactory, HashService hashService) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.hashService = Objects.requireNonNull(hashService, "hashService must not be null");
        this.branchService = new BranchService(storageFactory);
    }

    /**
     * Computes the status of the repository.
     *
     * @param repository the repository to inspect.
     * @return a {@link StatusReport} describing staged, unstaged, and untracked
     *         changes.
     * @throws StorageException if repository data or the working tree cannot be read.
     */
    public StatusReport status(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");

        Map<String, String> head = headManifest(repository);
        Map<String, String> index = indexManifest(repository);
        Map<String, String> working = workingManifest(repository);

        Map<String, ChangeType> staged = compare(head, index);
        Map<String, ChangeType> unstaged = compareTracked(index, working);
        TreeSet<String> untracked = new TreeSet<>();
        for (String path : working.keySet()) {
            if (!index.containsKey(path)) {
                untracked.add(path);
            }
        }
        return new StatusReport(staged, unstaged, untracked);
    }

    /**
     * Compares a newer tree against an older one, reporting additions,
     * modifications, and deletions across their combined paths.
     */
    private static Map<String, ChangeType> compare(Map<String, String> older, Map<String, String> newer) {
        Map<String, ChangeType> changes = new TreeMap<>();
        newer.forEach((path, blobId) -> {
            String previous = older.get(path);
            if (previous == null) {
                changes.put(path, ChangeType.ADDED);
            } else if (!previous.equals(blobId)) {
                changes.put(path, ChangeType.MODIFIED);
            }
        });
        older.keySet().forEach(path -> {
            if (!newer.containsKey(path)) {
                changes.put(path, ChangeType.DELETED);
            }
        });
        return changes;
    }

    /**
     * Compares the working tree against the index over tracked paths only. Paths
     * present in the working tree but not the index are untracked, not reported
     * here.
     */
    private static Map<String, ChangeType> compareTracked(Map<String, String> index, Map<String, String> working) {
        Map<String, ChangeType> changes = new TreeMap<>();
        index.forEach((path, blobId) -> {
            String current = working.get(path);
            if (current == null) {
                changes.put(path, ChangeType.DELETED);
            } else if (!current.equals(blobId)) {
                changes.put(path, ChangeType.MODIFIED);
            }
        });
        return changes;
    }

    private Map<String, String> headManifest(Repository repository) {
        Optional<String> tip = branchService.currentTip(repository);
        if (tip.isEmpty()) {
            return Map.of();
        }
        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        Commit commit = commitStorage.readCommit(tip.get())
                .orElseThrow(() -> new StorageException("HEAD commit not found: " + tip.get()));
        return toManifest(commit.manifest());
    }

    private Map<String, String> indexManifest(Repository repository) {
        IndexStorage indexStorage = storageFactory.createIndexStorage(repository.getMetadataPath());
        return toManifest(indexStorage.readIndex().getSnapshots());
    }

    private Map<String, String> workingManifest(Repository repository) {
        Path root = repository.getRootPath().toAbsolutePath().normalize();
        List<Path> files;
        try {
            files = FileUtil.listRegularFiles(root);
        } catch (IOException e) {
            throw new StorageException("Failed to scan working tree at " + root, e);
        }

        Map<String, String> manifest = new HashMap<>();
        for (Path file : files) {
            String relative = FileSnapshot.normalizePath(root.relativize(file).toString());
            if (isInsideMetadata(relative)) {
                continue;
            }
            try {
                manifest.put(relative, hashService.createBlob(FileUtil.readBytes(file)).getId());
            } catch (IOException e) {
                throw new StorageException("Failed to read working file " + file, e);
            }
        }
        return manifest;
    }

    private static Map<String, String> toManifest(List<FileSnapshot> snapshots) {
        Map<String, String> manifest = new HashMap<>();
        for (FileSnapshot snapshot : snapshots) {
            manifest.put(snapshot.path(), snapshot.blobId());
        }
        return manifest;
    }

    private static boolean isInsideMetadata(String relativePath) {
        return relativePath.equals(Repository.METADATA_DIR_NAME)
                || relativePath.startsWith(Repository.METADATA_DIR_NAME + "/");
    }
}
