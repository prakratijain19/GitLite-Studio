package app.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Thin, stateless wrapper over {@link java.nio.file.Files} providing the small
 * set of filesystem primitives GitLite needs.
 *
 * <p>This class exists so that <strong>no other part of the codebase imports
 * {@code java.nio.file.Files} directly</strong>. Centralizing filesystem access
 * here gives us three guarantees:
 * <ul>
 *   <li><strong>Consistent encoding.</strong> All text I/O uses UTF-8, removing
 *       any dependency on the platform default charset.</li>
 *   <li><strong>A single testing/mocking seam.</strong> Changes to how we touch
 *       the disk are confined to one class.</li>
 *   <li><strong>Layering discipline.</strong> Higher layers (e.g.
 *       {@code storage.FileStorage}) build on these primitives instead of
 *       reaching into NIO themselves.</li>
 * </ul>
 *
 * <p>The class is a utility holder: it is {@code final}, cannot be instantiated,
 * and exposes only static methods. It performs no logging; callers that have
 * business context (the service layer) are responsible for logging failures.
 * Methods propagate {@link IOException} rather than wrapping it, deferring
 * domain-specific exception translation to the storage layer.
 */
public final class FileUtil {

    /** Charset used for all text I/O performed by this class. */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private FileUtil() {
        throw new AssertionError("FileUtil is a utility class and must not be instantiated");
    }

    /**
     * @param path the path to test.
     * @return {@code true} if a file or directory exists at {@code path}.
     */
    public static boolean exists(Path path) {
        return Files.exists(path);
    }

    /**
     * @param path the path to test.
     * @return {@code true} if {@code path} exists and is a directory.
     */
    public static boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    /**
     * Creates the directory at {@code path}, including any missing parent
     * directories. This operation is <strong>idempotent</strong>: it succeeds
     * silently if the directory already exists.
     *
     * @param path the directory to create.
     * @return the created (or already-existing) directory path.
     * @throws IOException if the directory cannot be created.
     */
    public static Path ensureDirectory(Path path) throws IOException {
        return Files.createDirectories(path);
    }

    /**
     * Creates a new, empty file at {@code path}. This operation is
     * <strong>strict</strong>: it fails if a file already exists there. The
     * strictness is intentional — an existing metadata file signals an
     * already-initialized repository, which callers must not silently overwrite.
     *
     * @param path the file to create.
     * @return the created file path.
     * @throws java.nio.file.FileAlreadyExistsException if the file already exists.
     * @throws IOException if the file cannot be created for another reason.
     */
    public static Path createFile(Path path) throws IOException {
        return Files.createFile(path);
    }

    /**
     * Writes {@code content} to {@code path} as UTF-8 text, creating the file if
     * absent and truncating it if present.
     *
     * @param path    the file to write.
     * @param content the text to write.
     * @throws IOException if the file cannot be written.
     */
    public static void writeString(Path path, String content) throws IOException {
        Files.writeString(path, content, CHARSET);
    }

    /**
     * Reads the entire contents of {@code path} as UTF-8 text.
     *
     * @param path the file to read.
     * @return the file's contents as a string.
     * @throws IOException if the file does not exist or cannot be read.
     */
    public static String readString(Path path) throws IOException {
        return Files.readString(path, CHARSET);
    }

    /**
     * Writes raw bytes to {@code path}, creating the file if absent and
     * truncating it if present. Used for binary content such as blob objects.
     *
     * @param path    the file to write.
     * @param content the bytes to write.
     * @throws IOException if the file cannot be written.
     */
    public static void writeBytes(Path path, byte[] content) throws IOException {
        Files.write(path, content);
    }

    /**
     * Reads the entire contents of {@code path} as raw bytes.
     *
     * @param path the file to read.
     * @return the file's contents.
     * @throws IOException if the file does not exist or cannot be read.
     */
    public static byte[] readBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    /**
     * Recursively lists every regular file under {@code root} (directories and
     * symbolic-link targets are not included). The returned paths are as produced
     * by the walk, rooted at {@code root}.
     *
     * <p>This method is intentionally generic: it applies no filtering. Callers
     * that need to exclude specific locations (such as the {@code .gitlite}
     * metadata directory) are responsible for doing so.
     *
     * @param root the directory to walk.
     * @return the regular files found beneath {@code root}.
     * @throws IOException if the tree cannot be walked.
     */
    public static List<Path> listRegularFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }
}
