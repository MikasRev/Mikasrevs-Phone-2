package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.*;
import com.mikasrevs.newphone.client.gui.*;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneStateData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Creation and group info share the same restrained typography and inset member list. */
public final class PhoneGroupDetailsScreen extends AbstractZPhoneScreen {
    private final String id;
    private final boolean create;
    private final StringBuilder name=new StringBuilder();
    private final PhoneChatComposer editor=new PhoneChatComposer(name,40);
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private boolean focused,adding;
    private String confirm="",error="";
    private int offset;
    private long submittedAt;
    public PhoneGroupDetailsScreen(){this("",true);}
    public PhoneGroupDetailsScreen(String id){this(id,false);}
    private PhoneGroupDetailsScreen(String id,boolean create){
        super(Component.literal(PhoneSettingsStore.text("Grup bilgisi", "Group info")));this.create=create;this.id=create?UUID.randomUUID().toString():id;
        var group=PhoneGroupClient.group(id);if(group!=null)name.append(group.name);
    }
    private boolean owner(){var g=PhoneGroupClient.group(id);return create||(g!=null&&g.owner.equals(PhoneClientData.selfUuid()));}
    private List<PhoneStateData.PlayerEntry> rows(){
        var group=PhoneGroupClient.group(id);
        if(create||adding){var list=new ArrayList<>(PhoneClientData.sortedContacts());if(group!=null)list.removeIf(p->group.members.stream().anyMatch(m->m.uuid.equals(p.uuid)));return list;}
        return group==null?List.of():group.members;
    }
    @Override public void tick(){
        super.tick();
        if(create&&submittedAt>0&&PhoneGroupClient.group(id)!=null)PhoneNavigationState.replaceCurrent(minecraft,new PhoneGroupThreadScreen(id));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(22),PhoneGroupVisuals.BG);drawStatusBar(g);
        var group=PhoneGroupClient.group(id);boolean manager=owner();
        PhoneGroupVisuals.text(this,g,"‹",18,58,23,24,PhoneGroupVisuals.BLUE);
        PhoneGroupVisuals.center(this,g,create?PhoneSettingsStore.text("Yeni grup","New group"):PhoneSettingsStore.text("Grup bilgisi","Group info"),66,12,PhoneGroupVisuals.TEXT);
        if(manager&&!adding)PhoneGroupVisuals.text(this,g,create?PhoneSettingsStore.text("Oluştur","Create"):PhoneSettingsStore.text("Kaydet","Save"),239,69,8,48,PhoneGroupVisuals.BLUE);
        if(!create&&group==null){PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Artık bu grupta değilsin","You are no longer a member"),268,10,PhoneGroupVisuals.MUTED);drawHomeIndicator(g);drawFrame(g);return;}
        PhoneGroupVisuals.avatar(this,g,group,150,148,76);
        PhoneGroupVisuals.center(this,g,create?PhoneSettingsStore.text("Bir isim, birlikte bir sohbet.","A name. A shared conversation."):manager?PhoneSettingsStore.text("Fotoğrafı değiştir","Change photo"):group.members.size()+PhoneSettingsStore.text(" kişi"," members"),195,8,manager&&!create?PhoneGroupVisuals.BLUE:PhoneGroupVisuals.MUTED);
        editor.layout(this,22,268,256,size(11));
        if(manager)editor.render(g,focused,PhoneSettingsStore.text("Grup adı","Group name"),PhoneGroupVisuals.CARD);
        else PhoneGroupVisuals.center(this,g,group.name,237,14,PhoneGroupVisuals.TEXT);
        String title=create?PhoneSettingsStore.text("KİŞİLER","CONTACTS"):adding?PhoneSettingsStore.text("KİŞİ EKLE","ADD MEMBER"):PhoneSettingsStore.text("ÜYELER","MEMBERS");
        PhoneGroupVisuals.text(this,g,title,25,291,7,160,PhoneGroupVisuals.MUTED);
        if(create)PhoneGroupVisuals.text(this,g,selected.size()+PhoneSettingsStore.text(" seçildi"," selected"),211,291,7,65,PhoneGroupVisuals.BLUE);
        else if(manager)PhoneGroupVisuals.text(this,g,adding?PhoneSettingsStore.text("Bitti","Done"):PhoneSettingsStore.text("+ Ekle","+ Add"),232,289,9,48,PhoneGroupVisuals.BLUE);
        var list=rows();offset=Math.max(0,Math.min(offset,Math.max(0,list.size()-4)));
        drawRoundedRect(g,x(18),y(313),x(282),y(517),size(17),PhoneGroupVisuals.CARD);
        if(list.isEmpty()){
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Eklenecek kişi yok","No contacts to add"),380,10,PhoneGroupVisuals.MUTED);
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Kişilerini Mesajlar > İstekler'den ekle.","Add contacts in Messages > Requests."),403,7,PhoneGroupVisuals.MUTED);
        }
        for(int i=0;i<Math.min(4,list.size()-offset);i++){
            var person=list.get(offset+i);int t=317+i*50;
            PhonePlayerHeadRenderer.draw(g,person.uuid,x(29),y(t+10),size(29));
            PhoneGroupVisuals.text(this,g,person.name,70,t+12,10,160,PhoneGroupVisuals.TEXT);
            String subtitle=person.uuid.equals(PhoneClientData.selfUuid())?PhoneSettingsStore.text("Sen","You"):group!=null&&person.uuid.equals(group.owner)?PhoneSettingsStore.text("Yönetici","Admin"):"";
            if(person.uuid.equals(group==null?"":group.owner)&&person.uuid.equals(PhoneClientData.selfUuid()))subtitle=PhoneSettingsStore.text("Sen · Yönetici","You · Admin");
            PhoneGroupVisuals.text(this,g,subtitle,70,t+29,7,154,PhoneGroupVisuals.MUTED);
            if(create){fillCircle(g,x(260),y(t+25),size(10),selected.contains(person.uuid)?PhoneGroupVisuals.BLUE:0xFF3A3A42);if(selected.contains(person.uuid))PhoneGroupVisuals.text(this,g,"✓",254,t+20,9,15,PhoneGroupVisuals.TEXT);}
            else if(adding)PhoneGroupVisuals.text(this,g,"+",253,t+15,17,23,PhoneGroupVisuals.BLUE);
            else if(manager&&!person.uuid.equals(PhoneClientData.selfUuid()))PhoneGroupVisuals.text(this,g,"−",253,t+15,17,23,0xFFFF6961);
            if(i<3)g.fill(x(70),y(t+49),x(270),y(t+50),0xFF303037);
        }
        if(create)PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Sadece seçtiğin kişiler eklenir.","Only selected contacts will be added."),538,8,PhoneGroupVisuals.MUTED);
        else PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Gruptan ayrıl","Leave group"),545,11,0xFFFF6961);
        String notice=error.isBlank()?PhoneClientData.notice():error;
        if(submittedAt>0&&System.currentTimeMillis()-submittedAt<1500)notice=PhoneSettingsStore.text("Oluşturuluyor…","Creating…");
        PhoneGroupVisuals.center(this,g,notice,575,7,0xFFFFA39D);
        if(!confirm.isBlank()){
            beginOverlayLayer(g);
            drawRoundedRect(g,x(18),y(355),x(282),y(566),size(23),0xFF2C2C33);finishOverlayBackground(g);
            PhoneGroupVisuals.center(this,g,confirm.equals("LEAVE")?PhoneSettingsStore.text("Gruptan ayrılsın mı?","Leave this group?"):PhoneSettingsStore.text("Üye çıkarılsın mı?","Remove this member?"),380,13,PhoneGroupVisuals.TEXT);
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Grup geçmişine erişim sona erer.","Access to group history will end."),410,8,PhoneGroupVisuals.MUTED);
            if(confirm.equals("LEAVE")&&manager)PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Yönetim sıradaki üyeye geçer.","The next member becomes admin."),431,8,PhoneGroupVisuals.MUTED);
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Evet, devam et","Yes, continue"),471,12,0xFFFF6961);
            PhoneGroupVisuals.center(this,g,PhoneSettingsStore.text("Vazgeç","Cancel"),529,12,PhoneGroupVisuals.BLUE);
        }
        drawHomeIndicator(g);drawFrame(g);
    }
    private void save(){
        error="";
        if(name.toString().isBlank()||name.chars().anyMatch(Character::isISOControl)){error=PhoneSettingsStore.text("Geçerli bir grup adı yaz.","Enter a valid group name.");return;}
        if(create){
            if(selected.isEmpty()){error=PhoneSettingsStore.text("En az bir kişi seç.","Select at least one contact.");return;}
            if(System.currentTimeMillis()-submittedAt<1500)return;
            PhoneGroupClient.action("GROUP_CREATE",id,name.toString().trim(),"",selected);submittedAt=System.currentTimeMillis();
        }else PhoneGroupClient.action("GROUP_RENAME",id,name.toString().trim(),"",List.of());
        focused=false;
    }
    @Override public boolean mouseClicked(double mx,double my,int b){
        if(b!=0)return super.mouseClicked(mx,my,b);
        if(!confirm.isBlank()){
            if(inside(mx,my,18,454,282,504)){
                if(confirm.equals("LEAVE")){PhoneGroupClient.action("GROUP_LEAVE",id,"","",List.of());PhoneNavigationState.replaceCurrent(minecraft,new ZPhoneMessagesScreen());}
                else PhoneGroupClient.action("GROUP_REMOVE",id,"",confirm,List.of());
            }
            confirm="";return true;
        }
        if(inside(mx,my,8,48,45,102)){if(adding){adding=false;offset=0;}else PhoneNavigationState.backOrHome(minecraft);return true;}
        if(!create&&PhoneGroupClient.group(id)==null)return super.mouseClicked(mx,my,b);
        if(owner()&&!adding&&inside(mx,my,233,51,292,97)){save();return true;}
        if(owner()&&!create&&inside(mx,my,108,107,192,211)){PhoneNavigationState.open(minecraft,this,new PhonePhotoPickerScreen(id,"AVATAR"));return true;}
        if(owner()&&editor.contains(mx,my)){focused=true;editor.click(mx,my,hasShiftDown());return true;}
        focused=false;
        if(!create&&owner()&&inside(mx,my,222,279,283,310)){adding=!adding;offset=0;return true;}
        var list=rows();
        for(int i=0;i<Math.min(4,list.size()-offset);i++)if(inside(mx,my,18,317+i*50,282,367+i*50)){
            var person=list.get(offset+i);
            if(create){if(!selected.remove(person.uuid))selected.add(person.uuid);}
            else if(adding){PhoneGroupClient.action("GROUP_ADD",id,"",person.uuid,List.of());}
            else if(owner()&&!person.uuid.equals(PhoneClientData.selfUuid())&&mx>=x(238))confirm=person.uuid;
            return true;
        }
        if(!create&&inside(mx,my,18,527,282,563)){confirm="LEAVE";return true;}
        return super.mouseClicked(mx,my,b);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(!confirm.isBlank())return true;
        if(editor.scroll(mx,my,delta))return true;
        if(inside(mx,my,18,313,282,517)){offset=Math.max(0,Math.min(Math.max(0,rows().size()-4),offset+(delta>0?-1:1)));return true;}
        return super.mouseScrolled(mx,my,delta);
    }
    @Override public boolean charTyped(char c,int mods){if(focused&&owner()&&confirm.isBlank()&&c>=32){editor.type(c);return true;}return super.charTyped(c,mods);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(!confirm.isBlank())confirm="";else if(adding){adding=false;offset=0;}else PhoneNavigationState.backOrHome(minecraft);return true;}
        if(focused&&owner()&&confirm.isBlank()){if(key==GLFW.GLFW_KEY_ENTER){save();return true;}if(editor.key(key,mods))return true;}
        return super.keyPressed(key,scan,mods);
    }
}
