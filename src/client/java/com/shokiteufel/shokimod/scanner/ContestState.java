package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Der gemerkte Stand des laufenden Contests.
 *
 * Die Tab-Liste fuehrt den Contest nur dort, wo er stattfindet - anderswo faellt sie
 * weg und mit ihr die Zahlen. Gemerkt wird deshalb der zuletzt gesehene Stand, und
 * zwar in der Config: so ueberlebt er auch einen Neustart mitten im Contest.
 *
 * Zurueckgesetzt wird beim Tageswechsel, denn dann faengt der Contest von vorn an.
 */
public final class ContestState {

    /** So lange vor Schluss wird gewarnt */
    private static final long WARN_SECONDS = 60;
    /** Die halbe Minute Pause zwischen zwei Contests */
    private static final long PAUSE_SECONDS = 30;
    /** Ein SkyBlock-Tag in echten Millisekunden */
    private static final long DAY_MILLIS = 20 * 60 * 1000L;

    private ContestState() {
    }

    private static ModConfig.SafariCategory cfg() {
        return ModConfig.INSTANCE.safari;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    // ---- gemerkter Stand ----

    /** Wer den Contest ausrichtet. Leer, solange noch nie einer gesehen wurde */
    public static String host() {
        return cfg().contestHost == null ? "" : cfg().contestHost;
    }

    /** Das erreichte Bracket. Leer, solange man in keinem ist */
    public static String bracket() {
        return cfg().contestBracket == null ? "" : cfg().contestBracket;
    }

    /** Die eigene Menge. Nie negativ und nie unbekannt - ohne Wissen steht hier 0 */
    public static int amount() {
        return Math.max(0, cfg().contestAmount);
    }

    public static String next() {
        return cfg().contestNext == null ? "" : cfg().contestNext;
    }

    /**
     * Restsekunden bis zum naechsten Wechsel, sekundengenau.
     *
     * Waehrend der Laufzeit bis zum Ende, waehrend der Pause bis zum naechsten Start.
     * Beide Quellen springen - die SkyBlock-Uhr in 10-Minuten-Schritten, was 8,3 echten
     * Sekunden entspricht. Angezeigt wird deshalb nicht die Quelle, sondern der zuletzt
     * gelesene Wert, mit der echten Uhr heruntergezaehlt; bei jedem Sprung wird neu
     * aufgesetzt.
     *
     * Laeuft die Zeit ab, bevor die Quelle nachzieht, wird hier selbst auf die Pause
     * umgeschaltet. Sonst stuende die Anzeige bis zu acht Sekunden auf 0:00.
     */
    public static long secondsRemaining() {
        if (cfg().contestSecondsLeft < 0) return -1;

        long elapsed = elapsed();
        long left = cfg().contestSecondsLeft - elapsed;
        if (!cfg().contestRunning) return Math.max(0, left);
        if (left > 0) return left;

        // Der Contest ist gerade abgelaufen, jetzt kommt die halbe Minute Pause
        return Math.max(0, cfg().contestSecondsLeft + PAUSE_SECONDS - elapsed);
    }

    /** Laeuft gerade einer, oder ist die Pause dazwischen? */
    public static boolean running() {
        if (cfg().contestSecondsLeft < 0) return false;
        if (!cfg().contestRunning) return false;
        return cfg().contestSecondsLeft - elapsed() > 0;
    }

    /** Restzeit als "m:ss" */
    public static String remaining() {
        long seconds = Math.max(0, secondsRemaining());
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static long elapsed() {
        return (System.currentTimeMillis() - cfg().contestSecondsAt) / 1000L;
    }

    /** Wie viel bis zum naechsten Bracket fehlt, aus der Schwelle abzueglich der Menge */
    public static int needed() {
        int threshold = nextThreshold();
        return threshold <= 0 ? 0 : Math.max(0, threshold - amount());
    }

    /**
     * Die gelernte Schwelle des naechsten Brackets.
     *
     * Hypixel nennt sie nicht direkt, aber die Tab-Liste verraet sie: steht dort
     * "COMMON with 205" und "Uncommon requires +45", liegt Uncommon bei 250. Einmal
     * gesehen, gilt sie den ganzen Contest ueber - auch wenn die Tab-Liste weg ist.
     */
    public static int nextThreshold() {
        return Math.max(0, cfg().contestNextThreshold);
    }

    // ---- Fortschreibung ----

    private static void tick() {
        SkyblockClock.tick();
        // Die Seitenleiste fuehrt die Restzeit; die Tab-Liste tut das nicht
        TabContest.processSidebar(ScoreboardUtils.getSidebarLines(Minecraft.getInstance()));
        observe();

        // Der Tageswechsel haengt am SkyBlock-Datum, nicht an der Uhr der Minecraft-Welt:
        // die beiden laufen nicht synchron
        String date = SkyblockClock.date();
        if (date.isEmpty()) return;

        // Stimmt das gemerkte Datum nicht mehr, ist ein Tag vergangen - egal ob wir
        // dabei zugesehen haben oder Minecraft zu war.
        //
        // Der Datumsvergleich allein reicht aber nicht: die Namen wiederholen sich alle
        // 372 SkyBlock-Tage, also alle 5,2 echten Tage. Waere Minecraft so lange zu,
        // passte "Winter 7th" wieder und der alte Stand ueberlebte. Deshalb gilt ein
        // Stand, der aelter als ein SkyBlock-Tag ist, ohnehin als abgelaufen.
        boolean stale = cfg().contestSecondsAt > 0
                && System.currentTimeMillis() - cfg().contestSecondsAt > DAY_MILLIS;

        if (stale || !date.equals(cfg().contestDate)) {
            startNewDay(date);
            return;
        }

        anchorTime();

        warnIfDue(date);
    }

    /** Neuer Tag heisst neuer Contest: die Zahlen von gestern gelten nicht mehr */
    private static void startNewDay(String date) {
        cfg().contestDate = date;
        cfg().contestAmount = 0;
        cfg().contestBracket = "";
        cfg().contestNext = "";
        cfg().contestNextThreshold = 0;
        cfg().contestWarnedDate = "";
        cfg().contestSecondsLeft = -1;
        cfg().contestSecondsAt = 0L;
        cfg().contestRunning = true;
        ModConfig.INSTANCE.saveNow();
    }

    /**
     * Setzt die Uhr neu auf, sobald die Quelle etwas anderes sagt.
     *
     * Nur beim Sprung, nicht bei jedem Tick: sonst wuerde der Zeitpunkt staendig
     * nachgezogen und die Zahl bliebe stehen, statt herunterzuzaehlen.
     */
    private static void anchorTime() {
        long stated = ContestTimer.statedSeconds();
        long value;
        boolean isRunning;

        if (stated >= 0) {
            // Steht der Contest in der Seitenleiste, laeuft er auch
            value = stated;
            isRunning = true;
        } else {
            long toDayEnd = SkyblockClock.secondsToDayEnd();
            if (toDayEnd < 0) return;

            // Der Contest endet eine halbe Minute vor dem Tageswechsel. Darunter
            // laeuft die Pause, und dann zaehlt die Uhr bis zum Tagesende selbst
            isRunning = toDayEnd > PAUSE_SECONDS;
            value = isRunning ? toDayEnd - PAUSE_SECONDS : toDayEnd;
        }

        if (value == cfg().contestSecondsLeft && isRunning == cfg().contestRunning) return;

        cfg().contestSecondsLeft = (int) value;
        cfg().contestRunning = isRunning;
        cfg().contestSecondsAt = System.currentTimeMillis();
    }

    /**
     * Der Ton eine Minute vor Schluss.
     *
     * Einmal je Contest: das Datum, an dem gewarnt wurde, wird vermerkt - sonst kaeme
     * der Ton in jedem Tick der letzten Minute.
     */
    private static void warnIfDue(String date) {
        if (!cfg().contestWarning) return;
        if (date.equals(cfg().contestWarnedDate)) return;
        if (!running()) return;

        long left = secondsRemaining();
        if (left <= 0 || left > WARN_SECONDS) return;

        cfg().contestWarnedDate = date;
        ModConfig.INSTANCE.saveNow();

        String file = cfg().contestWarningSound;
        if (file != null && !file.isBlank()) {
            CustomSoundPlayer.play(file, 1.0f, ContestState.class);
        }
    }

    /**
     * Uebernimmt, was die Tab-Liste gerade hergibt. Fehlendes bleibt stehen.
     *
     * Die Punkte stehen nur im Canyon in der Liste. Ausserhalb greift diese Methode
     * nicht, der gemerkte Stand bleibt also unveraendert stehen - er soll ja nur dort
     * steigen, wo man auch sammelt.
     */
    public static void observe() {
        if (!TabContest.isActive()) return;

        boolean changed = false;
        if (!TabContest.host().equals(host())) {
            cfg().contestHost = TabContest.host();
            changed = true;
        }

        int seen = parse(TabContest.amount());
        if (seen >= 0 && seen != cfg().contestAmount) {
            cfg().contestAmount = seen;
            changed = true;
        }

        if (!TabContest.bracket().isEmpty() && !TabContest.bracket().equals(bracket())) {
            cfg().contestBracket = TabContest.bracket();
            changed = true;
        }

        if (!TabContest.next().isEmpty()) {
            int needed = parse(TabContest.needed());
            // Die Schwelle steht fest, der Abstand dorthin nicht - also die Schwelle merken
            int threshold = seen >= 0 && needed >= 0 ? seen + needed : -1;
            if (!TabContest.next().equals(next()) || (threshold >= 0 && threshold != nextThreshold())) {
                cfg().contestNext = TabContest.next();
                if (threshold >= 0) cfg().contestNextThreshold = threshold;
                changed = true;
            }
        }

        if (changed) ModConfig.INSTANCE.saveNow();
    }

    /** Hypixel schreibt Tausender mit Trennzeichen */
    private static int parse(String text) {
        if (text == null || text.isBlank()) return -1;
        try {
            return Integer.parseInt(text.replace(",", "").replace(".", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
