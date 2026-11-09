package com.shokiteufel.shokimod.dungeonkarte;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Wo NoammAddons seine Dungeon-Karte hinlegt: Lage (x, y in Bildschirmpunkten der GUI) und Groesse (Faktor) aus seiner Config.
 *
 * Die Funny Map legt sich auf diese Stelle und ersetzt dort seine Karte. Die Datei liegt in {@code config/<NoammAddons>/config.json}
 * (der Ordnername wird beim Bau von Noamm eingesetzt, darum wird jeder Ordner genommen, dessen Name "noamm" enthaelt); gibt es mehrere
 * Configs, steht der Name der gewaehlten in {@code currentConfig.json} und die Datei in {@code configs/<Name>.json}. Darin steht unter
 * {@code hud} je Anzeige {@code name, x, y, scale}; die Karte heisst "Dungeon Map".
 *
 * Grenze: Noamm schreibt seine Config nur alle fuenf Minuten und beim Beenden. Wer die Karte dort eben erst verschoben hat, sieht die
 * Funny Map erst nach einer Weile (oder nach dem naechsten Beenden) an der neuen Stelle.
 */
public final class NoammLage {

    public static final String HUD_NAME = "Dungeon Map";

    /** Wie der Schalter bei Noamm heisst, der die Zeile unter der Karte einblendet (Vorgabe dort: aus) */
    private static final String EXTRA_INFO = "Show Extra Info Under Map";

    /** Noamm legt alle HUD-Anzeigen in einer Referenzaufloesung von 960 x 540 Punkten an (seine Klasse Resolution) */
    private static final float REFERENZ_BREITE = 960f;
    private static final float REFERENZ_HOEHE = 540f;

    /** Lage und Groesse einer HUD-Anzeige von Noamm; {@code extraInfo}: die Zeile unter der Karte ist an (der Rahmen ist dann 140 statt 128 hoch) */
    public record Lage(float x, float y, float scale, boolean extraInfo) {
        public Lage(float x, float y, float scale) {
            this(x, y, scale, false);
        }
    }

    private NoammLage() {
    }

    /**
     * Der Faktor, mit dem Noamm seine Koordinaten auf den Bildschirm legt: x, y und Groesse in seiner Config gelten in der Referenzaufloesung
     * 960 x 540 und werden mit {@code min(Breite / 960, Hoehe / 540)} der GUI umgerechnet. Wer sie unverrechnet nimmt, liegt bei jeder anderen
     * GUI-Groesse als 2 auf einem Full-HD-Bild neben der Karte von Noamm (Schiggy, 09.11.: "liegt nicht uebereinander").
     */
    public static float referenzFaktor(int guiBreite, int guiHoehe) {
        if (guiBreite <= 0 || guiHoehe <= 0) return 1f;
        return Math.min(guiBreite / REFERENZ_BREITE, guiHoehe / REFERENZ_HOEHE);
    }

