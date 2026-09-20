package com.customjukebox;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

public class CustomJukeboxPlugin implements VoicechatPlugin {
    private static VoicechatServerApi serverApi;

    @Override
    public String getPluginId() {
        return "customjukebox";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        serverApi = event.getVoicechat();
        System.out.println("[CustomJukebox] Simple Voice Chat server API connected!");
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        serverApi = null;
        JukeboxMusicManager.stopAll();
    }

    public static VoicechatServerApi getApi() {
        return serverApi;
    }
}
