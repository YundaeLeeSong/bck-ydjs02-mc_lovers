---
inclusion: always
---

# Product

## Summary

This project is a self-contained Minecraft server wrapper, distributed as the application `mc-lovers`. Its purpose is to make a single portable artifact that boots a plugin-capable Java Edition Minecraft server and simultaneously accepts Bedrock Edition clients, so that players on PC, mobile, and console can join the same world without separate setups.

## The problem being solved

A vanilla Mojang server does not support plugins, secure proxying, or Bedrock clients, and it is heavy for small cloud instances. Running the required plugin stack by hand demands manual jar placement, EULA acceptance, config generation, and careful edits to `server.properties` and Geyser settings. Every one of those steps is a place where a first-time operator fails.

The wrapper removes that friction. It resolves a stable per-OS Base Runtime Directory (default `<user.home>/mclovers`, name supplied by the Download Config), then on every run downloads the latest server jar and every required plugin fresh over the internet into a Staging Directory (default `<user.home>/resources`, name supplied by the Download Config) and injects them into the Base Runtime Directory under their original filenames, accepts the EULA automatically, performs a throwaway "shadow run" to let the server write its default configs, and then rewrites those configs to the values that a cloud plus cross-play deployment actually needs. An operator runs one executable and gets a working cross-platform server.

## Target users

There are two audiences as follows.

- Operators who want to host a small cross-play server, typically on a low-resource cloud VM such as an Oracle Cloud Infrastructure (OCI) free tier instance, without learning the plugin ecosystem.
- Players connecting from either edition. Java Edition players connect on the standard TCP port. Bedrock Edition players (mobile and console) connect on the standard Bedrock UDP port and are translated into the Java server by the Geyser plugin, with Floodgate removing the need for a paid Java account.

## Key features

- Single portable artifact that carries a bundled Download Config and pulls the server jar and all plugins fresh over the internet at runtime rather than shipping them inside itself.
- Automatic per-run provisioning: the server jar and plugin jars are downloaded into the Staging Directory (default `<user.home>/resources`, name supplied by the Download Config) each run and injected under their original filenames into the Base Runtime Directory (default `<user.home>/mclovers`, name supplied by the Download Config), alongside directory creation and EULA acceptance.
- Shadow run that generates default configuration files, then skips itself on later runs when configs already exist.
- Opinionated config overrides tuned for cross-play and cloud, including Bedrock-friendly settings and a lowered network MTU for cloud networking.
- Environment-variable driven server settings (MOTD, max players, view and simulation distance, and related tuning) so a deployment is configured without editing files by hand.
- A startup network report that tells the operator exactly which ports and authentication paths each edition uses.
- Cross-platform native bundles produced for Windows, macOS, Linux, and Oracle Linux on both x64 and ARM64.

## Business objectives and design intent

The guiding objective is a zero-friction, low-cost, cross-platform hosting experience. Design decisions consistently favor that goal. A performance-oriented server flavor is preferred over vanilla because plugins and low-RAM operation are required. Memory limits are kept small so the wrapper fits a free tier instance. Bedrock compatibility settings are enforced by default because the cross-play promise fails without them. When a change is proposed, it is weighed against whether it keeps the "run one file, get a working cross-play server on a small VM" experience intact.
