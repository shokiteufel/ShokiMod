package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ForgeCategory;
import com.shokiteufel.shokimod.data.ModConfig.ForgeSlot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merkt sich, was in der Forge steckt und wann es fertig ist.
 *
 * Die Zeit steht nur in den Dwarven Mines in der Tab-Liste (Widget "Forges", siehe {@link ForgeTab}) - der Kasten soll sie aber auf
 * jeder Map zeigen. Deshalb wird jeder gelesene Platz mit dem Zeitpunkt seines Endes gespeichert: Wer ein Stueck hineinlegt und die
 * Minen verlaesst, sieht es weiterlaufen, auch nach einem Neustart des Spiels. Gelesen wird nur, wenn sich die Tab-Liste aendert
 * (ein Aufruf je Aenderung, kein Takt), geschrieben nur, wenn sich an einem Platz wirklich etwas aendert.
 *
 * Grenzen: Die Zeit auf anderen Maps ist eine Rechnung, keine Messung - ein Stueck, das man in den Minen holt, steht erst dann wieder
 * als leer da, wenn die Tab-Liste es dort zeigt. Und die Forge gehoert zum Profil: Wer das Profil wechselt, bekommt die Plaetze des
 * anderen, bis er in den Minen war (dafuer der Knopf "Forget saved times").
 */
public final class ForgeTracker {

    public static final String EMPTY = "EMPTY";
    public static final String LOCKED = "LOCKED";
    public static final String BUSY = "BUSY";

    /** Ein Stueck, das fertig ist, bleibt im Kasten "nur kurz vor Ende" noch so lange stehen (und gilt so lange als Meldung wert) */
    public static final long READY_STAYS_MS = 10 * 60_000L;
    /** Liegt das neue Ende weiter als das hinter dem alten, ist ein neues Stueck eingelegt worden (keine Schaetzung wandert so weit) */
    private static final long NEW_PIECE_MS = 2 * 60_000L;

    /**
     * Ein Platz, wie ihn der Kasten zeigt. {@code remainingMs} gilt nur, solange er laeuft, {@code readySinceMs} nur, wenn er fertig ist:
     * wie lange das schon so ist.
     */
    public record Row(int slot, String item, boolean empty, boolean ready, long remainingMs, long readySinceMs) {
    }

    /** Was die Forge-Warnung melden soll: ein Platz ist gleich fertig oder ist fertig */
    public enum Meldeart { GLEICH, FERTIG }

    /** Eine faellige Meldung. {@code restMs} gilt bei GLEICH */
    public record Meldung(int slot, String item, Meldeart art, long restMs) {
    }

    /** Unverstandene Zeilen stehen einmal im Log, nicht bei jeder Aenderung der Tab-Liste */
    private static final Set<String> reported = new HashSet<>();
    private static boolean sampleLogged = false;

    /** Wie gespeichert wird - ein Haken, damit sich der Tracker ohne Platte pruefen laesst (die Config schreibt ueber den Fabric-Ordner) */
    static Runnable speichern = () -> ModConfig.INSTANCE.saveNow();

    // ------------------------------------------------------------------ Tempo der Anzeige

    /**
     * Solange der Tab das Forge-Widget zeigt (zuletzt vor hoechstens so vielen Millisekunden bestaetigt - durch eine Aenderung der Liste oder
     * durch {@link #lebenszeichen}), rechnet der Kasten mit dem gemessenen Tempo; danach in Echtzeit. {@link #widgetWeg} beendet es sofort.
     */
    static final long LEBEND_MS = 8_000L;
    /** Das Tempo, mit dem die Anzeige zaehlt, bleibt zwischen diesen Grenzen (Forge-Sekunden je echte Sekunde) */
    private static final double TEMPO_MIN = 0.5;
    private static final double TEMPO_MAX = 6.0;
    /** Zwei Wechsel so dicht beieinander sagen nichts: der Tab liefert die Zeilen der Plaetze im Abstand von etwa drei Sekunden */
    private static final long WECHSEL_MIN_MS = 3_000L;

    /** Der laufende Stand eines Platzes aus dem Tab: was er zuletzt nannte, und ab wann die Restzeit von dort aus gerechnet wird */
    private static final class Lauf {
        final String item;
        long angezeigt;
        long ankerAt;
        long ankerRest;
        long wechselAt;
        boolean wechselBekannt;

        Lauf(String item, long angezeigt, long now, long ankerRest) {
            this.item = item;
            this.angezeigt = angezeigt;
            this.ankerAt = now;
            this.ankerRest = ankerRest;
            this.wechselAt = now;
        }
    }

