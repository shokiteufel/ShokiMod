package com.shokiteufel.shokimod.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Chatregeln als Code: eine oder mehrere Regeln zum Weitergeben, und der Weg zurueck.
 *
 * Gleiche Bauart wie beim Banner-Design (gepackt, in Buchstaben umgeschrieben, mit
 * Kennung vorn), aber eine eigene Kennung, damit man den falschen Code gleich erkennt.
 *
 * Der Weg zurueck traut dem Code nicht. Er kommt aus einem Discord-Kanal oder von einem
 * Freund, und eine Regel kann Chatzeilen verschlucken, ersetzen und Toene abspielen.
 * Deshalb kommen Regeln immer ausgeschaltet an, bekommen neue Kennungen, und alles, was
 * der Editor nicht zulassen wuerde, wird auf dessen Grenzen zurueckgesetzt.
 */
public final class ChatRuleShare {

    /** Die Eins ist die Fassung des Formats - eine spaetere kann am Praefix erkennen, was vorliegt */
    public static final String PREFIX = "SHOKIRULES1:";

    /** Kennung der Banner-Codes, nur um einem verwechselten Code die richtige Auskunft zu geben */
    private static final String BANNER_PREFIX = "SHOKI1:";

    /** Obergrenzen gegen Codes, die gar keine Regelliste sind - eine Zwischenablage kann alles enthalten */
    private static final int MAX_CODE_CHARS = 400_000;
    private static final int MAX_JSON_BYTES = 1_000_000;
    private static final int MAX_RULES = 200;

    /** Dieselben Grenzen wie die Eingabefelder im Editor (ChatRuleEditScreen) */
    private static final int LABEL_MAX = 48;
    private static final int TEXT_MAX = 256;
    private static final int ID_MAX = 128;
    private static final int AREA_MAX = 64;
    private static final int AREAS_MAX = 50;
    private static final int MILLIS_MIN = 200;
    private static final int MILLIS_MAX = 60_000;

    /**
     * Nur die Felder, die auch gespeichert werden - sonst wanderten die fluechtigen Felder
     * mit, die MoulConfig fuer Knoepfe braucht
     */
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    /** Ein Fehler, dessen Text man dem Spieler so zeigen kann */
    public static final class ShareException extends Exception {
        public ShareException(String message) {
            super(message);
        }
    }

    private ChatRuleShare() {
    }

    /**
     * Die Regeln als Code.
     *
     * Leere, unberuehrte Regeln fallen weg, sie waeren beim Einlesen ohnehin nutzlos. Die
     * Sperrliste einer Regel verweist auf andere Regeln; was nicht mitgeht, wird aus ihr
     * gestrichen, damit der Code keine Verweise ins Leere traegt.
     */
    public static String encode(List<ChatRule> rules) throws ShareException {
        List<ChatRule> shared = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ChatRule rule : rules) {
            if (rule == null || rule.isUntouched()) continue;
            shared.add(rule);
            if (rule.id != null) ids.add(rule.id);
        }
        if (shared.isEmpty()) throw new ShareException("There is nothing to share yet - the rule is empty");

        List<ChatRule> copies = new ArrayList<>();
        for (ChatRule rule : shared) {
            // Eine Kopie, damit die Sperrliste gekuerzt werden kann, ohne die echte Regel anzufassen
            ChatRule copy = GSON.fromJson(GSON.toJson(rule), ChatRule.class);
            if (copy.blocks != null) copy.blocks.retainAll(ids);
            copies.add(copy);
        }

