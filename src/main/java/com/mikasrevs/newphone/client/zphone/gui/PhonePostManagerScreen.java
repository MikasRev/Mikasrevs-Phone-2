package com.mikasrevs.newphone.client.zphone.gui;

import com.mikasrevs.newphone.client.data.*;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.network.PhoneNetwork;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Explicit archive/restore, so the 50-post allowance never deletes a player's memories. */
public final class PhonePostManagerScreen extends AbstractZPhoneScreen {
    private boolean archived; private int scroll; private String confirmDelete="";
    public PhonePostManagerScreen(){super(Component.literal(PhoneSettingsStore.text("Gönderiler", "Posts")));PhoneSocialClientData.requestSync();}
    private record Row(String id,String platform,String body,String imageRef){}
    private List<Row> rows(){var s=PhoneSocialClientData.latestState();String self=PhoneClientData.selfUuid();List<Row> rows=new ArrayList<>();
        if(archived){for(var p:s.archivedPosts)rows.add(new Row(p.id,p.platform,p.body,p.imageRef));}
        else{for(var p:s.blockGramPosts)if(self.equals(p.authorUuid))rows.add(new Row(p.id,"BLOCKGRAM",p.caption,p.imageRef));for(var p:s.blockPostPosts)if(self.equals(p.authorUuid))rows.add(new Row(p.id,"BLOCKPOST",p.body,p.imageRef));}
        return rows;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);drawWallpaper(g);drawRoundedRect(g,x(3),y(40),x(297),y(606),size(20),0xFF10151D);drawStatusBar(g);
        PhoneModernText.drawColored(g,"‹",x(18),y(60),size(20),0xFFFFFFFF);
        PhoneModernText.drawColored(g,PhoneSettingsStore.text("Gönderilerim","My posts"),x(53),y(63),size(16),0xFFFFFFFF);
        PhoneModernText.drawColoredFitted(g,PhoneSettingsStore.text("Her uygulamada 50 aktif gönderi. Arşiv özeldir.","50 active posts per app. Archives are private."),x(20),y(96),size(7),size(260),0xFFAEBCCC);
        button(g,20,119,145,PhoneSettingsStore.text("Profil","Profile")+(archived?"":" ✓"));button(g,153,119,280,PhoneSettingsStore.text("Arşiv","Archive")+(archived?" ✓":""));
        var rows=rows();scroll=Math.max(0,Math.min(scroll,Math.max(0,rows.size()-5)));
        for(int i=scroll;i<Math.min(rows.size(),scroll+5);i++){var row=rows.get(i);int y=164+(i-scroll)*73;
            drawRoundedRect(g,x(18),y(y),x(282),y(y+65),size(9),0xFF202B39);
            var texture=row.imageRef==null||row.imageRef.isBlank()?null:PhonePhotoStore.textureFor(minecraft,row.imageRef);
            if(texture!=null)g.blit(texture.location(),x(27),y(y+4),size(23),size(23),0,0,texture.width(),texture.height(),texture.width(),texture.height());
            PhoneModernText.drawColoredFitted(g,(row.platform.equals("BLOCKGRAM")?"Lumi · ":"X · ")+row.body,x(57),y(y+9),size(8),size(213),0xFFFFFFFF);
            button(g,27,y+30,archived?165:270,archived?PhoneSettingsStore.text("Geri yükle","Restore"):PhoneSettingsStore.text("Arşivle","Archive"));
            if(archived)button(g,177,y+30,271,confirmDelete.equals(row.id)?PhoneSettingsStore.text("Onayla","Confirm"):PhoneSettingsStore.text("Sil","Delete"));
        }
        PhoneModernText.drawColoredFitted(g,PhoneClientData.notice(),x(20),y(549),size(7),size(260),0xFFFFC57E);drawHomeIndicator(g);drawFrame(g);
    }
    private void button(GuiGraphics g,int l,int t,int r,String label){drawRoundedRect(g,x(l),y(t),x(r),y(t+27),size(7),0xFF30435C);PhoneModernText.drawCenteredColoredFitted(g,label,x((l+r)/2f),y(t+8),size(8),size(r-l-6),0xFFFFFFFF);}
    @Override public boolean mouseClicked(double mx,double my,int b){if(b!=0)return super.mouseClicked(mx,my,b);
        if(inside(mx,my,10,48,46,91)){PhoneNavigationState.backOrHome(minecraft);return true;}
        if(inside(mx,my,20,119,280,146)){archived=mx>=x(150);scroll=0;confirmDelete="";return true;}
        var rows=rows();for(int i=scroll;i<Math.min(rows.size(),scroll+5);i++){var row=rows.get(i);int y=194+(i-scroll)*73;
            if(inside(mx,my,27,y,archived?165:270,y+27)){PhoneNetwork.sendAction("SOCIAL_MANAGE_POST",row.id,row.platform+"|"+(archived?"RESTORE":"ARCHIVE"));return true;}
            if(archived&&inside(mx,my,177,y,271,y+27)){if(confirmDelete.equals(row.id))PhoneNetwork.sendAction("SOCIAL_MANAGE_POST",row.id,row.platform+"|DELETE");else confirmDelete=row.id;return true;}
        }return super.mouseClicked(mx,my,b);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){scroll=Math.max(0,scroll+(delta>0?-1:1));return true;}
}
