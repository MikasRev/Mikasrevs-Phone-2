package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhoneMessageClient;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.*;
import com.mikasrevs.newphone.client.voice.PhoneVoiceNotes;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneStateData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

final class PhoneChatExtras {
    private PhoneChatExtras(){}
    static int captionHeight(PhoneStateData.MessageEntry m){return m.caption==null||m.caption.isBlank()?0:ZPhoneSocialRenderUtil.wrap(m.caption,24).size()*12+5;}
    static void caption(AbstractZPhoneScreen s,GuiGraphics g,PhoneStateData.MessageEntry m,int left,int top,int width){
        var lines=ZPhoneSocialRenderUtil.wrap(m.caption,24);for(int i=0;i<lines.size();i++)PhoneModernText.drawColoredFitted(g,lines.get(i),s.x(left),s.y(top+i*12),s.size(8),s.size(width),0xFFFFFFFF);
    }
    static void draw(AbstractZPhoneScreen s,GuiGraphics g,String peer){
        PhoneModernText.drawColoredFitted(g,PhonePrivacyStore.muted(peer)?PhoneSettingsStore.text("Sesi aç","Unmute"):PhoneSettingsStore.text("Sessize al","Mute"),s.x(23),s.y(575),s.size(7),s.size(105),PhoneVoiceVisuals.SECONDARY);
        s.drawRoundedRect(g,s.x(166),s.y(565),s.x(282),s.y(592),s.size(13),0xFF232329);
        PhoneVoiceVisuals.microphone(s,g,181,578,0.65f,PhoneVoiceVisuals.BLUE);
        PhoneModernText.drawColoredFitted(g,PhoneSettingsStore.text("Sesli mesaj","Voice message"),s.x(196),s.y(575),s.size(7),s.size(77),PhoneVoiceVisuals.TEXT);
    }
    static boolean click(AbstractZPhoneScreen s,double mx,double my,int button,String peer,String channel){
        if(button!=0||my<s.y(565)||my>=s.y(592))return false;
        if(mx>=s.x(18)&&mx<s.x(139)){PhonePrivacyStore.toggleMute(peer);return true;}
        if(mx>=s.x(166)&&mx<s.x(282)){PhoneNavigationState.open(Minecraft.getInstance(),s,new PhoneVoiceNoteScreen(peer,channel));return true;}return false;
    }
    static boolean open(PhoneStateData.MessageEntry message){
        if(message==null)return false;if(PhoneMessageClient.retry(message.id))return true;
        if(message.type.endsWith("VOICE")&&message.localStatus.isBlank()){PhoneVoiceNotes.playMessage(message.body);return true;}return false;
    }
    static void voiceBubble(AbstractZPhoneScreen s,GuiGraphics g,PhoneStateData.MessageEntry m,int left,int top,int right){
        boolean mine=com.mikasrevs.newphone.client.data.PhoneClientData.isSelfMessageUuid(m.fromUuid);
        boolean playing=PhoneVoiceNotes.playing(m.body),loading=PhoneVoiceNotes.loading(m.body);
        String failure="FAILED".equals(m.localStatus)?m.localError:PhoneVoiceNotes.error(m.body);
        boolean failed=failure!=null&&!failure.isBlank();
        int background=mine?0xFF087CF0:0xFF27272D;
        s.drawRoundedRect(g,s.x(left),s.y(top),s.x(right),s.y(top+45),s.size(16),background);
        s.fillCircle(g,s.x(left+22),s.y(top+22),s.size(13),mine?0x2EFFFFFF:0xFF3A3A43);
        if(loading)PhoneModernText.drawCenteredColored(g,"…",s.x(left+22),s.y(top+15),s.size(10),0xFFFFFFFF);
        else PhoneVoiceVisuals.play(s,g,left+22,top+22,0.8f,playing,0xFFFFFFFF);
        PhoneVoiceVisuals.waveform(s,g,left+45,top+17,right-left-58,18,PhoneVoiceNotes.waveform(m.body),
                playing?PhoneVoiceNotes.playbackProgress():-1,0xFFF5F5F7,mine?0x668CCAFF:0xFF62626B);
        long duration=PhoneVoiceNotes.durationMillis(m.body);
        String label=failed?PhoneSettingsStore.text("Tekrar denemek için dokun","Tap to retry"):loading?PhoneSettingsStore.text("Yükleniyor","Loading")
                :duration>0?PhoneVoiceVisuals.time(playing?PhoneVoiceNotes.playbackMillis():duration)
                :PhoneSettingsStore.text("Sesli mesaj","Voice message");
        PhoneModernText.drawColoredFitted(g,label,s.x(left+46),s.y(top+31),s.size(6),s.size(right-left-57),mine?0xFFD2E8FF:PhoneVoiceVisuals.SECONDARY);
        if(failed){
            PhoneVoiceVisuals.pill(s,g,left+43,top+5,right-8,top+25,5,mine?0xFF096BD0:0xFF382C30);
            PhoneModernText.drawColoredFitted(g,failure,s.x(left+47),s.y(top+10),s.size(6),s.size(right-left-60),0xFFFFC1BC);
        }
    }
}
