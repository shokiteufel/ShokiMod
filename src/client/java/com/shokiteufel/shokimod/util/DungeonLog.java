package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.handler.DungeonLauf;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Das "Dungeon log" (Reiter Logs, von Haus aus aus): schreibt nur waehrend eines Dungeon-Laufs alles, was fuer die Dungeon-Funktionen wichtig
 * ist, in {@code logs/shokimod-dungeon.log}.
 *
 * <p>Drei Arten von Zeilen:
 * <ul>
 *   <li><b>Ereignisse</b> (EVENT): Lauf angefangen und zu Ende, Stockwerk, Gruppe, Boss-Raum, Tueren, Tode, die erkannten Raeume der Funny Map und
 *       die Hashes, die die Tabelle nicht kennt.</li>
 *   <li><b>Fenster</b> (alle 10 Sekunden): Bilder je Sekunde und das laengste Bild, wie oft die Karte gelesen, gescannt, veraendert und neu gemalt
 *       wurde, wie lange das Zeichnen und die Arbeit der Karte dauerten, Speicher und Muellsammlung.</li>
 *   <li><b>TOO OFTEN</b>: eine Zeile, wenn etwas haeufiger passiert, als es duerfte ({@link Zaehler#grenze}) - das ist die Stelle, an der etwas
 *       zu oft geladen oder gezeichnet wird und Bilder kostet. Dazu LOW FPS, wenn die Bilder je Sekunde unter 30 fallen.</li>
 * </ul>
 *
 * <p>Ist das Log aus oder man nicht im Dungeon, kosten alle Zaehler nur einen Blick auf {@link #aktiv()}: Die Stellen im Code rufen
 * {@code DungeonLog.zaehlen(...)}, ohne vorher zu fragen.
 */
public final class DungeonLog {

    /** Was gezaehlt wird, mit der Grenze je Sekunde, ueber der es "zu oft" ist (0: nur zur Information) */
    public enum Zaehler {
        KARTE_GELESEN("map read from the hotbar item", 6.0,
                "the map item is read far more often than packets arrive - check the map timer"),
        KARTENPAKETE("map packets received", 20.0, "Hypixel sends map data unusually often"),
        SCAN_DURCHGAENGE("world scan passes", 6.0, "the world scan runs more often than four times a second"),
        SCAN_WELTFRAGEN("height queries of the world scan", 400.0,
                "the scan keeps asking the world for rooms it already knows (it should rest once all chunks are in)"),
        RASTER_AENDERUNGEN("grid changes", 6.0, "the map grid changes again and again - the image has to be repainted every time"),
        BILD_GEMALT("map image repainted", 4.0, "the map image is repainted although nothing changed (it should only follow the grid)"),
        KARTE_GEZEICHNET("frames with the map drawn", 0.0, ""),
        STERN_MOBS("star mob scan passes (one per tick while the marking is on)", 0.0, ""),
        TUEREN_NEU_GEZEICHNET("door sections redrawn", 3.0, "the doors are marked for redrawing again and again - every one costs chunk meshing");

        final String name;
        final double grenze;
        final String hinweis;

        Zaehler(String name, double grenze, String hinweis) {
            this.name = name;
            this.grenze = grenze;
            this.hinweis = hinweis;
        }
    }

    /** Gemessene Zeit, mit der Grenze in Millisekunden je Sekunde */
    public enum Zeit {
        KARTE_ZEICHNEN("map drawing", 25.0, "drawing the map takes more than 2.5 % of the time"),
        KARTE_ARBEITEN("map scan and read work", 40.0, "the map's scan and read work takes more than 4 % of the time");

        final String name;
        final double grenzeMs;
        final String hinweis;

        Zeit(String name, double grenzeMs, String hinweis) {
            this.name = name;
            this.grenzeMs = grenzeMs;
            this.hinweis = hinweis;
        }
    }

    static final long FENSTER_TICKS = 200;
    static final double FPS_UNTEN = 30.0;
    private static final long DATEI_MAX = 3_000_000L;
    private static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT);

    private static volatile boolean aktiv;
    private static final long[] werte = new long[Zaehler.values().length];
    private static final long[] nanos = new long[Zeit.values().length];
    private static final int[] serie = new int[Zaehler.values().length];
    private static final int[] serieZeit = new int[Zeit.values().length];

    private static BufferedWriter schreiber;
    private static long laufBeginn;
    private static long fensterBeginn;
    private static long fensterTicks;
    private static int bilder;
    private static long letztesBild;
    private static long laengstesBild;
    private static long gcAnzahl;
    private static long gcMillis;
    private static boolean warImBoss;
    private static Integer warStockwerk;
    private static final long[] summen = new long[Zaehler.values().length];
    private static long summeBilder;

    private DungeonLog() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /** Laeuft das Log gerade (Schalter an und mitten in einem Dungeon-Lauf)? */
    public static boolean aktiv() {
        return aktiv;
    }

    // ------------------------------------------------------------------ Zaehlen und Messen (von den Stellen im Code gerufen)

    public static void zaehlen(Zaehler z) {
        if (aktiv) werte[z.ordinal()]++;
    }

    public static void zaehlen(Zaehler z, long menge) {
        if (aktiv && menge > 0) werte[z.ordinal()] += menge;
    }

    /** Der Anfang einer Zeitmessung; 0, wenn das Log aus ist (dann kostet {@link #ende} nichts und fragt die Uhr nicht) */
    public static long beginn() {
        return aktiv ? System.nanoTime() : 0L;
    }

    public static void ende(Zeit z, long beginn) {
        if (beginn != 0L) nanos[z.ordinal()] += System.nanoTime() - beginn;
    }

    /** Ein Bild wurde gezeichnet (vom HudRenderer): zaehlt die Bilder und merkt sich die laengste Pause zwischen zwei Bildern */
    public static void bild() {
        if (!aktiv) return;
        long jetzt = System.nanoTime();
        if (letztesBild != 0L) laengstesBild = Math.max(laengstesBild, jetzt - letztesBild);
        letztesBild = jetzt;
        bilder++;
    }

    /** Ein Ereignis des Laufs als Zeile (nur waehrend eines Laufs) */
    public static void ereignis(String text) {
        if (aktiv) zeile("EVENT", text);
    }

    /** Eine Chatzeile (ohne Farbcodes) - nur die, die fuer den Lauf wichtig sind, kommen ins Log */
    public static void chat(String text) {
        if (!aktiv || text == null || !wichtig(text)) return;
        zeile("CHAT", text.strip());
    }

    /** Welche Chatzeilen zum Lauf gehoeren: Tueren, Watcher/Boss, Tode, Start und Ende, Wertung */
    static boolean wichtig(String t) {
        return t.contains("opened a WITHER door") || t.contains("BLOOD DOOR") || t.startsWith("[BOSS]")
                || t.contains(" ☠ ") || t.contains("Mort: Here, I found this map") || t.contains("Catacombs - ")
                || t.contains("Team Score") || t.contains("Secrets Found") || t.contains("Defeated ") || t.contains("reconnected");
    }

    // ------------------------------------------------------------------ Takt

    private static void tick() {
        boolean soll = FeatureGate.dungeonLog() && DungeonLauf.imDungeon();
        if (soll && !aktiv) {
            starten();
        } else if (!soll && aktiv) {
            beenden(FeatureGate.dungeonLog() ? "left the dungeon" : "switched off");
            return;
        }
        if (!aktiv) return;

        boolean boss = DungeonLauf.imBoss();
        if (boss != warImBoss) {
            warImBoss = boss;
            zeile("EVENT", boss ? "entered the boss room area (the grid is left)" : "back in the room grid");
        }
        Integer stock = DungeonLauf.stockwerk();
        if (!java.util.Objects.equals(stock, warStockwerk)) {
            warStockwerk = stock;
            zeile("EVENT", "floor " + (stock == null ? "unknown" : stock));
        }
        if (++fensterTicks >= FENSTER_TICKS) {
            fensterTicks = 0;
            fensterSchreiben();
        }
    }

    private static void starten() {
        try {
            Path datei = datei();
            Files.createDirectories(datei.getParent());
            if (Files.exists(datei) && Files.size(datei) > DATEI_MAX) {
                Files.move(datei, datei.resolveSibling("shokimod-dungeon.log.old"), StandardCopyOption.REPLACE_EXISTING);
            }
            schreiber = Files.newBufferedWriter(datei, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[DungeonLog] cannot open the log file: {}", e.toString());
            schreiber = null;
        }
        java.util.Arrays.fill(werte, 0L);
        java.util.Arrays.fill(nanos, 0L);
        java.util.Arrays.fill(serie, 0);
        java.util.Arrays.fill(serieZeit, 0);
        java.util.Arrays.fill(summen, 0L);
        summeBilder = 0;
        bilder = 0;
        letztesBild = 0L;
        laengstesBild = 0L;
        fensterTicks = 0;
        laufBeginn = System.nanoTime();
        fensterBeginn = laufBeginn;
        warImBoss = false;
        warStockwerk = null;
        gcLesen();
        aktiv = true;
        zeile("RUN START", "ShokiMod " + FabricLoader.getInstance().getModContainer("shokimod")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + " | " + gruppe());
    }

    private static String gruppe() {
        List<String> zeilen = com.shokiteufel.shokimod.scanner.TabListScanner.lastLines();
        StringBuilder sb = new StringBuilder("party: ");
        int n = 0;
        if (zeilen != null) {
            for (String z : zeilen) {
                if (z != null && z.matches("^\\s*\\[\\d+\\] .*\\(\\w+(?: \\w+)?\\)\\s*$")) {
                    if (n++ > 0) sb.append("; ");
                    sb.append(z.strip());
                }
            }
        }
        return n == 0 ? "party: not in the tab list yet" : sb.toString();
    }

    private static void beenden(String grund) {
        fensterSchreiben();
        long sekunden = Duration.ofNanos(System.nanoTime() - laufBeginn).toSeconds();
        StringBuilder sb = new StringBuilder("after ").append(zeitText(sekunden)).append(" (").append(grund).append(") | totals: ");
        for (Zaehler z : Zaehler.values()) sb.append(z.name).append(' ').append(summen[z.ordinal()]).append(", ");
        sb.append("frames ").append(summeBilder);
        zeile("RUN END", sb.toString());
        aktiv = false;
        if (schreiber != null) {
            try {
                schreiber.close();
            } catch (IOException e) {
                ShokiMod.LOGGER.warn("[DungeonLog] cannot close the log file: {}", e.toString());
            }
            schreiber = null;
        }
    }

    private static void fensterSchreiben() {
        long jetzt = System.nanoTime();
        double sekunden = Math.max(0.001, (jetzt - fensterBeginn) / 1e9);
        fensterBeginn = jetzt;

        long[] zaehlerDelta = werte.clone();
        long[] zeitDelta = nanos.clone();
        java.util.Arrays.fill(werte, 0L);
        java.util.Arrays.fill(nanos, 0L);
        int frames = bilder;
        long laengstes = laengstesBild;
        bilder = 0;
        laengstesBild = 0L;
        letztesBild = 0L;
        for (int i = 0; i < zaehlerDelta.length; i++) summen[i] += zaehlerDelta[i];
        summeBilder += frames;

        double fps = frames / sekunden;
        Runtime rt = Runtime.getRuntime();
        long heapMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long gc0 = gcAnzahl;
        long gcMs0 = gcMillis;
        gcLesen();

        String fensterText = fensterText(sekunden, fps, laengstes, zaehlerDelta, zeitDelta, frames, heapMb, gcAnzahl - gc0, gcMillis - gcMs0);
        zeile("WINDOW", fensterText);
        for (String warnung : bewerten(zaehlerDelta, zeitDelta, sekunden, fps, frames > 0, serie, serieZeit)) {
            zeile("WARN", warnung);
        }
    }

    // ------------------------------------------------------------------ Auswertung (reine Rechnung, getestet)

    static String fensterText(double sekunden, double fps, long laengstesBildNanos, long[] zaehlerDelta, long[] zeitDelta, int frames,
                              long heapMb, long gcLaeufe, long gcMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%.1f s: fps %.0f, longest frame %d ms", sekunden, fps, laengstesBildNanos / 1_000_000L));
        sb.append(" | ");
        for (Zaehler z : Zaehler.values()) {
            sb.append(z.name).append(' ').append(zaehlerDelta[z.ordinal()]).append(", ");
        }
        sb.append("| ");
        for (Zeit z : Zeit.values()) {
            double ms = zeitDelta[z.ordinal()] / 1e6;
            sb.append(z.name).append(String.format(Locale.ROOT, " %.1f ms/s", ms / sekunden));
            if (z == Zeit.KARTE_ZEICHNEN && frames > 0) sb.append(String.format(Locale.ROOT, " (%.3f ms/frame)", ms / frames));
            sb.append(", ");
        }
        sb.append(String.format(Locale.ROOT, "| heap %d MB, GC %d runs %d ms", heapMb, gcLaeufe, gcMs));
        return sb.toString();
    }

    /**
     * Welche Zeilen "zu oft" ein Fenster ausloest. {@code serie} und {@code serieZeit} zaehlen, in wie vielen Fenstern hintereinander dieselbe
     * Grenze verletzt wurde (wird hier fortgeschrieben); ab dem zweiten Fenster steht es dabei.
     */
    static List<String> bewerten(long[] zaehlerDelta, long[] zeitDelta, double sekunden, double fps, boolean bilderGezaehlt,
                                 int[] serie, int[] serieZeit) {
        List<String> aus = new ArrayList<>();
        for (Zaehler z : Zaehler.values()) {
            int i = z.ordinal();
            double rate = zaehlerDelta[i] / sekunden;
            if (z.grenze > 0 && rate > z.grenze) {
                serie[i]++;
                aus.add(String.format(Locale.ROOT, "TOO OFTEN: %s %.1f/s (limit %.0f/s)%s - %s", z.name, rate, z.grenze,
                        serie[i] > 1 ? " for " + serie[i] + " windows in a row" : "", z.hinweis));
            } else {
                serie[i] = 0;
            }
        }
        for (Zeit z : Zeit.values()) {
            int i = z.ordinal();
            double msJeSekunde = zeitDelta[i] / 1e6 / sekunden;
            if (msJeSekunde > z.grenzeMs) {
                serieZeit[i]++;
                aus.add(String.format(Locale.ROOT, "TOO OFTEN: %s takes %.1f ms every second (limit %.0f ms)%s - %s", z.name, msJeSekunde,
                        z.grenzeMs, serieZeit[i] > 1 ? " for " + serieZeit[i] + " windows in a row" : "", z.hinweis));
            } else {
                serieZeit[i] = 0;
            }
        }
        if (bilderGezaehlt && fps < FPS_UNTEN) {
            aus.add(String.format(Locale.ROOT, "LOW FPS: %.0f frames per second in this window (below %.0f) - see the counters above for what ran more than it should",
                    fps, FPS_UNTEN));
        }
        return aus;
    }

    static String zeitText(long sekunden) {
        return String.format(Locale.ROOT, "%d:%02d", sekunden / 60, sekunden % 60);
    }

    // ------------------------------------------------------------------ Datei

    private static Path datei() {
        return FabricLoader.getInstance().getGameDir().resolve("logs").resolve("shokimod-dungeon.log");
    }

    private static void zeile(String art, String text) {
        if (schreiber == null) return;
        long sek = Duration.ofNanos(System.nanoTime() - laufBeginn).toSeconds();
        try {
            schreiber.write(LocalTime.now().format(UHR) + " [+" + zeitText(sek) + "] " + art + ": " + text);
            schreiber.newLine();
            schreiber.flush();
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[DungeonLog] cannot write the log file: {}", e.toString());
            schreiber = null;
        }
    }

    private static void gcLesen() {
        long n = 0;
        long ms = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (b.getCollectionCount() > 0) n += b.getCollectionCount();
            if (b.getCollectionTime() > 0) ms += b.getCollectionTime();
        }
        gcAnzahl = n;
        gcMillis = ms;
    }
}
