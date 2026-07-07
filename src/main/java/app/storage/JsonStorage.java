package app.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;

import app.model.RepositoryConfig;
import app.util.FileUtil;
import app.util.JsonUtil;

/**
 * Repository-scoped storage for the JSON parts of a {@code .gitlite} directory.
 *
 * <p>Each instance manages one {@code .gitlite} metadata directory, supplied at
 * construction. Its current responsibility is persisting and loading the
 * {@code config} file as a {@link RepositoryConfig}. It is the concrete
 * realization of the layering established earlier: {@link JsonUtil} turns objects
 * into JSON text, {@link FileUtil} moves that text to and from disk, and this
 * class combines the two into a domain-meaningful operation.
 *
 * <p>Like {@link FileStorage}, this is a translation boundary: both Jackson's
 * {@link JsonProcessingException} and {@link FileUtil}'s {@link IOException} are
 * rethrown as {@link StorageException} with business context.
 */
public final class JsonStorage {

    /** The JSON configuration file inside {@code .gitlite}. */
    public static final String CONFIG_FILE = "config";

    private final Path metadataDir;

    /**
     * @param metadataDir the {@code .gitlite} directory this instance manages
     *                    (typically {@code Repository.getMetadataPath()}).
     */
    public JsonStorage(Path metadataDir) {
        this.metadataDir = Objects.requireNonNull(metadataDir, "metadataDir must not be null");
    }

    /**
     * Serializes and writes the repository configuration to {@code config}.
     *
     * @param config the configuration to persist.
     * @throws StorageException if the configuration cannot be serialized or
     *                          written.
     */
    public void writeConfig(RepositoryConfig config) {
        Path configPath = metadataDir.resolve(CONFIG_FILE);
        try {
            String json = JsonUtil.toJson(config);
            FileUtil.writeString(configPath, json);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to serialize repository config", e);
        } catch (IOException e) {
            throw new StorageException("Failed to write config at " + configPath, e);
        }
    }

    /**
     * Reads and deserializes the repository configuration from {@code config}.
     *
     * @return the loaded {@link RepositoryConfig}.
     * @throws StorageException if the file is missing, unreadable, or not valid
     *                          configuration JSON.
     */
    public RepositoryConfig readConfig() {
        Path configPath = metadataDir.resolve(CONFIG_FILE);
        try {
            String json = FileUtil.readString(configPath);
            return JsonUtil.fromJson(json, RepositoryConfig.class);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to parse repository config at " + configPath, e);
        } catch (IOException e) {
            throw new StorageException("Failed to read config at " + configPath, e);
        }
    }
}
