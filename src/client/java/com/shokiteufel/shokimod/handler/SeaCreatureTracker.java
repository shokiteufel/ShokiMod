package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SeaCreatures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zaehlt die eigenen Faenge an Meeresbewohnern: wie viele insgesamt, wie viele davon
 * als Double Hook und wie viele als Cocoon.
 *
 * So liest es Feesh (Apache 2.0, Sleepy-Panda/Feesh), und so wird hier gezaehlt:
 * "It's a Double Hook!" steht eine Zeile VOR der Meldung des Bewohners und gilt fuer
 * genau diesen einen Fang - der zaehlt dann doppelt, und der Double Hook wird einmal
 * vermerkt. "CAUGHT! You cocooned a ...!" ist eine eigene Zeile; sie zaehlt nicht als
 * Fang, sondern nur im Cocoon-Zaehler.
 *
 * Was in der Auswahl steht, bekommt eine eigene Zeile, alles andere landet in "Others".
 * Gruppenzeilen ("Party > ...") sind nie der eigene Fang und passen auf kein Muster.
 */
public final class SeaCreatureTracker {

    private static final Pattern DOUBLE_HOOK = Pattern.compile("^It's a Double Hook!");
    private static final Pattern COCOONED = Pattern.compile("^CAUGHT! You cocooned (?:a|an) (?<name>.+)!$");

    /** Der naechste Fang kommt doppelt */
    private static boolean doubleHook = false;

    /**
     * Die Zaehlung dieser Sitzung - sie wird nicht gespeichert, ein neuer Spielstart beginnt
     * von vorn. Der Gesamtstand (Total) liegt dagegen in der Config.
     */
    private static final java.util.Map<String, Count> session = new java.util.HashMap<>();

    /**
     * Die Fischzeit der Sitzung. Sie laeuft nur, solange gefangen wird: Zwischen zwei Faengen
     * zaehlen hoechstens fuenf Minuten, alles darueber ist eine Pause - sonst wuerde
     * eine Kaffeepause die Rate druecken.
     */
    private static final long IDLE_MILLIS = 5L * 60_000L;
    private static long sessionActiveMillis = 0L;
    private static long lastCatchAt = 0L;

    /** Ein Zaehlstand: Faenge (Double Hook doppelt gerechnet), Double Hooks, Cocoons */
    public static final class Count {
        @com.google.gson.annotations.Expose
        public int total = 0;
        @com.google.gson.annotations.Expose
        public int dh = 0;
        @com.google.gson.annotations.Expose
        public int cc = 0;
    }

    /** Eine Zeile im Kasten */
    public record Row(String name, int colour, Count count) {
    }

    private SeaCreatureTracker() {
    }

    private static ModConfig.SeaTrackerCategory cfg() {
        return ModConfig.INSTANCE.fishing.tracker;
    }

    public static boolean sessionView() {
        return cfg().view == ModConfig.SeaView.SESSION;
    }

    /** Der Zaehlstand, den der Kasten gerade zeigt */
    private static java.util.Map<String, Count> counts() {
        if (sessionView()) return session;
        return cfg().perArea ? cfg().areaCounts.computeIfAbsent(area(), k -> new java.util.HashMap<>()) : cfg().counts;
    }

    /** Das Gebiet, in dem gerade gefangen wird (Area-Zeile der Tab-Liste) */
    private static String area() {
        String map = GameState.Server.map;
        return map == null || map.isBlank() ? "Unknown" : map;
    }

    /** Verbuchen: in den Gesamtstand und in die Sitzung */
    private static void bump(String name, boolean doppelt, boolean cocoon) {
        for (java.util.Map<String, Count> map : java.util.List.of(cfg().counts, session,
                cfg().areaCounts.computeIfAbsent(area(), k -> new java.util.HashMap<>()))) {
            Count count = map.computeIfAbsent(name, k -> new Count());
            if (cocoon) {
                count.cc++;
            } else {
                count.total += doppelt ? 2 : 1;
                if (doppelt) count.dh++;
            }
        }
        long now = System.currentTimeMillis();
        lastHookAt = now;
        if (lastCatchAt > 0L) sessionActiveMillis += Math.min(now - lastCatchAt, IDLE_MILLIS);
        lastCatchAt = now;
        ModConfig.INSTANCE.saveNow();
    }

    /** Aktive Fischzeit dieser Sitzung; das laufende Stueck seit dem letzten Fang zaehlt mit */
    public static long sessionMillis() {
        long running = lastCatchAt > 0L ? Math.min(System.currentTimeMillis() - lastCatchAt, IDLE_MILLIS) : 0L;
        return sessionActiveMillis + running;
    }

    /** Wie oft die Angel in dieser Sitzung etwas gebracht hat: ein Double Hook zaehlt zwei Fange, aber ein Auswurf */
    public static int sessionHooks() {
        int sum = 0;
        for (Count c : session.values()) sum += c.total - c.dh;
        return sum;
    }

