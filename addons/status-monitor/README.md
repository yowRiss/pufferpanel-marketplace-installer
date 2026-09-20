# PufferPanel Live Status Monitor & Discord Webhook

Real-time Minecraft server health monitor that tracks server ping, 24h/7d uptime rate, downtime incident history, in-game players, and updates a single live status message on Discord without channel spam.

---

## 🌟 Features

1. **Server Ping Tracking**: Measures real latency to the server tunnel gateway (strictly labeled **Server Ping**).
2. **Uptime & Downtime Tracking**:
   - Calculates real-time 24-hour and 7-day uptime percentage (e.g. `99.8%`).
   - Tracks continuous server uptime (`2d 4h 26m`).
   - Logs previous downtime incidents persistently in SQLite (e.g. `Down 15h ago (lasted 2m 14s)`).
3. **In-Game Player Telemetry**:
   - Real-time query over Minecraft Server List Ping (SLP) protocol.
   - Shows online count (`2/20`) and player names (`Shan71`, `frontpiano`).
4. **Zero-Spam Live Discord Webhook**:
   - Creates a message once and continuously edits it via `PATCH` every 30 seconds.
   - Never spams notifications or sends hundreds of messages.
   - Self-healing: Automatically detects if the message was deleted and recreates a new one.
5. **PufferPanel Web UI Integration**:
   - Exports live metrics to `/var/www/pufferpanel/js/server-status.json`.
   - Rendered natively in PufferPanel's **Stats** tab.

---

## 🚀 Installation

```bash
# 1. Copy daemon script
cp pufferpanel-status-monitor.py /usr/local/bin/
chmod +x /usr/local/bin/pufferpanel-status-monitor.py

# 2. Configure webhook
mkdir -p /etc/pufferpanel
cp status-monitor.json.example /etc/pufferpanel/status-monitor.json
nano /etc/pufferpanel/status-monitor.json

# 3. Enable & Start Systemd Service
cp pufferpanel-status-monitor.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now pufferpanel-status-monitor.service
```
