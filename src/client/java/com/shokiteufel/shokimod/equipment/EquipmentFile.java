package com.shokiteufel.shokimod.equipment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Das Format der Datei {@code config/shokimod/equipment.json}: je Profil bis zu drei Saetze (gewoehnlich, Rift, Safari) zu je vier Gegenstaenden.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}). Von dort stammt die Regel: Das Equipment wird je Profil gemerkt (eine Datei je Profil-Kennung)
 * und ueberlebt den Neustart; je Profil stehen zwoelf Plaetze darin - vier gewoehnliche, vier der Rift, vier der Safari. Die Umsetzung ist eigene Arbeit: Java, eine
 * Datei fuer alle Profile in JSON statt einer NBT-Datei je Profil; ein Gegenstand steht hier als das, was der Codec des Spiels daraus macht (Kennung, Anzahl, Komponenten),
 * ein leerer Platz als {@code {}}. Wie der Gegenstand ins JSON und zurueck kommt, steht in {@link EquipmentCodec}; hier geht es nur um die Form der Datei und darum, eine
 * beschaedigte oder von Hand veraenderte Datei zu ertragen.
 *
 * <pre>
 * { "version": 1, "profiles": { "833f3e3c-...": { "normal": [ {..}, {}, {}, {} ], "rift": [ ... ], "safari": [ ... ] } } }
 * </pre>
 */
public final class EquipmentFile {

    /** Die Fassung des Formats */
    public static final int VERSION = 1;
    /** So viele Profile bleiben hoechstens in der Datei (die aeltesten fallen weg): Ein Spieler hat wenige, die Grenze schuetzt vor einer wuchernden Datei */
    public static final int MAX_PROFILES = 40;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private EquipmentFile() {
    }

    /** Vier leere Plaetze: {@code {}} je Platz (jedes Mal neue Objekte, damit nichts geteilt wird) */
    public static List<JsonElement> blankSet() {
        List<JsonElement> set = new ArrayList<>(EquipmentRules.SLOTS);
        for (int i = 0; i < EquipmentRules.SLOTS; i++) set.add(new JsonObject());
        return set;
    }

    /**
     * Die Datei lesen. Gibt nie null zurueck: Was nicht zu lesen ist, fehlt einfach. Die Profile stehen in der Reihenfolge der Datei; je Satz kommen genau vier Eintraege
     * heraus (zu wenige werden mit leeren Plaetzen aufgefuellt, zu viele abgeschnitten, ein Eintrag, der kein Objekt ist, wird ein leerer Platz); Saetze, die es nicht gibt,
     * fallen weg. Sind es mehr als {@link #MAX_PROFILES} Profile, bleiben die letzten.
     */
    public static Map<String, Map<EquipmentSet, List<JsonElement>>> parse(String text) {
        Map<String, Map<EquipmentSet, List<JsonElement>>> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return out;
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) return out;
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return out;
        }
        JsonElement profiles = root.get("profiles");
        if (profiles == null || !profiles.isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> entry : profiles.getAsJsonObject().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || !entry.getValue().isJsonObject()) continue;
            Map<EquipmentSet, List<JsonElement>> sets = new EnumMap<>(EquipmentSet.class);
            for (Map.Entry<String, JsonElement> setEntry : entry.getValue().getAsJsonObject().entrySet()) {
                EquipmentSet set = EquipmentSet.byKey(setEntry.getKey());
                if (set == null || !setEntry.getValue().isJsonArray()) continue;
                sets.put(set, fourSlots(setEntry.getValue().getAsJsonArray()));
            }
            if (!sets.isEmpty()) out.put(entry.getKey().toLowerCase(Locale.ROOT), sets);
        }
        while (out.size() > MAX_PROFILES) {
            out.remove(out.keySet().iterator().next());
        }
        return out;
    }

    private static List<JsonElement> fourSlots(JsonArray array) {
        List<JsonElement> set = new ArrayList<>(EquipmentRules.SLOTS);
        for (int i = 0; i < EquipmentRules.SLOTS; i++) {
            JsonElement element = i < array.size() ? array.get(i) : null;
            set.add(element != null && element.isJsonObject() ? element : new JsonObject());
        }
        return set;
    }

    /** Die Datei als Text. Ein Profil ohne einen einzigen Satz kommt nicht hinein; mehr als {@link #MAX_PROFILES} Profile: die letzten */
    public static String write(Map<String, Map<EquipmentSet, List<JsonElement>>> data) {
        JsonObject profiles = new JsonObject();
        List<Map.Entry<String, Map<EquipmentSet, List<JsonElement>>>> entries = new ArrayList<>(data.entrySet());
        int from = Math.max(0, entries.size() - MAX_PROFILES);
        for (int i = from; i < entries.size(); i++) {
            Map.Entry<String, Map<EquipmentSet, List<JsonElement>>> entry = entries.get(i);
            JsonObject sets = new JsonObject();
            for (EquipmentSet set : EquipmentSet.values()) {
                List<JsonElement> items = entry.getValue().get(set);
                if (items == null) continue;
                JsonArray array = new JsonArray();
                for (JsonElement item : items) array.add(item);
                sets.add(set.key(), array);
            }
            if (sets.size() > 0) profiles.add(entry.getKey(), sets);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("profiles", profiles);
        return GSON.toJson(root);
    }
}
