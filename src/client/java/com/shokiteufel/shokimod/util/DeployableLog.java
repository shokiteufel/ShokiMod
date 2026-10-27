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

/**
 * Das Protokoll der Deployables: eine eigene Datei mit dem, was die Mod an den Ruestungsstaendern liest und daraus macht.
 *
 * Die Datei liegt neben dem latest.log: {@code logs/shokimod-deployables.log} im Ordner des Profils. Zeilen: USE (ein Rechtsklick
 * mit einem Deployable in der Hand), STAND (ein Staender, der nach einem Deployable aussieht - sein Text, ob er gelesen wird, ob er
 * als der eigene gilt und warum), TEXT (ein Text in der Naehe, den die Mod nicht versteht), TIME (die Zahl eines Staenders hat
 * sich geaendert, mit den Millisekunden seit der letzten Aenderung), GONE (ein Staender ist weg), STATUS (was der Kasten gerade
 * weiss und warum er leer ist).
 *
 * Gespeichert wird nur, was die Deployables betrifft. Die Datei bleibt unter {@link #MAX_BYTES}; wird sie groesser, faellt die
 * aelteste Haelfte weg.
 */
public final class DeployableLog {

    private static final String FILE = "shokimod-deployables.log";
    private static final long MAX_BYTES = 400_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static int writes = 0;
    private static boolean announced = false;

    private DeployableLog() {
    }

    /** Ist das Protokoll an? Von Haus aus nein; der Schalter steht im verborgenen Reiter "Logs" - Suchwort "log" */
    public static boolean enabled() {
        return ModConfig.INSTANCE.logs.deployableLog;
    }

    /** Wo die Datei liegt - fuer die Diagnose */
    public static Path path() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs").resolve(FILE);
    }

    /** Eine Zeile schreiben. Fehler beim Schreiben bleiben stumm: ein Protokoll darf nichts kaputt machen */
    public static synchronized void line(String tag, String text) {
        if (!enabled()) return;
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            if (!announced) {
                announced = true;
                write(file, "---- " + LocalDateTime.now().format(STAMP) + " session start, ShokiMod deployable log ----\n");
            }
            write(file, LocalDateTime.now().format(STAMP) + " " + tag + " " + text.replace('\n', ' ') + "\n");
            if (++writes % 100 == 0) trim(file);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.debug("[ShokiMod] deployable log not written: {}", e.toString());
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
