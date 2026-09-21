package com.customjukebox;

import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
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
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
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
        JukeboxConfig.load();

        playerManager = new DefaultAudioPlayerManager();
        playerManager.registerSourceManager(new LocalAudioSourceManager());
        AudioConfiguration config = playerManager.getConfiguration();
        config.setOutputFormat(new Pcm16AudioDataFormat(2, 48000, 960, true));
        config.setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        config.setOpusEncodingQuality(10);
    }

    public static File getMusicDir() {
        return MUSIC_DIR;
    }

    public static void setGlobalVolume(int vol) {
        JukeboxConfig.setVolume(vol);
        for (JukeboxPlayback pb : activePlaybacks.values()) {
            pb.setVolume(vol);
        }
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
                        if (JukeboxConfig.isLoop() && endReason.mayStartNext && active.get()) {
                            p.playTrack(t.makeClone());
                        }
                    }
                });

                // Set calibrated master volume (default 65 = -3.7 dB headroom against Opus bass clipping)
                lavaPlayer.setVolume(JukeboxConfig.getVolume());
                lavaPlayer.playTrack(track);

                UUID channelId = UUID.randomUUID();
                AudioChannel channel;
                double range = JukeboxConfig.getRange();
                ServerLevel vLevel = api.fromServerLevel(level);

                if ("3d".equalsIgnoreCase(JukeboxConfig.getMode())) {
                    Position vPos = api.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    LocationalAudioChannel locChannel = api.createLocationalAudioChannel(channelId, vLevel, vPos);
                    locChannel.setDistance((float) range);
                    locChannel.setCategory("music");
                    channel = locChannel;
                } else {
                    // DIRECT mode (StaticAudioChannel) - Studio Master, direct stereo, no OpenAL HRTF tin-can effect!
                    StaticAudioChannel staticChannel = api.createStaticAudioChannel(channelId);
                    staticChannel.setCategory("music");
                    staticChannel.setBypassGroupIsolation(true);

                    double maxDistSq = range * range;
                    staticChannel.setFilter(player -> {
                        if (player == null) return false;
                        if (player.getServerLevel() != null && !player.getServerLevel().equals(vLevel)) {
                            return false;
                        }
                        Position pPos = player.getPosition();
                        if (pPos == null) return false;
                        double dx = pPos.getX() - (pos.getX() + 0.5);
                        double dy = pPos.getY() - (pos.getY() + 0.5);
                        double dz = pPos.getZ() - (pos.getZ() + 0.5);
                        return (dx * dx + dy * dy + dz * dz) <= maxDistSq;
                    });

                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                        for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
                            VoicechatConnection conn = api.getConnectionOf(sp.getUUID());
                            if (conn != null) {
                                staticChannel.addTarget(conn);
                            }
                        }
                    }
                    channel = staticChannel;
                }

                OpusEncoder encoder = api.createEncoder(OpusEncoderMode.AUDIO);

                // High capacity jitter buffer queue (200 frames = 4 seconds of audio)
                BlockingQueue<short[]> audioQueue = new LinkedBlockingQueue<>(200);

                Thread feederThread = new Thread(() -> {
                    try {
                        MutableAudioFrame frame = new MutableAudioFrame();
                        ByteBuffer buffer = ByteBuffer.allocate(3840).order(ByteOrder.BIG_ENDIAN);
                        frame.setBuffer(buffer);
                        frame.setFormat(playerManager.getConfiguration().getOutputFormat());

                        int targetUpdateCounter = 0;

                        while (active.get()) {
                            buffer.clear();
                            boolean provided = lavaPlayer.provide(frame, 40, TimeUnit.MILLISECONDS);
                            if (provided) {
                                ShortBuffer sb = buffer.asShortBuffer();
                                short[] pcm = new short[960];
                                for (int i = 0; i < 960; i++) {
                                    short left = sb.get(i * 2);
                                    short right = sb.get(i * 2 + 1);
                                    int mixed = (int) (left * 0.5f + right * 0.5f);
                                    if (mixed > 32767) mixed = 32767;
                                    else if (mixed < -32768) mixed = -32768;
                                    pcm[i] = (short) mixed;
                                }
                                while (active.get() && !audioQueue.offer(pcm, 40, TimeUnit.MILLISECONDS)) {
                                    // Queue full, wait
                                }
                            } else {
                                if (!active.get()) break;
                                if (!JukeboxConfig.isLoop() && lavaPlayer.getPlayingTrack() == null) {
                                    break;
                                }
                                Thread.sleep(10);
                            }

                            // Periodically ensure all newly connected players are added to static channel targets
                            targetUpdateCounter++;
                            if (targetUpdateCounter >= 100) { // every ~2 seconds
                                targetUpdateCounter = 0;
                                if (channel instanceof StaticAudioChannel staticChannel) {
                                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                                        for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
                                            VoicechatConnection conn = api.getConnectionOf(sp.getUUID());
                                            if (conn != null) {
                                                staticChannel.addTarget(conn);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (InterruptedException ignored) {
                    } catch (Exception e) {
                        System.err.println("[CustomJukebox] Feeder error: " + e.getMessage());
                    } finally {
                        if (!active.get()) {
                            audioQueue.clear();
                        }
                    }
                }, "Jukebox-Feeder-" + pos.toShortString());
                feederThread.setDaemon(true);
                feederThread.start();

                // Pre-buffer: wait until queue has at least 25 frames (~500ms) or up to 1500ms
                long waitStart = System.currentTimeMillis();
                while (active.get() && audioQueue.size() < 25 && (System.currentTimeMillis() - waitStart < 1500)) {
                    Thread.sleep(20);
                }

                Supplier<short[]> supplier = new Supplier<>() {
                    @Override
                    public short[] get() {
                        if (!active.get()) {
                            return null;
                        }
                        try {
                            short[] pcm = audioQueue.poll(10, TimeUnit.MILLISECONDS);
                            if (pcm != null) {
                                return pcm;
                            }
                            if (!active.get()) {
                                return null;
                            }
                            if (!feederThread.isAlive() && audioQueue.isEmpty()) {
                                return null;
                            }
                            // Transient underrun recovery frame
                            return new short[960];
                        } catch (Exception e) {
                            return null;
                        }
                    }
                };

                de.maxhenkel.voicechat.api.audiochannel.AudioPlayer voicePlayer = 
                    api.createAudioPlayer(channel, encoder, supplier);

                JukeboxPlayback playback = new JukeboxPlayback(channel, voicePlayer, lavaPlayer, displayTitle, active, feederThread, audioQueue);
                activePlaybacks.put(pos, playback);

                voicePlayer.setOnStopped(() -> {
                    activePlaybacks.remove(pos);
                    playback.stop();
                });

                voicePlayer.startPlaying();

                String modeLabel = "3d".equalsIgnoreCase(JukeboxConfig.getMode()) ? "3D Positional" : "Studio Master";
                Component msg = Component.literal("§6Now playing: §e" + displayTitle + " §7(" + modeLabel + ")");
                if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                    for (ServerPlayer sp : sl.players()) {
                        if (sp.blockPosition().closerThan(pos, range)) {
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
