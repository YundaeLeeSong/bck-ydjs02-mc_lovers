package minecraft.wrapper;

import java.nio.file.Path;
import java.util.Map;

/**
 * <b>Record: Recognition Result</b>
 * <p>
 * Records the outcome of classifying the staged jars: the {@link Path} of the
 * chosen server jar and a {@link Map} from {@link PluginId} to the chosen plugin
 * jar {@link Path}. Each path points at a staged file whose filename is the
 * artifact's {@code Original_Filename}, which the {@code ResourceProvisioner}
 * preserves verbatim when injecting into the Base Runtime Directory.
 * </p>
 *
 * @param serverJar   The staged server jar selected by the server pattern.
 * @param pluginJars  The staged plugin jars keyed by {@link PluginId}.
 */
public record RecognitionResult(
        Path serverJar,
        Map<PluginId, Path> pluginJars) {
}
