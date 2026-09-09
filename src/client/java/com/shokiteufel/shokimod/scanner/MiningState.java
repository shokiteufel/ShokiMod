package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.render.AlertBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was in den Minen zaehlt: Auftraege, die Spitzhacken-Faehigkeit und der Sky-Mall-Buff.
 *
 * So sieht der Abschnitt in der Tab-Liste aus (gemessen in den Glacite Mineshafts):
 *
 * <pre>
 * Commissions:
 *  Mineshaft Explorer: 0%
 *  Scrap Collector: 0%
 * Pickaxe Ability:
 *  Pickobulus: 26s
 * </pre>
 *
 * Zwei Dinge daran sind wichtig. Erstens ist "Pickaxe Ability:" eine Ueberschrift und
 * nicht die Angabe selbst - Name und Abklingzeit stehen zusammen in der Zeile darunter.
 * Zweitens wird hier bewusst nicht mehr geprueft, auf welcher Insel der Spieler steht:
 * Hypixel benennt Inseln um und legt neue an, die Ueberschrift dagegen ist stabil. Steht
 * "Commissions:" in der Liste, ist man dort, wo es Auftraege gibt.
 *
 * Der Sky-Mall-Buff kommt nicht aus dem Tab, sondern aus der Chatzeile, mit der Hypixel
 * ihn zum Tagesbeginn ankuendigt.
 */
public final class MiningState {