    private static final Lauf[] laeufe = new Lauf[ForgeCategory.MAX_SLOTS];
    private static double tempo = 1.0;
    private static boolean tempoGemessen;
    private static boolean gesehen;
    private static long zuletztGesehen;

    /**
     * Was der Kasten zuletzt als Restzeit eines Platzes gezeigt hat und wann (-1: nichts). Wechselt das Tempo der Anzeige (das Farmen faengt
     * wieder an), liegt die Rechnung einen Wechsel lang zu hoch, und die Korrektur waere ein Sprung von bis zu einer Minute. Statt dessen
     * laeuft die Anzeige dann um {@link #GLEIT_PLUS} Sekunden je Sekunde schneller, bis sie den Wert wieder eingeholt hat.
     */
    private static final long[] sichtbar = newMinusEins();
    private static final long[] sichtbarAt = new long[ForgeCategory.MAX_SLOTS];
    private static final double GLEIT_PLUS = 4.0;

    private static long[] newMinusEins() {
        long[] a = new long[ForgeCategory.MAX_SLOTS];
        java.util.Arrays.fill(a, -1L);
        return a;
    }

    /** Die Restzeit so, wie der Kasten sie zeigt: nie schneller nach unten als {@code Tempo + GLEIT_PLUS}, nach oben (neues Stueck) sofort */
    private static long gleiten(int index, long ziel, long now) {
        long vorher = sichtbar[index];
        long out = ziel;
        if (vorher >= 0L && ziel < vorher && lebendig(now)) {
            long frei = vorher - (long) ((tempo + GLEIT_PLUS) * Math.max(0L, now - sichtbarAt[index]));
            out = Math.max(ziel, frei);
        }
        sichtbar[index] = out;
        sichtbarAt[index] = now;
        return out;
    }

    private static boolean lebendig(long now) {
        return gesehen && now - zuletztGesehen <= LEBEND_MS;
    }

    /** Das Tempo, mit dem die Anzeige gerade zaehlt: Forge-Sekunden je echte Sekunde (1 = Echtzeit) */
    static double tempo() {
        return tempo;
    }

    private static void messen(long gefallen, long dauer, long now) {
        if (dauer < WECHSEL_MIN_MS || gefallen <= 0L) return;
        double r = Math.max(TEMPO_MIN, Math.min(TEMPO_MAX, (double) gefallen / dauer));
        // Ein neues Tempo gilt ab jetzt, nicht rueckwirkend: erst alle Plaetze auf ihren jetzigen Stand verankern, sonst springt die Anzeige
        for (Lauf x : laeufe) {
            if (x == null) continue;
            x.ankerRest = hochgerechnet(x, now);
            x.ankerAt = now;
        }
        tempo = tempoGemessen ? 0.5 * tempo + 0.5 * r : r;
        tempoGemessen = true;
    }

    /** Die Restzeit eines Laufs zu einem Zeitpunkt: vom Anker aus mit dem Tempo heruntergezaehlt, innerhalb dessen, was die Anzeige zulaesst */
    private static long hochgerechnet(Lauf l, long at) {
        double rest = l.ankerRest - tempo * (at - l.ankerAt);
        long[] grenzen = ForgeTab.spanne(l.angezeigt);
        return Math.max(grenzen[0], Math.min(grenzen[1], (long) rest));
    }

    /**
     * Die Restzeit eines Platzes. Zeigt der Tab das Widget, zaehlt sie vom letzten Anker aus mit dem gemessenen Tempo herunter und bleibt
     * dabei in der Spanne, die die Anzeige zulaesst (bei "56m": zwischen 55m und 56m) - so springt sie bei einem Wechsel nicht; sonst ist es
     * das gemerkte Ende in Echtzeit.
     */
    private static long restMs(int index, long now) {
        ForgeSlot slot = data().get(index);
        Lauf l = laeufe[index];
        if (l == null || !lebendig(now) || !BUSY.equals(slot.state)) return slot.finishAt - now;
        return hochgerechnet(l, now);
    }

    /**
     * Der Tab zeigt das Widget nicht mehr (anderer Ort): Was zuletzt galt, wird zum Ende in Echtzeit. Das Tempo war das der Anzeige in den Minen
     * (Farmen verkuerzt die Zeit), ausserhalb zaehlt sie in Echtzeit weiter.
     */
    private static void abschliessen() {
        List<ForgeSlot> stored = data();
        for (int i = 0; i < laeufe.length; i++) {
            Lauf l = laeufe[i];
            laeufe[i] = null;
            ForgeSlot slot = stored.get(i);
            if (l == null || !BUSY.equals(slot.state) || !slot.item.equals(l.item)) continue;
            slot.finishAt = zuletztGesehen + hochgerechnet(l, zuletztGesehen);
        }
        tempo = 1.0;
        tempoGemessen = false;
        gesehen = false;
        speichern.run();
    }

