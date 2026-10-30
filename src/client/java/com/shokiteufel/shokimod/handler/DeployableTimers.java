package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployableHudCategory;
import com.shokiteufel.shokimod.handler.DeployableCountdown.Reading;
import com.shokiteufel.shokimod.handler.DeployableReminder.Ending;
import com.shokiteufel.shokimod.handler.DeployableReminder.Kind;
import com.shokiteufel.shokimod.util.DeployableLog;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Die Restzeit der Deployables in der Naehe, fuer den Zeitkasten - und zwar so, dass sie gleichmaessig herunterlaeuft.
 *
 * <p>Hypixel schreibt die Zeit in den Namen des Ruestungsstaenders ("Plasmaflux 28s") und aendert den Namen nicht jede Sekunde:
 * Wer die Zahl so anzeigt, wie sie ankommt, sieht sie in Spruengen von drei, vier Sekunden springen. Hier wird die Zahl nur als
 * Messwert genommen: Jeder neue Wert legt fest, wann das Deployable endet, und die Anzeige rechnet zwischen den Messwerten selbst
 * herunter, nach der Uhr. Jeden Tick wird nachgesehen, ob der Staender eine neue Zahl traegt; die Suche nach neuen Staendern
 * (DeployableCountdown) laeuft alle zehn Ticks.
 *
 * <p>Die Zahl ist auf eine Sekunde gerundet; ob Hypixel auf- oder abrundet, ist nicht bekannt. Das Ende wird deshalb eine halbe
 * Sekunde hinter die Zahl gelegt (die Mitte zwischen beiden Moeglichkeiten): Der Fehler ist hoechstens eine halbe Sekunde, in
 * keiner Richtung mehr. Weicht ein neuer Messwert weniger als anderthalb Sekunden vom Ende ab, ist das Rundung - dann ruckt die
 * Anzeige nicht, die Schaetzung wandert nur zur Haelfte hin. Weicht er mehr ab, gilt der neue Wert.
 *
 * <p>Der Flare traegt keine Zeit im Namen; sein Ende folgt aus dem Alter des Staenders in Ticks (wie bei der Warnung).
 *
 * <p>Eigene Deployables laufen weiter, auch wenn der Staender nicht mehr zu sehen ist (man ist weit weg gegangen): Das Ende steht
 * ja fest. Die anderer Spieler verschwinden mit ihrem Staender.
 */
public final class DeployableTimers {

    /** Ein Flare haelt drei Minuten, in Ticks */
    private static final int FLARE_TICKS = 180 * 20;
    /** Mehr als so viel weichen zwei Messwerte nur ab, wenn sich wirklich etwas geaendert hat - sonst ist es Rundung */
    private static final long ROUNDING_MILLIS = 1_500L;
    /** Das Ende liegt hinter der Zahl: die Mitte zwischen Auf- und Abrunden */
    private static final long HALF_SECOND = 500L;
    /** So lange bleibt ein abgelaufenes oder verschwundenes Deployable in der Liste, bevor es ausgetragen wird */
    private static final long GRACE_MILLIS = 1_500L;
    /** So viel naeher als die Reichweite muss man wieder sein, damit die Zeile zurueckkommt - sonst flackert sie an der Grenze */
    private static final double REACH_SLACK = 1.0;
    private static final Pattern COLOUR_CODE = Pattern.compile("§[0-9a-fk-or]");

    /** Eine Zeile des Zeitkastens */
    public record Row(String name, long remainingMs, boolean mine, double distance) {
    }

    private static final class Timer {
        final int id;
        final Kind kind;
        final String name;
        boolean mine;
        double distance;
        long expiryAt;
        int lastSeconds = -1;
        boolean lost = false;
        /** Wann sich die Zahl zuletzt geaendert hat - fuer das Protokoll: wie oft schreibt Hypixel den Namen um */
        long changedAt = 0L;
        /** Wann der Staender zum ersten Mal gesehen wurde - eine Chatzeile "previous ... removed" meint nie einen, der eben erst kam */
        long bornAt = 0L;
        double x, y, z;
        /** Kein Staender dahinter: der Flare ist an seiner Rakete erkannt (siehe shot), die Zeit rechnet vom Schuss */
        boolean standless = false;
        /** Wie weit das Deployable wirkt (Bloecke); 0: unbekannt, dann gilt es als in Reichweite */
        double range = 0.0;
        /** Reicht es gerade bis zu mir? Mit einem Block Spiel an der Grenze (siehe reach) */
        boolean inRange = true;

