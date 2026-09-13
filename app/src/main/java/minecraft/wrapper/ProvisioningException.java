package minecraft.wrapper;

import java.nio.file.Path;

/**
 * <b>Exception: Resource Provisioning Failure</b>
 * <p>
 * Signals that the wrapper could not provision the server or plugin resources
 * (for example: reading the Download Config, wiping or recreating the Staging
 * Directory, downloading an artifact, recognizing a staged jar, or injecting it
 * into the Base Runtime Directory). It carries the offending {@link Path} (which
 * may be {@code null} when no single path applies, such as a missing config key)
 * and the underlying cause, so that {@code App.main} can print a clear, actionable
 * message before exiting with a non-zero status.
 * </p>
 */
public class ProvisioningException extends Exception {

    // Serializable contract for the Exception hierarchy.
    private static final long serialVersionUID = 1L;

    private final transient Path path;

    /**
     * Creates an exception with a message and no associated path or cause.
     * <p>
     * Used when provisioning aborts before a path exists, such as a missing or
     * blank required config key.
     * </p>
     *
     * @param message Human-readable description of the failure.
     */
    public ProvisioningException(String message) {
        this(message, null, null);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message Human-readable description of the failure.
     * @param cause   The underlying throwable that triggered this failure.
     */
    public ProvisioningException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Creates an exception carrying the offending path and the underlying cause.
     *
     * @param message Human-readable description of the failure.
     * @param path    The path involved in the failure, or {@code null}.
     * @param cause   The underlying throwable that triggered this failure, or {@code null}.
     */
    public ProvisioningException(String message, Path path, Throwable cause) {
        super(message, cause);
        this.path = path;
    }

    /**
     * Returns the offending path.
     *
     * @return The path involved in the failure, or {@code null} when none applies
     *         (for example when a config key rather than a path is at fault).
     */
    public Path getPath() {
        return path;
    }
}
