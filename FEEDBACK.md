Since you didn't change the config files, your server is running, but you should be aware of two side effects of the settings you left in place:

1. velocity_proxy/plugins/Geyser-Proxy/config.yml
auth-type: online (in Geyser): Since this is still set to "online", only Bedrock players who have linked their Microsoft account to a valid Java Edition account can join.

If you invite a friend who only owns Bedrock (console/mobile) and hasn't bought Java Edition, they will be kicked.

Fix later if needed: Change to auth-type: floodgate.

2. velocity.toml
player-info-forwarding-mode = "none" (in Velocity): Since forwarding is off, your backend server (Lobby) thinks every single player is connecting from 127.0.0.1.

Side effect: You cannot IP-ban anyone (because you'd ban localhost and lock yourself out), and plugins that rely on real IPs won't work.

Fix later if needed: Change to modern in Velocity and set bungeecord: true in your Lobby's spigot.yml.


3. server.properties

Edit server.properties.

Find view-distance and simulation-distance.

Lower them to 6 or 8 (Default is usually 10).