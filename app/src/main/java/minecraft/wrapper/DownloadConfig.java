package minecraft.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * <b>Download Config loader</b>
 * <p>
 * Loads and validates the bundled {@code .properties} Download Config from the
 * classpath, which is the single source of truth for the two runtime directory
 * names plus the download URLs and recognition patterns for the server jar and
 * the four required plugin jars. It is loaded and validated <em>first</em> in
 * {@code App.main}, before the Base Runtime Directory is resolved (Phase 0), so
 * that a missing resource, malformed properties, or an absent or blank required
 * key aborts before any directory is created or any download is attempted.
 * </p>
 * <p>
 * The wrapper holds no hardcoded directory-name, URL, or regex literal in
 * compiled code. Every such value is read from this config. The documented
 * defaults {@code mclovers} (base) and {@code resources} (staging) are the
 * values shipped in {@code download.properties}, never behavioral fallbacks.
 * There are intentionally no {@code .target} keys: each artifact keeps the
 * {@code Original_Filename} it is downloaded and recognized under.
 * </p>
 */
public final class DownloadConfig {

    // [Directory_Name_Config] resolved as <user.home>/<value>.
    private static final String KEY_BASE_DIR_NAME = "base.dir.name";       // base.dir.name
    private static final String KEY_STAGING_DIR_NAME = "staging.dir.name"; // staging.dir.name

    // [Server keys] download endpoint and staged-jar recognition pattern.
    private static final String KEY_SERVER_URL = "server.url";             // server.url
    private static final String KEY_SERVER_PATTERN = "server.pattern";     // server.pattern

    private final String baseDirName;
    private final String stagingDirName;
    private final String serverUrl;
    private final Pattern serverPattern;

    // Per-plugin URLs and compiled patterns, keyed by identity.
    private final Map<PluginId, String> pluginUrls;
    private final Map<PluginId, Pattern> pluginPatterns;

    /*
     * [Private constructor]
     *
     * Instances are created only by load after every value has been validated
     * and every pattern compiled, so a constructed DownloadConfig is always fully
     * populated and never carries a missing or blank field.
     */
    private DownloadConfig(String baseDirName, String stagingDirName, String serverUrl,
            Pattern serverPattern, Map<PluginId, String> pluginUrls,
            Map<PluginId, Pattern> pluginPatterns) {
        this.baseDirName = baseDirName;
        this.stagingDirName = stagingDirName;
        this.serverUrl = serverUrl;
        this.serverPattern = serverPattern;
        this.pluginUrls = pluginUrls;
        this.pluginPatterns = pluginPatterns;
    }

    /**
     * Loads and validates the Download Config from the named classpath resource.
     * <p>
     * Reads the resource with {@code App.class.getResourceAsStream("/" + resourceName)};
     * a {@code null} stream aborts as a missing resource. The stream is parsed with
     * {@link java.util.Properties}; a parse failure aborts as malformed. Every required
     * key (the two directory-name keys, the server URL and pattern, and each plugin's
     * URL and pattern) is then checked present and non-blank, and every pattern is
     * compiled with {@link java.util.regex.Pattern#compile(String)}. Any failure raises
     * {@link ProvisioningException} naming the offending key so that {@code App.main}
     * can print an actionable message and exit non-zero before Phase 0.
     * </p>
     *
     * @param resourceName The classpath resource name, for example {@code download.properties}.
     * @return A fully populated, validated {@code DownloadConfig}.
     * @throws ProvisioningException When the resource is missing, the properties are
     *                               malformed, a required key is absent or blank, or a
     *                               pattern value is not a valid regular expression.
     */
    public static DownloadConfig load(String resourceName) throws ProvisioningException {
        Properties props = new Properties();

        /*
         * [Read + parse]
         *
         * A null stream means the resource is absent from the classpath (missing
         * resource). An IOException while parsing means the content is malformed.
         * Both abort before any directory is resolved or any download is attempted.
         */
        try (InputStream in = App.class.getResourceAsStream("/" + resourceName)) {
            if (in == null) {                                          // missing
                throw new ProvisioningException(
                        "Download config resource not found on the classpath: " + resourceName);
            }
            props.load(in);                                            // parse
        } catch (IOException e) {
            throw new ProvisioningException(
                    "Download config resource is malformed: " + resourceName, e);
        }

        // [Directory names] validated so App can abort before Phase 0.
        String baseDirName = required(props, KEY_BASE_DIR_NAME);
        String stagingDirName = required(props, KEY_STAGING_DIR_NAME);

        // [Server] URL plus compiled recognition pattern.
        String serverUrl = required(props, KEY_SERVER_URL);
        Pattern serverPattern = compile(props, KEY_SERVER_PATTERN);

        /*
         * [Plugins]
         *
         * Each PluginId maps to plugin.<lowercase>.url and plugin.<lowercase>.pattern.
         * The EnumMap keeps the per-identity lookup compact and ordered.
         */
        Map<PluginId, String> pluginUrls = new EnumMap<>(PluginId.class);
        Map<PluginId, Pattern> pluginPatterns = new EnumMap<>(PluginId.class);
        for (PluginId id : PluginId.values()) {
            pluginUrls.put(id, required(props, pluginUrlKey(id)));
            pluginPatterns.put(id, compile(props, pluginPatternKey(id)));
        }

        return new DownloadConfig(baseDirName, stagingDirName, serverUrl, serverPattern,
                pluginUrls, pluginPatterns);
    }

