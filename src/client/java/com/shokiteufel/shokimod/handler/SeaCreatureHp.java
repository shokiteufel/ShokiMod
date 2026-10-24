package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SeaHpCategory;
import com.shokiteufel.shokimod.data.ModConfig.SeaHpSort;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die HP der Meeresbewohner in der Naehe, als Liste auf dem Bildschirm.
 *
 * <p>Nachgebaut aus Feesh von MoonTheSadFisher (Apache 2.0, Sleepy-Panda/Feesh), und zwar aus der
 * Fassung 1.13.0 - die, die hier installiert ist. Gelesen werden die Namensschilder, die Hypixel ueber
 * jeden Bewohner stellt: "[Lv600] Symbole Name 69M/100M Herz". Wer in 30 Bloecken Umkreis steht (der
 * Reindrake zaehlt immer), gehoert zur Loot-Teilung und kommt in die Liste - sofern er in der Auswahl
 * steht ({@code /shoki seacreatures}, Spalte HP).
 *
 * <p>Drei Unterschiede zu Feesh, alle gewollt: Die Symbole links vom Namen und das Herz hinter den
 * Lebenspunkten fehlen, die Reihenfolge ist waehlbar, und die Namen stehen in der Farbe ihrer Seltenheit
 * (abschaltbar - dann die Farben des Namensschilds wie dort). Alles andere steht wie dort: die Farben der
 * Lebenspunkte, der Stern fuer den Shuriken, die rote Anzeige "[Immune 4s]".
 *
 * <p>Immun: Manche Bewohner nehmen in den ersten fuenf Sekunden kaum Schaden. Feesh schliesst das daraus,
 * dass das Wesen jung ist und man es eben erst gesehen hat - also "nicht auf die Sekunde genau".
 */
public final class SeaCreatureHp {

    /** So weit reicht die Loot-Teilung - wer weiter weg steht, bekommt nichts */
    static final double RANGE = 30.0;
    /** Alle fuenf Ticks nachsehen, wie bei Feesh */
    private static final int SCAN_TICKS = 5;
    private static final int CLEANUP_TICKS = 30 * 20;
    /** So lange bleibt ein gesehener Bewohner im Gedaechtnis */
    private static final long SEEN_MILLIS = 6 * 60 * 1000L;
    private static final int IMMUNITY_TICKS = 20 * 5;
    private static final long IMMUNITY_MILLIS = 5000L;

    /** Wer in den ersten Sekunden kaum Schaden nimmt - die Liste aus Feesh */
    private static final Set<String> IMMUNE = Set.of(
            "Fiery Scuttler", "Thunder", "Vanquisher", "Yeti", "Alligator", "Blue Ringed Octopus",
            "Wiki Tiki", "Titanoboa", "Abyssal Miner", "The Loch Emperor", "Nessie", "Water Hydra",
            "Phantom Fisher", "Grim Reaper", "Great White Shark", "Frog Prince", "Silkbreeze",
            "Giant Isopod");

    /**
     * Wer ohne eigene Wahl in der Liste steht: Feeshs Voreinstellung (1.13.0), achtundzwanzig Stueck.
     * Alles, was nicht darin steht, ist von Haus aus aus.
     */
    private static final Set<String> DEFAULT_ON = Set.of(
            "Abyssal Miner", "Alligator", "Blue Ringed Octopus", "Bloated Mithril Grubber", "Carrot King",
            "Fiery Scuttler", "Frog Prince", "Giant Isopod", "Great White Shark", "Grim Reaper",
            "Jawbus Follower", "Lord Jawbus", "Nessie", "Nutcracker", "Phantom Fisher", "Plhlegblast",
            "Puddle Jumper", "Ragnarok", "Reindrake", "Silkbreeze", "The Loch Emperor", "Thunder",
            "Titanoboa", "Vanquisher", "Water Hydra", "Wiki Tiki", "Wiki Tiki Laser Totem", "Yeti");

