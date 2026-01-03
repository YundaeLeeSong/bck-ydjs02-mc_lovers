package minecraft.wrapper;

import java.security.SecureRandom;

/**
 * <b>Service: Secret Management</b>
 * <p>
 * Responsible for generating secure tokens for the Velocity Modern Forwarding protocol.
 * This secret is shared between the Velocity Proxy and the Backend Server to authenticate
 * forwarded connections and prevent spoofing.
 * </p>
 */
public class SecretManager {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 12;

    /**
     * Generates a random alphanumeric secret.
     *
     * @return A random string of 12 characters.
     */
    public static String generateSecret() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}
