package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.PhoneClientData;
import com.mikasrevs.newphone.client.data.PhoneMessageClient;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.voice.PhoneVoiceNotes;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A quiet recording surface with a single hold control and a compact review dock. */
public final class PhoneVoiceNoteScreen extends AbstractZPhoneScreen {
    private final String peer, channel;
    private boolean held;

    public PhoneVoiceNoteScreen(String peer, String channel) {
        super(Component.literal(PhoneSettingsStore.text("Ses kaydı", "Voice note")));
        this.peer=peer;
        this.channel=channel;
        PhoneVoiceNotes.cancel();
    }

    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);
        drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),0xFF09090B);
        drawStatusBar(g);
        boolean recording=PhoneVoiceNotes.recording(), preparing=PhoneVoiceNotes.preparing();
        boolean ready=PhoneVoiceNotes.recorded()!=null, playing=PhoneVoiceNotes.playing();

        PhoneModernText.drawColored(g,"‹",x(20),y(58),size(22),PhoneVoiceVisuals.BLUE);
        PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Ses kaydı","Voice note"),
                x(150),y(68),size(12),PhoneVoiceVisuals.TEXT);
        var group=com.mikasrevs.newphone.client.data.PhoneGroupClient.group(peer);
        if(group!=null)PhoneModernText.drawCenteredColoredFitted(g,group.name,x(150),y(106),size(8),size(234),PhoneVoiceVisuals.SECONDARY);
        var contact=PhoneClientData.contactByUuid(peer);
        if(contact!=null) PhoneModernText.drawCenteredColoredFitted(g,contact.name,
                x(150),y(106),size(8),size(234),PhoneVoiceVisuals.SECONDARY);

        String state=recording?PhoneSettingsStore.text("KAYDEDİLİYOR","RECORDING")
                :preparing?PhoneSettingsStore.text("HAZIRLANIYOR","PREPARING")
                :ready?PhoneSettingsStore.text("SES NOTU","VOICE NOTE")
                :PhoneSettingsStore.text("KAYDA HAZIR","READY TO RECORD");
        int accent=recording?PhoneVoiceVisuals.RED:PhoneVoiceVisuals.BLUE;
        PhoneModernText.drawCenteredColored(g,state,x(150),y(161),size(7),recording?accent:PhoneVoiceVisuals.SECONDARY);
        if(recording) fillCircle(g,x(150),y(143),size(3),0xFFFF9F0A);
        long duration=PhoneVoiceNotes.durationMillis();
        long shown=playing?PhoneVoiceNotes.playbackMillis():duration;
        PhoneModernText.drawCenteredLight(g,PhoneVoiceVisuals.time(shown),x(150),y(191),size(38));

        drawRoundedRect(g,x(22),y(258),x(278),y(341),size(20),0xFF17171B);
        PhoneVoiceVisuals.waveform(this,g,39,299,224,47,PhoneVoiceNotes.recordingWaveform(),
                ready&&playing?PhoneVoiceNotes.playbackProgress():-1,accent,0xFF3A3A43);
        String hint=recording?PhoneSettingsStore.text("Bitirmek için bırak","Release to finish")
                :preparing?PhoneSettingsStore.text("Ses notun hazırlanıyor","Preparing your voice note")
                :ready?PhoneSettingsStore.text("Göndermeden önce dinle","Listen before sending")
                :PhoneSettingsStore.text("Kaydetmek için basılı tut","Touch and hold to record");
        PhoneModernText.drawCenteredColoredFitted(g,hint,x(150),y(363),size(9),size(258),PhoneVoiceVisuals.SECONDARY);

        if(ready) drawReview(g,mx,my,playing);
        else {
            boolean hover=inside(mx,my,115,417,185,487)&&!preparing;
            fillCircle(g,x(150),y(452),size(35),recording?0xFF49201F:hover?0xFF39393F:0xFF29292E);
            fillCircle(g,x(150),y(452),size(31),preparing?0xFF242428:recording?PhoneVoiceVisuals.RED:0xFFF5F5F7);
            if(recording)PhoneVoiceVisuals.play(this,g,150,452,1.3f,true,0xFFFFFFFF);
            else PhoneVoiceVisuals.microphone(this,g,150,452,1.3f,preparing?0xFF68686F:0xFF17171B);
            PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text("Basılı tut","Hold to record"),x(150),y(506),size(8),PhoneVoiceVisuals.SECONDARY);
        }
        PhoneModernText.drawCenteredColoredFitted(g,PhoneVoiceNotes.error(),x(150),y(545),size(8),size(254),0xFFFF827C);
        int seconds=com.mikasrevs.newphone.network.PhoneVoiceCodec.MAX_RECORD_SECONDS;
        PhoneModernText.drawCenteredColored(g,PhoneSettingsStore.text(seconds+" saniyeye kadar","Up to "+seconds+" seconds"),x(150),y(575),size(7),0xFF64646D);
        drawHomeIndicator(g);
        drawFrame(g);
    }

    private void drawReview(GuiGraphics g,int mx,int my,boolean playing) {
        drawRoundedRect(g,x(22),y(415),x(278),y(490),size(24),0xFF17171B);
        fillCircle(g,x(54),y(452),size(22),inside(mx,my,30,428,78,476)?0xFF333339:0xFF242428);
        PhoneVoiceVisuals.trash(this,g,54,452,PhoneVoiceVisuals.SECONDARY);
        fillCircle(g,x(150),y(452),size(26),inside(mx,my,120,422,180,482)?0xFF44444C:0xFF303037);
        PhoneVoiceVisuals.play(this,g,150,452,1.4f,playing,0xFFFFFFFF);
        fillCircle(g,x(246),y(452),size(23),inside(mx,my,220,426,272,478)?0xFF379AFF:PhoneVoiceVisuals.BLUE);
        PhoneVoiceVisuals.send(this,g,246,452,0xFFFFFFFF);
        label(g,54,PhoneSettingsStore.text("Sil","Delete"));
        label(g,150,playing?PhoneSettingsStore.text("Durdur","Stop"):PhoneSettingsStore.text("Dinle","Listen"));
        label(g,246,PhoneSettingsStore.text("Gönder","Send"));
    }

    private void label(GuiGraphics g,int cx,String label) {
        PhoneModernText.drawCenteredColored(g,label,x(cx),y(506),size(8),PhoneVoiceVisuals.SECONDARY);
    }

    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(button!=0)return super.mouseClicked(mx,my,button);
        if(inside(mx,my,10,48,54,95)){PhoneNavigationState.backOrHome(minecraft);return true;}
        boolean ready=PhoneVoiceNotes.recorded()!=null;
        if(!ready&&!PhoneVoiceNotes.preparing()&&inside(mx,my,115,417,185,487)){
            held=true;PhoneVoiceNotes.begin();return true;
        }
        if(ready){
            if(inside(mx,my,30,428,78,476)){PhoneVoiceNotes.cancel();return true;}
            if(inside(mx,my,120,422,180,482)){PhoneVoiceNotes.preview();return true;}
            if(inside(mx,my,220,426,272,478)){
                String type=channel.equals("MESSAGE")?"VOICE":channel+"_VOICE";
                PhoneMessageClient.send(peer,type,"","","",PhoneVoiceNotes.recorded());
                PhoneNavigationState.backOrHome(minecraft);return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }

    @Override public boolean mouseReleased(double mx,double my,int button) {
        if(button==0&&held){held=false;PhoneVoiceNotes.finish();return true;}
        return super.mouseReleased(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE){PhoneNavigationState.backOrHome(minecraft);return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){held=false;PhoneVoiceNotes.cancel();super.removed();}
}