        Timer(int id, Kind kind, String name) {
            this.id = id;
            this.kind = kind;
            this.name = name;
        }
    }

    /** Hoechstens so weit liegt ein neuer Staender vom alten entfernt, damit er als dessen Ersatz gilt (Bloecke) */
    private static final double RESPAWN_REACH = 2.0;

    /** Die Rakete eines Flares steigt auf, bevor der Staender da ist (gemessen: Staender 1,3 bis 1,7 s nach dem Rechtsklick gesehen) */
    private static final long ROCKET_RISE_MILLIS = 1_000L;
    /** Nummern fuer Eintraege ohne Staender: negativ, damit sie nie mit einer Entity-Nummer zusammenfallen */
    private static int standlessIds = 0;

    private static final Map<Integer, Timer> timers = new HashMap<>();
    /** Die Uhr des letzten Ticks - der Zeitkasten wird mit jedem neuen Tick neu gebaut (siehe SafariHud) */
    private static volatile long tickTime = 0L;

    private DeployableTimers() {
    }

    private static DeployableHudCategory cfg() {
        return ModConfig.INSTANCE.deployables.hud;
    }

    public static long tickTime() {
        return tickTime;
    }

    public static void reset() {
        timers.clear();
        standlessIds = 0;
    }

    /** Wie viele Deployables der Zeitkasten kennt - gezeigt oder nicht */
    static int count() {
        return timers.size();
    }

    /** Gibt es etwas zu zeigen? */
    public static boolean hasRows() {
        return !rows(tickTime).isEmpty();
    }

    // ==========================================
    // Messwerte
    // ==========================================

    /** Ein neuer Messwert: Die Zahl, wie der Staender sie zeigt, legt das Ende fest */
    static void anchor(Timer t, int seconds, long now) {
        long target = now + seconds * 1000L + HALF_SECOND;
        if (t.lastSeconds < 0) {
            t.expiryAt = target;
        } else {
            long diff = target - t.expiryAt;
            t.expiryAt = Math.abs(diff) <= ROUNDING_MILLIS ? t.expiryAt + diff / 2 : target;
        }
        if (DeployableLog.enabled()) {
            DeployableLog.line("TIME", String.format(Locale.ROOT, "id=%d %s %s -> %ds, %s, ends in %.1f s", t.id, t.kind,
                    t.lastSeconds < 0 ? "first reading" : t.lastSeconds + "s", seconds,
                    t.changedAt == 0L ? "first change" : (now - t.changedAt) + " ms after the last", (t.expiryAt - now) / 1000.0));
        }
        t.changedAt = now;
        t.lastSeconds = seconds;
    }