    /** Ueberschriften der beiden Abschnitte */
    private static final Pattern COMMISSION_HEADER =
            Pattern.compile("^Commissions?:?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABILITY_HEADER =
            Pattern.compile("^(?:Pickaxe )?Ability:?$", Pattern.CASE_INSENSITIVE);

    /** "Mineshaft Explorer: 0%" oder "Scrap Collector: DONE" */
    private static final Pattern COMMISSION_LINE = Pattern.compile(
            "^(?<name>[^:]{3,40}):\\s*(?<progress>DONE|\\d{1,3}(?:[.,]\\d+)?\\s*%)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Die Zeile unter "Pickaxe Ability:": "Pickobulus: 26s".
     *
     * Die Abklingzeit ist freiwillig - ist die Faehigkeit bereit, steht dort je nach Lage
     * nichts oder ein Wort wie READY.
     */
    private static final Pattern ABILITY_LINE = Pattern.compile(
            "^(?<name>[A-Za-z][A-Za-z '-]{2,30})(?::\\s*(?<cooldown>.{1,20}))?$");

    /**
     * Ueberschrift und Zeilen der gefrorenen Leichen - die gibt es nur in Mineshafts.
     *
     * <pre>
     * Frozen Corpses:
     *  Lapis: NOT LOOTED
     *  Umber: NOT LOOTED
     * </pre>
     *
     * Derselbe Typ kommt mehrfach vor, deshalb wird gezaehlt statt aufgelistet.
     */
    private static final Pattern CORPSE_HEADER =
            Pattern.compile("^Frozen Corpses?:?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CORPSE_LINE = Pattern.compile(
            "^(?<type>Lapis|Umber|Tungsten|Vanguard) *: *(?<state>.+)$", Pattern.CASE_INSENSITIVE);

    /** Teile einer Dauer: "1m 20s" besteht aus zwei davon */
    private static final Pattern DURATION = Pattern.compile("(?<value>[0-9]{1,4}) *(?<unit>[hms])");

    /** Was ein Spieler geschrieben hat, ist keine Ankuendigung */
    private static final Pattern PLAYER_LINE = Pattern.compile(
            "^(?:[A-Za-z-]+ > )?(?:\\[[^\\]]{1,20}\\] )*[A-Za-z0-9_]{3,16}: ");

    /**
     * Die Sky-Mall-Buffs, erkannt an Hypixels Wortlaut - dieselben Textstellen, an denen
     * auch SkyHanni sie festmacht.
     */
    private static final List<SkyMall> SKY_MALL = List.of(
            new SkyMall("more Powder while mining", "+15% Powder"),
            new SkyMall("Pickaxe Ability cooldown", "-20% Ability cooldown"),
            new SkyMall("Golden and Diamond Goblin", "10x Goblin chance"),
            new SkyMall("Titanium drops", "5x Titanium"));

    /**
     * "Gain +100 Mining Speed" - Hypixels Wortlaut fuer die beiden Stat-Buffs.
     *
     * Das "Gain" am Zeilenanfang ist Absicht: ohne es hielte die Mod auch ein
     * "hab grad 200 Mining Speed lol" aus dem Gildenchat fuer den Buff des Tages.
     */
    private static final Pattern SKY_MALL_STAT = Pattern.compile(
            "^Gain [+]?(?<amount>[0-9]{1,4})[^A-Za-z0-9]{0,4} *Mining +(?<stat>Speed|Fortune)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Die Spitzhacken-Faehigkeiten von Hypixel.
     *
     * Gebraucht nur fuer den Fall, dass die Zeile keine Abklingzeit traegt - dann fehlt der
     * Doppelpunkt, und ohne diese Liste wuerde jedes beliebige Wort unter der Ueberschrift
     * als Faehigkeit durchgehen. Genau so kam einmal "Goblin" ins HUD.
     */
    private static final List<String> ABILITIES = List.of(
            "mining speed boost", "pickobulus", "vein seeker", "maniac miner",
            "anomalous desire", "sheer force", "gemstone infusion", "hazardous miner");

    private record SkyMall(String needle, String label) {
    }

    /**
     * Wann die Faehigkeit wieder bereit ist. 0 heisst: nichts bekannt.
     *
     * Gerechnet wird gegen einen festen Zeitpunkt, nicht gegen einen nachgezogenen
     * Restwert - so faellt die Zahl gleichmaessig, auch wenn die Tab-Liste nur alle paar
     * Sekunden nachrueckt. Dasselbe macht der Contest-Zaehler.
     */
    private static final int READY_ALERT_COLOUR = 0x55FFFF;
    private static final long READY_ALERT_MILLIS = 2500L;
    /**
     * Der Banner kommt eine halbe Sekunde nach der Bereitschaft.
     *
     * Direkt im Moment des Umschaltens geht er im Blick auf die Zahl unter; mit dem
     * kurzen Abstand faellt er auf.
     */
    private static final long READY_ALERT_DELAY_MILLIS = 500L;

    private static long readyAt = 0L;
    /**
     * Was die Tab-Liste zuletzt sagte.
     *
     * Zwischen ihren Spruengen steht die Quelle still: sagt sie "26s", sagt sie das
     * womoeglich eine Sekunde lang. Nur im Moment des Sprungs traegt sie neue Information -
     * dann, und nur dann, wird der Zielzeitpunkt neu gesetzt.
     */
    private static long lastStatedSeconds = Long.MIN_VALUE;
    /** Ob fuer den laufenden Zyklus schon gemeldet wurde - sonst kaeme das Banner im Dauerlauf */
    private static boolean readyAnnounced = true;
    /** Wann zuletzt etwas aus der Tab-Liste kam - fuer die Fehlersuche */
    private static long lastReadAt = 0L;

    private MiningState() {
    }

    private static ModConfig.MiningHudCategory cfg() {
        return ModConfig.INSTANCE.mining.hud;
    }

    /**
     * Ist der Spieler dort, wo die Tab-Liste diese Angaben fuehrt?
     *
     * Gemessen wird am Ergebnis: Wurden gerade Auftraege gelesen, ist er es. Das kommt
     * ohne Liste von Inselnamen aus, die bei jeder neuen Mine nachgepflegt werden muesste.
     */
    public static boolean onMiningIsland() {
        return System.currentTimeMillis() - lastReadAt < 30_000L;
    }

    /** Was zuletzt gelesen wurde, als Text - fuer /shoki tab */
    public static String diagnostics() {
        long ago = lastReadAt == 0L ? -1 : (System.currentTimeMillis() - lastReadAt) / 1000L;
        return "area=" + GameState.Server.map
                + " lastRead=" + (ago < 0 ? "never" : ago + "s ago")
                + " commissions=" + commissions().size()
                + " ability=" + (ability().isBlank() ? "-" : ability())
                + " cooldown=" + (cooldown().isBlank() ? "-" : cooldown())
                + " skymall=" + (skyMall().isBlank() ? "-" : skyMall());
    }

    /**
     * Die Tab-Liste auswerten. Wird bei jeder Aenderung gerufen, muss also billig bleiben.
     */
    public static void processTabList(List<String> lines) {
        List<String> commissions = new ArrayList<>();
        java.util.LinkedHashMap<String, int[]> foundCorpses = new java.util.LinkedHashMap<>();
        String ability = null;
        String cd = null;
        Section section = Section.NONE;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                section = Section.NONE;
                continue;
            }

            if (COMMISSION_HEADER.matcher(line).matches()) {
                section = Section.COMMISSIONS;
                continue;
            }
            if (ABILITY_HEADER.matcher(line).matches()) {
                section = Section.ABILITY;
                continue;
            }
            if (CORPSE_HEADER.matcher(line).matches()) {
                section = Section.CORPSES;
                continue;
            }

            if (section == Section.COMMISSIONS) {
                Matcher m = COMMISSION_LINE.matcher(line);
                if (m.matches()) {
                    commissions.add(m.group("name").trim() + ": "
                            + m.group("progress").replace(" ", "").toUpperCase(Locale.ROOT));
                }
                // Der Abschnitt endet erst an der naechsten Ueberschrift, nicht schon an
                // der ersten fremden Zeile: sonst reisst eine einzelne Luecke die restlichen
                // Auftraege mit ab
                continue;
            }
            if (section == Section.CORPSES) {
                Matcher m = CORPSE_LINE.matcher(line);
                if (m.matches()) {
                    String type = m.group("type");
                    String key = Character.toUpperCase(type.charAt(0))
                            + type.substring(1).toLowerCase(Locale.ROOT);
                    boolean open = m.group("state").toUpperCase(Locale.ROOT).contains("NOT");
                    foundCorpses.merge(key, new int[]{open ? 1 : 0, 1},
                            (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});
                }
                continue;
            }
            if (section == Section.ABILITY) {
                Matcher m = ABILITY_LINE.matcher(line);
                if (m.matches()) {
                    String name = m.group("name").trim();
                    String time = m.group("cooldown") == null ? null : m.group("cooldown").trim();
                    // Ohne Abklingzeit nur, wenn der Name wirklich eine Faehigkeit ist
                    if (time != null || ABILITIES.contains(name.toLowerCase(Locale.ROOT))) {
                        ability = name;
                        cd = time == null ? "Ready" : time;
                    }
                }
                // Unter der Ueberschrift steht genau eine Zeile
                section = Section.NONE;
                continue;
            }
        }

        // Die Leichen gibt es nur in Mineshafts; anderswo bleibt die Liste leer, und dann
        // soll auch der alte Stand verschwinden statt stehenzubleiben
        List<Corpse> freshCorpses = new ArrayList<>(foundCorpses.size());
        for (var e : foundCorpses.entrySet()) {
            freshCorpses.add(new Corpse(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        corpses = List.copyOf(freshCorpses);
        if (!freshCorpses.isEmpty()) callParty(freshCorpses);

        if (commissions.isEmpty() && ability == null && freshCorpses.isEmpty()) return;
        lastReadAt = System.currentTimeMillis();

        boolean changed = false;
        if (!commissions.isEmpty() && !commissions.equals(cfg().lastCommissions)) {
            cfg().lastCommissions = commissions;
            changed = true;
        }
        if (ability != null && !ability.equals(cfg().lastAbility)) {
            cfg().lastAbility = ability;
            changed = true;
        }
        if (cd != null) noteCooldown(cd);
        // Der Stand ueberlebt den Inselwechsel nur, wenn er auch auf der Platte landet
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    private enum Section {
        NONE, COMMISSIONS, ABILITY, CORPSES
    }

    /** Je Typ: wie viele noch offen sind und wie viele es insgesamt gibt */
    public record Corpse(String type, int open, int total) {
    }

    private static List<Corpse> corpses = List.of();
    /**
     * Der Schacht, fuer den die Party schon gerufen wurde.
     *
     * Jeder Mineshaft ist eine eigene Server-Instanz, und deren Kennung steht in der
     * Tab-Liste. Sie zu merken ist die einfachste Art, genau einmal je Schacht zu rufen -
     * auch wenn die Liste danach noch hundertmal durchlaeuft.
     */
    private static String calledFor = "";

    /**
     * Die offenen Leichen, gefiltert nach der Einstellung.
     *
     * Vanguard bleibt immer aussen vor - die ist dem Auftraggeber egal.
     */
    public static List<Corpse> corpses() {
        ModConfig.CorpseFilter filter = cfg().corpses;
        if (filter == ModConfig.CorpseFilter.OFF) return List.of();
        List<Corpse> out = new ArrayList<>(corpses.size());
        for (Corpse c : corpses) {
            String type = c.type().toLowerCase(Locale.ROOT);
            if (type.equals("vanguard")) continue;
            if (filter == ModConfig.CorpseFilter.LAPIS_ONLY && !type.equals("lapis")) continue;
            out.add(c);
        }
        return out;
    }

    /**
     * Der Party sagen, was in diesem Schacht liegt - einmal beim Betreten.
     *
     * Gerufen wird nur, wenn genug Lapis-Leichen da sind: die anderen Sorten laufen
     * nebenher mit, sind aber kein Grund, die Party anzuschreiben.
     */
    private static void callParty(List<Corpse> found) {
        int threshold = ModConfig.INSTANCE.mining.hud.corpseCall.threshold();
        if (threshold <= 0) return;

        String shaft = GameState.Server.id;
        if (shaft == null || shaft.isBlank() || shaft.equals(calledFor)) return;

        int lapis = 0;
        for (Corpse c : found) {
            if (c.type().equalsIgnoreCase("Lapis")) lapis += c.total();
        }
        if (lapis < threshold) return;

        // Erst merken, dann senden: schlaegt das Senden fehl, soll es trotzdem bei
        // einem Versuch je Schacht bleiben
        calledFor = shaft;

        StringBuilder text = new StringBuilder("Corpses here: ");
        boolean first = true;
        for (Corpse c : found) {
            if (c.type().equalsIgnoreCase("Vanguard")) continue;
            if (!first) text.append(", ");
            text.append(c.total()).append("x ").append(c.type());
            first = false;
        }

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        ShokiMod.LOGGER.info("[Mining] telling the party: {}", text);
        connection.sendCommand("pc " + text);
    }

    /** Eine Chatzeile auf die Sky-Mall-Ankuendigung pruefen */
    public static void onChatMessage(String plain) {
        if (plain == null || plain.isBlank()) return;
        // Sonst setzt jeder, der im Gildenchat ueber Titanium redet, den Buff des Tages
        if (PLAYER_LINE.matcher(plain).find()) return;
        for (SkyMall buff : SKY_MALL) {
            if (plain.contains(buff.needle())) {
                remember(buff.label());
                return;
            }
        }
        Matcher stat = SKY_MALL_STAT.matcher(plain);
        if (stat.find()) {
            String name = stat.group("stat");
            remember("+" + stat.group("amount") + " Mining "
                    + Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT));
        }
    }

    private static void remember(String label) {
        if (label.equals(cfg().lastSkyMall)) return;
        cfg().lastSkyMall = label;
        ModConfig.INSTANCE.saveNow();
    }

    public static List<String> commissions() {
        List<String> stored = cfg().lastCommissions;
        return stored == null ? List.of() : stored;
    }

    public static String ability() {
        return cfg().lastAbility == null ? "" : cfg().lastAbility;
    }

    /**
     * Den Augenblick abpassen, in dem die Faehigkeit bereit wird.
     *
     * Wird im Tick gerufen, nicht beim Lesen der Tab-Liste: Der Zielzeitpunkt laeuft
     * lokal ab, die Tab-Liste braucht dafuer nicht zu wackeln.
     */
    public static void tick() {
        if (!ModConfig.INSTANCE.mining.hud.readyAlert) return;
        if (readyAnnounced || readyAt <= 0L) return;
        if (System.currentTimeMillis() < readyAt + READY_ALERT_DELAY_MILLIS) return;

        readyAnnounced = true;
        String name = ability();
        AlertBanner.show("Mining Ability Ready!!!", name.isBlank() ? "" : name, "",
                READY_ALERT_COLOUR, READY_ALERT_MILLIS);
    }

    /** Die Restzeit, laufend gerechnet: "26s", "1m 20s" oder "Ready" */
    public static String cooldown() {
        if (readyAt <= 0L) return "";
        long left = (readyAt - System.currentTimeMillis() + 999L) / 1000L;
        if (left <= 0L) return "Ready";
        if (left < 60L) return left + "s";
        return (left / 60L) + "m " + (left % 60L) + "s";
    }

    /**
     * Den Zielzeitpunkt aus dem setzen, was die Tab-Liste sagt.
     *
     * Nur beim Sprung der Quelle - sonst wanderte der Zeitpunkt bei jedem Durchlauf ein
     * Stueck nach hinten, und die Anzeige bliebe stehen.
     */
    private static void noteCooldown(String text) {
        long seconds = parseSeconds(text);
        if (seconds < 0L) return;
        if (seconds == lastStatedSeconds) return;
        lastStatedSeconds = seconds;
        readyAt = System.currentTimeMillis() + seconds * 1000L;
        // Ein neuer Cooldown heisst: die naechste Bereitschaft ist wieder eine Meldung wert
        if (seconds > 0L) readyAnnounced = false;
    }

    /** "26s", "1m 20s", "2m" oder "Ready" in Sekunden. -1, wenn nichts davon passt */
    private static long parseSeconds(String text) {
        String clean = text.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty() || clean.startsWith("ready") || clean.startsWith("available")) return 0L;
        Matcher m = DURATION.matcher(clean);
        long total = -1L;
        while (m.find()) {
            long value = Long.parseLong(m.group("value"));
            String unit = m.group("unit");
            long factor = switch (unit) {
                case "h" -> 3600L;
                case "m" -> 60L;
                default -> 1L;
            };
            total = (total < 0 ? 0 : total) + value * factor;
        }
        return total;
    }

    public static String skyMall() {
        return cfg().lastSkyMall == null ? "" : cfg().lastSkyMall;
    }
}