    private ForgeTracker() {
    }

    private static ForgeCategory cfg() {
        return ModConfig.INSTANCE.mining.forge;
    }

    /** Die sieben Plaetze, immer vollzaehlig - auch bei einer frisch angelegten Config, die load() noch nicht aufgefuellt hat */
    private static List<ForgeSlot> data() {
        List<ForgeSlot> list = cfg().slotData;
        if (list == null) {
            list = new ArrayList<>();
            cfg().slotData = list;
        }
        while (list.size() < ForgeCategory.MAX_SLOTS) list.add(new ForgeSlot());
        return list;
    }

    // ------------------------------------------------------------------ Lesen

    /** Die Tab-Liste hat sich geaendert: Steht das Forge-Widget darin, werden die Plaetze gemerkt */
    public static void processTabList(List<String> lines) {
        List<ForgeTab.Slot> slots = ForgeTab.parse(lines);
        if (slots.isEmpty()) {
            widgetWeg();
            return;
        }
        processSlots(slots, System.currentTimeMillis());
    }

    /**
     * Die Tab-Liste hat sich geaendert, und das Forge-Widget steht nicht mehr darin (anderer Ort): Das Tempo der Anzeige ist vorbei, was zuletzt
     * galt, laeuft in Echtzeit weiter.
     */
    static void widgetWeg() {
        if (gesehen) abschliessen();
    }

    /**
     * Jeder Tick, an dem die Tab-Liste gelesen wurde und sich NICHT geaendert hat: das Widget steht also noch so da wie zuletzt. Ohne das waere
     * eine Anzeige, die sich eine halbe Minute nicht aendert (in Ruhe wechselt sie nur jede Minute), von einem verschwundenen Widget nicht zu
     * unterscheiden. Bleibt das Zeichen aus (Verbindung weg, Spiel haengt), gilt das Widget nach {@link #LEBEND_MS} als weg.
     */
    public static void lebenszeichen() {
        lebenszeichen(System.currentTimeMillis());
    }

    static void lebenszeichen(long now) {
        if (gesehen && now - zuletztGesehen <= LEBEND_MS) zuletztGesehen = now;
    }

    /** Die gelesenen Plaetze in den Speicher uebertragen - getrennt vom Lesen, damit es sich mit eigener Uhr pruefen laesst */
    static void processSlots(List<ForgeTab.Slot> slots, long now) {
        if (!sampleLogged) {
            sampleLogged = true;
            StringBuilder sample = new StringBuilder();
            for (ForgeTab.Slot s : slots) sample.append(sample.length() == 0 ? "" : " | ").append(s.number()).append(") ").append(s.raw());
            ShokiMod.LOGGER.info("[Forge] tab list shows {} slots: {}", slots.size(), sample);
        }

        // War das Widget zwischendurch weg (anderer Ort), ist das letzte Tempo vorbei: erst die alten Laeufe abschliessen
        if (gesehen && now - zuletztGesehen > LEBEND_MS) abschliessen();
        zuletztGesehen = now;
        gesehen = true;

        List<ForgeSlot> stored = data();
        boolean changed = false;
        for (ForgeTab.Slot read : slots) {
            ForgeSlot slot = stored.get(read.number() - 1);
            if (read.kind() != ForgeTab.Kind.RUNNING) {
                laeufe[read.number() - 1] = null;
                sichtbar[read.number() - 1] = -1L;
            }
            changed |= switch (read.kind()) {
                case LOCKED -> set(slot, read.number(), LOCKED, "", 0L);
                case EMPTY -> set(slot, read.number(), EMPTY, "", 0L);
                case READY -> {
                    // Fertig: Hatten wir schon ein Ende fuer dasselbe Stueck, das frueher lag, bleibt es; sonst gilt jetzt
                    boolean same = BUSY.equals(slot.state) && slot.item.equals(read.item()) && slot.finishAt > 0L;
                    boolean moved = set(slot, read.number(), BUSY, read.item(), same ? Math.min(slot.finishAt, now) : now);
                    // Ein Stueck, das wir nicht haben laufen sehen, steht fertig in der Tab-Liste: Wer dort steht, sieht es - keine Meldung
                    // dazu. Lief es dagegen (same), meldet die Warnung das Ende, falls es nicht schon gemeldet ist.
                    if (!same && (!slot.announced || !slot.warned)) {
                        slot.announced = true;
                        slot.warned = true;
                        moved = true;
                    }
                    yield moved;
                }
                case RUNNING -> laeuft(slot, read, now);
                case UNKNOWN -> {
                    // Hoechstens fuenfzig verschiedene Zeilen merken: Aendert sich eine unverstandene Zeile laufend, wird es sonst unendlich
                    if (reported.size() < 50 && reported.add(read.raw())) {
                        ShokiMod.LOGGER.warn("[Forge] slot {} not understood: '{}' - please send logs/latest.log", read.number(), read.raw());
                    }
                    yield false;
                }
            };
        }
        if (changed) speichern.run();
    }

