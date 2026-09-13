package minecraft.wrapper;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * <b>Record: Recognition Patterns</b>
 * <p>
 * Carries the compiled regular expressions the {@code JarRecognizer} matches
 * staged filenames against: one {@link Pattern} for the server jar, and a
 * {@link Map} keyed by {@link PluginId} giving each of the four required plugins
 * its own pattern. The patterns originate from the {@code DownloadConfig} so no
 * recognition literal is compiled into the wrapper.
 * </p>
 *
 * @param serverPattern  The pattern that matches the server jar's versioned filename.
 * @param pluginPatterns The per-plugin patterns keyed by {@link PluginId}.
 */
public record RecognitionPatterns(
        Pattern serverPattern,
        Map<PluginId, Pattern> pluginPatterns) {
}
