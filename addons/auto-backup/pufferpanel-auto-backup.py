#!/usr/bin/env python3
import os
import sys
import json
import time
import shutil
import tarfile
import sqlite3
import datetime
import urllib.request
import urllib.error
import fcntl
import termios

CONFIG_FILE = "/etc/pufferpanel/auto-backup.json"

DEFAULT_CONFIG = {
    "enabled": True,
    "server_id": "35ca4939",
    "server_dir": "/var/lib/pufferpanel/servers/35ca4939",
    "backup_dir": "/var/lib/pufferpanel/backups/35ca4939",
    "database_path": "/var/lib/pufferpanel/database.db",
    "max_backups": 3,
    "include_targets": [
        "world",
        "config",
        "defaultconfigs",
        "server.properties",
        "whitelist.json",
        "ops.json",
        "banned-players.json",
        "banned-ips.json",
        "usercache.json"
    ],
    "include_mods": True,
    "safe_save": True,
    "console_pts": "/dev/pts/0",
    "discord_webhook": "",
    "notify_discord": True
}

def load_config():
    cfg = DEFAULT_CONFIG.copy()
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r") as f:
                user_cfg = json.load(f)
                cfg.update(user_cfg)
        except Exception as e:
            print(f"[WARN] Failed to load {CONFIG_FILE}: {e}, using defaults.")
    return cfg

def send_console_command(cmd, pts_path):
    if not os.path.exists(pts_path):
        return False
    try:
        with open(pts_path, "w") as fd:
            for char in (cmd + "\n"):
                fcntl.ioctl(fd, termios.TIOCSTI, char)
        return True
    except Exception as e:
        print(f"[WARN] Failed to send console command '{cmd}': {e}")
        return False

def is_server_running():
    try:
        res = os.popen("pgrep -f 'fabric-server-launch.jar|server.jar'").read().strip()
        return bool(res)
    except Exception:
        return False

def send_discord_notification(webhook_url, title, description, fields=None, color=0x2ecc71):
    if not webhook_url:
        return
    payload = {
        "username": "PufferPanel Backup Bot",
        "avatar_url": "https://cdn.icon-icons.com/icons2/2699/PNG/512/minecraft_logo_icon_168974.png",
        "embeds": [{
            "title": title,
            "description": description,
            "color": color,
            "fields": fields or [],
            "footer": {
                "text": "PufferPanel Automated Backup • Retention Policy: Max 3 Backups"
            },
            "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat()
        }]
    }
    try:
        req = urllib.request.Request(
            webhook_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "User-Agent": "PufferPanelBackup/1.0"
            }
        )
        urllib.request.urlopen(req, timeout=10)
    except Exception as e:
        print(f"[WARN] Failed to send Discord webhook: {e}")

