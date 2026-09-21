package com.customjukebox;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

public class CustomJukeboxPlugin implements VoicechatPlugin {
    private static VoicechatServerApi serverApi;

    private de.maxhenkel.voicechat.api.VolumeCategory jukeboxVolumeCategory;

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
        if (serverApi != null) {
            try {
                jukeboxVolumeCategory = serverApi.volumeCategoryBuilder()
                    .setId("music")
                    .setName("Jukebox Music")
                    .setDescription("Volume of Custom Jukebox music discs")
                    .build();
                serverApi.registerVolumeCategory(jukeboxVolumeCategory);
            } catch (Exception e) {
                System.err.println("[CustomJukebox] Note: Volume category registration: " + e.getMessage());
            }
        }
        System.out.println("[CustomJukebox] Simple Voice Chat server API connected with high-fidelity AUDIO mode!");
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        if (serverApi != null && jukeboxVolumeCategory != null) {
            try {
                serverApi.unregisterVolumeCategory(jukeboxVolumeCategory.getId());
            } catch (Exception ignored) {}
            jukeboxVolumeCategory = null;
        }
        serverApi = null;
        JukeboxMusicManager.stopAll();
    }

    public static VoicechatServerApi getApi() {
        return serverApi;
    }
}
