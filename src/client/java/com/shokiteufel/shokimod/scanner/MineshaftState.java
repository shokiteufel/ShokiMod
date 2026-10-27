package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.MineshaftRule;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.MineshaftCorpses;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In welchem Glacite Mineshaft man gerade steht.
 *
 * Hypixel schreibt es in die unterste Zeile der Seitenleiste, hinter Datum und
 * Server: "12/07/25 mini123 TUNG_1". Die vier Buchstaben sind der Bauplan
 * (Tungsten, Peridot, Ruby ...), das Zeichen danach die Ausfuehrung. Beides
 * zusammen sagt, welcher Schacht steht - und damit, wo die Leichen sein koennen.
 *
 * Woher das Muster stammt: SkyBlock API von thatgravyboat (MIT), die es fuer
 * dieselbe Sache liest. Es ist eine Tatsache ueber Hypixels Seitenleiste, kein
 * fremder Code.
 */
public final class MineshaftState {

    /** "12/07/25 mini123AB TUNG_1" - Datum, Server, Bauplan und Ausfuehrung */
    private static final Pattern SHAFT = Pattern.compile(
            "^\\d+/\\d+/\\d+\\s+\\S+\\s+([A-Za-z]{4})_([A-Za-z0-9])$");
    /** Nur alle paar Ticks: die Seitenleiste in Text zu verwandeln ist nicht umsonst */
    private static final int SCAN_INTERVAL_TICKS = 20;

    private static String type = null;
    private static String variant = null;
    private static int ticks = 0;
    /** Hoechstens eine Erklaerung je Schacht - sie soll helfen, nicht zutexten */
    private static boolean explained = false;
    /** Ob in dieser Welt schon gesagt wurde, in welchem Schacht man steht */
    private static boolean announced = false;
    /** Wie viele Durchgaenge in Folge (je etwa eine Sekunde) die Zeile des Schachts fehlte */
    private static int missingScans = 0;
    private static final int MISSING_SCANS_BEFORE_LEFT = 5;
    /** Wie oft in diesem Schacht schon keine Leichen-Zeilen in der Tab-Liste standen */
    private static int blindScans = 0;
    /** So viele Durchgaenge (je etwa eine Sekunde) ohne Zeilen, bevor es als Fehlen gilt */
    private static final int BLIND_SCANS_BEFORE_NOTE = 6;

