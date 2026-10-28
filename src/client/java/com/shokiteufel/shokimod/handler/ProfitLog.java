package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DayRecord;
import com.shokiteufel.shokimod.util.ModPaths;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Die abgeschlossenen Tage des Profit-Trackers, als lesbare Textdatei.
 *
 * Bis 2.0.5 standen sie in der Config - jeder Tag mit jeder Ware, bis zu dreissig Tage. Bei einem Spieler mit vielen Waren
 * waren das ein Vielfaches der Zahl, die der Rest der Config ausmacht, und jedes Speichern (alle 15 Sekunden) trug sie mit.
 * Jetzt liegen sie in {@code config/shokimod/profit-days.log}: eine Datei, ein Block je Tag, eine Zeile je Ware, mit
 * Stueckzahl und dem, was sie zu den Preisen des Tages wert waren. Die Config bleibt klein, und wer wissen will, was an einem Tag
 * herauskam, kann es nachlesen - auch ohne die Mod.
 *
 * Gelesen wird die Datei einmal, beim Start im Hintergrund; danach liegt sie im Speicher. Geschrieben wird sie nur, wenn ein Tag
 * endet (und beim Umschreiben der alten Tage), im Hintergrund und in einem Zug: Nebendatei, auf die Platte zwingen, austauschen.
 * Ein Tag im Speicher ist fertig und wird nicht mehr veraendert - Zusammenlegen heisst, einen neuen Satz zu bauen.
 *
 * Tage mit demselben Anfang werden aufaddiert: Endet derselbe Tag zweimal (etwa nach einem Wiederherstellen), steht er nicht
 * doppelt da.
 */
public final class ProfitLog {

    public static final String FILE_NAME = "profit-days.log";
    /** Wie viele Tage die Datei hoechstens fuehrt. Fuer den Tag mit 700 Waren sind das etwa fuenf Megabyte */
    public static final int MAX_DAYS = 120;

    private static final long CHECK_MILLIS = 10_000L;
    private static final long RETRY_MILLIS = 60_000L;
    private static final int SAMPLE = 60;

    private static final String HEADER = """
            # ShokiMod profit log - what the Profit tracker earned on every finished day, newest first.
            # DAY <TAB> date <TAB> start (ms) <TAB> active seconds <TAB> mayor <TAB> note
            # ITEM <TAB> item id <TAB> count <TAB> coins at the prices of that day (empty: no price known then; negative: spent)
            # Plain text on purpose - read it, search it, open it in a spreadsheet. The mod rewrites the whole file when a day ends.
            # Days with the same start are added up when they are read.
            """;

    /** Schuetzt {@link #days} */
    private static final Object LOCK = new Object();
    /** Ein Schreiber, nie zwei zugleich */
    private static final Object FILE_LOCK = new Object();
    /** Neuester zuerst. Die Saetze darin werden nach dem Einfuegen nicht mehr veraendert */
    private static List<DayRecord> days = null;
    private static volatile int version = 0;

