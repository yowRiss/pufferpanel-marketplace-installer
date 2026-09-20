package com.customjukebox;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class JukeboxMusicManager {
    private static final Map<BlockPos, JukeboxPlayback> activePlaybacks = new ConcurrentHashMap<>();
    private static AudioPlayerManager playerManager;
    private static final File MUSIC_DIR = new File("music");
    private static final ExecutorService ASYNC_LOADER = Executors.newCachedThreadPool();

    public static void init() {
        if (!MUSIC_DIR.exists()) {
            MUSIC_DIR.mkdirs();
        }
        playerManager = new DefaultAudioPlayerManager();
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        playerManager.getConfiguration().setOutputFormat(new Pcm16AudioDataFormat(1, 48000, 960, true));
    }

    public static File getMusicDir() {
        return MUSIC_DIR;
    }

    public static List<String> getTrackNames() {
        List<String> list = new ArrayList<>();
        if (!MUSIC_DIR.exists() || !MUSIC_DIR.isDirectory()) return list;
        File[] files = MUSIC_DIR.listFiles();
        if (files == null) return list;
        for (File f : files) {
            if (f.isFile() && isAudioFile(f.getName())) {
                list.add(f.getName());
            }
        }
        Collections.sort(list);
        return list;
    }

    public static boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") ||
               lower.endsWith(".flac") || lower.endsWith(".m4a") || lower.endsWith(".aac");
    }

    public static File resolveTrackFile(String input) {
        if (input == null || input.isBlank()) return null;
        input = input.trim();
        if ((input.startsWith("\"") && input.endsWith("\"")) || (input.startsWith("'") && input.endsWith("'"))) {
            if (input.length() >= 2) {
                input = input.substring(1, input.length() - 1).trim();
            }
        }
        File direct = new File(MUSIC_DIR, input);
        if (direct.exists() && direct.isFile()) return direct;

        String[] exts = {".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac"};
        for (String ext : exts) {
            File f = new File(MUSIC_DIR, input + ext);
            if (f.exists() && f.isFile()) return f;
        }

        File[] files = MUSIC_DIR.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equalsIgnoreCase(input) || 
                    f.getName().toLowerCase().startsWith(input.toLowerCase() + ".")) {
                    return f;
                }
            }
        }
        return null;
    }

    public static boolean isPlaying(BlockPos pos) {
        JukeboxPlayback pb = activePlaybacks.get(pos);
        return pb != null && !pb.isStopped();
    }

    public static void stop(BlockPos pos) {
        JukeboxPlayback pb = activePlaybacks.remove(pos);
        if (pb != null) {
            pb.stop();
        }
    }

    public static void stopAll() {
        for (BlockPos pos : new ArrayList<>(activePlaybacks.keySet())) {
            stop(pos);
        }
    }

    public static boolean play(Level level, BlockPos pos, String trackName, String displayTitle) {
        File file = resolveTrackFile(trackName);
        if (file == null || !file.exists()) {
            return false;
        }

        VoicechatServerApi api = CustomJukeboxPlugin.getApi();
        if (api == null) {
            return false;
        }

        stop(pos);

        ASYNC_LOADER.submit(() -> {
            try {
                AudioPlayer lavaPlayer = playerManager.createPlayer();
                CompletableFuture<AudioTrack> trackFuture = new CompletableFuture<>();

                playerManager.loadItem(file.getAbsolutePath(), new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        trackFuture.complete(track);
                    }
                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        if (!playlist.getTracks().isEmpty()) {
                            trackFuture.complete(playlist.getTracks().get(0));
                        } else {
                            trackFuture.complete(null);
                        }
                    }
                    @Override
                    public void noMatches() {
                        trackFuture.complete(null);
                    }
                    @Override
                    public void loadFailed(FriendlyException exception) {
                        trackFuture.completeExceptionally(exception);
                    }
                });

                AudioTrack track = trackFuture.get(10, TimeUnit.SECONDS);
                if (track == null) {
                    lavaPlayer.destroy();
                    return;
                }

                java.util.concurrent.atomic.AtomicBoolean active = new java.util.concurrent.atomic.AtomicBoolean(true);

                lavaPlayer.addListener(new com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter() {
                    @Override
                    public void onTrackEnd(AudioPlayer p, AudioTrack t, com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason endReason) {
                        if (endReason.mayStartNext && active.get()) {
                            p.playTrack(t.makeClone());
                        }
                    }
                });

                lavaPlayer.playTrack(track);

                Position vPos = api.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                ServerLevel vLevel = api.fromServerLevel(level);
                UUID channelId = UUID.randomUUID();

                LocationalAudioChannel channel = api.createLocationalAudioChannel(channelId, vLevel, vPos);
                channel.setDistance(32.0f);
                channel.setCategory("music");

                OpusEncoder encoder = api.createEncoder();

                MutableAudioFrame frame = new MutableAudioFrame();
                ByteBuffer buffer = ByteBuffer.allocate(1920);
                frame.setBuffer(buffer);
                frame.setFormat(playerManager.getConfiguration().getOutputFormat());

                Supplier<short[]> supplier = new Supplier<>() {
                    @Override
                    public short[] get() {
                        try {
                            if (!active.get()) {
                                return null;
                            }
                            buffer.clear();
                            if (!lavaPlayer.provide(frame, 20, TimeUnit.MILLISECONDS)) {
                                if (!active.get()) {
                                    return null;
                                }
                                return new short[960];
                            }
                            byte[] raw = buffer.array();
                            short[] pcm = new short[960];
                            for (int i = 0; i < 960; i++) {
                                pcm[i] = (short) ((raw[i * 2] << 8) | (raw[i * 2 + 1] & 0xFF));
                            }
                            return pcm;
                        } catch (Exception e) {
                            return null;
                        }
                    }
                };

                de.maxhenkel.voicechat.api.audiochannel.AudioPlayer voicePlayer = 
                    api.createAudioPlayer(channel, encoder, supplier);

                JukeboxPlayback playback = new JukeboxPlayback(channel, voicePlayer, lavaPlayer, displayTitle, active);
                activePlaybacks.put(pos, playback);

                voicePlayer.setOnStopped(() -> {
                    activePlaybacks.remove(pos);
                    playback.stop();
                });

                voicePlayer.startPlaying();

                Component msg = Component.literal("§6Now playing: §e" + displayTitle);
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    for (ServerPlayer sp : sl.players()) {
                        if (sp.blockPosition().closerThan(pos, 32.0)) {
                            sp.sendSystemMessage(msg, true);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[CustomJukebox] Failed to play track " + trackName + ": " + e.getMessage());
            }
        });

        return true;
    }
}
