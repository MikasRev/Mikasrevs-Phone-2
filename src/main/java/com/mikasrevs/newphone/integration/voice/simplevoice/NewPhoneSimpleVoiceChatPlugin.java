package com.mikasrevs.newphone.integration.voice.simplevoice;

import com.mikasrevs.newphone.integration.voice.PhoneVoiceManager;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

@ForgeVoicechatPlugin
public final class NewPhoneSimpleVoiceChatPlugin implements VoicechatPlugin {
    private volatile SimpleVoiceChatProvider provider;

    @Override
    public String getPluginId() {
        return "newphone";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
            SimpleVoiceChatProvider newProvider = new SimpleVoiceChatProvider(event.getVoicechat());
            this.provider = newProvider;
            PhoneVoiceManager.install(newProvider);
        });

        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            SimpleVoiceChatProvider current = this.provider;
            if (current != null) {
                current.routeMicrophone(event);
            }
        });

        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            SimpleVoiceChatProvider current = this.provider;
            if (current != null) {
                current.shutdown();
                PhoneVoiceManager.uninstall(current);
                this.provider = null;
            }
        });
    }
}
