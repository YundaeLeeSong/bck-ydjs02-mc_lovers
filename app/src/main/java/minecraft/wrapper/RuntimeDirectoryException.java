package minecraft.wrapper;

import java.nio.file.Path;

/**
 * <b>Exception: Base Runtime Directory Failure</b>
 * <p>
 * Signals that the wrapper could not resolve, create, or validate the Base Runtime
 * Directory ({@code <user.home>/mclovers}). It carries the offending {@link Path}
 * (which may be {@code null} when the user home itself is unavailable) and the
 * underlying cause, so that {@code App.main} can print a clear, actionable message
 * before exiting with a non-zero status.
 * </p>
 */
public class RuntimeDirectoryException extends Exception {

    // Serializable contract for the Exception hierarchy.
    private static final long serialVersionUID = 1L;

    private final transient Path path;

    /**
     * Creates an exception with a message and no associated path or cause.
     * <p>
     * Used when resolution aborts before a path exists, such as a missing user home.
     * </p>
     *
     * @param message Human-readable description of the failure.
     */
    public RuntimeDirectoryException(String message) {
        this(message, null, null);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message Human-readable description of the failure.
     * @param cause   The underlying throwable that triggered this failure.
     */
    public RuntimeDirectoryException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Creates an exception carrying the offending path and the underlying cause.
     *
     * @param message Human-readable description of the failure.
     * @param path    The Base Runtime Directory path involved in the failure, or {@code null}.
     * @param cause   The underlying throwable that triggered this failure, or {@code null}.
     */
    public RuntimeDirectoryException(String message, Path path, Throwable cause) {
        super(message, cause);
        this.path = path;
    }

    /**
     * Returns the offending Base Runtime Directory path.
     *
     * @return The path involved in the failure, or {@code null} when none applies
     *         (for example when the user home is unavailable).
     */
    public Path getPath() {
        return path;
    }
}