    private static final AtomicReference<List<DayRecord>> PENDING = new AtomicReference<>();
    private static volatile long failedAt = 0L;
    private static long lastCheck = 0L;

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ShokiMod profit log writer");
        thread.setDaemon(true);
        return thread;
    });

    static {
        // Beim Beenden darf nichts verloren gehen, was noch wartet
        Runtime.getRuntime().addShutdownHook(new Thread(ProfitLog::flush, "ShokiMod profit log flush"));
    }

    private ProfitLog() {
    }

    public static Path file() {
        return ModPaths.configDir().resolve(FILE_NAME);
    }

    private static Path backup() {
        return ModPaths.configDir().resolve(FILE_NAME + ".bak");
    }

    /** Aendert sich mit jeder Aenderung der Tagesliste - die Fenster merken sich damit, ob ihre Zeilen noch stimmen */
    public static int version() {
        return version;
    }

    /** Die Datei beim Start im Hintergrund lesen, damit das Fenster sie nicht erst dann holen muss */
    public static void preload() {
        Thread thread = new Thread(() -> {
            synchronized (LOCK) {
                ensureLoaded();
            }
        }, "ShokiMod profit log loader");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Die abgeschlossenen Tage, neuester zuerst: das Log, und was noch aus der Zeit vor 2.0.6 in der Config steht.
     *
     * Eine neue Liste, die Saetze darin gehoeren aber dem Log - wer sie zusammenlegen will, kopiert sie.
     */
    public static List<DayRecord> days() {
        List<DayRecord> out;
        synchronized (LOCK) {
            ensureLoaded();
            out = new ArrayList<>(days);
        }
        List<DayRecord> legacy = ModConfig.INSTANCE.profit.history;
        if (legacy != null && !legacy.isEmpty()) {
            out.addAll(legacy);
            out.sort(Comparator.comparingLong((DayRecord day) -> day.start).reversed());
        }
        return out;
    }

    /** Ein abgelaufener Tag kommt ins Log - mit demselben Anfang aufaddiert, sonst neu */
    public static void archive(DayRecord record) {
        if (record == null) return;
        // Die alten Tage zuerst: Sonst stuende der neue im Log, und die Altlast haette keinen Platz mehr in der Reihenfolge
        migrate(true);
        synchronized (LOCK) {
            ensureLoaded();
            insert(days, record.copy());
            trim(days);
            version++;
        }
        scheduleWrite();
    }

    /**
     * Die Tage aus der Config ins Log schreiben - einmal.
     *
     * Von selbst, sobald Preise da sind (siehe {@link #tick}), sonst auf Befehl. Alte Tage kennen keinen Preis von damals;
     * sie werden mit dem von jetzt bewertet und tragen den Vermerk "migrated <Datum>". Aus der Config geloescht wird erst, wenn
     * die Datei auf der Platte liegt und beim Zuruecklesen dasselbe ergibt. Ein Tag, der schon als umgeschrieben im Log
     * steht, wird nicht noch einmal gezaehlt (falls das Speichern der Config nach dem Schreiben scheiterte).
     *
     * @param force auch ohne Preise
     * @return wie viele Tage umgeschrieben wurden; 0 = nichts zu tun, -1 = Preise noch nicht da, -2 = Schreiben gescheitert
     */
    public static int migrate(boolean force) {
        ModConfig.ProfitCategory cfg = ModConfig.INSTANCE.profit;
        synchronized (LOCK) {
            ensureLoaded();
            List<DayRecord> legacy = cfg.history;
            if (legacy == null || legacy.isEmpty()) {
                if (cfg.dayLogVersion < 1) {
                    cfg.dayLogVersion = 1;
                    ModConfig.INSTANCE.saveNow();
                }
                return 0;
            }
            if (!force && !pricesReady(legacy)) return -1;

            String note = "migrated " + LocalDate.now();
            List<DayRecord> merged = new ArrayList<>(days);
            int moved = 0;
            for (DayRecord old : legacy) {
                if (old == null) continue;
                if (alreadyMigrated(merged, old.start)) continue;
                DayRecord copy = new DayRecord(old.start, old.uptimeMillis, old.counts, old.mayor,
                        ProfitTracker.frozenFromNow(old.counts));
                copy.note = note;
                insert(merged, copy);
                moved++;
            }
            trim(merged);

            List<DayRecord> back = writeAndVerify(merged);
            if (back == null) {
                ShokiMod.LOGGER.error("[Profit] could not move {} days into {} - they stay in the config", legacy.size(), FILE_NAME);
                return -2;
            }
            safetyCopyOfConfig();
            days = back;
            version++;
            cfg.history.clear();
            cfg.dayLogVersion = 1;
            ModConfig.INSTANCE.saveNow();
            ShokiMod.LOGGER.info("[Profit] {} days moved from the config into {}", moved, FILE_NAME);
            return moved;
        }
    }

    /** Wird jeden Tick gerufen; arbeitet nur alle zehn Sekunden */
    public static void tick(long now) {
        if (now - lastCheck < CHECK_MILLIS) return;
        lastCheck = now;
        if (ModConfig.INSTANCE.profit.dayLogVersion < 1) migrate(false);
        if (PENDING.get() != null && failedAt > 0L && now - failedAt >= RETRY_MILLIS) {
            failedAt = now;
            WRITER.execute(ProfitLog::writePending);
        }
    }

    // ---- Chat ----

    /** /shoki profitlog */
    public static void sayStatus() {
        int inLog;
        synchronized (LOCK) {
            ensureLoaded();
            inLog = days.size();
        }
        List<DayRecord> legacy = ModConfig.INSTANCE.profit.history;
        int inConfig = legacy == null ? 0 : legacy.size();
        ProfitTracker.say(Component.literal("Profit log: " + inLog + " days in " + file().toAbsolutePath()
                        + (inConfig > 0 ? " - " + inConfig + " older days are still in the config (/shoki profitlog migrate moves them)." : "."))
                .withStyle(ChatFormatting.YELLOW));
    }

    /** /shoki profitlog migrate */
    public static void migrateNow() {
        int moved = migrate(true);
        String text = switch (moved) {
            case 0 -> "Nothing to move - the days are already in the log.";
            case -2 -> "Could not write the log, the days stay in the config. The game log has the reason.";
            default -> "Moved " + moved + " days from the config into the log. Old days are priced as of today.";
        };
        ProfitTracker.say(Component.literal(text).withStyle(moved == -2 ? ChatFormatting.RED : ChatFormatting.YELLOW));
    }

    // ---- Lesen und Schreiben ----

    private static void ensureLoaded() {
        if (days != null) return;
        List<DayRecord> loaded = read(file());
        if (loaded == null) loaded = read(backup());
        days = loaded == null ? new ArrayList<>() : loaded;
        trim(days);
    }

    /** Null, wenn es die Datei nicht gibt oder sie nichts Brauchbares enthaelt, obwohl sie nicht leer ist */
    private static List<DayRecord> read(Path path) {
        try {
            if (!Files.exists(path)) return null;
            String text = Files.readString(path, StandardCharsets.UTF_8);
            int[] skipped = new int[1];
            List<DayRecord> out = parse(text, skipped);
            if (skipped[0] > 0) {
                ShokiMod.LOGGER.warn("[Profit] {} lines of {} could not be read and were skipped", skipped[0], path.getFileName());
            }
            if (out.isEmpty() && skipped[0] > 0) return null;
            return out;
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Profit] could not read {}: {}", path.getFileName(), e.toString());
            return null;
        }
    }

    /** Schreiben und zuruecklesen; die gelesene Liste, wenn sie dasselbe enthaelt, sonst null */
    private static List<DayRecord> writeAndVerify(List<DayRecord> list) {
        if (!writeNow(format(list))) return null;
        try {
            List<DayRecord> back = parse(Files.readString(file(), StandardCharsets.UTF_8), new int[1]);
            return sameContent(list, back) ? back : null;
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Profit] could not read {} back: {}", FILE_NAME, e.toString());
            return null;
        }
    }

    private static void scheduleWrite() {
        List<DayRecord> snapshot;
        synchronized (LOCK) {
            snapshot = List.copyOf(days);
        }
        PENDING.set(snapshot);
        WRITER.execute(ProfitLog::writePending);
    }

    private static void writePending() {
        List<DayRecord> snapshot = PENDING.getAndSet(null);
        if (snapshot == null) return; // ein neuerer Auftrag hat den Stand schon mitgenommen
        if (!writeNow(format(snapshot))) {
            // Der Stand bleibt im Speicher; ein neuerer ersetzt ihn, sonst versucht es tick() in einer Minute noch einmal
            PENDING.compareAndSet(null, snapshot);
            failedAt = System.currentTimeMillis();
        } else {
            failedAt = 0L;
        }
    }

    /** Schreibt, was noch wartet, und kehrt erst zurueck, wenn es auf der Platte liegt - fuer das Beenden und fuer Proben */
    public static void flush() {
        List<DayRecord> snapshot = PENDING.getAndSet(null);
        if (snapshot != null && !writeNow(format(snapshot))) PENDING.compareAndSet(null, snapshot);
    }

    /** Nie direkt in die Datei: erst eine Nebendatei, auf die Platte zwingen, dann in einem Zug austauschen */
    private static boolean writeNow(String text) {
        synchronized (FILE_LOCK) {
            Path target = file();
            Path temp = target.resolveSibling(FILE_NAME + ".tmp");
            try {
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(temp.toFile())) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                if (Files.exists(target) && Files.size(target) > 0) {
                    Files.copy(target, backup(), StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException | RuntimeException e) {
                ShokiMod.LOGGER.error("[Profit] could not write {}: {}", FILE_NAME, e.toString());
                return false;
            }
        }
    }

    /** Eine Kopie der Config, bevor die Tage daraus verschwinden - einmal, und nur, wenn es sie nicht schon gibt */
    private static void safetyCopyOfConfig() {
        try {
            Path config = ModPaths.configDir().resolve("shokimod_config.properties");
            Path copy = ModPaths.configDir().resolve("shokimod_config.properties.before-profit-log");
            if (Files.exists(config) && !Files.exists(copy)) Files.copy(config, copy);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Profit] could not keep a copy of the config: {}", e.toString());
        }
    }

    // ---- Zusammenlegen ----

    /** Mit demselben Anfang aufaddieren, sonst einsortieren (neuester zuerst) */
    private static void insert(List<DayRecord> list, DayRecord record) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).start == record.start) {
                DayRecord merged = list.get(i).copy();
                merged.add(record);
                list.set(i, merged);
                return;
            }
        }
        list.add(record);
        list.sort(Comparator.comparingLong((DayRecord day) -> day.start).reversed());
    }

    private static boolean alreadyMigrated(List<DayRecord> list, long start) {
        for (DayRecord day : list) {
            if (day.start == start && day.note != null && day.note.startsWith("migrated")) return true;
        }
        return false;
    }

    private static void trim(List<DayRecord> list) {
        while (list.size() > MAX_DAYS) list.remove(list.size() - 1);
    }

    /** Stehen fuer ein Drittel der Waren Preise da? Sonst waeren die alten Tage ohne Wert eingefroren */
    private static boolean pricesReady(List<DayRecord> legacy) {
        int sampled = 0;
        int priced = 0;
        for (DayRecord day : legacy) {
            if (day == null) continue;
            for (String id : day.counts.keySet()) {
                sampled++;
                if (ProfitTracker.unitPrice(id) > 0) priced++;
                if (sampled >= SAMPLE) return priced * 3 >= sampled;
            }
        }
        return sampled == 0 || priced * 3 >= sampled;
    }

    // ---- Text ----

    private static String date(long start) {
        return start <= 0L ? "unknown"
                : Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Die ganze Datei als Text: Kopf, dann je Tag ein Block, die wertvollste Ware zuerst */
    static String format(List<DayRecord> list) {
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append(HEADER);
        for (DayRecord day : list) {
            out.append("DAY\t").append(date(day.start)).append('\t').append(day.start).append('\t')
                    .append(day.uptimeMillis / 1000L).append('\t').append(clean(day.mayor)).append('\t')
                    .append(clean(day.note)).append('\n');
            List<Map.Entry<String, Integer>> items = new ArrayList<>(day.counts.entrySet());
            items.sort(Comparator
                    .comparingLong((Map.Entry<String, Integer> e) -> day.coins.getOrDefault(e.getKey(), 0L)).reversed()
                    .thenComparing(Map.Entry::getKey));
            for (Map.Entry<String, Integer> item : items) {
                int count = item.getValue() == null ? 0 : item.getValue();
                Long coins = day.coins.get(item.getKey());
                if (count == 0 && (coins == null || coins == 0L)) continue;
                out.append("ITEM\t").append(clean(item.getKey())).append('\t').append(count).append('\t');
                if (coins != null) out.append(coins);
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** Die Datei zurueck in Saetze, neuester zuerst. Was nicht lesbar ist, wird uebersprungen und gezaehlt, nie geworfen */
    static List<DayRecord> parse(String text, int[] skipped) {
        List<DayRecord> raw = new ArrayList<>();
        DayRecord current = null;
        for (String line : text.split("\r?\n")) {
            if (line.isBlank() || line.charAt(0) == '#') continue;
            String[] fields = line.split("\t", -1);
            try {
                if (fields[0].equals("DAY") && fields.length >= 4) {
                    current = new DayRecord();
                    current.start = Long.parseLong(fields[2].trim());
                    current.uptimeMillis = Long.parseLong(fields[3].trim()) * 1000L;
                    current.mayor = fields.length > 4 ? fields[4] : "";
                    current.note = fields.length > 5 ? fields[5] : "";
                    raw.add(current);
                } else if (fields[0].equals("ITEM") && fields.length >= 3 && current != null) {
                    String id = fields[1].trim();
                    if (id.isEmpty()) throw new NumberFormatException("empty item id");
                    current.counts.merge(id, Integer.parseInt(fields[2].trim()), Integer::sum);
                    if (fields.length > 3 && !fields[3].isBlank()) {
                        current.coins.merge(id, Long.parseLong(fields[3].trim()), Long::sum);
                    }
                } else if (skipped != null) {
                    skipped[0]++;
                }
            } catch (NumberFormatException e) {
                if (skipped != null) skipped[0]++;
            }
        }

        // Derselbe Anfang zweimal in der Datei (von Hand bearbeitet?): aufaddieren
        Map<Long, DayRecord> byStart = new HashMap<>();
        for (DayRecord day : raw) {
            DayRecord first = byStart.get(day.start);
            if (first == null) byStart.put(day.start, day);
            else first.add(day);
        }
        List<DayRecord> out = new ArrayList<>(byStart.values());
        out.sort(Comparator.comparingLong((DayRecord day) -> day.start).reversed());
        return out;
    }

    /** Dieselben Tage mit denselben Summen? Die Zeit zaehlt in Sekunden, so wie sie in der Datei steht */
    static boolean sameContent(List<DayRecord> a, List<DayRecord> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            DayRecord x = a.get(i);
            DayRecord y = b.get(i);
            if (x.start != y.start || x.uptimeMillis / 1000L != y.uptimeMillis / 1000L) return false;
            if (sum(x.counts) != sum(y.counts) || sumCoins(x) != sumCoins(y)) return false;
            if (nonZero(x) != nonZero(y)) return false;
        }
        return true;
    }

    private static long sum(Map<String, Integer> counts) {
        long total = 0;
        for (int value : counts.values()) total += value;
        return total;
    }

    private static long sumCoins(DayRecord day) {
        long total = 0;
        for (long value : day.coins.values()) total += value;
        return total;
    }

    private static int nonZero(DayRecord day) {
        int n = 0;
        for (Map.Entry<String, Integer> entry : day.counts.entrySet()) {
            Long coins = day.coins.get(entry.getKey());
            if (entry.getValue() != 0 || (coins != null && coins != 0L)) n++;
        }
        return n;
    }
}