    private MineshaftState() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MineshaftState::tick);
    }

    /** Der Bauplan des Schachts, vierstellig - oder null, wenn man in keinem steht */
    public static String type() {
        return type;
    }

    /** Die Ausfuehrung: 1, 2 oder C */
    public static String variant() {
        return variant;
    }

    public static boolean inMineshaft() {
        return type != null && variant != null;
    }

    /** Die bekannten Stellen fuer den Schacht, in dem man steht */
    public static List<net.minecraft.core.BlockPos> corpses() {
        return inMineshaft() ? CorpseFinder.spots() : List.of();
    }

    /** "TUNG" wird zu "Tungsten" - die Seitenleiste kuerzt, Menue und Chat nicht */
    public static String readable(String shaft) {
        if (shaft == null) return "";
        return switch (shaft.toUpperCase(java.util.Locale.ROOT)) {
            case "FAIR" -> "Fairy";
            case "LITT" -> "Little";
            case "TITA" -> "Titanium";
            case "TUNG" -> "Tungsten";
            case "UMBE" -> "Umber";
            case "RUBY" -> "Ruby";
            case "JADE" -> "Jade";
            case "SAPP" -> "Sapphire";
            case "AMBE" -> "Amber";
            case "AMET" -> "Amethyst";
            case "TOPA" -> "Topaz";
            case "JASP" -> "Jasper";
            case "OPAL" -> "Opal";
            case "ONYX" -> "Onyx";
            case "CITR" -> "Citrine";
            case "PERI" -> "Peridot";
            case "AQUA" -> "Aquamarine";
            default -> shaft.charAt(0) + shaft.substring(1).toLowerCase(java.util.Locale.ROOT);
        };
    }

    /**
     * Warum hier keine Marker stehen.
     *
     * Ein leerer Schacht sieht aus wie eine kaputte Mod, und genau so wurde er auch
     * gemeldet: "bei Amethyst mit 2 Lapis keine Waypoints, obwohl ab 1 eingestellt".
     * Es lag an der geteilten Liste, die Amethyst nur in der ersten Ausfuehrung kennt -
     * das haette dastehen muessen, statt es raten zu lassen. Also steht es jetzt da,
     * hoechstens eine Zeile je Schacht.
     */
    private static void explain(Minecraft client) {
        ModConfig.MineshaftCategory cfg = ModConfig.INSTANCE.mining.mineshaft;
        if (explained || client.player == null) return;
        if (!cfg.corpseWaypoints && cfg.veins == ModConfig.VeinFilter.OFF) return;

        String where = readable(type) + " " + variant;
        // Die Regel haelt die Erz-Marker zurueck, nicht die Leichen-Stellen
        if (cfg.veins != ModConfig.VeinFilter.OFF && !cfg.shaftAllowed(type)) {
            MineshaftRule rule = cfg.shaftRules.get(type);
            say(client, where + ": your rule wants " + (rule == null ? "more corpses" : rule.describe())
                    + " - no gemstone markers here.");
            explained = true;
            return;
        }
        // Eine Regel, die nicht geprueft werden konnte: ohne Leichen-Zeilen in der Tab-Liste
        // gilt der Schacht als erfuellt - und genau so kam es zu "3 eingestellt, 2 Lapis, trotzdem
        // Marker". Das muss dastehen, statt nach einem Fehler der Regel auszusehen. Ursache ist
        // meist eine andere Mod oder Einstellung, die die Tab-Liste umbaut oder ausblendet
        MineshaftRule gestellt = cfg.shaftRules.get(type);
        if (cfg.veins != ModConfig.VeinFilter.OFF && gestellt != null && !gestellt.empty() && !gestellt.never()
                && MiningState.allCorpses().isEmpty()) {
            if (++blindScans >= BLIND_SCANS_BEFORE_NOTE) {
                say(client, where + ": your rule (" + gestellt.describe() + ") could not be checked - the "
                        + "\"Frozen Corpses\" lines are missing from the tab list, so the gemstone markers "
                        + "show anyway.");
                com.shokiteufel.shokimod.ShokiMod.LOGGER.info(
                        "[Mineshaft] rule {} cannot be checked: no corpse lines in the tab list", gestellt.describe());
                explained = true;
                return;
            }
        }
        if (cfg.corpseWaypoints && corpses().isEmpty()) {
            // Die Liste kommt aus dem Netz; bevor sie da ist, ist leer keine Aussage
            if (!MineshaftCorpses.ready()) return;
            say(client, where + ": no corpse spots known for this layout yet"
                    + " - the ones you find get remembered.");
            explained = true;
            return;
        }
    }

    private static void say(Minecraft client, String text) {
        client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[ShokiMod] ")
                .withStyle(net.minecraft.ChatFormatting.DARK_AQUA)
                .append(net.minecraft.network.chat.Component.literal(text)
                        .withStyle(net.minecraft.ChatFormatting.GRAY)));
    }

    /** Ein Serverwechsel: Jeder Schacht ist eine eigene Welt, und in jeder kommt die Meldung einmal */
    public static void worldJoined() {
        announced = false;
    }

    /**
     * "Tungsten", "Vanguard", "Topaz 2", "Ruby Crystal" - so, wie ihn auch SkyHanni nennt. Die Seitenleiste kuerzt auf vier
     * Buchstaben und eine Ausfuehrung; hier wird daraus der Name. Der Fairy-Schacht heisst im Spiel Vanguard.
     */
    public static String displayName(String shaft, String variant) {
        if (shaft == null) return "";
        String code = shaft.toUpperCase(java.util.Locale.ROOT);
        if (code.equals("LITT")) return "Littlefoot's Den";
        String name = code.equals("FAIR") ? "Vanguard" : readable(code);
        if ("C".equalsIgnoreCase(variant)) return name + " Crystal";
        // Diese Bauplaene gibt es nur in einer Ausfuehrung, dort steht keine Zahl
        boolean single = switch (code) {
            case "TITA", "UMBE", "TUNG", "FAIR", "JASP", "OPAL" -> true;
            default -> false;
        };
        return single || variant == null || variant.isEmpty() ? name : name + " " + variant;
    }

    /** Die Farbe des Schachts in der Meldung - dieselbe, die SkyHanni nimmt (Onyx nicht in Schwarz: das ist im Chat nicht zu lesen) */
    private static net.minecraft.ChatFormatting colour(String shaft) {
        return switch (shaft == null ? "" : shaft.toUpperCase(java.util.Locale.ROOT)) {
            case "TOPA", "CITR" -> net.minecraft.ChatFormatting.YELLOW;
            case "SAPP" -> net.minecraft.ChatFormatting.BLUE;
            case "AMET" -> net.minecraft.ChatFormatting.DARK_PURPLE;
            case "AMBE", "UMBE" -> net.minecraft.ChatFormatting.GOLD;
            case "JADE" -> net.minecraft.ChatFormatting.GREEN;
            case "TITA" -> net.minecraft.ChatFormatting.GRAY;
            case "TUNG", "ONYX" -> net.minecraft.ChatFormatting.DARK_GRAY;
            case "RUBY" -> net.minecraft.ChatFormatting.RED;
            case "AQUA" -> net.minecraft.ChatFormatting.DARK_AQUA;
            case "PERI" -> net.minecraft.ChatFormatting.DARK_GREEN;
            case "JASP" -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
            case "LITT" -> net.minecraft.ChatFormatting.AQUA;
            default -> net.minecraft.ChatFormatting.WHITE;
        };
    }

    /** Dieselben Farben als Zahl, fuer das Banner. ChatFormatting.getColor() gibt es nicht in jeder Version */
    private static int rgb(net.minecraft.ChatFormatting colour) {
        return switch (colour) {
            case YELLOW -> 0xFFFF55;
            case BLUE -> 0x5555FF;
            case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00;
            case GREEN -> 0x55FF55;
            case GRAY -> 0xAAAAAA;
            case DARK_GRAY -> 0x555555;
            case RED -> 0xFF5555;
            case DARK_AQUA -> 0x00AAAA;
            case DARK_GREEN -> 0x00AA00;
            case LIGHT_PURPLE -> 0xFF55FF;
            case AQUA -> 0x55FFFF;
            default -> 0xFFFFFF;
        };
    }

    /**
     * Sagt, in welchem Schacht man ist - einmal je Welt, im Chat, als Banner oder beides (Einstellung "Announce the shaft").
     *
     * Wird bei jedem Lesen der Seitenleiste gerufen, solange man im Schacht steht; das Merkzeichen {@link #announced} haelt es auf
     * einmal. Es faellt erst beim Serverwechsel, nicht wenn die Zeile in der Seitenleiste einen Augenblick fehlt.
     */
    private static void announce(Minecraft client, String shaft, String shaftVariant) {
        ModConfig.MineshaftCategory cfg = ModConfig.INSTANCE.mining.mineshaft;
        if (announced || cfg.announce == ModConfig.ShaftAnnounce.OFF || client.player == null) return;
        announced = true;

        String name = displayName(shaft, shaftVariant);
        net.minecraft.ChatFormatting colour = colour(shaft);
        ShokiMod.LOGGER.info("[Mineshaft] entered {} ({}_{}), announced as {}", name, shaft, shaftVariant, cfg.announce);
        if (cfg.announce.chat()) {
            net.minecraft.network.chat.MutableComponent line = net.minecraft.network.chat.Component.literal("[ShokiMod] ")
                    .withStyle(net.minecraft.ChatFormatting.DARK_AQUA);
            if (shaft.equalsIgnoreCase("LITT")) {
                line.append(net.minecraft.network.chat.Component.literal("You entered ").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(net.minecraft.network.chat.Component.literal(name).withStyle(colour))
                        .append(net.minecraft.network.chat.Component.literal("!").withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                line.append(net.minecraft.network.chat.Component.literal("You entered a ").withStyle(net.minecraft.ChatFormatting.GRAY))
                        .append(net.minecraft.network.chat.Component.literal(name).withStyle(colour))
                        .append(net.minecraft.network.chat.Component.literal(" mineshaft!").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            client.player.sendSystemMessage(line);
        }
        if (cfg.announce.banner()) {
            com.shokiteufel.shokimod.render.DropBanner.show(
                    ModConfig.INSTANCE.chat.banner.designOrDefault(cfg.announceBannerDesign),
                    "Mineshaft", name, "", "", rgb(colour), net.minecraft.world.item.ItemStack.EMPTY, 0d);
        }
    }

    private static void tick(Minecraft client) {
        boolean known = FeatureGate.mineshaftKnown();
        if (!known && !FeatureGate.mineshaftAnnounce()) {
            forget();
            return;
        }
        if (client.player == null || client.level == null || !GameState.Server.isSkyblock()) {
            forget();
            return;
        }
        if (++ticks < SCAN_INTERVAL_TICKS) return;
        ticks = 0;

        String foundType = null;
        String foundVariant = null;
        List<String> lines = ScoreboardUtils.getSidebarLines(client);
        for (int i = 0; i < lines.size(); i++) {
            String clean = ScoreboardUtils.stripColor(lines.get(i)).trim();
            Matcher matcher = SHAFT.matcher(clean);
            if (matcher.matches()) {
                foundType = matcher.group(1).toUpperCase(java.util.Locale.ROOT);
                foundVariant = matcher.group(2).toUpperCase(java.util.Locale.ROOT);
                break;
            }
        }

        if (foundType == null) {
            // Fehlt die Zeile eine Weile, ist man draussen: der naechste Schacht bekommt seine Meldung, auch wenn kein Serverwechsel
            // bemerkt wurde. Ein einzelner Durchgang ohne Zeile ist noch kein Verlassen
            if (++missingScans >= MISSING_SCANS_BEFORE_LEFT) announced = false;
            forget();
            return;
        }
        missingScans = 0;
        // Die Meldung hat ihren eigenen Schalter: Wer nur sie anhat, bekommt nichts von dem, was sonst am Schacht haengt
        announce(client, foundType, foundVariant);
        if (!known) {
            forget();
            return;
        }
        if (!foundType.equals(type) || !foundVariant.equals(variant)) {
            type = foundType;
            variant = foundVariant;
            explained = false;
            blindScans = 0;
            CorpseFinder.forgetVisited();
            OreVeins.forgetReached();
            com.shokiteufel.shokimod.handler.MineshaftProfit.enter(type, variant);
            // Die Liste liegt auf der Platte; der Abruf laeuft nur, wenn sie alt ist
            MineshaftCorpses.prefetch();
            ShokiMod.LOGGER.info("[Mineshaft] {}_{} - {} known corpse spots ({} of them your own finds)",
                    type, variant, corpses().size(), CorpseFinder.learnedCount(type, variant));
        }
        explain(client);
    }

    private static void forget() {
        type = null;
        variant = null;
        explained = false;
        blindScans = 0;
        CorpseFinder.forgetVisited();
        OreVeins.forgetReached();
        // Beim Hinausgehen die eine Zeile - der Aufruf tut nichts, wenn nichts lief
        com.shokiteufel.shokimod.handler.MineshaftProfit.leave();
    }
}
