package app.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import app.util.FileUtil;

/**
 * Repository-scoped storage for the plain-text and structural parts of a
 * {@code .gitlite} directory.
 *
 * <p>Each instance manages exactly one {@code .gitlite} metadata directory,
 * supplied at construction. Its responsibilities are the non-JSON parts of the
 * on-disk contract: creating the skeleton directory structure
 * ({@code objects/}, {@code commits/}, {@code branches/}), creating the empty
 * {@code index} staging file, and reading/writing the {@code HEAD} pointer.
 * JSON configuration is handled separately by {@link JsonStorage}.
 *
 * <p>This class is the boundary at which low-level {@link IOException}s from
 * {@link FileUtil} are translated into {@link StorageException}s carrying
 * business context. It contains no Git semantics: it does not know the format of
 * the {@code HEAD} ref string, only how to store and retrieve its text. Composing
 * that ref string is the responsibility of the service layer.
 */
public final class FileStorage {

    /** Subdirectory holding content-addressed blob objects. */
    public static final String OBJECTS_DIR = "objects";
    /** Subdirectory holding one file per commit. */
    public static final String COMMITS_DIR = "commits";
    /** Subdirectory holding one file per branch (its tip commit ID). */
    public static final String BRANCHES_DIR = "branches";
    /** The staging-area file. */
    public static final String INDEX_FILE = "index";
    /** The HEAD pointer file. */
    public static final String HEAD_FILE = "HEAD";

    private final Path metadataDir;

    /**
     * @param metadataDir the {@code .gitlite} directory this instance manages
     *                    (typically {@code Repository.getMetadataPath()}).
     */
    public FileStorage(Path metadataDir) {
        this.metadataDir = Objects.requireNonNull(metadataDir, "metadataDir must not be null");
    }

    /**
     * @return {@code true} if the managed {@code .gitlite} directory already
     *         exists, which callers treat as a signal that the repository is
     *         already initialized.
     */
    public boolean isInitialized() {
        return FileUtil.exists(metadataDir) && FileUtil.isDirectory(metadataDir);
    }

    /**
     * Creates the {@code .gitlite} directory and its skeleton subdirectories
     * ({@code objects/}, {@code commits/}, {@code branches/}). Directory
     * creation is idempotent, so re-running is safe; guarding against re-init is
     * the caller's decision via {@link #isInitialized()}.
     *
     * @throws StorageException if any directory cannot be created.
     */
    public void createSkeleton() {
        try {
            FileUtil.ensureDirectory(metadataDir);
            FileUtil.ensureDirectory(metadataDir.resolve(OBJECTS_DIR));
            FileUtil.ensureDirectory(metadataDir.resolve(COMMITS_DIR));
            FileUtil.ensureDirectory(metadataDir.resolve(BRANCHES_DIR));
        } catch (IOException e) {
            throw new StorageException(
                    "Failed to create repository skeleton at " + metadataDir, e);
        }
    }

    /**
     * Creates the empty {@code index} staging file. Fails if it already exists,
     * so an accidental re-initialization over an existing repository is rejected.
     *
     * @throws StorageException if the index file cannot be created.
     */
    public void createIndex() {
        Path index = metadataDir.resolve(INDEX_FILE);
        try {
            FileUtil.createFile(index);
        } catch (IOException e) {
            throw new StorageException("Failed to create index file at " + index, e);
        }
    }

    /**
     * Writes the {@code HEAD} pointer content verbatim.
     *
     * @param content the exact text to store in {@code HEAD} (e.g. a git-style
     *                ref string composed by the service layer).
     * @throws StorageException if HEAD cannot be written.
     */
    public void writeHead(String content) {
        Path head = metadataDir.resolve(HEAD_FILE);
        try {
            FileUtil.writeString(head, content);
        } catch (IOException e) {
            throw new StorageException("Failed to write HEAD at " + head, e);
        }
    }

    /**
     * Reads the {@code HEAD} pointer content.
     *
     * @return the text stored in {@code HEAD}.
     * @throws StorageException if HEAD does not exist or cannot be read.
     */
    public String readHead() {
        Path head = metadataDir.resolve(HEAD_FILE);
        try {
            return FileUtil.readString(head);
        } catch (IOException e) {
            throw new StorageException("Failed to read HEAD at " + head, e);
        }
    }

    /**
     * Writes a branch's tip commit id to {@code branches/<branch>}, creating the
     * file (and any parent directories, so nested names like {@code feature/x}
     * work) or overwriting an existing tip. This is the operation that <em>births</em>
     * a previously unborn branch on its first commit.
     *
     * @param branchName the branch name.
     * @param commitId   the id of the commit the branch now points at.
     * @throws StorageException if the branch tip cannot be written.
     */
    public void writeBranchTip(String branchName, String commitId) {
        Path branch = branchPath(branchName);
        try {
            FileUtil.ensureDirectory(branch.getParent());
            FileUtil.writeString(branch, commitId);
        } catch (IOException e) {
            throw new StorageException("Failed to write branch tip at " + branch, e);
        }
    }

    /**
     * Reads a branch's tip commit id.
     *
     * @param branchName the branch name.
     * @return the tip commit id, or an empty optional if the branch has no file
     *         yet (an unborn branch).
     * @throws StorageException if the branch file exists but cannot be read.
     */
    public Optional<String> readBranchTip(String branchName) {
        Path branch = branchPath(branchName);
        if (!FileUtil.exists(branch)) {
            return Optional.empty();
        }
        try {
            return Optional.of(FileUtil.readString(branch).strip());
        } catch (IOException e) {
            throw new StorageException("Failed to read branch tip at " + branch, e);
        }
    }

    /**
     * @param branchName the branch name.
     * @return {@code true} if the branch has a tip file (it is born).
     */
    public boolean branchExists(String branchName) {
        return FileUtil.exists(branchPath(branchName));
    }

    /**
     * Lists the names of all branches (born branches, i.e. those with a tip file)
     * under {@code branches/}, sorted. Nested names such as {@code feature/x} are
     * returned with forward slashes.
     *
     * @return the sorted branch names.
     * @throws StorageException if the branches directory cannot be listed.
     */
    public List<String> listBranchNames() {
        Path branchesDir = metadataDir.resolve(BRANCHES_DIR);
        try {
            return FileUtil.listRegularFiles(branchesDir).stream()
                    .map(file -> branchesDir.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Failed to list branches in " + branchesDir, e);
        }
    }

    /**
     * Deletes a branch's tip file.
     *
     * @param branchName the branch to delete.
     * @throws StorageException if the branch file cannot be deleted (for example
     *                          it does not exist).
     */
    public void deleteBranch(String branchName) {
        Path branch = branchPath(branchName);
        try {
            FileUtil.delete(branch);
        } catch (IOException e) {
            throw new StorageException("Failed to delete branch at " + branch, e);
        }
    }

    private Path branchPath(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            throw new IllegalArgumentException("branch name is required and must not be blank");
        }
        return metadataDir.resolve(BRANCHES_DIR).resolve(branchName);
    }
}
