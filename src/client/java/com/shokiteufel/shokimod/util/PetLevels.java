package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die Erfahrungstabellen der Pets: was eine Stufe kostet und wo Schluss ist.
 *
 * Die Zahlen stehen im NotEnoughUpdates-Verzeichnis, aus dem die Mod ohnehin schon
 * Item-Namen holt. Hier werden daraus die drei Fragen beantwortet, die der Kasten auf
 * dem Bildschirm stellt: Wie hoch geht dieses Pet? Wie viel Erfahrung liegt bis dahin?
 * Und was kostet die letzte Stufe - denn daran misst sich der Ueberschuss.
 *
 * <p><b>Ueberschuss-Stufen:</b> Hypixel zeigt sie nicht, sie sind eine Rechnung der
 * Mods. Wer die Hoechststufe erreicht hat, sammelt weiter Erfahrung; je volle Kosten
 * der letzten Stufe zaehlt eine Stufe darueber. Nachgerechnet an einem Golden Dragon
 * mit 627.958.704,5 Erfahrung im Ueberschuss: geteilt durch 1.886.700 ergibt 332,8 -
 * und im Spiel stand [332].
 */
public final class PetLevels {

    private static final String ENDPOINT =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/pets.json";
    /** Die Tabellen aendern sich fast nie - einmal am Tag nachsehen genuegt */
    private static final long REFRESH_MILLIS = 24 * 60 * 60 * 1000L;
    private static final int DEFAULT_MAX_LEVEL = 100;

    /** Alles, was ein Pet zum Rechnen braucht */
    public record Table(List<Integer> steps, int offset, int maxLevel) {

        /** Erfahrung, die bis zu dieser Stufe insgesamt gesammelt sein muss */
        public double totalTo(int level) {
            int bis = offset + Math.max(1, Math.min(level, maxLevel)) - 1;
            double sum = 0;
            for (int i = offset; i < bis && i < steps.size(); i++) sum += steps.get(i);
            return sum;
        }

        /** Was die letzte regulaere Stufe kostet - das Mass fuer den Ueberschuss */
        public int lastStep() {
            int index = Math.min(offset + maxLevel - 2, steps.size() - 1);
            return index >= 0 && index < steps.size() ? steps.get(index) : 0;
        }
    }

    private static final RemoteMap<JsonElement> SOURCE = new RemoteMap<>(
            "pet levels", ENDPOINT, "pet-levels.json", REFRESH_MILLIS, PetLevels::parse);

    /** Aufbereitet, damit nicht bei jedem Bild neu durch die Antwort gegangen wird */
    private static final Map<String, Table> cache = new ConcurrentHashMap<>();

    private PetLevels() {
    }

    private static Map<String, JsonElement> parse(JsonObject root) {
        Map<String, JsonElement> out = new ConcurrentHashMap<>();
        for (String key : List.of("pet_levels", "pet_rarity_offset", "custom_pet_leveling")) {
            JsonElement value = root.get(key);
            if (value != null && !value.isJsonNull()) out.put(key, value);
        }
        // Ein neuer Stand macht die aufbereiteten Tabellen ungueltig
        if (!out.isEmpty()) cache.clear();
        return out;
    }

    public static boolean ready() {
        SOURCE.prefetch();
        return SOURCE.get("pet_levels") != null;
    }

    public static String status() {
        return SOURCE.status();
    }

    /**
     * Die Tabelle fuer dieses Pet, oder null solange die Zahlen noch nicht da sind.
     *
     * @param petId    Kennung wie GOLDEN_DRAGON - aus dem Namen gebildet
     * @param rarity   COMMON bis MYTHIC
     */
    public static Table tableFor(String petId, String rarity) {
        SOURCE.prefetch();
        String key = petId + "|" + rarity;
        Table fertig = cache.get(key);
        if (fertig != null) return fertig;

        JsonElement basis = SOURCE.get("pet_levels");
        if (basis == null || !basis.isJsonArray()) return null;

        List<Integer> steps = toList(basis.getAsJsonArray());
        int offset = offsetFor(rarity, null);
        int maxLevel = DEFAULT_MAX_LEVEL;

        JsonElement custom = SOURCE.get("custom_pet_leveling");
        if (custom != null && custom.isJsonObject()) {
            JsonElement eigen = custom.getAsJsonObject().get(petId);
            if (eigen != null && eigen.isJsonObject()) {
                JsonObject o = eigen.getAsJsonObject();
                if (o.has("rarity_offset")) offset = offsetFor(rarity, o.getAsJsonObject("rarity_offset"));
                if (o.has("max_level")) maxLevel = o.get("max_level").getAsInt();
                if (o.has("pet_levels") && o.get("pet_levels").isJsonArray()) {
                    // Die eigene Tabelle SETZT DIE BASIS FORT, sie ersetzt sie nicht: Die
                    // Drachen steigen bis Stufe 100 wie jedes Pet ihrer Seltenheit, erst
                    // darueber gelten ihre eigenen Kosten. Wer sie ersetzt, rechnet mit
                    // einer Grundlage von fuenf Millionen statt zweihundertvierzehn - und
                    // bekommt aus 332 Ueberschuss-Stufen die 2179 vom 10.09.
                    List<Integer> fortsetzung = toList(o.getAsJsonArray("pet_levels"));
                    List<Integer> zusammen = new ArrayList<>(
                            steps.subList(Math.min(offset, steps.size()), steps.size()));
                    zusammen.addAll(fortsetzung);
                    steps = zusammen;
                    offset = 0;   // der Versatz steckt schon im Ausschnitt
                }
            }
        }
        if (steps.isEmpty()) return null;

        Table table = new Table(List.copyOf(steps), Math.max(0, offset), Math.max(1, maxLevel));
        cache.put(key, table);
        return table;
    }

    private static int offsetFor(String rarity, JsonObject eigene) {
        String key = rarity == null ? "" : rarity.toUpperCase(Locale.ROOT);
        if (eigene != null && eigene.has(key)) return eigene.get(key).getAsInt();
        JsonElement offsets = SOURCE.get("pet_rarity_offset");
        if (offsets != null && offsets.isJsonObject() && offsets.getAsJsonObject().has(key)) {
            return offsets.getAsJsonObject().get(key).getAsInt();
        }
        return 0;
    }

    private static List<Integer> toList(JsonArray array) {
        List<Integer> out = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            try {
                out.add(e.getAsInt());
            } catch (RuntimeException ignored) {
                // eine kaputte Zahl macht die restliche Tabelle nicht unbrauchbar
            }
        }
        return out;
    }

    /**
     * Aus "Golden Dragon" wird GOLDEN_DRAGON - so heisst es in den Tabellen.
     * Das angehaengte "Egg" der Auktionsnamen faellt weg.
     */
    public static String idFor(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        name = name.replaceAll("(?i)\\s+Egg$", "");
        return name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
