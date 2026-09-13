package minecraft.wrapper;

/**
 * <b>Enum: Required Plugin Identity</b>
 * <p>
 * Identifies each of the four required plugin artifacts the wrapper provisions
 * over the internet: Geyser, Floodgate, ViaVersion, and ViaBackwards. The
 * {@code DownloadConfig} keys each identity to its download URL and recognition
 * pattern, and the {@code JarRecognizer} uses these identities to classify staged
 * jars so that each plugin is distinguished from the others.
 * </p>
 */
public enum PluginId {
    GEYSER,
    FLOODGATE,
    VIAVERSION,
    VIABACKWARDS
}
