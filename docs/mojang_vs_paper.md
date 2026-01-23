# Mojang vs Paper Server Comparison

## Quick Comparison

| Feature | Mojang (Vanilla) | Paper |
|---------|------------------|-------|
| **Source** | Official Mojang | Community fork (Spigot-based) |
| **Performance** | Slower, single-threaded | Fast, optimized, async chunk loading |
| **Plugins** | ❌ No | ✅ Yes |
| **Configuration** | Minimal | Extensive |
| **Proxy Support** | ❌ No (insecure) | ✅ Native Velocity/Bungee |
| **Resource Usage** | Higher CPU/RAM | Lower, optimized for low-end VMs |
| **Best For** | Single-player, testing | Multiplayer, public servers, proxies |

## Why Paper is Required for Your Setup

Your setup uses:
- Velocity proxy
- Geyser + Floodgate
- Low-resource cloud VM (OCI free tier)

| Requirement | Mojang | Paper |
|-------------|--------|-------|
| Secure proxy connections | ❌ | ✅ |
| Plugin support (Geyser/Floodgate) | ❌ | ✅ |
| Performance under load | ❌ Poor | ✅ Good |
| Low-RAM optimization | ❌ | ✅ |

**Bottom line:** For cloud hosting with Velocity/Geyser, Paper is the only viable option.
