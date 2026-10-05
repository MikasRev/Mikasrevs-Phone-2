package com.mikasrevs.newphone.marketplace.client;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import com.mikasrevs.newphone.marketplace.data.MarketplaceRequest;
import net.minecraft.client.gui.GuiGraphics;

public final class MarketplaceFilterScreen extends MarketplaceBaseScreen {
    private final MarketplaceScreen parent;
    private final MarketplaceRequest filter;
    private final Field minimum,maximum;
    public MarketplaceFilterScreen(MarketplaceScreen parent,MarketplaceRequest current){
        super("Filters");this.parent=parent;
        filter=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(current),MarketplaceRequest.class);
        minimum=field(tr("En az","Minimum"),filter.minPrice,13);maximum=field(tr("En çok","Maximum"),filter.maxPrice,13);
        if(filter.category.equals("ITEMS"))clearPrices();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        begin(g,tr("Filtrele","Filters"),tr("Aradığını daha kolay bul","Find exactly what you need"),true);
        panel(g,18,108,264,97);text(g,tr("KATEGORİ","CATEGORY"),30,121,7,240,MUTED);
        String[] categories={"","CARS","HOUSES","ITEMS","OTHERS"};
        for(int i=0;i<categories.length;i++){
            String cat=categories[i];boolean selected=filter.category.equals(cat);
            button(g,category(cat),28+i%3*84,141+i/3*30,77,24,selected?YELLOW:PALE,selected?DARK:INK,()->{filter.category=cat;focused=null;if(cat.equals("ITEMS"))clearPrices();});
        }
        panel(g,18,220,264,119);
        if(filter.category.equals("ITEMS")){
            minimum.visible=false;maximum.visible=false;
            text(g,tr("↔ TAKAS İLANLARI","↔ TRADE LISTINGS"),30,233,9,240,YELLOW);
            wrap(g,tr("Eşya ilanlarında fiyat yerine karşılıklı takas teklifleri kullanılır.","Item listings use mutual trade offers."),30,264,240,9,4,INK);
        }else{
        text(g,tr("FİYAT ARALIĞI","PRICE RANGE"),30,233,7,240,MUTED);
        minimum.draw(g,29,254,116,29);maximum.draw(g,155,254,116,29);
        var balances=MarketplaceClient.balances();
        var selected=balances.stream().filter(b->b.id().equals(filter.currencyId)).findFirst().orElse(null);
        button(g,selected==null?tr("Para birimi: Tümü","Currency: All"):selected.name(),29,294,242,30,PALE,YELLOW,()->{
            if(balances.isEmpty())return;
            int index=selected==null?-1:balances.indexOf(selected);
            filter.currencyId=index+1>=balances.size()?"":balances.get(index+1).id();
        });
        }
        panel(g,18,354,264,128);text(g,tr("SIRALAMA","SORT BY"),30,367,7,240,MUTED);
        String[] sorts={"NEWEST","OLDEST","LOWEST","HIGHEST","VIEWS"};
        String[] labels={tr("En yeni","Newest"),tr("En eski","Oldest"),tr("En ucuz","Lowest price"),tr("En pahalı","Highest price"),tr("En çok bakılan","Most viewed")};
        for(int i=0;i<sorts.length;i++){
            String sort=sorts[i];boolean active=filter.sort.equals(sort);
            button(g,labels[i],29+i%2*126,389+i/2*28,i==4?242:116,23,active?YELLOW:PALE,active?DARK:INK,
                    filter.category.equals("ITEMS")&&(sort.equals("LOWEST")||sort.equals("HIGHEST"))?null:()->filter.sort=sort);
        }
        button(g,(filter.favoritesOnly?"✓ ":"○ ")+tr("Yalnızca favoriler","Favorites only"),18,497,264,31,filter.favoritesOnly?YELLOW:PALE,filter.favoritesOnly?DARK:INK,()->filter.favoritesOnly=!filter.favoritesOnly);
        button(g,tr("Sıfırla","Reset"),18,554,84,33,PALE,INK,()->{
            minimum.clear();maximum.clear();filter.category="";filter.currencyId="";filter.sort="NEWEST";filter.favoritesOnly=false;
        });
        button(g,tr("Filtreleri uygula","Apply filters"),110,554,172,33,YELLOW,DARK,()->{
            filter.minPrice=minimum.get();filter.maxPrice=maximum.get();filter.page=0;
            try{com.mikasrevs.newphone.marketplace.MarketplaceManager.validateQuery(filter);parent.applyFilters(filter);PhoneNavigationState.returnTo(minecraft,parent);}
            catch(IllegalArgumentException failure){notice(MarketplaceClient.message(failure.getMessage()));}
        });
        finish(g,"",false);
    }
    private void clearPrices(){minimum.clear();maximum.clear();filter.minPrice="";filter.maxPrice="";filter.currencyId="";if(filter.sort.equals("LOWEST")||filter.sort.equals("HIGHEST"))filter.sort="NEWEST";}
}
