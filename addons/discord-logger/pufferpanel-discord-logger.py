#!/usr/bin/env python3
import os
import time
import json
import re
import subprocess
import urllib.request
import urllib.error
from collections import deque
from datetime import datetime, timezone

CONFIG_PATH = "/var/lib/pufferpanel/servers/35ca4939/discord-alerts.json"
LOG_PATH = "/var/lib/pufferpanel/servers/35ca4939/logs/latest.log"
CRASH_DIR = "/var/lib/pufferpanel/servers/35ca4939/crash-reports"
NETWORK_STATS_PATH = "/var/www/pufferpanel/js/network-stats.json"

DISCONNECT_REGEX = re.compile(
    r"\[(?P<time>[^\]]+)\] \[[^\]]+\]:\s+(?:Player\s+)?(?P<player>[a-zA-Z0-9_]{1,16})(?:\s+\([^)]+\))?\s+lost connection:\s*(?P<reason>.*)"
)

LOGIN_REGEX = re.compile(
    r"\[Server thread/INFO\]:\s+(?P<player>[a-zA-Z0-9_]{1,16})\[/(?P<ip>[0-9a-fA-F\.:]+):(?P<port>\d+)\] logged in with entity id (?P<entity_id>\d+)(?: at \((?P<coords>[^\)]+)\))?"
)

ERROR_REGEX = re.compile(
    r"\[(?P<time>[^\]]+)\] \[(?P<thread>[^/]+)/(?P<level>ERROR|FATAL)\]:\s+(?P<message>.*)"
)

COLOR_GREEN = 5763719      # #57F287
COLOR_ORANGE = 16753920    # #FFA500
COLOR_YELLOW = 16766720    # #FEE75C
COLOR_RED = 15548997       # #ED4245
COLOR_DARK_RED = 10038562  # #992D22

def format_bytes(b):
    if b < 1024:
        return f"{b:.0f} B"
    elif b < 1024**2:
        return f"{b/1024:.2f} KiB"
    elif b < 1024**3:
        return f"{b/1024**2:.2f} MiB"
    else:
        return f"{b/1024**3:.2f} GiB"