    /**
     * Die Suche hat einen Staender gefunden (alle zehn Ticks). Legt ihn an oder frischt Besitz und Abstand auf.
     *
     * Taucht statt eines Staenders, der gerade verschwunden ist, einer derselben Sorte an derselben Stelle auf (Hypixel schreibt
     * den Namen dann nicht um, sondern setzt einen neuen Staender), erbt er dessen Besitz und loest ihn ab: Sonst waere das eigene
     * Deployable nach dem ersten Wechsel keins mehr.
     *
     * @param mine ob das Deployable von mir ist (siehe DeployableCountdown.owned)
     * @return ob es von mir ist - auch dann, wenn es der Ersatz eines meiner Staender ist und owned() ihn nicht kennt
     */
    static boolean seen(ArmorStand stand, Reading reading, boolean mine, double distance, long now) {
        Timer t = timers.get(stand.getId());
        if (t == null) {
            t = new Timer(stand.getId(), reading.kind(), reading.name());
            Timer before = replacedBy(stand, reading.kind());
            if (before != null) {
                mine |= before.mine;
                timers.remove(before.id);
            }
            t.bornAt = now;
            timers.put(stand.getId(), t);
            if (DeployableLog.enabled()) {
                DeployableLog.line("STAND", String.format(Locale.ROOT, "id=%d %s \"%s\" %ds, %.1f blocks away at %.1f %.1f %.1f, %s%s",
                        t.id, t.kind, reading.name(), reading.seconds(), distance, stand.getX(), stand.getY(), stand.getZ(),
                        mine ? "counts as mine" : "not mine",
                        before == null ? "" : ", replaces id " + before.id + (before.mine ? " (was mine)" : "")));
            }
            // Man kann nur eines seiner Sorte draussen haben: Kommt ein neues, ist das alte weg - auch wenn sein Staender nur ausser Sicht ist
            if (mine) dropOtherMine(reading.kind(), stand.getId(), "a new one of mine took its place");
        }
        t.x = stand.getX();
        t.y = stand.getY();
        t.z = stand.getZ();
        // Wer einmal meiner war, bleibt es: owned() kennt einen Ersatzstaender nicht, und beim naechsten Durchgang soll er nicht wieder fremd sein
        t.mine |= mine;
        t.distance = distance;
        t.range = reading.range();
        reach(t);
        t.lost = false;
        if (reading.kind() == Kind.FLARE) {
            t.expiryAt = now + Math.max(0, FLARE_TICKS - stand.tickCount) * 50L;
        } else if (reading.seconds() != t.lastSeconds) {
            anchor(t, reading.seconds(), now);
        }
        return t.mine;
    }

    /**
     * Ein Flare-Schuss, den die Rakete bestaetigt hat (siehe DeployableCountdown.Shot): ein eigener Eintrag ohne Staender, der drei Minuten
     * vom Schuss an laeuft. Wird der Staender des Flares danach als meiner gelesen, loest er diesen Eintrag ab (dropOtherMine) und die Zeit
     * folgt seinem Alter; wird er nie gelesen oder gilt er nicht als meiner, bleibt der Eintrag bis zum Ablauf - oder bis Hypixel schreibt,
     * dass der Flare weg ist. Ein neuer Schuss ersetzt den alten, man kann nur einen Flare draussen haben.
     */
    static void shot(String name, long at, Vec3 pos, long now) {
        int id = --standlessIds;
        Timer t = new Timer(id, Kind.FLARE, name);
        t.standless = true;
        t.mine = true;
        t.lost = true;
        t.bornAt = now;
        t.expiryAt = at + FLARE_TICKS * 50L + ROCKET_RISE_MILLIS;
        t.x = pos.x;
        t.y = pos.y;
        t.z = pos.z;
        t.range = DeployableCountdown.rangeOf(name, Kind.FLARE);
        dropOtherMine(Kind.FLARE, id, "a new shot of mine took its place");
        timers.put(id, t);
        if (DeployableLog.enabled()) {
            DeployableLog.line("STAND", String.format(Locale.ROOT, "id=%d %s \"%s\" no stand, from the rocket: ends in %.1f s, counts as mine",
                    id, t.kind, name, (t.expiryAt - now) / 1000.0));
        }
    }

    /** Ein Staender gilt doch nicht als meiner (siehe DeployableCountdown.placedByMe): bleibt im Kasten nur im Modus "alle in der Naehe" */
    static void revoke(int id) {
        Timer t = timers.get(id);
        if (t != null) t.mine = false;
    }

    /**
     * Meine anderen Deployables dieser Sorte austragen: Man kann nur eines seiner Sorte draussen haben (Feesh fuehrt je Sorte genau eines).
     * Bis hierher blieben eigene, die nicht mehr zu sehen waren, bis zum Ablauf im Kasten stehen - auch wenn Hypixel sie beim Aufstellen eines
     * neuen laengst entfernt hatte: vier SOS Flares im Kasten, obwohl nur einer draussen war.
     */
    private static void dropOtherMine(Kind kind, int keepId, String why) {
        for (Iterator<Timer> it = timers.values().iterator(); it.hasNext(); ) {
            Timer t = it.next();
            if (t.id == keepId || !t.mine || t.kind != kind) continue;
            if (DeployableLog.enabled()) {
                DeployableLog.line("DROP", String.format(Locale.ROOT, "id=%d %s \"%s\": %s", t.id, t.kind, t.name, why));
            }
            DeployableCountdown.forgetMine(t.id);
            it.remove();
        }
    }

