package com.mikasrevs.newphone.marketplace.client;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.client.zphone.gui.ZPhoneGalleryScreen;
import com.mikasrevs.newphone.marketplace.data.*;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

public final class MarketplaceCreateListingScreen extends MarketplaceBaseScreen {
    private final MarketplaceListing original;
    private final Field title,price,description,location;
    private final List<String> photos=new ArrayList<>();
    private String category="ITEMS",currency="",pendingPhoto="",requestId="";
    private MarketplaceRequest submitted;
    private boolean busy;
    private long sentAt,photoSentAt;
    private int scroll;
    public MarketplaceCreateListingScreen(MarketplaceListing listing){
        super(listing==null?"Post a listing":"Edit listing");original=listing;
        title=field(tr("İlan başlığı","Listing title"),listing==null?"":listing.title,64);
        price=field("0",listing==null?"0":Long.toString(listing.price),13);
        description=field(tr("Ne sattığını anlat…","Describe what you are selling…"),listing==null?"":listing.description,1800);
        location=field(tr("Konum (isteğe bağlı)","Location (optional)"),listing==null?"":listing.location,64);
        fieldViewportTop=103;fieldViewportBottom=515;
        if(listing!=null){category=listing.category;currency=listing.currencyId;photos.addAll(listing.images);}
    }
    private boolean locked(){return busy||submitted!=null;}
    @Override public void tick(){
        super.tick();
        if(!pendingPhoto.isEmpty()){
            var result=MarketplaceClient.result(pendingPhoto);
            if(result!=null){
                if(result.code().equals("PHOTO_READY")&&photos.size()<4)photos.add(pendingPhoto);
                else notice(MarketplaceClient.message(result.code()));
                pendingPhoto="";
            }else if(System.currentTimeMillis()-photoSentAt>15000){
                pendingPhoto="";notice(tr("Fotoğraf yüklenemedi. Tekrar seçebilirsin.","Photo upload timed out. Select it again."));
            }
        }
        if(!requestId.isEmpty()){
            var result=MarketplaceClient.result(requestId);
            if(result!=null){
                busy=false;submitted=null;requestId="";
                if(result.code().equals("CREATED")||result.code().equals("UPDATED"))
                    PhoneNavigationState.replaceCurrent(minecraft,new MarketplaceListingDetailsScreen(result.listingId()));
                else notice(MarketplaceClient.message(result.code()));
            }else if(busy&&System.currentTimeMillis()-sentAt>15000){
                busy=false;notice(tr("Yanıt gecikti. Aynı ilan isteğini kontrol et.","No reply yet. Check the same listing request."));
            }
        }
    }
    private void choosePhoto(){
        if(locked()||photos.size()>=4||!pendingPhoto.isEmpty())return;
        open(new ZPhoneGalleryScreen(path->{
            try{pendingPhoto=MarketplaceClient.upload(path);photoSentAt=System.currentTimeMillis();}
            catch(RuntimeException failure){notice(failure.getMessage());}
            PhoneNavigationState.returnTo(minecraft,this);
        }));
    }
    private void submit(){
        if(busy||!pendingPhoto.isEmpty())return;
        try{
            if(submitted==null){
                MarketplaceRequest request=MarketplaceClient.command(original==null?"CREATE":"EDIT",original==null?"":original.id);
                request.title=title.get();request.price=price.get();request.description=description.get();request.location=location.get();
                request.category=category;request.currencyId=currency;request.images=new ArrayList<>(photos);
                if(category.equals("ITEMS")){request.currencyId="newphone:trade";request.price="0";}
                request.expectedRevision=original==null?0:original.revision;request.validateDraft();submitted=request;
            }
            requestId=MarketplaceClient.send(submitted);busy=true;sentAt=System.currentTimeMillis();focused=null;
        }catch(IllegalArgumentException failure){submitted=null;notice(MarketplaceClient.message(failure.getMessage()));}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        begin(g,original==null?tr("İlan ver","Post a listing"):tr("İlanı düzenle","Edit listing"),
                tr("Sunucundaki alıcılarla buluş","Find a buyer on your server"),true);
        if(currency.isEmpty()&&!MarketplaceClient.balances().isEmpty())currency=MarketplaceClient.balances().get(0).id();
        scroll=Math.max(0,Math.min(scroll,244));
        int top=107-scroll,firstHit=hits.size();
        g.flush();g.enableScissor(x(12),y(103),x(288),y(515));
        panel(g,18,top,264,112);
        text(g,tr("İLAN BİLGİLERİ","LISTING INFORMATION"),30,top+12,7,240,YELLOW);
        title.draw(g,29,top+32,242,31);
        String[] categories={"CARS","HOUSES","ITEMS","OTHERS"};
        for(int i=0;i<4;i++){String cat=categories[i];boolean selected=cat.equals(category);
            button(g,category(cat),29+i*61,top+76,57,25,selected?YELLOW:PALE,selected?DARK:INK,locked()?null:()->{category=cat;focused=null;});
        }
        panel(g,18,top+126,264,108);
        if(category.equals("ITEMS")){
            price.visible=false;
            text(g,tr("↔ EŞYA TAKASI","↔ ITEM TRADING"),30,top+138,9,240,YELLOW);
            wrap(g,tr("İstediğin eşyaları açıklamada belirt. Oyuncular teklif gönderir; takas iki taraf onaylayınca tamamlanır.","Describe the items you want. Players send offers; the trade completes when both approve."),30,top+166,240,8,4,INK);
            text(g,tr("Banka hesabı gerekmez.","No Bank account required."),30,top+218,6,240,MUTED);
        }else{
        text(g,tr("FİYAT VE PARA BİRİMİ","PRICE & CURRENCY"),30,top+138,7,240,YELLOW);
        price.draw(g,29,top+158,104,31);
        var balances=MarketplaceClient.balances();var selected=balances.stream().filter(b->b.id().equals(currency)).findFirst().orElse(null);
        button(g,selected==null?tr("Bankayı aç","Open Bank"):selected.name(),141,top+158,130,31,PALE,INK,locked()?null:()->{
            if(balances.isEmpty()){bank();return;}
            int index=0;for(int i=0;i<balances.size();i++)if(balances.get(i).id().equals(currency))index=i+1;
            currency=balances.get(index%balances.size()).id();
        });
        String preview=tr("Tam sayı gir","Enter a whole amount");
        try{preview=MarketplaceClient.format(currency,MarketplaceRequest.amount(price.get(),false));}catch(IllegalArgumentException ignored){}
        text(g,preview,30,top+200,9,240,YELLOW);
        text(g,tr("Fiyat en küçük para birimiyle girilir.","Price is in the smallest currency unit."),30,top+218,6,240,MUTED);
        }
        panel(g,18,top+248,264,175);
        text(g,tr("AÇIKLAMA VE KONUM","DESCRIPTION & LOCATION"),30,top+260,7,240,YELLOW);
        description.draw(g,29,top+280,242,87);
        location.draw(g,29,top+380,242,30);
        panel(g,18,top+437,264,145);
        text(g,tr("FOTOĞRAFLAR","PHOTOS")+"  ·  "+photos.size()+" / 4",30,top+449,7,240,YELLOW);
        for(int i=0;i<photos.size();i++){
            final int index=i;photo(g,photos.get(i),29+i*61,top+470,56,51);
            if(!locked())hits.add(new Hit(29+i*61,top+470,85+i*61,top+521,()->photos.remove(index)));
        }
        if(photos.size()<4)
            button(g,pendingPhoto.isEmpty()?"+":tr("…","…"),29+photos.size()*61,top+470,56,51,PALE,YELLOW,pendingPhoto.isEmpty()&&!locked()?this::choosePhoto:null);
        text(g,tr("Galeriden seç · kaldırmak için dokun","Choose from Gallery · tap to remove"),30,top+534,6,240,MUTED);
        text(g,category.equals("ITEMS")?tr("↔ Eşya takasına açık","↔ Item trades available"):tr("Satıcıyla mesajlaşarak anlaş","Contact the seller to arrange a sale"),30,top+550,8,240,YELLOW);
        wrap(g,tr("Eşya ilanlarında oyuncular takas isteği gönderebilir.","Players can send trade requests on item listings."),20,top+597,260,8,3,MUTED);
        // Only visible form controls can receive clicks; fixed actions are added afterwards.
        for(int i=hits.size()-1;i>=firstHit;i--){
            Hit hit=hits.get(i);int upper=Math.max(103,hit.t()),lower=Math.min(515,hit.b());
            if(lower<=upper)hits.remove(i);else hits.set(i,new Hit(hit.l(),upper,hit.r(),lower,hit.action()));
        }
        g.flush();g.disableScissor();
        g.fill(x(3),y(521),x(297),y(591),DARK);
        text(g,tr("Hazır olduğunda ilanını paylaş.","Publish when your listing is ready."),20,532,7,259,MUTED);
        button(g,busy?tr("Kaydediliyor…","Saving…"):submitted!=null?tr("İsteği kontrol et","Check request"):
                original==null?tr("İlanı yayınla","Publish listing"):tr("Değişiklikleri kaydet","Save changes"),
                18,551,264,36,YELLOW,DARK,busy||!pendingPhoto.isEmpty()?null:this::submit);
        finish(g,"POST",false);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(locked()&&inside(mx,my,12,103,288,515))return true;
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean charTyped(char character,int modifiers){return locked()||super.charTyped(character,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(locked()&&key!=org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)return true;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(inside(mx,my,12,103,288,515)){scroll=Math.max(0,Math.min(244,scroll+(int)(-delta*30)));return true;}
        return super.mouseScrolled(mx,my,delta);
    }
}