def create_backup(cfg):
    server_dir = cfg["server_dir"]
    backup_dir = cfg["backup_dir"]
    server_id = cfg["server_id"]
    db_path = cfg["database_path"]
    max_backups = max(1, int(cfg.get("max_backups", 3)))

    os.makedirs(backup_dir, exist_ok=True)
    try:
        shutil.chown(backup_dir, user="pufferpanel", group="pufferpanel")
    except Exception:
        pass

    now = datetime.datetime.now()
    timestamp_str = now.strftime("%Y%m%d-%H%M%S")
    backup_name = f"Auto Backup {now.strftime('%Y-%m-%d %H:%M:%S')}"
    file_name = f"{server_id}-{timestamp_str}.tar.gz"
    final_tar_path = os.path.join(backup_dir, file_name)
    temp_tar_path = os.path.join(backup_dir, f".{file_name}.tmp")

    print(f"[*] Starting backup process for server {server_id}...")
    print(f"[*] Backup Name: {backup_name}")
    print(f"[*] Output File: {final_tar_path}")

    server_running = is_server_running()
    safe_save = cfg.get("safe_save", True) and server_running

    if safe_save:
        pts = cfg.get("console_pts", "/dev/pts/0")
        print("[*] Server is online. Running safe-save commands (save-off, save-all flush)...")
        send_console_command("save-off", pts)
        time.sleep(0.5)
        send_console_command("save-all flush", pts)
        time.sleep(3.0)  # Wait for disk sync

    targets = list(cfg.get("include_targets", ["world", "config"]))
    if cfg.get("include_mods", True) and "mods" not in targets:
        targets.append("mods")

    start_time = time.time()
    try:
        with tarfile.open(temp_tar_path, "w:gz", compresslevel=6) as tar:
            for item in targets:
                item_path = os.path.join(server_dir, item)
                if os.path.exists(item_path):
                    print(f"  + Adding: {item}")
                    tar.add(item_path, arcname=item)
                else:
                    print(f"  - Skipped (not found): {item}")

        os.rename(temp_tar_path, final_tar_path)
        try:
            shutil.chown(final_tar_path, user="pufferpanel", group="pufferpanel")
            os.chmod(final_tar_path, 0o644)
        except Exception:
            pass

    except Exception as e:
        if os.path.exists(temp_tar_path):
            os.remove(temp_tar_path)
        if safe_save:
            send_console_command("save-on", cfg.get("console_pts", "/dev/pts/0"))
        raise RuntimeError(f"Failed to create tar.gz archive: {e}")

    finally:
        if safe_save:
            print("[*] Re-enabling auto-save (save-on)...")
            send_console_command("save-on", cfg.get("console_pts", "/dev/pts/0"))

    duration = time.time() - start_time
    file_size_bytes = os.path.getsize(final_tar_path)
    file_size_mb = file_size_bytes / (1024 * 1024)
    print(f"[+] Archive created successfully: {file_size_mb:.2f} MB in {duration:.1f}s")

    # Format created_at to match PufferPanel's standard timestamp
    # e.g. 2026-09-20 11:00:00+08:00
    tz_offset = datetime.datetime.now().astimezone().strftime("%z")
    tz_formatted = f"{tz_offset[:3]}:{tz_offset[3:]}" if tz_offset else "+00:00"
    created_at_str = f"{now.strftime('%Y-%m-%d %H:%M:%S')}{tz_formatted}"

    # Register in PufferPanel SQLite database
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    cur.execute(
        "INSERT INTO backups (name, file_name, server_id, created_at) VALUES (?, ?, ?, ?);",
        (backup_name, file_name, server_id, created_at_str)
    )
    new_backup_id = cur.lastrowid
    con.commit()
    print(f"[+] Registered backup in PufferPanel DB with ID: {new_backup_id}")

    # Enforce retention policy: Max 3 backups
    # Query all backups for this server ordered from oldest to newest
    cur.execute(
        "SELECT id, name, file_name FROM backups WHERE server_id = ? ORDER BY id ASC;",
        (server_id,)
    )
    all_backups = cur.fetchall()
    total_count = len(all_backups)
    deleted_info = []

    print(f"[*] Total backups currently tracked: {total_count} (Retention limit: {max_backups})")

    if total_count > max_backups:
        num_to_delete = total_count - max_backups
        oldest_to_delete = all_backups[:num_to_delete]
        for b_id, b_name, b_file in oldest_to_delete:
            print(f"[*] Rotation limit reached: Deleting oldest backup ID {b_id}: {b_name} ({b_file})...")
            # Delete physical file
            target_del_file = os.path.join(backup_dir, b_file)
            if os.path.exists(target_del_file):
                try:
                    os.remove(target_del_file)
                    print(f"    - Deleted file: {target_del_file}")
                except Exception as ex:
                    print(f"    [WARN] Failed to delete file {target_del_file}: {ex}")
            
            # Delete database row
            cur.execute("DELETE FROM backups WHERE id = ?;", (b_id,))
            con.commit()
            print(f"    - Removed row ID {b_id} from PufferPanel database.")
            deleted_info.append(f"{b_name} (`{b_file}`)")

    con.close()

    # Discord notification
    if cfg.get("notify_discord", True) and cfg.get("discord_webhook"):
        fields = [
            {"name": "📦 Backup Name", "value": f"**{backup_name}**", "inline": True},
            {"name": "📁 File Size", "value": f"**{file_size_mb:.2f} MB**", "inline": True},
            {"name": "⏱️ Duration", "value": f"**{duration:.1f}s**", "inline": True},
            {"name": "📊 Panel Status", "value": "✅ Registered in PufferPanel (Backup Tab)", "inline": False},
            {"name": "🛡️ Retention Status", "value": f"Total Stored: **{min(total_count, max_backups)} / {max_backups}**", "inline": True}
        ]
        if deleted_info:
            fields.append({
                "name": "🗑️ Rotated (Oldest Deleted)",
                "value": "\n".join(deleted_info),
                "inline": False
            })

        send_discord_notification(
            cfg["discord_webhook"],
            title="💾 PufferPanel Auto-Backup Successful",
            description=f"Automated backup of Minecraft server `{server_id}` finished successfully and is ready for download or restore in PufferPanel.",
            fields=fields,
            color=0x2ecc71
        )

    print("[+] Auto-backup completed successfully!")
    return {
        "id": new_backup_id,
        "name": backup_name,
        "file_name": file_name,
        "size_mb": file_size_mb,
        "rotated": deleted_info
    }

def list_backups(cfg):
    con = sqlite3.connect(cfg["database_path"])
    cur = con.cursor()
    cur.execute("SELECT id, name, file_name, created_at FROM backups WHERE server_id = ? ORDER BY id DESC;", (cfg["server_id"],))
    rows = cur.fetchall()
    con.close()

    print(f"=== PufferPanel Backups for Server {cfg['server_id']} (Total: {len(rows)}) ===")
    for r in rows:
        fpath = os.path.join(cfg["backup_dir"], r[2])
        size_str = f"{os.path.getsize(fpath) / (1024*1024):.2f} MB" if os.path.exists(fpath) else "MISSING ON DISK"
        print(f" ID: {r[0]} | Name: {r[1]} | File: {r[2]} | Size: {size_str} | Created: {r[3]}")

if __name__ == "__main__":
    config = load_config()
    if len(sys.argv) > 1 and sys.argv[1] == "--list":
        list_backups(config)
    else:
        try:
            create_backup(config)
        except Exception as err:
            print(f"[ERROR] Auto-backup failed: {err}")
            if config.get("notify_discord", True) and config.get("discord_webhook"):
                send_discord_notification(
                    config["discord_webhook"],
                    title="🚨 PufferPanel Auto-Backup FAILED",
                    description=f"An error occurred while creating automatic server backup: `{err}`",
                    color=0xe74c3c
                )
            sys.exit(1)
