package com.mikasrevs.newphone.client.settings;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Phone-owned resource text follows the phone language, independently of Minecraft's language. */
public final class PhoneResourceTranslations {
    private static final Map<String, Map<String, String>> CATALOGS = load();
    private PhoneResourceTranslations() {}

    public static String text(String language, String key, Object... arguments) {
        String code = switch (language) {
            case "tr" -> "tr_tr";
            case "en" -> "en_us";
            case "es" -> "es_es";
            case "pt_br" -> "pt_br";
            case "de" -> "de_de";
            case "fr" -> "fr_fr";
            case "ru" -> "ru_ru";
            case "pl" -> "pl_pl";
            case "it" -> "it_it";
            case "zh_cn", "zh_tw" -> language;
            default -> "en_us";
        };
        String pattern = CATALOGS.get(code).getOrDefault(key, CATALOGS.get("en_us").getOrDefault(key, key));
        return arguments.length == 0 ? pattern : String.format(Locale.forLanguageTag(code.replace('_', '-')), pattern, arguments);
    }

    private static Map<String, Map<String, String>> load() {
        Map<String, Map<String, String>> catalogs = new HashMap<>();
        for (String code : new String[]{"tr_tr", "en_us", "es_es", "pt_br", "de_de", "fr_fr", "ru_ru", "pl_pl", "it_it", "zh_cn", "zh_tw"}) {
            try (var input = PhoneResourceTranslations.class.getResourceAsStream("/assets/newphone/lang/" + code + ".json")) {
                if (input == null) throw new IllegalStateException("Missing phone language: " + code);
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String, String> entries = new HashMap<>();
                json.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
                catalogs.put(code, Map.copyOf(entries));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not load phone language: " + code, exception);
            }
        }
        return Map.copyOf(catalogs);
    }
}
