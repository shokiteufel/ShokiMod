package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Das Protokoll des Angelns: eine eigene Datei mit dem, was um den eigenen Haken steht und was die Hook display daraus macht.
 *
 * Die Datei liegt neben dem latest.log: {@code logs/shokimod-fishing.log} im Ordner des Profils. Anlass (28.11.2026): Die Zeit ueber dem Haken stand
 * zwischendurch nicht im Overlay, obwohl der Haken sie zeigte - ohne zu wissen, was Hypixel dort in der Luecke schreibt, laesst sich der Fehler nicht
 * eingrenzen. Zeilen: STATE (was die Hook display gerade tut oder warum nicht: Schalter, Welt, Haken, Angel), SCENE (alle benannten Ruestungsstaender und
 * anderen benannten Dinge im Umkreis von sechs Bloecken um den Haken - mit Text samt Farbcodes, Abstand, ob sie im Suchbereich liegen, ob die Mod sie liest und
 * warum nicht), GAP (wie SCENE, aber das Overlay ist leer, obwohl etwas Zeitaehnliches am Haken steht - die Zeilen, auf die es ankommt) und OVERLAY (wann das
 * Overlay erscheint und verschwindet, mit der Dauer des vorigen Zustands).
 *
 * Eine Zeile kommt nur, wenn sich etwas geaendert hat ({@link #lineIfChanged}); sonst stuende dort jeder Tick. Die Datei bleibt unter {@link #MAX_BYTES};
 * wird sie groesser, faellt die aelteste Haelfte weg.
 */
public final class FishingLog {

    private static final String FILE = "shokimod-fishing.log";
    private static final long MAX_BYTES = 1_000_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static int writes = 0;
    private static boolean announced = false;
    /** Die letzte Unterschrift je Platz - nur eine neue bringt eine Zeile */
    private static final Map<String, String> LAST = new HashMap<>();

    private FishingLog() {
    }

    /** Ist das Protokoll an? Von Haus aus nein; der Schalter steht im verborgenen Reiter "Logs" - Suchwort "log" */
    public static boolean enabled() {
        return ModConfig.INSTANCE.logs.fishingLog;
    }

    /** Wo die Datei liegt - fuer die Diagnose */
    public static Path path() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs").resolve(FILE);
    }

    /**
     * Hat sich die Unterschrift eines Platzes seit dem letzten Mal geaendert? Merkt sich die neue. Der erste Aufruf je Platz zaehlt als Aenderung.
     * Rein - die Karte kommt herein, damit sich das ohne Spiel pruefen laesst.
     */
    static boolean changed(Map<String, String> last, String slot, String signature) {
        String before = last.put(slot, signature);
        return !signature.equals(before);
    }

    /** Vergessen, was zuletzt geschrieben wurde - nach dem Ausschalten, damit der naechste Lauf mit einer Zeile beginnt */
    public static synchronized void forget() {
        if (!LAST.isEmpty()) LAST.clear();
    }

    /** Eine Zeile schreiben, aber nur wenn sich die Unterschrift des Platzes geaendert hat. Gibt zurueck, ob geschrieben wurde */
    public static synchronized boolean lineIfChanged(String slot, String signature, String tag, String text) {
        if (!enabled()) return false;
        if (!changed(LAST, slot, signature)) return false;
        line(tag, text);
        return true;
    }

    /** Eine Zeile schreiben. Fehler beim Schreiben bleiben stumm: ein Protokoll darf nichts kaputt machen */
    public static synchronized void line(String tag, String text) {
        if (!enabled()) return;
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            if (!announced) {
                announced = true;
                write(file, "---- " + LocalDateTime.now().format(STAMP) + " session start, ShokiMod fishing log ----\n");
            }
            write(file, LocalDateTime.now().format(STAMP) + " " + tag + " " + text.replace('\n', ' ') + "\n");
            if (++writes % 100 == 0) trim(file);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.debug("[ShokiMod] fishing log not written: {}", e.toString());
        }
    }

    private static void write(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Zu gross: die aeltere Haelfte weg, ab dem naechsten Zeilenanfang */
    private static void trim(Path file) throws IOException {
        if (Files.size(file) <= MAX_BYTES) return;
        String all = Files.readString(file, StandardCharsets.UTF_8);
        int cut = all.indexOf('\n', all.length() / 2);
        if (cut < 0) return;
        Files.writeString(file, "---- (older lines cut) ----\n" + all.substring(cut + 1), StandardCharsets.UTF_8);
    }
}