    /** Hooks je Stunde - die Meeresbewohner ohne Double Hooks und ohne Blood Shots */
    public static double hooksPerHour() {
        long millis = sessionMillis();
        return millis < 1000L ? 0.0 : sessionHooks() * 3_600_000.0 / millis;
    }

    /** Gebiete, in denen es nichts zu fangen gibt - der Kasten bleibt dort weg (wie bei Feesh) */
    private static final java.util.Set<String> NO_FISHING = java.util.Set.of(
            "The Rift", "Garden", "Kuudra's Hollow", "Dungeons", "Dungeon Hub", "The End",
            "Glacite Mineshafts", "Safari");
    /** So lange nach dem letzten Wurf in Wasser oder Lava bleibt der Kasten stehen */
    private static final long HIDE_AFTER_MILLIS = 5L * 60_000L;
    private static long lastHookAt = 0L;

    /** Jeden Tick: liegt die eigene Angel gerade in Wasser oder Lava? Dann wird das gemerkt */
    public static void tick(net.minecraft.client.Minecraft client) {
        if (client == null || client.player == null) return;
        net.minecraft.world.entity.projectile.FishingHook hook = client.player.fishing;
        if (hook != null && (hook.isInWater() || hook.isInLava())) lastHookAt = System.currentTimeMillis();
    }

    /**
     * Soll der Kasten gerade stehen?
     *
     * Wie bei Feesh: nur in einem Gebiet, in dem man angeln kann, und nur wenn in den letzten
     * fuenf Minuten eine Angel im Wasser oder in der Lava lag. Ein offenes Fenster (Inventar,
     * HUD-Editor) zeigt ihn immer, damit man ihn anklicken und verschieben kann.
     */
    public static boolean shownNow() {
        if (!cfg().hideWhenIdle) return true;
        if (net.minecraft.client.Minecraft.getInstance().screen != null) return true;
        if (NO_FISHING.contains(GameState.Server.map)) return false;
        return lastHookAt > 0L && System.currentTimeMillis() - lastHookAt <= HIDE_AFTER_MILLIS;
    }

    /** Gebietswechsel: Die Fischzeit haelt an und laeuft mit dem naechsten Fang weiter */
    public static void pauseNow() {
        lastCatchAt = 0L;
        lastHookAt = 0L;
    }

    /** Wechselt zwischen Total und Session - der Klick auf die Ueberschrift */
    public static void toggleView() {
        cfg().view = sessionView() ? ModConfig.SeaView.TOTAL : ModConfig.SeaView.SESSION;
        ModConfig.INSTANCE.saveNow();
    }

    // ------------------------------------------------------------------ HPH?

    private static final Pattern PARTY_LINE = Pattern.compile(
            "^(?:Party|\u041f\u0430\u0440\u0442\u0438\u044f|\u041a\u043e\u043c\u043f\u0430\u043d\u0438\u044f) > (?:\\[[^\\]]*\\] )?(?<player>[A-Za-z0-9_]+): (?<text>.+)$");
    private static final Pattern HPH_ASK = Pattern.compile("^(?i:hph\\??)$");
    /** Mindestens so viel Fischzeit und so viele Hooks, bevor eine Zahl etwas sagt */
    private static final long HPH_MIN_MILLIS = 60_000L;
    private static final int HPH_MIN_HOOKS = 10;
    /** Fragen mehrere, antwortet die Mod nur einmal */
    private static final long HPH_COOLDOWN_MILLIS = 8_000L;
    private static long lastAnswerAt = 0L;

    /** Fragt jemand in der Gruppe "HPH?", gibt es die Antwort in den Gruppenchat */
    private static void answerHph(String plain) {
        if (!cfg().answerHph) return;
        Matcher party = PARTY_LINE.matcher(plain.trim());
        if (!party.matches() || !HPH_ASK.matcher(party.group("text").trim()).matches()) return;

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) return;
        // Auch die eigene Frage wird beantwortet; die Antwort selbst passt nicht auf "HPH?", das gibt keine Schleife

        long now = System.currentTimeMillis();
        if (now - lastAnswerAt < HPH_COOLDOWN_MILLIS) return;
        lastAnswerAt = now;

