package com.customjukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class JukeboxPlayback {
    private final LocationalAudioChannel channel;
    private final de.maxhenkel.voicechat.api.audiochannel.AudioPlayer voicePlayer;
    private final AudioPlayer lavaPlayer;
    private final String trackTitle;
    private final AtomicBoolean active;

    public JukeboxPlayback(LocationalAudioChannel channel, de.maxhenkel.voicechat.api.audiochannel.AudioPlayer voicePlayer, AudioPlayer lavaPlayer, String trackTitle, AtomicBoolean active) {
        this.channel = channel;
        this.voicePlayer = voicePlayer;
        this.lavaPlayer = lavaPlayer;
        this.trackTitle = trackTitle;
        this.active = active;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public void stop() {
        if (active.compareAndSet(true, false)) {
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