    private static final Pattern FORMAT = Pattern.compile("§.");
    /** "Lv600] " - hinter der Stufe geht der eigentliche Name los; die Farbcodes dazwischen sind erlaubt */
    private static final Pattern AFTER_LEVEL = Pattern.compile("\\](?:§.)* ");
    private static final Pattern HEALTH = Pattern.compile("([0-9.,]+[kKmMbB]?)/([0-9.,]+[kKmMbB]?)\\s*❤");
    private static final Pattern NON_NAME = Pattern.compile("[^a-zA-Z\\s'-]");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z0-9]");
    private static final Pattern HEART_WITH_CODES = Pattern.compile("(?:§.)*❤");
    /** Ein Farbcode oder das Zuruecksetzen - Formate wie Fett (§l) sind es nicht und bleiben */
    private static final Pattern COLOUR_OR_RESET = Pattern.compile("(?i)§[0-9a-fr]");

    /**
     * Was ein Namensschild hergibt.
     *
     * @param line     die Zeile, wie das Namensschild sie malt (Name und Rest zusammen)
     * @param nameText nur der Name, mit den Farbcodes des Schilds
     * @param tail     alles hinter dem Namen, mit dem Zustand der Farbcodes davor - so behaelt es seine Farben, auch wenn es
     *                 im Schild zusammen mit dem Namen in einem Stueck stand und die Farbe nur vom Namen erbte
     */
    record Parsed(int id, String name, String line, double hp, double maxHp, String nameText, String tail) {
    }

    /** Was in der Liste steht - samt dem, was fuers Sortieren gebraucht wird */
    public record Mob(int id, String name, String line, double hp, double maxHp, boolean immune,
                      int immuneSeconds, double distance, String nameText, String tail) {

        /** Die fertige Zeile, mit der roten Immune-Anzeige dahinter */
        public String display() {
            String base = coloured();
            if (!immune) return base;
            String timer = immuneSeconds > 0 ? " §f" + immuneSeconds + "s" : "";
            return base + " §c§l[Immune" + timer + "§c§l]";
        }

        /**
         * Der Name in der Farbe seiner Seltenheit - wenn die Einstellung an ist und der Bewohner eine hat. Sonst die Zeile,
         * wie das Namensschild sie malt.
         */
        private String coloured() {
            if (!cfg().rarityColours) return line;
            String code = rarityCode(name);
            return code == null ? line : paint(nameText, code) + tail;
        }
    }

    private static volatile List<Mob> shown = List.of();
    private static final Map<Integer, Long> seen = new HashMap<>();
    private static int ticks = 0;
    private static int cleanupTicks = 0;

    private SeaCreatureHp() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SeaCreatureHp::tick);
    }

    private static SeaHpCategory cfg() {
        return ModConfig.INSTANCE.fishing.hp;
    }

    /** Die Zeilen, wie die Liste sie zeigt - hoechstens "Zeilen" an der Zahl */
    public static List<Mob> rows() {
        return shown;
    }

    public static boolean hasRows() {
        return !shown.isEmpty();
    }

    // ---- Die Auswahl ----

    /** Steht dieser Bewohner in der Liste? Ohne eigene Wahl gilt Feeshs Voreinstellung */
    public static boolean wants(SeaCreatures.Creature creature) {
        if (creature == null) return false;
        return cfg().picks.getOrDefault(creature.name(), DEFAULT_ON.contains(creature.name()));
    }

    public static void toggle(SeaCreatures.Creature creature) {
        if (creature == null) return;
        cfg().picks.put(creature.name(), !wants(creature));
        ModConfig.INSTANCE.saveNow();
    }

    /** Wie viele von allen (mit den vier, die nur hier vorkommen) stehen in der Liste */
    public static int pickedCount() {
        int n = 0;
        for (SeaCreatures.Creature c : SeaCreatures.withHpOnly()) {
            if (wants(c)) n++;
        }
        return n;
    }

    // ---- Tick ----

    private static void tick(Minecraft client) {
        if (!FeatureGate.seaHp()) {
            if (!shown.isEmpty()) shown = List.of();
            if (!seen.isEmpty()) seen.clear();
            return;
        }
        if (++cleanupTicks >= CLEANUP_TICKS) {
            cleanupTicks = 0;
            long now = System.currentTimeMillis();
            seen.values().removeIf(at -> now - at > SEEN_MILLIS);
        }
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;

        if (client.level == null || client.player == null || !GameState.Server.isSkyblock()) {
            if (!shown.isEmpty()) shown = List.of();
            return;
        }
        try {
            scan(client);
        } catch (RuntimeException e) {
            // Ein Namensschild, das wir nicht kennen, soll die Liste nicht lahmlegen
            ShokiMod.LOGGER.warn("[SeaHp] scan failed: {}", e.toString());
        }
    }

    private static void scan(Minecraft client) {
        Set<String> wanted = new HashSet<>();
        String map = GameState.Server.map;
        for (SeaCreatures.Creature c : SeaCreatures.withHpOnly()) {
            if (wants(c) && SeaCreatures.availableIn(c.name(), map)) wanted.add(c.name());
        }

        List<Parsed> found = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
            // Billig zuerst: ein Namensschild eines Bewohners traegt "Lv" - alles andere faellt hier raus
            String raw = stand.getCustomName().getString();
            if (!raw.contains("Lv")) continue;
            double distance = client.player.distanceTo(stand);
            // Der Reindrake fliegt aus der Sichtweite der Namensschilder und kommt wieder: er zaehlt immer
            if (distance > RANGE && !raw.contains("Reindrake")) continue;

            String formatted = legacy(stand.getCustomName());
            String plain = plainOf(formatted);
            if (wanted.isEmpty() || !mentions(plain, wanted)) continue;

            Parsed parsed = parse(stand.getId(), formatted, plain);
            if (parsed == null || !wanted.contains(parsed.name())) continue;
            found.add(parsed);
            distances.add(distance);
        }
        remember(found);

        List<Mob> mobs = new ArrayList<>(found.size());
        long now = System.currentTimeMillis();
        for (int i = 0; i < found.size(); i++) {
            mobs.add(withImmunity(client, found.get(i), distances.get(i), now));
        }
        mobs.sort(order(cfg().sort));

        int limit = Math.max(1, Math.min(20, cfg().maxRows));
        List<Mob> next = mobs.size() > limit ? new ArrayList<>(mobs.subList(0, limit)) : mobs;
        if (joined(shown, next)) sound();
        shown = List.copyOf(next);
    }

    private static boolean mentions(String plain, Set<String> names) {
        for (String name : names) {
            if (plain.contains(name)) return true;
        }
        return false;
    }

    /** Wer zum ersten Mal auftaucht, wird vermerkt - daran haengt die Immune-Anzeige */
    private static void remember(List<Parsed> found) {
        long now = System.currentTimeMillis();
        for (Parsed p : found) {
            int mobId = p.id() - 1;
            Long at = seen.get(mobId);
            if (at == null || now - at > SEEN_MILLIS) seen.put(mobId, now);
        }
    }

    private static Mob withImmunity(Minecraft client, Parsed p, double distance, long now) {
        boolean immune = false;
        int seconds = 0;

        if (IMMUNE.contains(p.name())) {
            Entity mob = client.level.getEntity(p.id() - 1);
            int age = mob == null ? 0 : mob.tickCount;
            long first = seen.getOrDefault(p.id() - 1, 0L);
            immune = age <= IMMUNITY_TICKS && now - first <= IMMUNITY_MILLIS;
            seconds = immune ? Math.max(1, (int) Math.ceil((IMMUNITY_TICKS - age) / 20.0)) : 0;
        }
        // Nessie flieht bei halbem Leben und ist dabei unverwundbar - ohne Zeit, sie hat keine
        if ("Nessie".equals(p.name()) && nessieRunsAway(client, p)) {
            immune = true;
            seconds = 0;
        }
        return new Mob(p.id(), p.name(), p.line(), p.hp(), p.maxHp(), immune, seconds, distance, p.nameText(), p.tail());
    }

    private static boolean nessieRunsAway(Minecraft client, Parsed p) {
        Entity mob = client.level.getEntity(p.id() - 1);
        if (!(mob instanceof Sniffer sniffer)) return false;
        return sniffer.getScale() != 2.0f && p.hp() == p.maxHp() * 0.5;
    }

    /**
     * Soll ein Ton spielen? Ja, wenn die Liste laenger wird - nicht, wenn nur der Reindrake dazukommt:
     * Er fliegt immer wieder aus der Sichtweite der Namensschilder und kommt zurueck.
     */
    static boolean joined(List<Mob> before, List<Mob> after) {
        if (after.size() <= before.size()) return false;
        Set<String> known = new HashSet<>();
        for (Mob m : before) known.add(m.name());
        for (Mob m : after) {
            if (!known.contains(m.name()) && !"Reindrake".equals(m.name())) return true;
        }
        return false;
    }

    // ---- Reihenfolge ----

    static Comparator<Mob> order(SeaHpSort sort) {
        Comparator<Mob> byHp = Comparator.comparingDouble(Mob::hp);
        if (sort == SeaHpSort.HIGHEST_RARITY) {
            return Comparator.comparingInt((Mob m) -> -SeaCreatures.rarityRank(m.name())).thenComparing(byHp);
        }
        return byHp;
    }

    // ---- Namensschild lesen ----

    /**
     * Das Namensschild als Text mit Farbcodes.
     *
     * Vor jedem Stueck steht ein Zuruecksetzen: Die Stuecke einer Komponente erben nichts voneinander,
     * ein Text mit Codes aber schon - ohne das Zuruecksetzen bliebe die Fettschrift eines Stuecks im
     * naechsten haengen.
     */
    static String legacy(Component text) {
        StringBuilder sb = new StringBuilder();
        text.visit((Style style, String part) -> {
            sb.append("§r");
            TextColor color = style.getColor();
            if (color != null) {
                int rgb = color.getValue();
                // 26.2: ChatFormatting.isColor()/getColor()/getChar() sind weg; die Zuordnung Farbe -> Code
                // kommt von TextColor.fromLegacyFormat(), und toString() einer ChatFormatting ist "§c"
                for (ChatFormatting f : ChatFormatting.values()) {
                    TextColor legacy = TextColor.fromLegacyFormat(f);
                    if (legacy != null && legacy.getValue() == rgb) {
                        sb.append(f);
                        break;
                    }
                }
            }
            if (style.isObfuscated()) sb.append("§k");
            if (style.isBold()) sb.append("§l");
            if (style.isStrikethrough()) sb.append("§m");
            if (style.isUnderlined()) sb.append("§n");
            if (style.isItalic()) sb.append("§o");
            sb.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static String plainOf(String formatted) {
        return FORMAT.matcher(formatted).replaceAll("");
    }

    /**
     * Ein Namensschild lesen - oder null, wenn es keines von einem Bewohner ist.
     *
     * Gebaut nach Feesh 1.13.0: Es muss "[Lv" tragen und ein Herz (nur der Puddle Jumper hat keines).
     * Der Name ist, was vor den Lebenspunkten steht, ohne Symbole und ohne "Corrupted".
     *
     * @param formatted das Schild mit Farbcodes
     * @param plain     dasselbe ohne
     */
    static Parsed parse(int id, String formatted, String plain) {
        if (plain == null || plain.isEmpty() || !plain.contains("[Lv") || !plain.contains("]")) return null;
        if (!plain.contains("❤") && !plain.contains("Puddle Jumper")) return null;

        String cleaned = formatted
                .replace("§e﴾ ", "")
                .replace(" §e﴿", "")
                .replace("§5§ka", "")
                .trim();
        String[] parts = AFTER_LEVEL.split(cleaned, 2);
        if (parts.length < 2) return null;
        String shortName = parts[1].replace("Corrupted ", "");

        String[] tokens = shortName.split(" ");
        int hpAt = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (plainOf(tokens[i]).contains("/")) {
                hpAt = i;
                break;
            }
        }
        int nameEnd = hpAt >= 0 ? hpAt : tokens.length;

        // Die Symbole links vom Namen: ein Wort ohne einen Buchstaben davor
        List<String> nameTokens = new ArrayList<>();
        for (int i = 0; i < nameEnd; i++) {
            if (!plainOf(tokens[i]).isBlank()) nameTokens.add(tokens[i]);
        }
        if (!nameTokens.isEmpty() && !HAS_LETTER.matcher(plainOf(nameTokens.get(0))).find()) nameTokens.remove(0);
        if (nameTokens.isEmpty()) return null;

        String base = NON_NAME.matcher(plainOf(String.join(" ", nameTokens))).replaceAll("").trim();
        if (base.isEmpty()) return null;

        double hp = 0.0;
        double maxHp = 0.0;
        Matcher m = HEALTH.matcher(plainOf(shortName));
        if (m.find()) {
            hp = number(m.group(1));
            maxHp = number(m.group(2));
        }

        String nameText = String.join(" ", nameTokens);
        StringBuilder tail = new StringBuilder();
        if (hpAt >= 0) {
            // Ohne das Herz: "§a69M§f/§a100M" statt "§a69M§f/§a100M§c❤"
            String lives = HEART_WITH_CODES.matcher(tokens[hpAt]).replaceAll("");
            // Vor jedem Stueck steht, was an seiner Stelle galt: Stand es im Schild im selben Stueck wie der Name, erbte es dessen
            // Farbe - und die wird beim Faerben des Namens ausgetauscht
            if (!plainOf(lives).isBlank()) tail.append(' ').append(stateBefore(tokens, hpAt)).append(lives);
            // Der Stern des Shurikens bleibt; was sonst noch hinter dem Herz steht, nicht
            for (int i = hpAt + 1; i < tokens.length; i++) {
                if (plainOf(tokens[i]).contains("✯")) tail.append(' ').append(stateBefore(tokens, i)).append(tokens[i]);
            }
        }
        return new Parsed(id, base, nameText + tail, hp, maxHp, nameText, tail.toString());
    }

    /**
     * Die Farbcodes, die nach diesem Text gelten: der letzte Farbcode und die Formate danach.
     *
     * So rechnet auch das Spiel beim Zeichnen: Ein Zuruecksetzen loescht alles, ein Farbcode loescht die Formate davor.
     */
    static String stateAfter(String text) {
        String colour = "";
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '§') continue;
            char code = Character.toLowerCase(text.charAt(++i));
            if (code == 'r') {
                colour = "";
                formats.setLength(0);
            } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                colour = "§" + code;
                formats.setLength(0);
            } else if ("klmno".indexOf(code) >= 0) {
                formats.append('§').append(code);
            }
        }
        return "§r" + colour + formats;
    }

    /** Die Farbcodes, die vor dem Wort mit dieser Nummer gelten */
    private static String stateBefore(String[] tokens, int index) {
        return stateAfter(String.join(" ", java.util.Arrays.copyOfRange(tokens, 0, index)));
    }

    /**
     * Der Farbcode der Seltenheit dieses Bewohners, oder null.
     *
     * Die Farbe steht in der Tabelle (SeaCreatures) und ist dort Hypixels Farbe der Seltenheit: gewoehnlich weiss, ungewoehnlich gruen, selten
     * blau, episch lila, legendaer gold, mythisch rosa. Wer nicht in der Tabelle steht - die vier, die man nur sieht und deren Seltenheit nur
     * angenommen ist -, hat keine und behaelt die Farben seines Namensschilds.
     */
    static String rarityCode(String name) {
        SeaCreatures.Creature creature = SeaCreatures.byName(name);
        if (creature == null) return null;
        // 26.2: ChatFormatting.isColor()/getColor()/getChar() sind weg - wie in legacy() ueber TextColor.fromLegacyFormat,
        // und toString() einer ChatFormatting ist "§c"
        for (ChatFormatting f : ChatFormatting.values()) {
            TextColor legacy = TextColor.fromLegacyFormat(f);
            if (legacy != null && legacy.getValue() == creature.colour()) return f.toString();
        }
        return null;
    }

    /**
     * Den Namen in diese Farbe bringen: Jeder Farbcode im Text wird dieser, und nach jedem Zuruecksetzen gilt sie wieder. Formate wie Fett
     * bleiben. Vorn steht sie noch einmal, falls der Text gar keinen Code traegt.
     */
    static String paint(String text, String code) {
        return code + COLOUR_OR_RESET.matcher(text).replaceAll(match ->
                java.util.regex.Matcher.quoteReplacement(match.group().equalsIgnoreCase("§r") ? "§r" + code : code));
    }

    /** "69M", "6.3M", "521.8k", "75,000" -> Zahl */
    static double number(String text) {
        String t = text.replace(",", "").trim();
        if (t.isEmpty()) return 0.0;
        double factor = 1.0;
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        if (last == 'k') factor = 1_000.0;
        else if (last == 'm') factor = 1_000_000.0;
        else if (last == 'b') factor = 1_000_000_000.0;
        if (factor != 1.0) t = t.substring(0, t.length() - 1);
        try {
            // Auf ganze Lebenspunkte gerundet: 521.8 * 1000 ist in Gleitkomma nicht genau 521800
            return Math.round(Double.parseDouble(t) * factor);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ---- Ton ----

    private static void sound() {
        SeaHpCategory c = cfg();
        if (!c.sound) return;
        play();
    }

    /** Der Ton, ob er an ist oder nicht - der Knopf in den Einstellungen */
    public static void play() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        String file = cfg().soundFile;
        if (file != null && !file.isBlank() && GardenReminder.soundExists(file.trim())) {
            CustomSoundPlayer.play(file.trim(), AlertVolume.factor(), SeaCreatureHp.class);
            return;
        }
        // Wie bei Feesh, wenn nichts gewaehlt ist: der Erfahrungskugel-Ton
        client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, AlertVolume.factor(), 1.0f);
    }

    /** Zwei Beispielzeilen fuer den HUD-Editor, wenn gerade nichts in der Naehe steht */
    public static List<String> sample() {
        return List.of(
                "§c§lJawbus Follower §a3M§f/§a3M",
                "§d§lLord Jawbus §a1M§f/§a2M");
    }

    /** Fuer die Probe: die Liste direkt setzen */
    static void setShownForTest(List<Mob> mobs) {
        shown = List.copyOf(mobs);
    }
}