        String text;
        if (cfg().enabled && sessionHooks() >= HPH_MIN_HOOKS && sessionMillis() >= HPH_MIN_MILLIS) {
            text = "HPH: " + Math.round(hooksPerHour()) + " hooks/h (" + Math.round(perHour())
                    + " SC/h, " + (sessionMillis() / 60_000L) + " min)";
        } else {
            text = "to less Hooks to post \uD83D\uDE1E";
        }
        client.getConnection().sendCommand("pc " + text);
    }

    /** Meeresbewohner je Stunde: Double Hooks sind schon doppelt gezaehlt, Blood Shots kommen dazu */
    public static double perHour() {
        long millis = sessionMillis();
        if (millis < 1000L) return 0.0;
        int sum = 0;
        for (Count c : session.values()) sum += c.total + c.cc;
        return sum * 3_600_000.0 / millis;
    }

    public static void onChatMessage(String plain) {
        if (plain == null || plain.isEmpty()) return;
        // Die Antwort gilt auch bei ausgeschaltetem Tracker: dann sagt sie, dass nichts da ist
        answerHph(plain);
        if (!cfg().enabled) return;
        String text = plain.trim();

        if (DOUBLE_HOOK.matcher(text).find()) {
            doubleHook = true;
            return;
        }

        Matcher cocoon = COCOONED.matcher(text);
        if (cocoon.matches()) {
            SeaCreatures.Creature creature = SeaCreatures.byName(cocoon.group("name"));
            if (creature != null) bump(creature.name(), false, true);
            return;
        }

        SeaCreatures.Creature caught = SeaCreatures.caughtIn(text);
        if (caught == null) return;
        boolean doppelt = doubleHook;
        doubleHook = false;
        bump(caught.name(), doppelt, false);
    }

    // ------------------------------------------------------------------ Anzeige

    /** Die Summe ueber alle Bewohner - auch die, die nicht einzeln gezeigt werden */
    public static Count total() {
        Count sum = new Count();
        for (Count c : counts().values()) {
            sum.total += c.total;
            sum.dh += c.dh;
            sum.cc += c.cc;
        }
        return sum;
    }

    /** Die gewaehlten Bewohner mit Fang, nach Seltenheit sortiert (Mythic zuerst) */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (SeaCreatures.Creature creature : SeaCreatures.all()) {
            Count count = counts().get(creature.name());
            if (count == null || !picked(creature)) continue;
            if (cfg().onlyThisArea && !SeaCreatures.availableIn(creature.name(), GameState.Server.map)) continue;
            out.add(new Row(creature.name(), 0xFF000000 | creature.colour(), count));
        }
        // Nach Seltenheit: Mythic zuerst, dann Legendary, Epic ...; innerhalb davon der haeufigste
        out.sort(Comparator.comparingInt((Row r) -> rarityRank(r.name())).reversed()
                .thenComparing(Comparator.comparingInt((Row r) -> r.count().total).reversed()));
        return out;
    }

    private static int rarityRank(String name) {
        SeaCreatures.Creature creature = SeaCreatures.byName(name);
        if (creature == null) return -1;
        return switch (creature.rarity()) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    /** Alles, was nicht einzeln gezeigt wird, zusammengezaehlt - null, wenn nichts da ist */
    public static Count others() {
        Count sum = new Count();
        for (var entry : counts().entrySet()) {
            SeaCreatures.Creature creature = SeaCreatures.byName(entry.getKey());
            // Ein Name, den die Liste nicht mehr kennt, gehoert zu "Others" und nirgends sonst hin
            if (creature != null && picked(creature)) continue;
            sum.total += entry.getValue().total;
            sum.dh += entry.getValue().dh;
            sum.cc += entry.getValue().cc;
        }
        return sum.total == 0 && sum.cc == 0 ? null : sum;
    }

    // ------------------------------------------------------------------ Auswahl

    /**
     * Bekommt dieser Bewohner eine eigene Zeile?
     *
     * Ohne Eintrag gilt die Voreinstellung: die seltenen ja, die uebrigen landen in
     * "Others". Ein Bewohner, den Hypixel spaeter hinzufuegt, verhaelt sich so von selbst
     * richtig, statt zu fehlen, bis jemand die Liste pflegt.
     */
    public static boolean picked(SeaCreatures.Creature creature) {
        return cfg().mobs.getOrDefault(creature.name(), creature.rare());
    }

    public static void togglePicked(SeaCreatures.Creature creature) {
        cfg().mobs.put(creature.name(), !picked(creature));
        ModConfig.INSTANCE.saveNow();
    }

    /** Setzt die gerade gezeigte Ansicht zurueck - den Gesamtstand oder die Sitzung */
    public static void reset() {
        if (sessionView()) {
            session.clear();
            sessionActiveMillis = 0L;
            lastCatchAt = 0L;
        } else {
            counts().clear();
        }
        doubleHook = false;
        ModConfig.INSTANCE.saveNow();
    }

    /** Der Reset-Knopf, im Kasten wie in den Einstellungen: erst fragen - und sagen, welche Ansicht es trifft */
    public static void askReset() {
        com.shokiteufel.shokimod.gui.ResetConfirm.ask("Sea creature tracker",
                sessionView() ? "Clears the counts of this session and its timer." : "Sets the total counts back to zero.",
                SeaCreatureTracker::reset);
    }
}
