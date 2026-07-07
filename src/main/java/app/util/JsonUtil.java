package app.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Thin, stateless wrapper over Jackson providing POJO ⇄ JSON-text conversion.
 *
 * <p>This class is the <strong>single seam over Jackson</strong>: no other class
 * constructs an {@link ObjectMapper} or imports {@code com.fasterxml.jackson.*}.
 * Its responsibility is deliberately narrow — it converts objects to JSON text
 * and back, and <em>does not touch the filesystem</em>. Persisting that text is
 * the job of {@code storage.FileStorage} / {@code storage.JsonStorage}, which
 * combine this class with {@link FileUtil}. Keeping serialization and disk I/O in
 * separate classes preserves the rule that only {@code FileUtil} performs file
 * access.
 *
 * <p>A single {@link ObjectMapper} is shared as a {@code static final} field.
 * This is not the request-scoped Singleton the project avoids: an
 * {@code ObjectMapper} holds only immutable configuration, is thread-safe once
 * built, and is expensive to construct, so a single shared instance is the
 * recommended Jackson usage.
 *
 * <p>The mapper is configured to write ISO-8601 dates (not numeric timestamps),
 * pretty-print output for human inspection of on-disk files, and ignore unknown
 * JSON properties so the format can evolve without breaking older read code.
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = buildMapper();

    private JsonUtil() {
        throw new AssertionError("JsonUtil is a utility class and must not be instantiated");
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Enable java.time support (e.g. LocalDateTime on config/commit models).
        mapper.registerModule(new JavaTimeModule());
        // Write dates as readable ISO-8601 strings rather than numeric arrays.
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Produce indented, human-readable JSON for files inspected by hand.
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Tolerate unknown properties so the on-disk format can evolve.
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /**
     * Serializes an object to pretty-printed JSON text.
     *
     * @param value the object to serialize.
     * @return the JSON representation of {@code value}.
     * @throws JsonProcessingException if the object cannot be serialized.
     */
    public static String toJson(Object value) throws JsonProcessingException {
        return MAPPER.writeValueAsString(value);
    }

    /**
     * Deserializes JSON text into an instance of the given type.
     *
     * @param json the JSON text to parse.
     * @param type the target type.
     * @param <T>  the target type parameter.
     * @return an instance of {@code type} populated from {@code json}.
     * @throws JsonProcessingException if the text is not valid JSON or cannot be
     *                                 mapped to {@code type}.
     */
    public static <T> T fromJson(String json, Class<T> type) throws JsonProcessingException {
        return MAPPER.readValue(json, type);
    }
}
