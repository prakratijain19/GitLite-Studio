package app.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import app.model.Commit;
import app.model.Repository;
import app.storage.CommitStorage;
import app.storage.DefaultStorageFactory;
import app.storage.StorageException;
import app.storage.StorageFactory;

/**
 * Queries the commit history of a repository.
 */
public final class HistoryService {

    private final StorageFactory storageFactory;
    private final BranchService branchService;

    public HistoryService() {
        this(new DefaultStorageFactory());
    }

    public HistoryService(StorageFactory storageFactory) {
        this.storageFactory = Objects.requireNonNull(storageFactory, "storageFactory must not be null");
        this.branchService = new BranchService(storageFactory);
    }

    /**
     * Returns the commit history of the current branch, most recent first.
     * Follows only first parents (like git log --first-parent).
     */
    public List<Commit> history(Repository repository) {
        Objects.requireNonNull(repository, "repository must not be null");
        String tipId = branchService.currentTip(repository).orElse(null);
        if (tipId == null) {
            return List.of();
        }
        return historyFrom(repository, tipId);
    }

    /**
     * Returns the commit history starting from the given commit id, most recent first.
     * Follows only first parents. Includes cycle detection.
     */
    public List<Commit> historyFrom(Repository repository, String startCommitId) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (startCommitId == null || startCommitId.isBlank()) {
            throw new IllegalArgumentException("startCommitId must not be null or blank");
        }

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        List<Commit> commits = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentId = startCommitId;

        while (currentId != null) {
            if (!visited.add(currentId)) {
                break; // cycle detected
            }
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            commits.add(commit);
            currentId = commit.parentId();
        }
        return commits;
    }

    /**
     * Checks whether {@code ancestorId} is reachable from {@code descendantId}
     * by walking the parent chain backwards (BFS, follows both parents of merge commits).
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
        Queue<String> queue = new ArrayDeque<>();
        queue.add(descendantId);
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (currentId.equals(ancestorId)) {
                return true;
            }
            if (!visited.add(currentId)) {
                continue;
            }
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            for (String pid : commit.parents()) {
                if (!visited.contains(pid)) {
                    queue.add(pid);
                }
            }
        }
        return false;
    }

    /**
     * Finds the merge base (most recent common ancestor) of two commits.
     */
    public Optional<String> findMergeBase(Repository repository,
                                           String commitIdA, String commitIdB) {
        Objects.requireNonNull(repository, "repository must not be null");
        if (commitIdA == null || commitIdA.isBlank()) {
            throw new IllegalArgumentException("commitIdA must not be null or blank");
        }
        if (commitIdB == null || commitIdB.isBlank()) {
            throw new IllegalArgumentException("commitIdB must not be null or blank");
        }

        if (commitIdA.equals(commitIdB)) {
            return Optional.of(commitIdA);
        }

        CommitStorage commitStorage = storageFactory.createCommitStorage(repository.getMetadataPath());
        Set<String> ancestorsOfA = collectAncestors(commitStorage, commitIdA);

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(commitIdB);
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (ancestorsOfA.contains(currentId)) {
                return Optional.of(currentId);
            }
            if (!visited.add(currentId)) {
                continue;
            }
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            for (String pid : commit.parents()) {
                if (!visited.contains(pid)) {
                    queue.add(pid);
                }
            }
        }
        return Optional.empty();
    }

    private static Set<String> collectAncestors(CommitStorage commitStorage, String startId) {
        Set<String> ancestors = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (!ancestors.add(currentId)) {
                continue;
            }
            final String lookup = currentId;
            Commit commit = commitStorage.readCommit(lookup)
                    .orElseThrow(() -> new StorageException("Commit not found: " + lookup));
            for (String pid : commit.parents()) {
                if (!ancestors.contains(pid)) {
                    queue.add(pid);
                }
            }
        }
        return ancestors;
    }
}
