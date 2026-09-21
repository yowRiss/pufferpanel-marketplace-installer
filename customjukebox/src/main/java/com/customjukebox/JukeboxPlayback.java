package com.customjukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class JukeboxPlayback {
    private final AudioChannel channel;
    private final de.maxhenkel.voicechat.api.audiochannel.AudioPlayer voicePlayer;
    private final AudioPlayer lavaPlayer;
    private final String trackTitle;
    private final AtomicBoolean active;
    private final Thread feederThread;
    private final BlockingQueue<short[]> audioQueue;

    public JukeboxPlayback(AudioChannel channel, de.maxhenkel.voicechat.api.audiochannel.AudioPlayer voicePlayer, AudioPlayer lavaPlayer, String trackTitle, AtomicBoolean active, Thread feederThread, BlockingQueue<short[]> audioQueue) {
        this.channel = channel;
        this.voicePlayer = voicePlayer;
        this.lavaPlayer = lavaPlayer;
        this.trackTitle = trackTitle;
        this.active = active;
        this.feederThread = feederThread;
        this.audioQueue = audioQueue;
    }

    public AudioChannel getChannel() {
        return channel;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public void setVolume(int volume) {
        if (lavaPlayer != null) {
            lavaPlayer.setVolume(volume);
        }
    }

    public void stop() {
        if (active.compareAndSet(true, false)) {
            try {
                if (feederThread != null) {
                    feederThread.interrupt();
                }
            } catch (Exception ignored) {}
            if (audioQueue != null) {
                audioQueue.clear();
            }
            try {
                if (voicePlayer != null) {
                    voicePlayer.stopPlaying();
                }
            } catch (Exception ignored) {}
            try {
                if (lavaPlayer != null) {
                    lavaPlayer.stopTrack();
                    lavaPlayer.destroy();
                }
            } catch (Exception ignored) {}
            try {
                if (channel != null) {
                    channel.flush();
                }
            } catch (Exception ignored) {}
        }
    }

    public boolean isStopped() {
        return !active.get();
    }
}