    /**
     * Returns the Base Runtime Directory name (key {@code base.dir.name}).
     * <p>
     * Resolved by {@code App} as {@code <user.home>/<baseDirName>} before Phase 0.
     * The documented default shipped in {@code download.properties} is {@code mclovers}.
     * </p>
     *
     * @return The configured base directory name.
     */
    public String baseDirName() {
        return baseDirName;
    }

    /**
     * Returns the Staging Directory name (key {@code staging.dir.name}).
     * <p>
     * Resolved by {@code ResourceProvisioner} as {@code <user.home>/<stagingDirName>},
     * wiped and recreated on every run. The documented default shipped in
     * {@code download.properties} is {@code resources}.
     * </p>
     *
     * @return The configured staging directory name.
     */
    public String stagingDirName() {
        return stagingDirName;
    }

    /**
     * Returns the server jar download URL (key {@code server.url}).
     *
     * @return The configured server download URL.
     */
    public String serverUrl() {
        return serverUrl;
    }

    /**
     * Returns the download URL for the given plugin.
     * <p>
     * Maps {@code id} to the key {@code plugin.<lowercase>.url}.
     * </p>
     *
     * @param id The plugin identity.
     * @return The configured download URL for that plugin.
     */
    public String pluginUrl(PluginId id) {
        return pluginUrls.get(id);
    }

    /**
     * Returns the compiled recognition pattern for the server jar (key {@code server.pattern}).
     *
     * @return The compiled server recognition pattern.
     */
    public Pattern serverPattern() {
        return serverPattern;
    }

    /**
     * Returns the compiled recognition pattern for the given plugin.
     * <p>
     * Maps {@code id} to the key {@code plugin.<lowercase>.pattern}.
     * </p>
     *
     * @param id The plugin identity.
     * @return The compiled recognition pattern for that plugin.
     */
    public Pattern pluginPattern(PluginId id) {
        return pluginPatterns.get(id);
    }

    // [URL key] plugin.<lowercase>.url for the given identity.
    private static String pluginUrlKey(PluginId id) {
        return "plugin." + id.name().toLowerCase() + ".url";
    }

    // [Pattern key] plugin.<lowercase>.pattern for the given identity.
    private static String pluginPatternKey(PluginId id) {
        return "plugin." + id.name().toLowerCase() + ".pattern";
    }

    /**
     * Returns the value for {@code key}, requiring it to be present and non-blank.
     *
     * @param props The parsed properties.
     * @param key   The required key.
     * @return The trimmed-of-nothing raw value (validated non-blank).
     * @throws ProvisioningException When the key is absent or its value is blank,
     *                               naming the offending key.
     */
    private static String required(Properties props, String key) throws ProvisioningException {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {                        // absent/blank
            throw new ProvisioningException(
                    "Download config is missing a required key or its value is blank: " + key);
        }
        return value;
    }

    /**
     * Compiles the pattern value for {@code key} after requiring it present and non-blank.
     *
     * @param props The parsed properties.
     * @param key   The pattern key.
     * @return The compiled {@link Pattern}.
     * @throws ProvisioningException When the key is absent or blank, or its value is
     *                               not a valid regular expression, naming the key.
     */
    private static Pattern compile(Properties props, String key) throws ProvisioningException {
        String value = required(props, key);
        try {
            return Pattern.compile(value);                             // regex
        } catch (PatternSyntaxException e) {
            throw new ProvisioningException(
                    "Download config value for key is not a valid pattern: " + key, e);
        }
    }
}