    /**
     * Ein laufender Platz aus dem Tab. Erstmals gesehen (oder ein neues Stueck, oder die Zeit ist gestiegen): die erste Schaetzung setzt den
     * Anker. Wechselt die Anzeige auf einen kleineren Wert, ist die Restzeit in diesem Augenblick genau bekannt (der neue Anker), und aus dem
     * Abstand zum letzten Wechsel folgt das Tempo. Gleiche Anzeige: nichts zu tun, der Kasten rechnet zwischen den Ankern selbst.
     *
     * @return ob etwas zu speichern ist
     */
    private static boolean laeuft(ForgeSlot slot, ForgeTab.Slot read, long now) {
        int index = read.number() - 1;
        long angezeigt = read.shownMs();
        boolean gleich = BUSY.equals(slot.state) && slot.item.equals(read.item());
        Lauf l = laeufe[index];
        if (!gleich || l == null || !l.item.equals(read.item()) || angezeigt > l.angezeigt) {
            long vorher = gleich ? restMs(index, now) : 0L;
            long rest = ForgeTab.erstSchaetzung(vorher, angezeigt);
            laeufe[index] = new Lauf(read.item(), angezeigt, now, rest);
            sichtbar[index] = -1L;
            return set(slot, read.number(), BUSY, read.item(), now + rest);
        }
        if (angezeigt < l.angezeigt) {
            if (l.wechselBekannt) messen(l.angezeigt - angezeigt, now - l.wechselAt, now);
            long vorher = l.angezeigt;
            l.angezeigt = angezeigt;
            l.ankerAt = now;
            l.ankerRest = ForgeTab.nachWechsel(vorher, angezeigt);
            l.wechselAt = now;
            l.wechselBekannt = true;
            return set(slot, read.number(), BUSY, read.item(), now + l.ankerRest);
        }
        return false;
    }

