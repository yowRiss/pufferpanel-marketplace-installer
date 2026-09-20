package com.customjukebox;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackDownloader {
    private static final ExecutorService DOWNLOAD_POOL = Executors.newFixedThreadPool(2);
    private static final Set<UUID> activeDownloads = ConcurrentHashMap.newKeySet();
    private static final int MAX_BYTES_PER_SEC = 256 * 1024; // 256 KB/s throttled
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB limit

    public static int startDownload(CommandSourceStack source, ServerPlayer player, String rawUrl) {
        UUID uuid = player.getUUID();
        if (activeDownloads.contains(uuid)) {
            player.sendSystemMessage(Component.literal("§c[MusicDisc] Anda sedang memiliki unduhan yang berjalan! Harap tunggu hingga selesai."));
            return 0;
        }

        String url = rawUrl.trim();
        if (url.startsWith("\"") && url.endsWith("\"") && url.length() >= 2) {
            url = url.substring(1, url.length() - 1).trim();
        }
        if (url.startsWith("'") && url.endsWith("'") && url.length() >= 2) {
            url = url.substring(1, url.length() - 1).trim();
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            player.sendSystemMessage(Component.literal("§c[MusicDisc] URL harus diawali dengan http:// atau https:// !"));
            return 0;
        }

        String lower = url.toLowerCase();
        if (!lower.contains(".mp3") && !lower.contains(".wav") && !lower.contains(".ogg") && !lower.contains(".flac") && !lower.contains(".m4a")) {
            player.sendSystemMessage(Component.literal("§c[MusicDisc] Link URL harus mengarah ke file audio (.mp3, .wav, .ogg, .flac, .m4a)!"));
            return 0;
        }

        activeDownloads.add(uuid);
        String finalUrl = url;
        MinecraftServer server = source.getServer();

        DOWNLOAD_POOL.submit(() -> {
            File destFile = null;
            File tempFile = null;
            try {
                URL downloadUrl = URI.create(finalUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CustomJukebox/1.0");
                conn.setInstanceFollowRedirects(true);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == 307 || responseCode == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    downloadUrl = URI.create(newUrl).toURL();
                    conn = (HttpURLConnection) downloadUrl.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CustomJukebox/1.0");
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(20000);
                    responseCode = conn.getResponseCode();
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception("HTTP " + responseCode + " " + conn.getResponseMessage());
                }

                long contentLength = conn.getContentLengthLong();
                if (contentLength > MAX_FILE_SIZE) {
                    throw new Exception("Ukuran file melebihi batas maksimal (50 MB)");
                }

                String filename = extractFileName(finalUrl, conn);
                File musicDir = JukeboxMusicManager.getMusicDir();
                if (!musicDir.exists()) {
                    musicDir.mkdirs();
                }

                destFile = new File(musicDir, filename);
                tempFile = new File(musicDir, filename + ".download.tmp");

                // Calculate estimate
                String sizeStr = (contentLength > 0) ? String.format("%.2f MB", contentLength / (1024.0 * 1024.0)) : "Ukuran dinamis";
                int estSeconds = (contentLength > 0) ? (int) Math.ceil((double) contentLength / MAX_BYTES_PER_SEC) : -1;
                String estTimeStr = (estSeconds > 0) ? formatDuration(estSeconds) : "Sedang mengunduh...";

                // Send initial whisper to player
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal("§6[MusicDisc] §7Memulai download: §e" + filename + " §7(§f" + sizeStr + "§7)"));
                        p.sendSystemMessage(Component.literal("§6[MusicDisc] §7Kecepatan: §f256 KB/s §7| Perkiraan selesai: §e" + estTimeStr));
                        p.sendSystemMessage(Component.literal("§7Disc kustom akan otomatis diberikan ke inventory Anda begitu selesai."));
                    }
                });

                // Download with throttling
                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesInSec = 0;
                long secStart = System.currentTimeMillis();
                long lastProgressNotice = System.currentTimeMillis();

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalRead += read;
                        bytesInSec += read;

                        if (totalRead > MAX_FILE_SIZE) {
                            throw new Exception("Ukuran file melebihi batas maksimal (50 MB)");
                        }

                        // Rate limit to MAX_BYTES_PER_SEC
                        if (bytesInSec >= MAX_BYTES_PER_SEC) {
                            long elapsed = System.currentTimeMillis() - secStart;
                            if (elapsed < 1000) {
                                Thread.sleep(1000 - elapsed);
                            }
                            secStart = System.currentTimeMillis();
                            bytesInSec = 0;
                        }

                        // Periodic notification every 10 seconds if file is large
                        long now = System.currentTimeMillis();
                        if (now - lastProgressNotice >= 10000 && contentLength > 0) {
                            lastProgressNotice = now;
                            int pct = (int) ((totalRead * 100) / contentLength);
                            long remainingBytes = contentLength - totalRead;
                            int remSec = (int) Math.ceil((double) remainingBytes / MAX_BYTES_PER_SEC);
                            server.execute(() -> {
                                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                                if (p != null) {
                                    p.sendSystemMessage(Component.literal("§6[MusicDisc] §7Download §e" + filename + "§7: §a" + pct + "% §7(Sisa: §e" + formatDuration(remSec) + "§7)"), true);
                                }
                            });
                        }
                    }
                }

                if (tempFile.length() <= 0) {
                    throw new Exception("File hasil unduhan kosong (0 bytes)");
                }

                // Replace or rename to destination
                if (destFile.exists()) {
                    destFile.delete();
                }
                boolean renamed = tempFile.renameTo(destFile);
                if (!renamed) {
                    throw new Exception("Gagal memindahkan file unduhan ke direktori musik");
                }

                String finalTitle = JukeboxCommands.cleanTitle(destFile.getName());
                ItemStack disc = JukeboxCommands.createMusicDisc(destFile.getName(), finalTitle);

                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        boolean added = p.getInventory().add(disc.copy());
                        if (!added) {
                            p.drop(disc.copy(), false, Prediction.SERVER_ONLY);
                        }
                        p.sendSystemMessage(Component.literal("§a[MusicDisc] Unduhan selesai! Disc §e" + finalTitle + " §atelah masuk ke inventory Anda."));
                        p.sendSystemMessage(Component.literal("§7Putar di Jukebox mana saja untuk mendengarkan!"));
                        p.playSound(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
                    }
                });

            } catch (Exception e) {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                String err = e.getMessage() != null ? e.getMessage() : e.toString();
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal("§c[MusicDisc] Gagal mendownload lagu: " + err));
                    }
                });
            } finally {
                activeDownloads.remove(uuid);
            }
        });

        return 1;
    }

    private static String extractFileName(String urlStr, HttpURLConnection conn) {
        // 1. Try Content-Disposition header
        String disposition = conn.getHeaderField("Content-Disposition");
        if (disposition != null && disposition.contains("filename=")) {
            int idx = disposition.indexOf("filename=");
            String name = disposition.substring(idx + 9).trim();
            if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
                name = name.substring(1, name.length() - 1);
            }
            name = sanitizeFilename(name);
            if (!name.isBlank() && hasAudioExtension(name)) {
                return name;
            }
        }

        // 2. Extract from URL path
        try {
            int qIdx = urlStr.indexOf('?');
            String pathPart = (qIdx != -1) ? urlStr.substring(0, qIdx) : urlStr;
            int hashIdx = pathPart.indexOf('#');
            if (hashIdx != -1) pathPart = pathPart.substring(0, hashIdx);

            int slashIdx = pathPart.lastIndexOf('/');
            String name = (slashIdx != -1) ? pathPart.substring(slashIdx + 1) : pathPart;
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
            name = sanitizeFilename(name);
            if (!name.isBlank()) {
                if (!hasAudioExtension(name)) {
                    name += ".mp3";
                }
                return name;
            }
        } catch (Exception ignored) {}

        return "custom_track_" + (System.currentTimeMillis() % 100000) + ".mp3";
    }

    private static boolean hasAudioExtension(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") ||
               lower.endsWith(".flac") || lower.endsWith(".m4a") || lower.endsWith(".aac");
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String formatDuration(int totalSec) {
        if (totalSec < 60) {
            return totalSec + " detik";
        }
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return (sec > 0) ? (min + " menit " + sec + " detik") : (min + " menit");
    }
}
