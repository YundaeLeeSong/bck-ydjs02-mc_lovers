package minecraft.wrapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <b>Component: Jar Recognizer</b>
 * <p>
 * Pure classification of staged jar filenames against the configured regular
 * expressions carried by {@link RecognitionPatterns}. It selects the single
 * server jar and each of the four required plugin jars, applying two rules:
 * server-over-plugin precedence (a file matching both the server pattern and a
 * plugin pattern is the server jar only), and a deterministic tie-break (when
 * several staged files match one artifact, the filename first in case-sensitive
 * ASCII-lexicographic ascending order is chosen).
 * </p>
 * <p>
 * Recognition performs no I/O beyond reading the supplied list: it matches each
 * {@link Path}'s filename only, and it leaves the staged files unmodified. When
 * the server pattern matches nothing, or any one of the four plugin patterns
 * matches nothing, it aborts with a {@link ProvisioningException} naming the
 * unrecognized artifact.
 * </p>
 */
final class JarRecognizer {

    private final RecognitionPatterns patterns;

    /**
     * Creates a recognizer bound to the given patterns.
     *
     * @param patterns The compiled server and per-plugin patterns to match against.
     */
    JarRecognizer(RecognitionPatterns patterns) {
        this.patterns = patterns;
    }

    /**
     * Classifies the staged files into the server jar and the four plugin jars.
     * <p>
     * A file matching the server pattern is recorded as the server jar and is
     * never considered for any plugin. Each remaining artifact is chosen from
     * the files matching its pattern by taking the ASCII-lexicographically
     * smallest filename. The supplied list is not modified.
     * </p>
     *
     * @param stagedFiles The staged jar paths to classify.
     * @return The chosen server jar and plugin jars.
     * @throws ProvisioningException When the server pattern or any one of the
     *                               four plugin patterns matches no staged file.
     */
    RecognitionResult recognize(List<Path> stagedFiles) throws ProvisioningException {
        // Server-over-plugin precedence: pick the server jar first, and exclude
        // it from every plugin candidate pool so it is never installed as a plugin.
        Path serverJar = choose(stagedFiles, patterns.serverPattern(), null);
        if (serverJar == null) {
            throw new ProvisioningException(
                    "Server jar could not be recognized among the staged files.");
        }

        Map<PluginId, Path> pluginJars = new EnumMap<>(PluginId.class); // recognized plugins
        for (PluginId id : PluginId.values()) {
            Pattern pluginPattern = patterns.pluginPatterns().get(id); // per-plugin
            Path chosen = choose(stagedFiles, pluginPattern, serverJar);
            if (chosen == null) {
                throw new ProvisioningException(
                        "Plugin jar could not be recognized: " + id);
            }
            pluginJars.put(id, chosen);
        }

        return new RecognitionResult(serverJar, pluginJars);
    }

    /*
     * [Deterministic tie-break]
     *
     * Collects every staged file whose filename matches the pattern, skipping the
     * excluded server jar for plugin passes, then returns the ASCII-lexicographic
     * minimum filename so multiple matches resolve to a single, stable choice.
     * Returns null when nothing matches so the caller can abort naming the artifact.
     */
    private Path choose(List<Path> stagedFiles, Pattern pattern, Path excluded) {
        List<Path> matches = new ArrayList<>(); // candidates
        for (Path file : stagedFiles) {
            if (file.equals(excluded)) {
                continue; // server-over-plugin precedence
            }
            String name = file.getFileName().toString(); // filename
            if (pattern.matcher(name).matches()) {
                matches.add(file);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        Path best = matches.get(0); // running minimum
        for (Path candidate : matches) {
            // String.compareTo is case-sensitive ASCII-lexicographic ascending.
            if (candidate.getFileName().toString()
                    .compareTo(best.getFileName().toString()) < 0) {
                best = candidate;
            }
        }
        return best;
    }
}
