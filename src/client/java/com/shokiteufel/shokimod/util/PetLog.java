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
 * Das Pet-Protokoll: eine eigene Datei mit allem, was die Mod ueber die Pets liest und entscheidet.
 *
 * Das Spiel-Log ist dafuer zu voll, und wer es weitergibt, soll nicht erst die Pet-Zeilen heraussuchen muessen.
 * Die Datei liegt neben dem latest.log: {@code logs/shokimod-pet.log} im Ordner des Profils. Sie nennt je Wechsel
 * des Pets (Autopet-Zeile mit ihrem Mauszeiger-Text, Tab-Liste, Pet-Menue), wie die Mod es zugeordnet hat und
 * warum - damit sich ein falsches Bild oder eine falsche Zahl beim naechsten Mal nachvollziehen laesst, statt sie
 * raten zu muessen.
 *
 * Zeilen: CHAT (Zeilen zum Pet-Wechsel mit Farbcodes, Mauszeiger-Text und Klick-Ziel, dazu Hypixels eigene Aufstiegsmeldung),
 * ID (was die Mod daraus macht und was danach im Kasten steht), TAB (der Pet-Block der Tab-Liste: nach jedem Ereignis zwanzig
 * Sekunden lang jeder neue Stand mit den Millisekunden seit dem Ereignis, sonst bei Aenderung und einmal je Minute), MENU,
 * LEVEL.
 *
 * Gespeichert wird nur, was die Pets betrifft: keine Chatzeilen anderer Spieler, keine Zugangsdaten. Die Datei bleibt
 * unter {@link #MAX_BYTES}; wird sie groesser, faellt die aelteste Haelfte weg.
 */
public final class PetLog {

    private static final String FILE = "shokimod-pet.log";
    private static final long MAX_BYTES = 600_000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static int writes = 0;
    private static boolean announced = false;

    private PetLog() {
    }

    /** Ist das Protokoll an? Von Haus aus ja - es ist klein und nur fuer Pets */
    public static boolean enabled() {
        return ModConfig.INSTANCE.hud.pet.petLog;
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
                write(file, "---- " + LocalDateTime.now().format(STAMP) + " session start, ShokiMod pet log ----\n");
            }
            write(file, LocalDateTime.now().format(STAMP) + " " + tag + " " + text.replace('\n', ' ') + "\n");
            if (++writes % 100 == 0) trim(file);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.debug("[ShokiMod] pet log not written: {}", e.toString());
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
