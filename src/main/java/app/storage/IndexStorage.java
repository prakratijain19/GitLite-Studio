package app.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;

import app.model.FileSnapshot;
import app.model.Index;
import app.util.FileUtil;
import app.util.JsonUtil;

/**
 * Repository-scoped persistence for the staging area ({@code .gitlite/index}).
 *
 * <p>Like {@link JsonStorage}, it combines {@link JsonUtil} (object ⇄ JSON text)
 * with {@link FileUtil} (text ⇄ disk) and translates low-level failures into
 * {@link StorageException}. The index is stored as a JSON array of
 * {@link FileSnapshot} entries rather than by serializing the {@link Index}
 * object directly: this keeps the on-disk format a stable, self-describing list
 * that is decoupled from the in-memory representation of {@code Index}.
 */
public final class IndexStorage {

    private final Path indexPath;

    /**
     * @param metadataDir the {@code .gitlite} directory this instance manages
     *                    (typically {@code Repository.getMetadataPath()}).
     */
    public IndexStorage(Path metadataDir) {
        Objects.requireNonNull(metadataDir, "metadataDir must not be null");
        this.indexPath = metadataDir.resolve(FileStorage.INDEX_FILE);
    }

    /**
     * Serializes and writes the staging area to the index file, replacing any
     * previous content. Entries are written in the index's path-sorted order,
     * producing stable, diff-friendly output.
     *
     * @param index the staging area to persist (non-null).
     * @throws StorageException if the index cannot be serialized or written.
     */
    public void writeIndex(Index index) {
        Objects.requireNonNull(index, "index must not be null");
        try {
            String json = JsonUtil.toJson(index.getSnapshots());
            FileUtil.writeString(indexPath, json);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize index", e);
        } catch (IOException e) {
            throw new StorageException("Failed to write index at " + indexPath, e);
        }
    }

    /**
     * Reads and deserializes the staging area from the index file. An empty
     * index file (as produced at initialization) yields an empty index.
     *
     * @return the loaded {@link Index}.
     * @throws StorageException if the file is unreadable or not valid index JSON.
     */
    public Index readIndex() {
        try {
            String json = FileUtil.readString(indexPath);
            if (json.isBlank()) {
                return new Index();
            }
            FileSnapshot[] snapshots = JsonUtil.fromJson(json, FileSnapshot[].class);
            List<FileSnapshot> entries = Arrays.asList(snapshots);
            return Index.fromSnapshots(entries);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to parse index at " + indexPath, e);
        } catch (IOException e) {
            throw new StorageException("Failed to read index at " + indexPath, e);
        }
    }
}