        try {
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            try (GZIPOutputStream zip = new GZIPOutputStream(packed)) {
                zip.write(GSON.toJson(copies).getBytes(StandardCharsets.UTF_8));
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray());
        } catch (IOException | RuntimeException e) {
            throw new ShareException("Could not build the code: " + e);
        }
    }

    /**
     * Regeln aus einem Code. Die Liste ist geprueft und bereit zum Anhaengen - ausgeschaltet,
     * mit neuen Kennungen und mit Sperrlisten, die nur auf Regeln aus demselben Code zeigen.
     */
    public static List<ChatRule> decode(String clipboard) throws ShareException {
        if (clipboard == null || clipboard.isBlank()) throw new ShareException("Your clipboard is empty");

        String trimmed = clipboard.strip();
        if (trimmed.startsWith(BANNER_PREFIX)) {
            throw new ShareException("That is a banner design code - import it in the banner designer");
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new ShareException("That is not a chat rule code - it should start with " + PREFIX);
        }
        if (trimmed.length() > MAX_CODE_CHARS) {
            throw new ShareException("That code is far too long to be a list of rules");
        }
        // Beim Kopieren aus einer Nachricht rutschen gern Umbrueche in den Code
        String code = trimmed.replaceAll("\\s+", "");

        String json;
        try {
            byte[] packed = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
            try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
                // Eine Stelle mehr lesen als erlaubt: so merkt man, dass es nicht aufgehoert haette
                byte[] raw = zip.readNBytes(MAX_JSON_BYTES + 1);
                if (raw.length > MAX_JSON_BYTES) {
                    throw new ShareException("That code unpacks to far more than a list of rules");
                }
                json = new String(raw, StandardCharsets.UTF_8);
            }
        } catch (IOException | IllegalArgumentException e) {
            // Ein schiefer Base64-Text ist eine IllegalArgumentException, ein abgeschnittener Code eine IOException
            throw new ShareException("Could not read that code - is it complete?");
        }

        ChatRule[] parsed;
        try {
            parsed = GSON.fromJson(json, ChatRule[].class);
        } catch (RuntimeException e) {
            throw new ShareException("The code did not contain chat rules");
        }
        if (parsed == null || parsed.length == 0) throw new ShareException("The code did not contain any rules");
        if (parsed.length > MAX_RULES) {
            throw new ShareException("Too many rules in one code (the limit is " + MAX_RULES + ")");
        }

        List<ChatRule> adopted = adopt(parsed);
        if (adopted.isEmpty()) throw new ShareException("The rules in that code are all empty");
        return adopted;
    }

    /** Eine Regel samt der Sperrliste, wie sie im Code stand - die Kennungen darin gelten nur dort */
    private record Incoming(ChatRule rule, List<String> oldBlocks) {
    }

    private static List<ChatRule> adopt(ChatRule[] parsed) {
        List<Incoming> kept = new ArrayList<>();
        Map<String, String> newIds = new HashMap<>();

        for (ChatRule rule : parsed) {
            if (rule == null) continue;
            String oldId = rule.id;
            List<String> oldBlocks = rule.blocks == null ? List.of() : new ArrayList<>(rule.blocks);
            clean(rule);
            if (rule.isUntouched()) continue;

            // Neue Kennung: dieselbe Regel zweimal einzulesen soll keine zwei gleichen Kennungen ergeben
            rule.id = UUID.randomUUID().toString();
            if (oldId != null && !oldId.isBlank()) newIds.putIfAbsent(oldId, rule.id);
            kept.add(new Incoming(rule, oldBlocks));
        }

        List<ChatRule> out = new ArrayList<>();
        for (Incoming in : kept) {
            ChatRule rule = in.rule();
            rule.blocks = new ArrayList<>();
            for (String old : in.oldBlocks()) {
                String mapped = old == null ? null : newIds.get(old);
                // Was nicht mitgekommen ist, kann nichts sperren; sich selbst sperren waere sinnlos
                if (mapped != null && !mapped.equals(rule.id) && !rule.blocks.contains(mapped)) {
                    rule.blocks.add(mapped);
                }
            }
            out.add(rule);
        }
        return out;
    }

    /** Alles, was der Editor nicht zulassen wuerde, auf dessen Grenzen bringen */
    private static void clean(ChatRule rule) {
        // Eine fremde Regel schaltet sich nicht von selbst ein
        rule.enabled = false;
        // Der alte Mindestwert wird beim Laden in den Wert-Alarm uebernommen - das soll ein Code nicht ausloesen
        rule.minValue = null;

        rule.label = text(rule.label, "New rule", LABEL_MAX);
        rule.filter = text(rule.filter, "", TEXT_MAX);
        rule.except = text(rule.except, "", TEXT_MAX);
        rule.replacement = text(rule.replacement, "", TEXT_MAX);
        rule.actionBar = text(rule.actionBar, "", TEXT_MAX);
        rule.announcement = text(rule.announcement, "", TEXT_MAX);
        rule.toast = text(rule.toast, "", TEXT_MAX);
        rule.toastIcon = text(rule.toastIcon, "", ID_MAX);
        if (rule.toastIcon.isBlank()) rule.toastIcon = "minecraft:paper";
        rule.soundId = text(rule.soundId, "", ID_MAX);
        rule.soundFile = soundFileName(rule.soundFile);

        rule.announcementMillis = Math.clamp(rule.announcementMillis, MILLIS_MIN, MILLIS_MAX);
        rule.toastMillis = Math.clamp(rule.toastMillis, MILLIS_MIN, MILLIS_MAX);
        rule.volume = Float.isNaN(rule.volume) ? 1.0f : Math.clamp(rule.volume, 0.01f, 1.0f);

        List<String> areas = new ArrayList<>();
        if (rule.areas != null) {
            for (String area : rule.areas) {
                if (area == null || area.isBlank() || areas.size() >= AREAS_MAX) continue;
                areas.add(text(area, "", AREA_MAX));
            }
        }
        rule.areas = areas;
    }

    private static String text(String value, String fallback, int max) {
        if (value == null) return fallback;
        return value.length() > max ? value.substring(0, max) : value;
    }

    /**
     * Der Name einer Datei in config/shokimod/sounds - und nur der Name.
     *
     * Der Abspieler haengt ihn an den Tonordner. Ein Pfad mit Trennzeichen oder ".." liesse
     * einen fremden Code auf Dateien ausserhalb davon zeigen, deshalb faellt so etwas weg.
     */
    static String soundFileName(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() > ID_MAX) return "";
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0
                || value.contains("..")) {
            return "";
        }
        return value;
    }
}