    /**
     * Eine Zeile von Hypixel sagt, dass ein Deployable weg ist: "Your previous X was removed!" (ein neues hat es ersetzt),
     * "Your flare disappeared because you were too far away!" oder "Your X despawned." Meine Deployables dieser Sorte fallen aus dem Kasten.
     * Wer in den letzten 250 ms erst auftauchte, ist das neue und bleibt.
     */
    static void ended(Kind kind, Ending ending, long now) {
        if (ending == Ending.SOON) return;
        for (Iterator<Timer> it = timers.values().iterator(); it.hasNext(); ) {
            Timer t = it.next();
            if (!t.mine || t.kind != kind || now - t.bornAt < NEW_MILLIS) continue;
            if (DeployableLog.enabled()) {
                DeployableLog.line("DROP", String.format(Locale.ROOT, "id=%d %s \"%s\": Hypixel says it is gone (%s)", t.id, t.kind, t.name, ending));
            }
            DeployableCountdown.forgetMine(t.id);
            it.remove();
        }
    }

    /** So jung ist ein Staender "neu": eine Zeile, die zeitgleich kommt, meint den Vorgaenger */
    private static final long NEW_MILLIS = 250L;

    // ==========================================
    // Jeden Tick
    // ==========================================

    /** Jeden Tick: neue Zahlen der bekannten Staender lesen, Abgelaufene austragen */
    static void tick(Minecraft client, long now) {
        tickTime = now;
        if (client.level == null || client.player == null || !FeatureGate.deployableHud() || !GameState.Server.isSkyblock()) {
            if (!timers.isEmpty()) timers.clear();
            return;
        }
        if (timers.isEmpty()) return;

        for (Iterator<Timer> it = timers.values().iterator(); it.hasNext(); ) {
            Timer t = it.next();
            Entity entity = t.standless ? null : client.level.getEntity(t.id);
            if (entity instanceof ArmorStand stand && !stand.isRemoved()) {
                t.lost = false;
                t.x = stand.getX();
                t.y = stand.getY();
                t.z = stand.getZ();
                refresh(t, stand, now);
            } else {
                if (!t.lost && !t.standless && DeployableLog.enabled()) {
                    DeployableLog.line("GONE", String.format(Locale.ROOT, "id=%d %s \"%s\": stand no longer there, %s, %.1f s left by my count",
                            t.id, t.kind, t.name, t.mine ? "mine - keeps counting" : "not mine - dropped", (t.expiryAt - now) / 1000.0));
                }
                t.lost = true;
            }
            // Der Abstand gilt von der Stelle des Deployables, auch wenn sein Staender nicht mehr zu sehen ist (man ist weggegangen):
            // sonst bliebe der letzte Wert stehen, und die Zeile wuesste nie, dass man weit weg ist
            t.distance = Math.sqrt(client.player.distanceToSqr(t.x, t.y, t.z));
            reach(t);
            // Abgelaufen, oder verschwunden und nicht mein eigenes (das zaehlt weiter, bis es ablaeuft)
            boolean over = now > t.expiryAt + GRACE_MILLIS;
            if (over || (t.lost && !t.mine)) it.remove();
        }
    }