class DiscordLogger:
    def __init__(self):
        self.config = {}
        self.last_config_mtime = 0
        self.load_config()
        self.recent_errors = {}
        self.seen_crashes = set()
        self.init_crashes()

        # Network spike detection state
        self.rate_history = deque(maxlen=60) # rolling 60s baseline (timestamp, total_bytes_sec)
        self.last_spike_alert_time = 0
        self.in_spike = False

        # Player IP & Session Tracking
        self.player_ips = {} # ip -> {"player": name, "coords": coords, "port": port}
        self.init_player_ips()

        # Ring buffer of recent log entries for diagnostics
        self.recent_logs = deque(maxlen=100)

        # Socket byte tracking for per-connection rate calculation
        self.socket_bytes = {} # peer -> (timestamp, bytes_sent)

    def load_config(self):
        try:
            if os.path.exists(CONFIG_PATH):
                mtime = os.path.getmtime(CONFIG_PATH)
                if mtime != self.last_config_mtime:
                    with open(CONFIG_PATH, "r") as f:
                        self.config = json.load(f)
                    self.last_config_mtime = mtime
                    print(f"[{datetime.now().strftime('%H:%M:%S')}] Loaded config: enabled={self.config.get('enabled')}, bot={self.config.get('bot_name')}")
        except Exception as e:
            print(f"Error reading config: {e}")

    def init_crashes(self):
        if os.path.exists(CRASH_DIR):
            for f in os.listdir(CRASH_DIR):
                if f.endswith(".txt"):
                    self.seen_crashes.add(f)

    def init_player_ips(self):
        if not os.path.exists(LOG_PATH):
            return
        try:
            with open(LOG_PATH, "r", errors="ignore") as f:
                lines = f.readlines()
                for line in lines[-800:]:
                    m = LOGIN_REGEX.search(line)
                    if m:
                        self.player_ips[m.group("ip")] = {
                            "player": m.group("player"),
                            "coords": m.group("coords") or "",
                            "port": m.group("port")
                        }
        except Exception as e:
            print(f"Error reading player logins: {e}")

    def send_webhook(self, embeds, is_critical=False):
        if not self.config.get("enabled", True):
            return
        webhook_url = self.config.get("webhook_url", "").strip()
        if not webhook_url or not webhook_url.startswith("http"):
            return

        payload = {
            "username": self.config.get("bot_name", "Minecraft Server Alerts"),
            "avatar_url": self.config.get("avatar_url", "https://cdn.icon-icons.com/icons2/2699/PNG/512/minecraft_logo_icon_168974.png"),
            "embeds": embeds
        }

        # Auto-tag Discord user ID on critical errors or crashes
        if is_critical and self.config.get("mention_critical", True):
            raw_id = str(self.config.get("mention_discord_id", "586719784490631180")).strip()
            discord_id = re.sub(r"\D", "", raw_id)
            if discord_id:
                payload["content"] = f"🚨 <@{discord_id}> **CRITICAL SERVER ALERT**"
                payload["allowed_mentions"] = {"users": [discord_id]}

        try:
            req = urllib.request.Request(
                webhook_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "User-Agent": "PufferPanel-DiscordLogger/1.0"
                }
            )
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                pass
        except Exception as e:
            print(f"Error sending webhook: {e}")

    def check_crashes(self):
        if not self.config.get("alerts", {}).get("crashes", True):
            return
        if not os.path.exists(CRASH_DIR):
            return

        try:
            crashes = [f for f in os.listdir(CRASH_DIR) if f.endswith(".txt")]
            for crash in crashes:
                if crash not in self.seen_crashes:
                    self.seen_crashes.add(crash)
                    crash_path = os.path.join(CRASH_DIR, crash)
                    print(f"Detected new crash report: {crash_path}")

                    # Read first 40 lines of crash report
                    with open(crash_path, "r", errors="ignore") as cf:
                        crash_content = "".join([cf.readline() for _ in range(40)])

                    embed = {
                        "title": f"🚨 Minecraft Server Crash: {crash}",
                        "description": f"A fatal server crash occurred. Crash report `{crash}` has been generated:\n```\n{crash_content[:1800]}\n```",
                        "color": COLOR_DARK_RED,
                        "fields": [
                            {"name": "Report File", "value": f"`{crash_path}`", "inline": False}
                        ],
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "footer": {"text": "PufferPanel Crash Reporter"}
                    }
                    self.send_webhook([embed], is_critical=True)
        except Exception as e:
            print(f"Error checking crashes: {e}")

    def get_top_bandwidth_player(self):
        try:
            p = subprocess.run(["ss", "-tni", "sport = :25565"], capture_output=True, text=True, timeout=1.5)
            lines = p.stdout.splitlines()
            now = time.time()
            rates = {}
            current_peer = None

            for line in lines:
                line_str = line.strip()
                if line_str.startswith("ESTAB"):
                    parts = line_str.split()
                    if len(parts) >= 5:
                        current_peer = parts[4]
                elif current_peer and ("bytes_sent:" in line_str or "bytes_acked:" in line_str):
                    m_b = re.search(r"bytes_sent:(\d+)", line_str)
                    if not m_b:
                        m_b = re.search(r"bytes_acked:(\d+)", line_str)
                    if m_b:
                        sent_bytes = int(m_b.group(1))
                        if current_peer in self.socket_bytes:
                            prev_t, prev_b = self.socket_bytes[current_peer]
                            dt = max(0.2, now - prev_t)
                            if sent_bytes >= prev_b:
                                rates[current_peer] = (sent_bytes - prev_b) / dt
                        self.socket_bytes[current_peer] = (now, sent_bytes)
                    current_peer = None

            if not rates:
                return None, None, 0, 0

            top_peer = max(rates, key=rates.get)
            top_rate = rates[top_peer]
            total_rate = sum(rates.values())
            top_share = (top_rate / total_rate * 100.0) if total_rate > 0 else 100.0

            peer_ip = top_peer.split(":")[0]
            player_info = self.player_ips.get(peer_ip, {})
            player_name = player_info.get("player", None)

            return player_name, peer_ip, top_rate, top_share
        except Exception:
            return None, None, 0, 0

    def check_network_spikes(self):
        if not self.config.get("alerts", {}).get("network_spikes", True):
            return

        if not os.path.exists(NETWORK_STATS_PATH):
            return

        try:
            with open(NETWORK_STATS_PATH, "r") as f:
                data = json.load(f)
        except Exception:
            return

        srv = data.get("server", {})
        host = data.get("host", {})

        srv_rx_rate = srv.get("rx_rate", 0)
        srv_tx_rate = srv.get("tx_rate", 0)
        total_srv_rate = srv_rx_rate + srv_tx_rate
        srv_mbps = (total_srv_rate * 8) / 1_000_000

        host_rx_rate = host.get("rx_rate", 0)
        host_tx_rate = host.get("tx_rate", 0)
        total_host_rate = host_rx_rate + host_tx_rate
        host_mbps = (total_host_rate * 8) / 1_000_000

        now = time.time()

        self.rate_history.append((now, total_srv_rate))
        avg_baseline = sum(r[1] for r in self.rate_history) / max(1, len(self.rate_history))
        avg_baseline_mbps = (avg_baseline * 8) / 1_000_000

        threshold_mbps = float(self.config.get("spike_threshold_mbps", 20.0))
        host_threshold_mbps = float(self.config.get("host_spike_threshold_mbps", 60.0))

        is_server_spike = (srv_mbps >= threshold_mbps) or (srv_mbps >= 8.0 and srv_mbps >= avg_baseline_mbps * 5.0 and avg_baseline_mbps > 0.05)
        is_host_spike = (not is_server_spike) and (host_mbps >= host_threshold_mbps)

        if not is_server_spike and not is_host_spike:
            if self.in_spike and (now - self.last_spike_alert_time) > 30:
                self.in_spike = False
            return

        if (now - self.last_spike_alert_time) < 180:
            return

        self.in_spike = True
        self.last_spike_alert_time = now

        embed = self.diagnose_network_spike(
            is_server_spike, srv_mbps, total_srv_rate, srv_rx_rate, srv_tx_rate,
            avg_baseline, host_mbps, host_rx_rate, host_tx_rate
        )
        self.send_webhook([embed])

    def diagnose_network_spike(self, is_server_spike, srv_mbps, total_srv_rate, srv_rx_rate, srv_tx_rate, baseline, host_mbps, host_rx_rate, host_tx_rate):
        now = time.time()
        conn_count = 0
        try:
            p_ss = subprocess.run(["ss", "-tn", "sport = :25565"], capture_output=True, text=True, timeout=1.5)
            conn_count = max(0, len(p_ss.stdout.strip().splitlines()) - 1)
        except Exception:
            conn_count = 0

        top_player, top_ip, top_rate, top_share = self.get_top_bandwidth_player()

        recent_window_logs = [entry[1] for entry in self.recent_logs if (now - entry[0]) <= 45]

        primary_reason = "Unspecified High Traffic"
        evidence_details = []

        if not is_server_spike:
            primary_reason = "🌐 External Host Traffic (Non-Minecraft)"
            evidence_details.append(f"• Minecraft port 25565 is only consuming **{srv_mbps:.1f} Mbps**, but total server network is **{host_mbps:.1f} Mbps**.")
            evidence_details.append("• High probability of external downloads, backup synchronization, or background host operations.")
        else:
            log_text = " ".join(recent_window_logs)
            if "logged in" in log_text or "joined the game" in log_text:
                primary_reason = "🚀 Player World Login & Initial Chunk Burst"
                evidence_details.append("• One or more players joined the server within the last 45 seconds.")
                evidence_details.append("• Massive initial chunk data, block entities, and inventories transmitted to client.")
            elif "teleported" in log_text or "waystone" in log_text.lower():
                primary_reason = "🌀 Player Teleportation & New Chunk Loading"
                evidence_details.append("• Detected teleportation or dimension change in recent logs.")
                evidence_details.append("• Minecraft server pushed a full radius of world chunks to client immediately.")
            elif "moved too quickly" in log_text or "elytra" in log_text.lower():
                primary_reason = "🦅 Fast World Exploration (Elytra / High Velocity)"
                evidence_details.append("• Players moving at high velocity force continuous chunk generation and streaming.")
            elif top_share >= 75 and top_player:
                primary_reason = f"👤 Single Heavy Client Session ({top_player})"
                evidence_details.append(f"• Client **{top_player}** is responsible for **~{top_share:.0f}%** ({format_bytes(top_rate)}/s) of bandwidth.")
                evidence_details.append("• Typical cause: Rapid movement across the map or opening high-density container areas.")
            elif conn_count >= 3:
                primary_reason = f"👥 Concurrent Multi-Player World Activity ({conn_count} online)"
                evidence_details.append("• Multiple active players are concurrently generating chunk updates.")
            else:
                primary_reason = "📈 High Chunk / Entity Synchronization Burst"
                evidence_details.append("• Sudden spike in outgoing Minecraft protocol packets.")
                evidence_details.append("• Common causes: Fast map exploration, mob farm activity, or redstone chunk loading.")

            if top_player:
                evidence_details.append(f"• Top active client: **{top_player}** (`{top_ip}`) consuming **~{top_share:.0f}%** of traffic.")

        baseline_str = f"{format_bytes(baseline)}/s" if baseline > 0 else "Normal"

        embed = {
            "title": "📈 Abnormal Network Traffic Spike Detected",
            "description": "Server network bandwidth spiked above normal thresholds. Real-time root-cause diagnostic is detailed below.",
            "color": COLOR_ORANGE,
            "fields": [
                {
                    "name": "📊 Current Bandwidth",
                    "value": f"**{format_bytes(total_srv_rate)}/s** (~{srv_mbps:.1f} Mbps)\n• Outbound (Tx): {format_bytes(srv_tx_rate)}/s\n• Inbound (Rx): {format_bytes(srv_rx_rate)}/s",
                    "inline": True
                },
                {
                    "name": "📉 Normal Baseline",
                    "value": f"**~{baseline_str}**\nThreshold: {self.config.get('spike_threshold_mbps', 20.0)} Mbps",
                    "inline": True
                },
                {
                    "name": "🔍 Root Cause Diagnosis",
                    "value": f"**{primary_reason}**\n" + "\n".join(evidence_details),
                    "inline": False
                },
                {
                    "name": "👥 Server State",
                    "value": f"• Connected: **{conn_count}** player(s)",
                    "inline": True
                },
                {
                    "name": "🌐 Host Interface (eno1)",
                    "value": f"Total: **{format_bytes(host_rx_rate + host_tx_rate)}/s** (~{host_mbps:.1f} Mbps)",
                    "inline": True
                }
            ],
            "footer": {"text": "PufferPanel Traffic Diagnostic • Automated Anomaly Alert"},
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        return embed

    def analyze_disconnect(self, player, raw_reason):
        clean_reason = re.sub(r"§[0-9a-fk-or]", "", raw_reason).strip() or "Disconnected"
        reason_lower = clean_reason.lower()

        # Find player info (coords, ip)
        player_info = {}
        for ip, p_data in self.player_ips.items():
            if p_data.get("player") == player:
                player_info = p_data
                break
        coords = player_info.get("coords", "")

        # Find recent activity in self.recent_logs
        last_activity = ""
        now = time.time()
        for log_time, log_msg in reversed(self.recent_logs):
            if player in log_msg and "lost connection" not in log_msg:
                sec_ago = int(now - log_time)
                clean_msg = re.sub(r"§[0-9a-fk-or]", "", log_msg).strip()
                if "<" + player + ">" in clean_msg:
                    msg_text = clean_msg.split(">", 1)[-1].strip()
                    last_activity = f"Chat message: \"{msg_text[:100]}\" ({sec_ago}s before drop)"
                elif "whisper to " + player in clean_msg:
                    last_activity = f"Received whisper ({sec_ago}s before drop)"
                elif "logged in" in clean_msg:
                    last_activity = f"Logged in ({sec_ago}s before drop)"
                else:
                    last_activity = f"{clean_msg[:80]} ({sec_ago}s before drop)"
                break

        diag_lines = []
        title = f"⚠️ Player Dropped: {player}"
        color = COLOR_ORANGE

        if "timed out" in reason_lower:
            title = f"⏱️ Player Timed Out: {player}"
            color = COLOR_ORANGE
            diag_lines.append("• **Cause**: Client stopped acknowledging server keepalive packets (30s threshold reached).")
            diag_lines.append("• **Probable Reason**: Client network drop / Wi-Fi disconnected / ISP routing loss, or client game froze / tabbed out.")
            diag_lines.append("• **Server State**: Healthy (Minecraft server is ticking normally; not caused by server lag).")
        elif "flying" in reason_lower:
            title = f"🚫 Kicked for Flying: {player}"
            color = COLOR_RED
            diag_lines.append("• **Cause**: Server movement validation triggered.")
            diag_lines.append("• **Probable Reason**: Elytra flight speed desync, boat on ice velocity glitch, or high-ping rubberbanding.")
        elif "exception" in reason_lower:
            title = f"⚡ Network Pipeline Error: {player}"
            color = COLOR_ORANGE
            diag_lines.append("• **Cause**: Netty network pipeline error (packet serialization or socket reset).")
            diag_lines.append("• **Probable Reason**: Client abruptly closed connection, network interface changed, or packet ordering mismatch.")
        elif "kicked" in reason_lower or "banned" in reason_lower:
            title = f"🔨 Player Kicked: {player}"
            color = COLOR_RED
            diag_lines.append(f"• **Cause**: Administrative action: `{clean_reason}`")
        else:
            diag_lines.append(f"• **Reason**: `{clean_reason}`")

        if coords:
            diag_lines.append(f"• **Last Known Position**: `({coords})`")
        if last_activity:
            diag_lines.append(f"• **Recent Player Action**: {last_activity}")

        return title, color, clean_reason, "\n".join(diag_lines)

    def dispatch_error(self, err_info):
        if not self.config.get("alerts", {}).get("critical_errors", True):
            return

        thread = err_info["thread"]
        level = err_info["level"]
        headline = err_info["message"]
        trace_lines = err_info["trace_lines"]
        now = time.time()

        if "standing on air" in headline:
            return

        err_key = (headline[:60] + ":" + (trace_lines[0][:60] if trace_lines else ""))
        if err_key in self.recent_errors and (now - self.recent_errors[err_key]) < 60:
            return
        self.recent_errors[err_key] = now
        self.recent_errors = {k: v for k, v in self.recent_errors.items() if (now - v) < 120}

        exception_name = "Server Error"
        exception_msg = headline
        culprit_mod = None
        culprit_frame = None

        for l in trace_lines:
            l_s = l.strip()
            if not exception_name or exception_name == "Server Error":
                m_ex = re.search(r"([a-zA-Z0-9_\.]*(?:Exception|Error|Throwable))(?::\s*(.*))?", l_s)
                if m_ex:
                    exception_name = m_ex.group(1).split(".")[-1]
                    if m_ex.group(2):
                        exception_msg = m_ex.group(2).strip()
                    continue

            if "at " in l_s:
                l_lower = l_s.lower()
                if not culprit_mod:
                    if "modenforcer" in l_lower:
                        culprit_mod = "ModEnforcer"
                    elif "voicechat" in l_lower:
                        culprit_mod = "Simple Voice Chat"
                    elif "xaero" in l_lower:
                        culprit_mod = "Xaero's Map"
                    elif "c2me" in l_lower:
                        culprit_mod = "C2ME"
                    elif "lithium" in l_lower:
                        culprit_mod = "Lithium"
                    elif "polymer" in l_lower:
                        culprit_mod = "Polymer"
                    elif "fabric" not in l_lower and "minecraft" not in l_lower:
                        m_pkg = re.search(r"at (?:knot//)?([a-zA-Z0-9_\.]+)\.([a-zA-Z0-9_\$]+)\(", l_s)
                        if m_pkg:
                            culprit_mod = m_pkg.group(1).split(".")[-1]

                if not culprit_frame and "fabric" not in l_s.lower() and "minecraft" not in l_s.lower():
                    m_frame = re.search(r"at (?:knot//)?([a-zA-Z0-9_\.\$]+\([^\)]+\))", l_s)
                    if m_frame:
                        culprit_frame = m_frame.group(1)

        if not culprit_frame and trace_lines:
            for l in trace_lines:
                if "at " in l:
                    m_frame = re.search(r"at (?:knot//)?([a-zA-Z0-9_\.\$]+\([^\)]+\))", l.strip())
                    if m_frame:
                        culprit_frame = m_frame.group(1)
                        break

        is_crash = ("watchdog" in thread.lower() or "fatal" in level.lower() or "outofmemory" in exception_name.lower())

        diag_lines = []
        if culprit_mod == "ModEnforcer" and "isVoiceChatPresent" in str(culprit_frame):
            diag_lines.append("• **Culprit Mod**: `ModEnforcer` (`ModEnforcerMod.java:198`)")
            diag_lines.append("• **Root Cause**: ModEnforcer checked player voice chat network channels during login before Fabric finished channel registration.")
            diag_lines.append("• **Impact**: ⚠️ Handled Task Exception. Caught by Minecraft event loop; server remained running normally, player logged in successfully.")
        else:
            if culprit_mod:
                diag_lines.append(f"• **Culprit Mod / Package**: `{culprit_mod}`")
            if culprit_frame:
                diag_lines.append(f"• **Failing Location**: `{culprit_frame}`")
            diag_lines.append(f"• **Impact**: {'🚨 CRITICAL SERVER CRASH' if is_crash else '⚠️ Handled Task Exception (Non-Fatal, Server Running)'}")

        fields = [
            {"name": "⚠️ Exception / Error", "value": f"**`{exception_name}`**\n```{exception_msg[:300]}```", "inline": False},
            {"name": "🔍 Root Cause Diagnosis", "value": "\n".join(diag_lines), "inline": False}
        ]

        if trace_lines:
            formatted_trace = "\n".join(trace_lines[:7])
            fields.append({"name": "📜 Stack Trace Preview", "value": f"```java\n{formatted_trace[:950]}\n```", "inline": False})

        embed = {
            "title": f"{'🚨' if is_crash else '⚠️'} Server {level}: [{thread}]" + (f" - {culprit_mod}" if culprit_mod else ""),
            "color": COLOR_RED if is_crash else COLOR_ORANGE,
            "fields": fields,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "footer": {"text": "PufferPanel Error Diagnostic"}
        }

        # Only trigger emergency mention on true crashes
        self.send_webhook([embed], is_critical=is_crash)

    def handle_line(self, line):
        line = line.strip()
        if not line:
            return

        now = time.time()
        self.recent_logs.append((now, line))

        m_login = LOGIN_REGEX.search(line)
        if m_login:
            self.player_ips[m_login.group("ip")] = {
                "player": m_login.group("player"),
                "coords": m_login.group("coords") or "",
                "port": m_login.group("port")
            }

        m_disc = DISCONNECT_REGEX.search(line)
        if m_disc:
            alerts_cfg = self.config.get("alerts", {})
            player = m_disc.group("player")
            raw_reason = m_disc.group("reason")
            clean_reason = re.sub(r"§[0-9a-fk-or]", "", raw_reason).strip() or "Disconnected"
            reason_lower = clean_reason.lower()

            # Ignore missing client/required mods disconnects as requested
            if "missing" in reason_lower or "required mod" in reason_lower or "client mod" in reason_lower:
                return

            is_normal = reason_lower == "disconnected"

            if is_normal:
                if alerts_cfg.get("normal_leaves", False):
                    embed = {
                        "title": f"👋 Player Left: {player}",
                        "description": f"**Player**: `{player}`\n**Status**: Voluntarily disconnected",
                        "color": COLOR_GREEN,
                        "thumbnail": {"url": f"https://mc-heads.net/avatar/{player}/64.png"},
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "footer": {"text": "PufferPanel Player Monitor"}
                    }
                    self.send_webhook([embed])
            else:
                if alerts_cfg.get("disconnects", True):
                    title, color, clean_reason, diagnosis = self.analyze_disconnect(player, raw_reason)
                    embed = {
                        "title": title,
                        "color": color,
                        "thumbnail": {"url": f"https://mc-heads.net/avatar/{player}/64.png"},
                        "fields": [
                            {"name": "Player", "value": f"`{player}`", "inline": True},
                            {"name": "Reported Reason", "value": f"```{clean_reason}```", "inline": False},
                            {"name": "🔍 Root Cause Diagnosis", "value": diagnosis, "inline": False}
                        ],
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "footer": {"text": "PufferPanel Disconnect Diagnostic"}
                    }
                    self.send_webhook([embed])
            return

    def run(self):
        print("Starting PufferPanel Discord Logger & Network Diagnostic daemon...")
        while not os.path.exists(LOG_PATH):
            print(f"Waiting for {LOG_PATH}...")
            time.sleep(2.0)

        with open(LOG_PATH, "r", errors="ignore") as f:
            f.seek(0, os.SEEK_END)
            last_inode = os.fstat(f.fileno()).st_ino

            loop_counter = 0
            pending_error = None

            while True:
                line = f.readline()
                if line:
                    line_clean = line.strip()
                    if pending_error:
                        # Check if line continues stack trace
                        if not line.startswith("[") or line.startswith("    ") or line.startswith("\t") or line_clean.startswith("at ") or line_clean.startswith("Caused by"):
                            pending_error["trace_lines"].append(line_clean)
                            continue
                        else:
                            self.dispatch_error(pending_error)
                            pending_error = None

                    m_err = ERROR_REGEX.search(line)
                    if m_err:
                        pending_error = {
                            "time": m_err.group("time"),
                            "thread": m_err.group("thread"),
                            "level": m_err.group("level"),
                            "message": m_err.group("message").strip(),
                            "trace_lines": [],
                            "detected_at": time.time()
                        }
                        continue

                    self.handle_line(line)
                else:
                    if pending_error and (time.time() - pending_error["detected_at"]) > 0.4:
                        self.dispatch_error(pending_error)
                        pending_error = None

                    time.sleep(0.5)
                    loop_counter += 1

                    if loop_counter % 2 == 0:
                        self.check_network_spikes()

                    if loop_counter % 4 == 0:
                        self.load_config()
                        self.check_crashes()

                    try:
                        if os.path.exists(LOG_PATH):
                            current_stat = os.stat(LOG_PATH)
                            if current_stat.st_ino != last_inode or current_stat.st_size < f.tell():
                                print("Log rotated, reopening...")
                                f.close()
                                f = open(LOG_PATH, "r", errors="ignore")
                                last_inode = os.fstat(f.fileno()).st_ino
                    except Exception as e:
                        print(f"Error checking log rotation: {e}")

if __name__ == "__main__":
    logger = DiscordLogger()
    logger.run()
