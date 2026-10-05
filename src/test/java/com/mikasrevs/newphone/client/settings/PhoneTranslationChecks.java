package com.mikasrevs.newphone.client.settings;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Coverage and formatting checks for both translation systems, without launching Minecraft. */
public final class PhoneTranslationChecks {
    private static final String LITERAL = "\"(?:\\\\.|[^\"\\\\])*\"";
    private static final String[] NAMES = {"Spanish", "Portuguese", "German", "French", "Russian", "Polish", "Italian"};
    private static final String[] CODES = {"es", "pt_br", "de", "fr", "ru", "pl", "it"};
    private PhoneTranslationChecks() {}

    public static void run() throws Exception {
        Path java = Path.of("src/main/java/com/mikasrevs/newphone");
        var calls = Pattern.compile("(?:PhoneSettingsStore\\.text|\\btr|(?<![\\w.])text)\\(\\s*("+LITERAL+"|raw)\\s*,\\s*("+LITERAL+")");
        Map<String,String> texts = new HashMap<>();
        try(var paths=Files.walk(java)) {
            for(Path p:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                String name=p.getFileName().toString();
                if(Arrays.stream(NAMES).anyMatch(n->name.equals("Phone"+n+"Translations.java"))||p.toString().contains("tablet"))continue;
                var matcher=calls.matcher(Files.readString(p));
                while(matcher.find()) {
                    String en=decode(matcher.group(2)),tr=matcher.group(1).equals("raw")?en:decode(matcher.group(1));
                    texts.putIfAbsent(en,tr);
                }
            }
        }
        List<BiFunction<String,String,String>> translators=List.of(PhoneSpanishTranslations::text,PhonePortugueseTranslations::text,
                PhoneGermanTranslations::text,PhoneFrenchTranslations::text,PhoneRussianTranslations::text,PhonePolishTranslations::text,PhoneItalianTranslations::text);
        int checks=0;
        for(int i=0;i<NAMES.length;i++) {
            String source=Files.readString(java.resolve("client/settings/Phone"+NAMES[i]+"Translations.java")).split("static String network")[0];
            Set<String> keys=new HashSet<>();var cases=Pattern.compile("case\\s+("+LITERAL+")").matcher(source);
            while(cases.find())keys.add(decode(cases.group(1)));
            for(var entry:texts.entrySet()) {
                require(keys.contains(entry.getKey()),NAMES[i]+" missing phone text: "+entry.getKey());
                String result=translators.get(i).apply(entry.getValue(),entry.getKey());
                require(result!=null&&(entry.getKey().isBlank()||!result.isBlank()),"Empty phone translation: "+entry.getKey());
                require(placeholders(result).equals(placeholders(entry.getKey())),"Phone format placeholders differ: "+entry.getKey());
                checks++;
            }
        }
        Path lang=Path.of("src/main/resources/assets/newphone/lang");
        for(boolean traditional:new boolean[]{false,true}) {
            var catalog=PhoneChineseTranslations.catalog(traditional);
            String settingsSource=Files.readString(java.resolve("client/zphone/gui/ZPhoneSettingsScreen.java"));
            var rows=Pattern.compile("new Row\\(Icon\\.\\w+,\\s*("+LITERAL+"),\\s*("+LITERAL+"),\\s*("+LITERAL+"),\\s*("+LITERAL+")").matcher(settingsSource);
            while(rows.find()) {
                require(catalog.containsKey(decode(rows.group(1))),"Chinese setting title missing");
                require(catalog.containsKey(decode(rows.group(3))),"Chinese setting description missing: "+rows.group(3));
                checks+=2;
            }
            for(var model:com.mikasrevs.newphone.phone.model.PhoneModelRegistry.all()) {
                require(catalog.containsKey(model.name())&&catalog.containsKey(model.finish()),"Chinese model labels missing");
                checks+=2;
            }
            for(var entry:texts.entrySet()) {
                require(catalog.containsKey(entry.getKey()),"Chinese phone text missing: "+entry.getKey());
                String result=PhoneChineseTranslations.text(entry.getValue(),entry.getKey(),traditional);
                require(!result.isBlank()||entry.getKey().isBlank(),"Chinese phone translation empty");
                checks++;
            }
            for(var entry:catalog.entrySet()) {
                require(placeholders(entry.getValue()).equals(placeholders(entry.getKey())),"Chinese placeholders differ: "+entry.getKey());
                require(entry.getKey().isBlank()||!entry.getValue().isBlank(),"Chinese catalog entry empty");
                checks++;
            }
        }
        require(PhoneChineseTranslations.text("Gönder","Post",false).equals("发布"),"Chinese Post action became a noun");
        require(PhoneChineseTranslations.text("Açık","Clear",true).equals("晴朗"),"Chinese weather became a clear action");
        require(PhoneChineseTranslations.text("Arka","Back",true).equals("後置"),"Camera back became navigation back");
        require(PhoneChineseTranslations.text("Duraklat","Pause",false).equals("暂停"),"Stopwatch pause became delist");
        require(PhoneChineseTranslations.text("Kaydedildi","Saved",true).equals("已儲存"),"Passcode saved became a bookmark");
        require(PhoneSettingsStore.normalizeLanguage("zh-Hans").equals("zh_cn"),"Simplified alias failed");
        require(PhoneSettingsStore.normalizeLanguage("ZH-TW").equals("zh_tw"),"Traditional alias failed");
        require(PhoneSettingsStore.languageCodes().size()==11,"Language selection count incorrect");
        var english=JsonParser.parseString(Files.readString(lang.resolve("en_us.json"))).getAsJsonObject();
        for(String code:new String[]{"tr_tr","en_us","es_es","pt_br","de_de","fr_fr","ru_ru","pl_pl","it_it","zh_cn","zh_tw"}) {
            var data=JsonParser.parseString(Files.readString(lang.resolve(code+".json"))).getAsJsonObject();
            require(data.keySet().equals(english.keySet()),"Resource key mismatch: "+code);
            for(var entry:english.entrySet()) {
                String result=data.get(entry.getKey()).getAsString();
                require(!result.isBlank(),"Empty resource translation: "+code+" "+entry.getKey());
                require(placeholders(result).equals(placeholders(entry.getValue().getAsString())),"Resource placeholders differ: "+code+" "+entry.getKey());
                checks++;
            }
        }
        var language=PhoneSettingsStore.class.getDeclaredField("language");language.setAccessible(true);
        var loaded=PhoneSettingsStore.class.getDeclaredField("loaded");loaded.setAccessible(true);
        Object previousLanguage=language.get(null),previousLoaded=loaded.get(null);
        try {
            loaded.set(null,true);
            for(String code:PhoneSettingsStore.languageCodes()) {
                language.set(null,code);
                String emergency=PhoneSettingsStore.translation("screen.newphone.app.emergency");
                require(!emergency.equals("screen.newphone.app.emergency"),"Emergency label fell back to key: "+code);
                String notice=PhoneSettingsStore.translation("emergency.newphone.notice.cooldown",17);
                require(notice.contains("17")&&!notice.contains("%s"),"Emergency formatting failed: "+code);
                String system="Grup adı 1–40 karakter olmalı.";
                require(code.equals("tr")||!PhoneSettingsStore.localizeNetworkText(system).equals(system),"Group notice untranslated: "+code);
                String player="Özel mesajım — 123, hello!";
                require(PhoneSettingsStore.localizeNetworkText(player).equals(player),"Player text was modified: "+code);
                if(code.startsWith("zh_")) {
                    String contacts=PhoneSettingsStore.text("Kişiler","Contacts");
                    require(contacts.equals(code.equals("zh_cn")?"联系人":"聯絡人"),"Chinese contact terminology incorrect");
                    require(!PhoneSettingsStore.accentName().equals("Blue"),"Accent untranslated");
                    require(!PhoneSettingsStore.wallpaperName(0).equals("Alpine Sunset"),"Wallpaper untranslated");
                    String peer="Player_中文%name";
                    for(String suffix:new String[]{" seni arıyor."," seni görüntülü arıyor."," görüşmeyi sonlandırdı."," gönderini beğendi"}) {
                        String raw=peer+suffix,result=PhoneSettingsStore.localizeNetworkText(raw);
                        require(result.contains(peer)&&!result.equals(raw),"Chinese dynamic notice failed: "+raw);
                    }
                    require(!PhoneSettingsStore.localizeNetworkText("Mesaj gönderilemedi.").equals("Mesaj gönderilemedi."),"Chinese legacy notice untranslated");
                    require(PhoneSettingsStore.languageLabel().equals(code.equals("zh_cn")?"简体中文":"繁體中文"),"Chinese language label incorrect");
                }
            }
        } finally {language.set(null,previousLanguage);loaded.set(null,previousLoaded);}
        System.out.println("Phone translation checks passed: "+texts.size()+" static texts, eleven languages, "+checks+" coverage/format checks.");
    }
    private static List<String> placeholders(String s) {
        var matcher=Pattern.compile("%(?:\\d+\\$)?[sd]").matcher(s);List<String> result=new ArrayList<>();
        while(matcher.find())result.add(matcher.group());Collections.sort(result);return result;
    }
    private static String decode(String literal) {return JsonParser.parseString(literal.replace("\\'","'")).getAsString();}
    private static void require(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
}
