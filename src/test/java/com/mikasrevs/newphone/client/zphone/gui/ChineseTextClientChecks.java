package com.mikasrevs.newphone.client.zphone.gui;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import com.mikasrevs.newphone.client.gui.PhoneModernText;
import com.mikasrevs.newphone.client.gui.PhoneTextField;
import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;

/** Tests native scalable CJK providers, readable labels and lossless Chinese message wrapping. */
public final class ChineseTextClientChecks {
    public static void run() throws Exception {
        Font font=Minecraft.getInstance().font;
        var lookup=Font.class.getDeclaredMethod("getFontSet",ResourceLocation.class);lookup.setAccessible(true);
        var characters=new HashSet<Integer>();
        for(String code:new String[]{"zh_cn","zh_tw"}) {
            try(var input=ChineseTextClientChecks.class.getResourceAsStream("/assets/newphone/phone_translations/"+code+".json")) {
                var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e->e.getValue().getAsString().codePoints()
                        .filter(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN).forEach(characters::add));
            }
        }
        for(String region:new String[]{"sc","tc"}) {
            FontSet phone=(FontSet)lookup.invoke(font,new ResourceLocation("newphone","phone_cjk_"+region));
            for(int c:characters) {
                var glyph=phone.getGlyphInfo(c,false);
                if(glyph==SpecialGlyphs.MISSING)throw new AssertionError("Missing Chinese phone glyph: U+"+Integer.toHexString(c));
                if(!glyph.getClass().getName().startsWith("com.mojang.blaze3d.font.TrueTypeGlyphProvider"))
                    throw new AssertionError("Phone font fell back to bitmap: "+region+" U+"+Integer.toHexString(c));
            }
        }
        String previous=PhoneSettingsStore.language();
        for(String code:new String[]{"zh_cn","zh_tw"}) {
            PhoneSettingsStore.setLanguage(code);
            ResourceLocation expected=new ResourceLocation("newphone",code.equals("zh_cn")?"phone_cjk_sc":"phone_cjk_tc");
            if(!PhoneModernText.component("设置與聯絡人").getStyle().getFont().equals(expected))throw new AssertionError("Wrong regional phone font");
        }
        PhoneSettingsStore.setLanguage(previous);
        String text="你好，我们可以先把物品放入交易栏，双方确认之后再完成交易。這段訊息也包含繁體中文，沒有空格也應該正常換行。";
        var lines=ZPhoneSocialRenderUtil.wrap(text,27,162,8);
        if(lines.size()<2||!String.join("",lines).equals(text))throw new AssertionError("Chinese message wrapping lost text");
        for(String line:lines)if(PhoneModernText.width(line,8)>162)throw new AssertionError("Chinese message overflow");
        for(int width:new int[]{40,80,140,220}) {
            var wrapped=ZPhoneSocialRenderUtil.wrap(text,27,width,8);
            if(!String.join("",wrapped).equals(text))throw new AssertionError("Readable message lost characters");
            for(String line:wrapped)if(PhoneModernText.width(line,8)>width)throw new AssertionError("Message exceeds readable width");
        }
        for(int max:new int[]{8,20,65,140,300}) {
            String fitted=PhoneModernText.fitChinese("在手機上顯示一段比較長的訊息😀",6,max);
            if(!fitted.isEmpty()&&PhoneModernText.width(fitted,6)>max)throw new AssertionError("Readable Chinese label overflow");
            if(fitted.chars().anyMatch(c->Character.isSurrogate((char)c))) {
                if(fitted.codePoints().anyMatch(c->c>=0xD800&&c<=0xDFFF))throw new AssertionError("Chinese fit split an emoji");
            }
        }
        int height=PhoneModernText.fittedHeight("设置",3,100);
        if(height*Minecraft.getInstance().getWindow().getGuiScale()<12)throw new AssertionError("Chinese text became too small");
        var uiCharacters=new HashSet<Integer>();
        for(String code:new String[]{"tr_tr","en_us","es_es","pt_br","de_de","fr_fr","ru_ru","pl_pl","it_it"}) {
            try(var input=ChineseTextClientChecks.class.getResourceAsStream("/assets/newphone/lang/"+code+".json")) {
                JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(
                        e->e.getValue().getAsString().codePoints().filter(c->Character.isAlphabetic(c)||Character.isDigit(c)).forEach(uiCharacters::add));
            }
        }
        "İı Şş Ğğ Çç Öö Üü Éé Ññ Łł Ąą Ęę ß Русский Ёё".codePoints().filter(Character::isAlphabetic).forEach(uiCharacters::add);
        for(String weight:new String[]{"semibold","bold"}) {
            FontSet ui=(FontSet)lookup.invoke(font,new ResourceLocation("newphone","phone_ui_"+weight));
            for(int c:uiCharacters)if(!ui.getGlyphInfo(c,false).getClass().getName().startsWith("com.mojang.blaze3d.font.TrueTypeGlyphProvider"))
                throw new AssertionError("UI font missing character: "+weight+" U+"+Integer.toHexString(c));
        }
        if(!PhoneModernText.component("Settings",8).getStyle().getFont().equals(new ResourceLocation("newphone","phone_ui_semibold")))throw new AssertionError("Body font mismatch");
        if(!PhoneModernText.component("Settings",24).getStyle().getFont().equals(new ResourceLocation("newphone","phone_ui_bold")))throw new AssertionError("Heading font mismatch");
        for(String body:new String[]{"İstanbul Şişli • Türkçe karakterler: İı Ğğ Şş Çç Öö Üü 😀","Привет! Это длинное сообщение без потери символов.","WWWWWWWWWWWWWWWWW","Café España Português Łódź"}) {
            for(int max:new int[]{40,80,140,220}) {
                var wrapped=PhoneModernText.wrap(body,8,max);
                if(!String.join("",wrapped).equals(body))throw new AssertionError("UI message lost characters");
                for(String line:wrapped)if(PhoneModernText.width(line,8)>max)throw new AssertionError("UI message overflow");
                String fitted=PhoneModernText.fit(body,8,max);
                if(!fitted.isEmpty()&&PhoneModernText.width(fitted,8)>max)throw new AssertionError("UI label overflow");
            }
        }
        int lifted=PhoneModernText.readableColor(0x80778493);
        if((lifted>>>24)!=0x80||(lifted&255)<=0x93)throw new AssertionError("Muted text contrast or fade alpha changed incorrectly");
        for(int color:new int[]{0xFF0A84FF,0xFFFF4E67,0xFF111111,0xFFF5F5F7})if(PhoneModernText.readableColor(color)!=color)throw new AssertionError("Accent or light-surface ink changed");
        PhoneTextField field=new PhoneTextField(178,18,64);
        field.setValue("İstanbul Şişli 😀");
        field.mouseClicked(PhoneModernText.width("İstanbul ",9),0,0);
        field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE,0,0);
        if(!field.getValue().equals("İstanbul işli 😀"))throw new AssertionError("Phone field clicked incorrect glyph");
        field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END,0,0);
        field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0);
        if(!field.getValue().equals("İstanbul işli "))throw new AssertionError("Phone field split emoji on deletion");
        field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
        if(!field.getValue().equals("İstanbul "))throw new AssertionError("Phone field word deletion failed");
        field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,0);
        field.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_END,0,org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT);
        field.charTyped('Ş',0);
        if(!field.getValue().equals("Ş"))throw new AssertionError("Phone field selection replacement failed");
        System.out.println("Phone typography native checks passed: "+uiCharacters.size()+" Latin/Cyrillic glyphs in both weights, glyph-aware wrapping, fitted labels, contrast, search input.");
        System.out.println("Chinese native font checks passed: "+characters.size()+" glyphs in both scalable fonts, readable fitting and unspaced message wrapping.");
    }
}
