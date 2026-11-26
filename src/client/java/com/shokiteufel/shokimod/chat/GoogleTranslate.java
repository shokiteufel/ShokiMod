package com.shokiteufel.shokimod.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Chat (Teil 4) > Translator: die Anfrage an Googles oeffentliche Uebersetzungs-Adresse und das Lesen ihrer Antwort - der reine Teil, ohne Netz. Das Senden steht in
 * {@link Translator}.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code GoogleTranslator}). Von dort stammen die Adresse ({@code translate.googleapis.com/translate_a/single} mit {@code
 * client=gtx&dt=t&sl=...&tl=...&q=...&dj=1}, ohne Schluessel), die Sprache "auto" fuer die Quelle und die Form der Antwort: ein Objekt mit {@code src} (die erkannte
 * Sprache) und {@code sentences}, jeder Satz mit {@code trans} (die Uebersetzung); die Uebersetzung ist alle {@code trans} hintereinander. Die Umsetzung ist eigene Arbeit:
 * Java; SkyHanni haengt die Werte als Gson-Elemente an (mit Anfuehrungszeichen), hier werden sie als Text gelesen, und eine unerwartete Antwort ist ein Ergebnis (null), kein
 * Absturz.
 *
 * <p>Was Google bekommt: der Text der einen angeklickten Zeile (oder des Befehls) und die zwei Sprachcodes - sonst nichts, keine Kennung des Spielers.
 */
public final class GoogleTranslate {

    private static final String BASE = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t";

    /** Die Uebersetzung und die Sprache, die Google im Original erkannt hat (leer, wenn es sie nicht nannte) */
    public record Result(String translation, String detectedLanguage) {
    }

    private GoogleTranslate() {
    }

    /**
     * Die Adresse der Anfrage.
     *
     * @param text   der Text, wie er uebersetzt werden soll
     * @param target der Code der Zielsprache
     * @param source der Code der Quellsprache, oder "auto"
     * @return die Adresse, oder null, wenn ein Code kein Sprachcode ist (er wuerde die Anfrage veraendern) oder der Text leer ist
     */
    public static String buildUrl(String text, String target, String source) {
        if (text == null || text.isBlank()) return null;
        if (!TranslatableLanguage.isValidCode(target)) return null;
        if (!"auto".equals(source) && !TranslatableLanguage.isValidCode(source)) return null;
        return BASE + "&sl=" + source + "&tl=" + target + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "&dj=1";
    }

    /**
     * Liest die Antwort.
     *
     * @return die Uebersetzung samt erkannter Sprache; null, wenn die Antwort nicht die erwartete Form hat oder nichts uebersetzt wurde
     */
    public static Result parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonObject object = root.getAsJsonObject();
            JsonElement sentences = object.get("sentences");
            if (sentences == null || !sentences.isJsonArray()) return null;
            StringBuilder out = new StringBuilder();
            JsonArray array = sentences.getAsJsonArray();
            for (JsonElement sentence : array) {
                // Auch Eintraege ohne "trans" kommen vor (die Umschrift in lateinische Schrift): sie tragen keine Uebersetzung
                if (!sentence.isJsonObject()) continue;
                JsonElement trans = sentence.getAsJsonObject().get("trans");
                if (trans != null && trans.isJsonPrimitive()) out.append(trans.getAsString());
            }
            if (out.length() == 0) return null;
            JsonElement src = object.get("src");
            String detected = src != null && src.isJsonPrimitive() ? src.getAsString() : "";
            return new Result(out.toString(), detected);
        } catch (RuntimeException e) {
            // Kein JSON, oder ein Wert hat nicht die Art, die er haben sollte
            return null;
        }
    }
}