    /**
     * Reicht das Deployable bis zu mir? Wer weiter weg steht als seine Wirkung reicht (Laterne 30 Bloecke), hat nichts davon: Die
     * Zeile verschwindet aus dem Kasten und kommt wieder, wenn man naeher ist. Die Zeit laeuft die ganze Zeit weiter.
     */
    private static void reach(Timer t) {
        if (t.range <= 0.0) {
            t.inRange = true;
            return;
        }
        boolean was = t.inRange;
        if (was && t.distance > t.range) t.inRange = false;
        else if (!was && t.distance < t.range - REACH_SLACK) t.inRange = true;
        if (was != t.inRange && DeployableLog.enabled()) {
            DeployableLog.line("RANGE", String.format(Locale.ROOT, "id=%d %s \"%s\": %.1f blocks away, reaches %.0f - %s", t.id, t.kind, t.name,
                    t.distance, t.range, t.inRange ? "back in range, listed again" : "out of range, not listed"));
        }
    }

    /** Ein Staender derselben Sorte an der Stelle eines gerade verschwundenen - dessen Ersatz ist der neue (siehe seen) */
    private static Timer replacedBy(ArmorStand stand, Kind kind) {
        Timer best = null;
        double bestDistance = RESPAWN_REACH * RESPAWN_REACH;
        for (Timer t : timers.values()) {
            if (!t.lost || t.kind != kind) continue;
            double dx = t.x - stand.getX();
            double dy = t.y - stand.getY();
            double dz = t.z - stand.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestDistance) {
                bestDistance = d;
                best = t;
            }
        }
        return best;
    }

    /** Was der Zeitkasten gerade weiss, in einer Zeile - fuer das Protokoll */
    static String describe(long now) {
        if (timers.isEmpty()) return "no timers";
        StringBuilder out = new StringBuilder();
        for (Timer t : timers.values()) {
            if (out.length() > 0) out.append("; ");
            out.append(String.format(Locale.ROOT, "id=%d %s %s%s %.1fm %.1fs%s", t.id, t.kind, t.mine ? "mine" : "other",
                    t.lost ? " lost" : "", t.distance, (t.expiryAt - now) / 1000.0, t.inRange ? "" : " out-of-range"));
        }
        return out.toString();
    }

    private static void refresh(Timer t, ArmorStand stand, long now) {
        if (t.kind == Kind.FLARE) {
            t.expiryAt = now + Math.max(0, FLARE_TICKS - stand.tickCount) * 50L;
            return;
        }
        Component name = stand.getCustomName();
        if (name == null) return;
        String text = COLOUR_CODE.matcher(name.getString()).replaceAll("").trim();
        int seconds;
        if (t.kind == Kind.TOTEM) {
            seconds = DeployableCountdown.remainingSeconds(text);
        } else {
            Reading reading = DeployableCountdown.parse(text);
            seconds = reading == null ? -1 : reading.seconds();
        }
        if (seconds >= 0 && seconds != t.lastSeconds) anchor(t, seconds, now);
    }

    // ==========================================
    // Die Zeilen
    // ==========================================

    /** Was der Kasten zeigt: nach Einstellung die eigenen oder alle in der Naehe, das nahste Ende zuerst */
    public static List<Row> rows(long now) {
        DeployableHudCategory c = cfg();
        List<Row> out = new ArrayList<>();
        if (c == null || !c.enabled) return out;
        boolean own = c.mode == ModConfig.DeployableHudMode.OWN;
        for (Timer t : timers.values()) {
            if (own ? !t.mine : (!t.mine && t.distance > c.nearbyRange)) continue;
            if (c.onlyInRange && !t.inRange) continue;
            long left = t.expiryAt - now;
            if (left < -200L) continue;
            out.add(new Row(t.name, Math.max(0L, left), t.mine, t.distance));
        }
        out.sort((a, b) -> Long.compare(a.remainingMs(), b.remainingMs()));
        int max = Math.max(1, Math.min(10, c.maxRows));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** "2:07" ab einer Minute, darunter "27.4s" (oder "28s" ohne Zehntel) - aufgerundet, damit die Anzeige erst bei 0 auf 0 steht */
    public static String format(long ms, boolean tenths) {
        long left = Math.max(0L, ms);
        if (left >= 60_000L) {
            long s = (left + 999L) / 1000L;
            return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
        }
        if (tenths) return String.format(Locale.ROOT, "%.1fs", left / 1000.0);
        return ((left + 999L) / 1000L) + "s";
    }
}
