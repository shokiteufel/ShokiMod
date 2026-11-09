package com.shokiteufel.shokimod.dungeonkarte;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Die Raumtabelle: zu jedem Kern-Hash ({@link KernHash}) der Raum, zu dem er gehoert.
 *
 * Die Daten sind {@code rooms-modern.json} aus NoammAddons (Zweig {@code data}, CC0-1.0, siehe CREDITS.md): 140 Raeume mit 240 Hashes.
 * Ein Raum, der mehrere Kacheln belegt, hat je Kachel einen Hash. Geprueft am Dungeon-Lauf von Schiggy (09.11., Catacombs Floor V):
 * 27 von 36 Rasterplaetzen fanden ihren Raum, die uebrigen lagen ausserhalb des Stockwerks.
 */
public final class RaumTabelle {

    private static final String RESSOURCE = "/dungeonkarte/rooms-modern.json";
    private static RaumTabelle standard;

    private final Map<Integer, RaumDaten> nachKern = new HashMap<>();
    private int raeume;

    private RaumTabelle() {
    }

    /** Die Tabelle aus dem Jar, einmal geladen. Fehlt sie, ist sie leer: die Karte zeigt dann nur, was die Hypixel-Karte hergibt */
    public static synchronized RaumTabelle standard() {
        if (standard == null) {
            try (InputStream in = RaumTabelle.class.getResourceAsStream(RESSOURCE)) {
                standard = in == null ? new RaumTabelle() : laden(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                standard = new RaumTabelle();
            }
        }
        return standard;
    }

    /** Die Tabelle aus einem JSON-Feld wie dem von NoammAddons */
    public static RaumTabelle laden(Reader quelle) {
        RaumTabelle t = new RaumTabelle();
        JsonArray liste = JsonParser.parseReader(quelle).getAsJsonArray();
        for (JsonElement element : liste) {
            JsonObject o = element.getAsJsonObject();
            JsonArray kerneJson = o.has("cores") ? o.getAsJsonArray("cores") : new JsonArray();
            int[] kerne = new int[kerneJson.size()];
            for (int i = 0; i < kerne.length; i++) kerne[i] = kerneJson.get(i).getAsInt();
            RaumDaten daten = new RaumDaten(o.get("name").getAsString(), RaumTyp.vonTabelle(text(o, "type")),
                    kacheln(text(o, "shape")), kerne, zahl(o, "secrets"), zahl(o, "crypts"));
            t.raeume++;
            for (int kern : kerne) t.nachKern.put(kern, daten);
        }
        return t;
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static int zahl(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    /** Wie viele Kacheln eine Form belegt: "1x1" 1, "1x2" 2, "1x3" 3, "1x4" 4, "2x2" 4, "L" 3 */
    static int kacheln(String form) {
        if (form == null) return 0;
        return switch (form) {
            case "1x1" -> 1;
            case "1x2" -> 2;
            case "1x3", "L" -> 3;
            case "1x4", "2x2" -> 4;
            default -> 0;
        };
    }

    /** Der Raum zu einem Kern-Hash; null, wenn die Tabelle ihn nicht kennt */
    public RaumDaten nachKern(int hash) {
        return nachKern.get(hash);
    }

    /** Irgendein Kern-Hash des Raums mit diesem Namen (fuer Proben mit gebauten Welten); null, wenn es ihn nicht gibt */
    public Integer kernVon(String raumName) {
        for (Map.Entry<Integer, RaumDaten> e : nachKern.entrySet()) {
            if (e.getValue().name().equals(raumName)) return e.getKey();
        }
        return null;
    }

    /** Der Raum mit diesem Namen (der erste Eintrag); null, wenn es ihn nicht gibt */
    public RaumDaten nachName(String raumName) {
        Integer kern = kernVon(raumName);
        return kern == null ? null : nachKern.get(kern);
    }

    public int anzahlRaeume() {
        return raeume;
    }

    public int anzahlKerne() {
        return nachKern.size();
    }
}