    /** Einen Platz setzen. Gibt zurueck, ob sich etwas geaendert hat - ein Ende, das nur um eine Sekunde abweicht, zaehlt nicht */
    private static boolean set(ForgeSlot slot, int number, String state, String item, long finishAt) {
        boolean same = state.equals(slot.state) && item.equals(slot.item) && Math.abs(finishAt - slot.finishAt) <= 1000L;
        if (same) return false;
        boolean wasBusy = BUSY.equals(slot.state);
        // Ein neues Stueck (anderer Gegenstand, vorher nicht belegt, oder ein Ende weit hinter dem alten) darf wieder melden
        boolean newPiece = BUSY.equals(state) && (!wasBusy || !item.equals(slot.item) || finishAt > slot.finishAt + NEW_PIECE_MS);
        if (newPiece || !BUSY.equals(state)) {
            slot.warned = false;
            slot.announced = false;
        }
        boolean neu = !state.equals(slot.state) || !item.equals(slot.item);
        slot.state = state;
        slot.item = item;
        slot.finishAt = finishAt;
        // Ins Log nur, was neu ist: Bei jedem Wechsel der Anzeige (alle halbe Minute je Platz) wuerde es sonst volllaufen
        if (neu) {
            ShokiMod.LOGGER.info("[Forge] slot {}: {}{}", number, state, BUSY.equals(state)
                    ? " " + item + ", ready at " + java.time.Instant.ofEpochMilli(finishAt) : (wasBusy ? " (was busy)" : ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ Zeigen

    /**
     * Die Plaetze fuer den Kasten: Platz 1 bis {@code slots}, gesperrte und noch nie gesehene bleiben weg.
     * Ein Stueck, dessen Ende vorbei ist, steht als fertig da - ob es schon geholt wurde, weiss die Mod erst, wenn die Tab-Liste es wieder zeigt.
     */
    public static List<Row> rows(long now) {
        if (gesehen && !lebendig(now)) abschliessen();
        List<Row> out = new ArrayList<>();
        List<ForgeSlot> stored = data();
        int shown = Math.max(1, Math.min(ForgeCategory.MAX_SLOTS, cfg().slots));
        for (int i = 0; i < shown && i < stored.size(); i++) {
            ForgeSlot slot = stored.get(i);
            if (EMPTY.equals(slot.state)) {
                out.add(new Row(i + 1, "", true, false, 0L, 0L));
            } else if (BUSY.equals(slot.state)) {
                long rest = gleiten(i, restMs(i, now), now);
                out.add(new Row(i + 1, slot.item, false, rest <= 0L, Math.max(0L, rest), Math.max(0L, -rest)));
            }
        }
        return out;
    }

    /** Die Minuten des Fensters "kurz vor Ende", auf 1 bis 5 zurechtgerueckt */
    static long shortlyMillis() {
        return Math.max(1, Math.min(ForgeCategory.MAX_SHORTLY_MINUTES, cfg().shortlyMinutes)) * 60_000L;
    }

    /**
     * Die Plaetze, die der Kasten wirklich zeigt. Mit "Only shortly before" nur die belegten, die in den letzten Minuten laufen oder noch
     * nicht lange fertig sind - sonst alle aus {@link #rows}. Leer heisst: der Kasten bleibt weg.
     */
    public static List<Row> panelRows(long now) {
        List<Row> all = rows(now);
        if (!cfg().onlyShortly) return all;
        long window = shortlyMillis();
        List<Row> out = new ArrayList<>();
        for (Row row : all) {
            if (row.empty()) continue;
            if (row.ready() ? row.readySinceMs() <= READY_STAYS_MS : row.remainingMs() <= window) out.add(row);
        }
        return out;
    }

    /** Gibt es etwas zu zeigen? Sonst bleibt der Kasten weg - wer die Forge nie benutzt hat, sieht nie einen leeren */
    public static boolean hasRows(long now) {
        return !panelRows(now).isEmpty();
    }

    // ------------------------------------------------------------------ Melden

    /**
     * Was jetzt zu melden ist, und wird als gemeldet vermerkt: Je Stueck kommt hoechstens "gleich fertig" (so viele Minuten vorher, wie
     * eingestellt, 0 = keine Vorwarnung) und einmal "fertig". Ein Stueck, das schon vor dem Fenster {@link #READY_STAYS_MS} fertig war
     * (Neustart nach langer Pause, Warnung eben erst eingeschaltet), meldet nichts mehr - sonst kaeme beim Start eine Meldung zu Dingen von gestern.
     *
     * Die Meldungen stehen mit im Platz (warned/announced) und ueberleben so einen Neustart. Wird nur nach Aenderungen gespeichert.
     */
    public static List<Meldung> faellige(long now) {
        if (gesehen && !lebendig(now)) abschliessen();
        List<Meldung> out = new ArrayList<>();
        ForgeCategory c = cfg();
        long before = Math.max(0, Math.min(ForgeCategory.MAX_SHORTLY_MINUTES, c.alertBeforeMinutes)) * 60_000L;
        boolean changed = false;
        List<ForgeSlot> stored = data();
        for (int i = 0; i < stored.size(); i++) {
            ForgeSlot slot = stored.get(i);
            if (!BUSY.equals(slot.state) || slot.finishAt <= 0L) continue;
            long rest = restMs(i, now);
            if (rest <= 0L) {
                if (slot.announced) continue;
                slot.announced = true;
                slot.warned = true;
                changed = true;
                if (c.alertWhenReady && -rest <= READY_STAYS_MS) out.add(new Meldung(i + 1, slot.item, Meldeart.FERTIG, 0L));
            } else if (before > 0L && rest <= before && !slot.warned) {
                slot.warned = true;
                changed = true;
                out.add(new Meldung(i + 1, slot.item, Meldeart.GLEICH, rest));
            }
        }
        if (changed) speichern.run();
        return out;
    }

    // ------------------------------------------------------------------ Vergessen

    /** Alle gemerkten Plaetze loeschen - der Knopf "Forget saved times" */
    public static void forget() {
        for (ForgeSlot slot : data()) {
            slot.state = "";
            slot.item = "";
            slot.finishAt = 0L;
            slot.warned = false;
            slot.announced = false;
        }
        reported.clear();
        sampleLogged = false;
        java.util.Arrays.fill(laeufe, null);
        java.util.Arrays.fill(sichtbar, -1L);
        tempo = 1.0;
        tempoGemessen = false;
        gesehen = false;
        speichern.run();
        ShokiMod.LOGGER.info("[Forge] saved times forgotten");
    }
}
