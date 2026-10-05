package com.mikasrevs.newphone.client.settings;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Mainland and Taiwan phone terminology share the existing phone language routing. */
final class PhoneChineseTranslations {
    private static final Map<String, String> SIMPLIFIED = load("zh_cn");
    private static final Map<String, String> TRADITIONAL = load("zh_tw");
    private static final String[] NOTICE_PATTERNS = {
            "Wait %s seconds before sharing another post.", "You have a new match: %s",
            "Video calling %s...", "Calling %s...", "%s is video calling you.", "%s is calling you.",
            "%s declined the call.", "%s ended the call.", "%s sent you a follow request",
            "%s accepted your follow request", "%s reposted your post", "%s shared your post",
            "%s liked your post", "%s commented on your post", "%s replied to your post",
            "%s replied to your comment", "%s liked your comment", "%s liked your reply"
    };

    private PhoneChineseTranslations() {}

    static String text(String turkish, String english, boolean traditional) {
        // These English labels also occur as verbs or weather states.
        if ("Post".equals(english) && "Gönder".equals(turkish)) return traditional ? "發布" : "发布";
        if ("Clear".equals(english) && "Açık".equals(turkish)) return "晴朗";
        if ("Back".equals(english) && "Arka".equals(turkish)) return traditional ? "後置" : "后置";
        if ("Pause".equals(english) && "Duraklat".equals(turkish)) return traditional ? "暫停" : "暂停";
        if ("Saved".equals(english) && "Kaydedildi".equals(turkish)) return traditional ? "已儲存" : "已保存";
        return catalog(traditional).getOrDefault(english == null ? "" : english, english == null ? "" : english);
    }

    static String network(String original, String english, boolean traditional) {
        if (original.equals(english)) return original;
        Map<String, String> entries = catalog(traditional);
        String exact = entries.get(english);
        if (exact != null) return exact;
        for (String pattern : NOTICE_PATTERNS) {
            int marker = pattern.indexOf("%s");
            String prefix = pattern.substring(0, marker), suffix = pattern.substring(marker + 2);
            if (english.startsWith(prefix) && english.endsWith(suffix)
                    && english.length() >= prefix.length() + suffix.length()) {
                String argument = english.substring(prefix.length(), english.length() - suffix.length());
                return String.format(Locale.ROOT, entries.getOrDefault(pattern, pattern), argument);
            }
        }
        return english;
    }

    static Map<String, String> catalog(boolean traditional) { return traditional ? TRADITIONAL : SIMPLIFIED; }

    private static Map<String, String> load(String code) {
        try (var input = PhoneChineseTranslations.class.getResourceAsStream("/assets/newphone/phone_translations/" + code + ".json")) {
            if (input == null) throw new IllegalStateException("Missing Chinese phone catalog: " + code);
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> entries = new HashMap<>();
            json.entrySet().forEach(entry -> entries.put(entry.getKey(), entry.getValue().getAsString()));
            return Map.copyOf(entries);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not load Chinese phone catalog: " + code, exception);
        }
    }
}
