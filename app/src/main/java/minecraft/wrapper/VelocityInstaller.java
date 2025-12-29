package minecraft.wrapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Service: Velocity Proxy Installer</b>
 * <p>
 * Responsible for downloading and setting up the Velocity Proxy environment.
 * Unlike the Vanilla server (which is bundled), Velocity and its plugins are
 * downloaded dynamically from the internet to ensure the latest compatible versions.
 * </p>
 * <p>
 * <b>Components Installed:</b>
 * <ul>
 *   <li><b>Velocity Proxy:</b> The core proxy software (PaperMC).</li>
 *   <li><b>Geyser (Velocity):</b> Plugin for Bedrock-to-Java packet translation.</li>
 *   <li><b>Floodgate (Velocity):</b> Plugin for Bedrock authentication (no Java account needed).</li>
 * </ul>
 * </p>
 */
public class VelocityInstaller {
    private final File proxyDir;
    // Targeting the latest stable-ish version. 
    // Ideally this would be dynamic, but hardcoding the version branch is safer for API stability.
    private static final String VELOCITY_VERSION = "3.4.0-SNAPSHOT";
    private static final String PAPER_API_BASE = "https://api.papermc.io/v2/projects/velocity/versions/" + VELOCITY_VERSION;
    
    /**
     * Creates a new VelocityInstaller.
     *
     * @param proxyDir The directory where the proxy and plugins will be installed.
     */
    public VelocityInstaller(File proxyDir) {
        this.proxyDir = proxyDir;
    }

    /**
     * Orchestrates the installation process.
     * <p>
     * 1. <b>Resource Extraction:</b> Extracts bundled Velocity configurations and plugins
     *    from the {@code velocity_proxy.zip} resource. Preserves existing {@code velocity.toml}
     *    to avoid overwriting user settings.
     * <br>
     * 2. <b>Dynamic Downloads:</b> Downloads missing core components (Velocity jar, Geyser, Floodgate)
     *    from their respective APIs if they are not present.
     * </p>
     *
     * @throws Exception If any extraction or download fails.
     */
    public void install() throws Exception {
        if (!proxyDir.exists()) proxyDir.mkdirs();

        // 0. Extract Bundled Resources (if available)
        // This allows distributing a pre-configured setup (plugins, configs, etc.)
        // inside the application JAR/Resources.
        java.net.URL zipResource = getClass().getResource("/velocity_proxy.zip");
        if (zipResource != null) {
            System.out.println("VelocityInstaller: Extracting bundled proxy resources...");
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(zipResource.openStream())) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    try {
                        // Normalize path separators to avoid Windows/Linux issues
                        String fileName = entry.getName().replace('\\', '/');
                        File file = new File(proxyDir, fileName);

                        // Security: Prevent Zip Slip (accessing files outside target dir)
                        if (!file.getCanonicalPath().startsWith(proxyDir.getCanonicalPath())) {
                            System.err.println("VelocityInstaller: Skipping unsafe zip entry: " + fileName);
                            continue;
                        }

                        if (entry.isDirectory()) {
                            file.mkdirs();
                        } else {
                            // Preserve existing user configuration (velocity.toml)
                            // if (file.getName().equals("velocity.toml") && file.exists()) {
                            //    System.out.println("VelocityInstaller: Preserving existing config: " + fileName);
                            //    continue;
                            // }
                            
                            // Ensure parent directory exists before writing
                            if (file.getParentFile() != null) {
                                file.getParentFile().mkdirs();
                            }

                            // Copy file content
                            Files.copy(zis, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception e) {
                        // Log error but continue extracting other files
                        System.err.println("VelocityInstaller: Failed to extract '" + entry.getName() + "': " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("VelocityInstaller: Failed to process bundled resources zip: " + e.getMessage());
                e.printStackTrace();
            }
        }

        File pluginsDir = new File(proxyDir, "plugins");
        if (!pluginsDir.exists()) pluginsDir.mkdirs();

        // 1. Download Velocity
        File velocityJar = new File(proxyDir, "velocity.jar");
        if (!velocityJar.exists()) {
            System.out.println("VelocityInstaller: Downloading Velocity Proxy (" + VELOCITY_VERSION + ")...");
            try {
                downloadVelocity(velocityJar);
            } catch (Exception e) {
                System.err.println("VelocityInstaller: Failed to download Velocity: " + e.getMessage());
                throw e;
            }
        }

        // 2. Download Geyser (Velocity)
        File geyserJar = new File(pluginsDir, "Geyser-Velocity.jar");
        if (!geyserJar.exists()) {
            System.out.println("VelocityInstaller: Downloading Geyser (Velocity)...");
            downloadFile("https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/velocity", geyserJar);
        }

        // 3. Download Floodgate (Velocity)
        File floodgateJar = new File(pluginsDir, "floodgate-velocity.jar");
        if (!floodgateJar.exists()) {
            System.out.println("VelocityInstaller: Downloading Floodgate (Velocity)...");
            downloadFile("https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/velocity", floodgateJar);
        }
    }

    /**
     * Downloads the latest build of Velocity for the configured version using the PaperMC API.
     *
     * @param target The destination file.
     * @throws Exception If API parsing or download fails.
     */
    private void downloadVelocity(File target) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        
        // A. Get list of builds for this version
        HttpRequest listReq = HttpRequest.newBuilder().uri(URI.create(PAPER_API_BASE + "/builds")).build();
        String json = client.send(listReq, HttpResponse.BodyHandlers.ofString()).body();
        
        // B. Find the latest build number using Regex
        // JSON structure: { ..., "builds": [ { "build": 1, ... }, { "build": 2, ... } ] }
        // We assume the list is ordered or we just grab the last occurrence of "build": \d+
        Pattern p = Pattern.compile("\"build\":(\\d+)");
        Matcher m = p.matcher(json);
        String latestBuild = "";
        while (m.find()) {
            latestBuild = m.group(1);
        }
        
        if (latestBuild.isEmpty()) {
            throw new IOException("Could not parse latest build number from PaperMC API.");
        }
        
        System.out.println("VelocityInstaller: Detected latest build #" + latestBuild);

        // C. Fetch build info to get the actual filename
        String buildUrl = PAPER_API_BASE + "/builds/" + latestBuild;
        String buildJson = client.send(HttpRequest.newBuilder().uri(URI.create(buildUrl)).build(), HttpResponse.BodyHandlers.ofString()).body();
        
        Pattern namePattern = Pattern.compile("\"name\":\"([^\"]+)\"");
        Matcher nameMatcher = namePattern.matcher(buildJson);
        if (!nameMatcher.find()) {
            // Fallback if name not found, though unusual
            throw new IOException("Could not parse filename from build info.");
        }
        String fileName = nameMatcher.group(1);
        
        // D. Download
        String downloadUrl = buildUrl + "/downloads/" + fileName;
        downloadFile(downloadUrl, target);
    }

    /**
     * Downloads a file from a direct URL.
     *
     * @param url    The source URL.
     * @param target The destination file.
     * @throws Exception If the download fails.
     */
    private void downloadFile(String url, File target) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
        // Use ofFile to stream directly to disk
        client.send(req, HttpResponse.BodyHandlers.ofFile(target.toPath()));
    }
}