    /** Die Lage der Anzeige {@code hudName} aus dem Text einer Noamm-Config; null, wenn sie nicht darin steht */
    public static Lage ausJson(String json, String hudName) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement wurzel = JsonParser.parseString(json);
            if (!wurzel.isJsonObject()) return null;
            JsonElement hud = wurzel.getAsJsonObject().get("hud");
            if (hud == null || !hud.isJsonArray()) return null;
            JsonArray liste = hud.getAsJsonArray();
            for (JsonElement e : liste) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                JsonElement name = o.get("name");
                if (name == null || !hudName.equals(name.getAsString())) continue;
                float x = zahl(o, "x", 20f);
                float y = zahl(o, "y", 20f);
                float scale = zahl(o, "scale", 1f);
                return scale > 0f ? new Lage(x, y, scale, extraInfo(wurzel.getAsJsonObject(), hudName)) : null;
            }
        } catch (RuntimeException e) {
            // keine lesbare Config: dann gibt es keine Lage
        }
        return null;
    }

    /**
     * Alle Einstellungen der Karte aus dem Text einer Noamm-Config: der Teil {@code config[Dungeon Map].configSettings}. Null, wenn die Datei keinen
     * Eintrag fuer die Karte hat (dann gelten die eigenen Einstellungen der Funny Map); ein Eintrag ohne einzelne Werte ergibt seine Vorgaben.
     */
    public static KartenEinstellungen einstellungenAusJson(String json, String hudName) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement wurzel = JsonParser.parseString(json);
            if (!wurzel.isJsonObject()) return null;
            JsonElement config = wurzel.getAsJsonObject().get("config");
            if (config == null || !config.isJsonArray()) return null;
            for (JsonElement e : config.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject feature = e.getAsJsonObject();
                JsonElement name = feature.get("name");
                if (name == null || !hudName.equals(name.getAsString())) continue;
                JsonElement einst = feature.get("configSettings");
                return KartenEinstellungen.ausNoamm(einst != null && einst.isJsonObject() ? einst.getAsJsonObject() : null);
            }
        } catch (RuntimeException e) {
            // keine lesbare Config: dann gibt es keine Einstellungen
        }
        return null;
    }

    /** Die Einstellungen der Karte aus der laufenden Config von Noamm; null, wenn Noamm nicht da ist oder die Karte nicht darin steht */
    public static KartenEinstellungen einstellungenLesen(Path configOrdner) {
        Path datei = configDatei(configOrdner);
        if (datei == null) return null;
        try {
            return einstellungenAusJson(Files.readString(datei, StandardCharsets.UTF_8), HUD_NAME);
        } catch (IOException e) {
            return null;
        }
    }

    /** Steht die Zeile unter der Karte an? Unter {@code config[Name der Anzeige].configSettings}; fehlt der Eintrag, gilt Noamms Vorgabe: aus */
    private static boolean extraInfo(JsonObject wurzel, String hudName) {
        JsonElement config = wurzel.get("config");
        if (config == null || !config.isJsonArray()) return false;
        for (JsonElement e : config.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject feature = e.getAsJsonObject();
            JsonElement name = feature.get("name");
            if (name == null || !hudName.equals(name.getAsString())) continue;
            JsonElement einstellungen = feature.get("configSettings");
            if (einstellungen == null || !einstellungen.isJsonObject()) return false;
            JsonElement wert = einstellungen.getAsJsonObject().get(EXTRA_INFO);
            return wert != null && wert.isJsonPrimitive() && wert.getAsJsonPrimitive().isBoolean() && wert.getAsBoolean();
        }
        return false;
    }

    private static float zahl(JsonObject o, String key, float vorgabe) {
        JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? vorgabe : e.getAsFloat();
    }

    /** Die Config-Datei, die Noamm gerade benutzt; null, wenn keine zu finden ist */
    public static Path configDatei(Path configOrdner) {
        if (configOrdner == null || !Files.isDirectory(configOrdner)) return null;
        try (Stream<Path> inhalt = Files.list(configOrdner)) {
            for (Path ordner : (Iterable<Path>) inhalt::iterator) {
                if (!Files.isDirectory(ordner)) continue;
                if (!ordner.getFileName().toString().toLowerCase(Locale.ROOT).contains("noamm")) continue;
                Path gewaehlt = gewaehlteConfig(ordner);
                if (gewaehlt != null) return gewaehlt;
                Path standard = ordner.resolve("config.json");
                if (Files.isRegularFile(standard)) return standard;
            }
        } catch (IOException e) {
            // Ordner nicht lesbar: keine Datei
        }
        return null;
    }

    private static Path gewaehlteConfig(Path ordner) {
        Path zeiger = ordner.resolve("currentConfig.json");
        if (!Files.isRegularFile(zeiger)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(zeiger, StandardCharsets.UTF_8));
            if (!e.isJsonPrimitive()) return null;
            String name = e.getAsString();
            if (name.isBlank() || "default".equals(name) || !name.matches("^[A-Za-z0-9_-]{1,32}$")) return null;
            Path datei = ordner.resolve("configs").resolve(name + ".json");
            return Files.isRegularFile(datei) ? datei : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Ein Wert, der sich aendert, sobald Noamm seine Config schreibt oder eine andere Config waehlt (der Zeitpunkt der letzten Aenderung der
     * benutzten Datei und des Zeigers darauf); 0, wenn es keine Datei gibt. Noamm schreibt beim Schliessen seines HUD-Editors (/na hud),
     * beim Beenden und alle fuenf Minuten - wer das hier jede Sekunde fragt, sieht eine verschobene Karte gleich.
     */
    public static long aenderung(Path configOrdner) {
        Path datei = configDatei(configOrdner);
        if (datei == null) return 0L;
        long stand = zeit(datei);
        // Der Zeiger auf die gewaehlte Config liegt im Ordner von Noamm, neben der Standard-Config oder zwei Ebenen hoeher neben configs/
        Path ordner = datei.getParent();
        if (ordner != null && "configs".equals(ordner.getFileName().toString()) && ordner.getParent() != null) ordner = ordner.getParent();
        if (ordner != null) stand = Math.max(stand, zeit(ordner.resolve("currentConfig.json")));
        return stand;
    }

    private static long zeit(Path datei) {
        try {
            return Files.getLastModifiedTime(datei).toMillis();
        } catch (IOException | RuntimeException e) {
            return 0L;
        }
    }

    /** Die Lage der Dungeon-Karte aus der laufenden Config von Noamm; null, wenn Noamm nicht da ist oder die Karte nicht darin steht */
    public static Lage lesen(Path configOrdner) {
        Path datei = configDatei(configOrdner);
        if (datei == null) return null;
        try {
            return ausJson(Files.readString(datei, StandardCharsets.UTF_8), HUD_NAME);
        } catch (IOException e) {
            return null;
        }
    }
}
