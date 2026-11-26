package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.PowderGemstones;
import com.shokiteufel.shokimod.data.ModConfig.PowderGoblinEggs;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat Filter Types > Powder Mining: die Truhen der Bergbau-Inseln. Der Block der Belohnungen ist abgeschlossen, bevor ein Chat-Filter ihn sieht - hier
 * steht, was mit seinen Zeilen geschieht ({@link WrapperBlocks} sagt, wann man im Block ist).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PowderMiningChatFilter}, {@code ChatFilter}). Von dort stammen die Regeln: die drei Zeilen vor der Truhe
 * ("You uncovered a treasure chest!" ...) und die Warnung zur Abbaustaerke fallen weg; im Block fallen die leeren Zeilen und die Ueberschriften weg; eine
 * Belohnung faellt weg, wenn sie unter der Schwelle (Powder, Essence), in der Liste der unwichtigen (Seile, Kompasse ...), unter der gewaehlten Farbe
 * (Goblin-Eier) oder unter der gewaehlten Guete (Edelsteine) liegt; was bleibt, steht als eine kurze Zeile "+ 537 Gemstone Powder". Die Umsetzung ist
 * eigene Arbeit: Java, Muster fuer die Zeile ohne Farbcodes (die Farbe der kurzen Zeile kommt aus dem Namen der Belohnung), und eine Abweichung:
 * Bei SkyHanni blendet "Show all" bei den Edelsteinen die rohen trotzdem aus (der Kommentar dort sagt das Gegenteil), hier zeigt es alle.
 */
final class PowderMiningFilter {

    private PowderMiningFilter() {
    }

    /** Die vier Zeilen vor und nach der Truhe, die immer wegfallen, wenn der Filter an ist */
    private static final String UNCOVERED = "You uncovered a treasure chest!";
    private static final String PICKED = "You have successfully picked the lock on this chest!";
    private static final String ALREADY_LOOTED = "This chest has already been looted.";
    private static final ChatRule BREAKING_POWER = ChatRule.regex("Breaking Power of ", "You need a tool with a Breaking Power of \\d+ to mine .+");

    private static final String AMOUNT = "(?: x([\\d,]+))?";
    private static final Pattern POWDER = Pattern.compile("(?:Gemstone|Mithril) Powder" + AMOUNT);
    private static final Pattern ESSENCE = Pattern.compile("(?:Gold|Diamond) Essence" + AMOUNT);
    private static final Pattern GOBLIN_EGG = Pattern.compile("(?:([a-zA-Z]+) )?Goblin Egg" + AMOUNT);
    private static final Pattern GEMSTONE = Pattern.compile(". (Rough|Flawed|Fine|Flawless) (Ruby|Amethyst|Jade|Amber|Sapphire|Topaz|Jasper) Gemstone" + AMOUNT);
    /** Jede Belohnung: der Name (so kurz wie moeglich), dahinter die Menge */
    private static final Pattern ANY_REWARD = Pattern.compile("(.+?)" + AMOUNT);

    /** Die Belohnungen, die eine eigene Wahl haben, in der Reihenfolge der Schalter (siehe {@link #simpleHidden}) */
    private static final Pattern[] SIMPLE = {
            Pattern.compile("Ascension Rope" + AMOUNT),
            Pattern.compile("Wishing Compass" + AMOUNT),
            Pattern.compile("Oil Barrel" + AMOUNT),
            Pattern.compile("Prehistoric Egg" + AMOUNT),
            Pattern.compile("Pickonimbus 2000" + AMOUNT),
            Pattern.compile("Jungle Heart" + AMOUNT),
            Pattern.compile("Sludge Juice" + AMOUNT),
            Pattern.compile("Yoggie" + AMOUNT),
            Pattern.compile("(?:FTX 3070|Synthetic Heart|Control Switch|Robotron Reflector|Electron Transmitter|Superlite Motor)" + AMOUNT),
            Pattern.compile("Treasurite" + AMOUNT),
    };

    /** Faellt diese einzelne Zeile weg, ob in einem Block oder nicht? */
    static boolean hidesSingle(String line, ChatFilterTypesCategory c) {
        if (line.equals(UNCOVERED) || line.equals(PICKED) || line.equals(ALREADY_LOOTED)) return true;
        return c.powderStrongerTool && BREAKING_POWER.hits(line);
    }

    /** Gehoert die Zeile zum Kopf des Blocks (die Zeilen "CHEST LOCKPICKED", "LOOT CHEST COLLECTED" und "REWARDS")? Sie sagt auch, dass der Block eine Truhe ist */
    static boolean isBlockHeader(String line) {
        return line.contains("CHEST LOCKPICKED") || line.contains("LOOT CHEST COLLECTED") || line.contains("REWARDS");
    }

    /** Eine Zeile im Block einer Truhe: null heisst, sie bleibt wie sie ist */
    static ChatVerdict inside(String line, ChatFilterTypesCategory c) {
        if (line.isBlank() || isBlockHeader(line)) return ChatVerdict.HIDE;
        return reward(line, c);
    }

    /** Eine Belohnung: weg, kurz geschrieben, oder null (bleibt wie sie ist) */
    static ChatVerdict reward(String line, ChatFilterTypesCategory c) {
        // Alle Belohnungen stehen mit vier Leerzeichen eingerueckt
        if (!line.startsWith("    ")) return null;
        String reward = line.substring(4);
        Matcher m = POWDER.matcher(reward);
        if (m.matches()) {
            if (c.powderThreshold >= 60000) return ChatVerdict.HIDE;
            // Bei 0 ("Show all") bleibt die Zeile, wie sie ist - bei SkyHanni ebenso
            if (c.powderThreshold <= 0) return null;
            return amount(m.group(1)) < c.powderThreshold ? ChatVerdict.HIDE : compact(reward);
        }
        m = ESSENCE.matcher(reward);
        if (m.matches()) {
            if (c.essenceThreshold >= 20) return ChatVerdict.HIDE;
            if (c.essenceThreshold <= 0) return null;
            return amount(m.group(1)) < c.essenceThreshold ? ChatVerdict.HIDE : compact(reward);
        }
        for (int i = 0; i < SIMPLE.length; i++) {
            if (SIMPLE[i].matcher(reward).matches()) return simpleHidden(i, c) ? ChatVerdict.HIDE : compact(reward);
        }
        m = GOBLIN_EGG.matcher(reward);
        if (m.matches()) return goblinEgg(m.group(1), c.powderGoblinEggs, reward);
        m = GEMSTONE.matcher(reward);
        if (m.matches()) return gemstone(m.group(1), m.group(2), c, reward);
        return null;
    }

    private static boolean simpleHidden(int index, ChatFilterTypesCategory c) {
        switch (index) {
            case 0:
                return c.powderAscensionRope;
            case 1:
                return c.powderWishingCompass;
            case 2:
                return c.powderOilBarrel;
            case 3:
                return c.powderPrehistoricEgg;
            case 4:
                return c.powderPickonimbus;
            case 5:
                return c.powderJungleHeart;
            case 6:
                return c.powderSludgeJuice;
            case 7:
                return c.powderYoggie;
            case 8:
                return c.powderRobotParts;
            default:
                return c.powderTreasurite;
        }
    }

    /** Ein Goblin-Ei: "Green Goblin Egg" hat die Farbe Green; ein Ei ohne Farbe wird nie gezeigt (ausser bei "Show all") */
    private static ChatVerdict goblinEgg(String colour, PowderGoblinEggs entry, String reward) {
        if (entry == null || entry == PowderGoblinEggs.SHOW_ALL) return compact(reward);
        if (entry == PowderGoblinEggs.HIDE_ALL) return ChatVerdict.HIDE;
        if (colour == null) return ChatVerdict.HIDE;
        switch (colour.toLowerCase(Locale.ROOT)) {
            case "green":
                return entry.ordinal() > PowderGoblinEggs.GREEN_UP.ordinal() ? ChatVerdict.HIDE : compact(reward);
            case "yellow":
                return entry.ordinal() > PowderGoblinEggs.YELLOW_UP.ordinal() ? ChatVerdict.HIDE : compact(reward);
            case "red":
                return entry.ordinal() > PowderGoblinEggs.RED_UP.ordinal() ? ChatVerdict.HIDE : compact(reward);
            default:
                // Blau gibt es immer; eine unbekannte Farbe wird nicht ausgeblendet
                return compact(reward);
        }
    }

    /** Ein Edelstein: die Wahl gilt je Sorte; rohe Steine fallen weg, ausser bei "Show all" */
    private static ChatVerdict gemstone(String tier, String gem, ChatFilterTypesCategory c, String reward) {
        PowderGemstones entry = entryFor(gem, c);
        if (entry == PowderGemstones.HIDE_ALL) return ChatVerdict.HIDE;
        if (entry == null || entry == PowderGemstones.SHOW_ALL) return compact(reward);
        switch (tier) {
            case "Rough":
                return ChatVerdict.HIDE;
            case "Flawed":
                return entry.ordinal() > PowderGemstones.FLAWED_UP.ordinal() ? ChatVerdict.HIDE : compact(reward);
            case "Fine":
                return entry.ordinal() > PowderGemstones.FINE_UP.ordinal() ? ChatVerdict.HIDE : compact(reward);
            default:
                return compact(reward);
        }
    }

    private static PowderGemstones entryFor(String gem, ChatFilterTypesCategory c) {
        switch (gem) {
            case "Ruby":
                return c.powderRuby;
            case "Sapphire":
                return c.powderSapphire;
            case "Amber":
                return c.powderAmber;
            case "Amethyst":
                return c.powderAmethyst;
            case "Jade":
                return c.powderJade;
            case "Topaz":
                return c.powderTopaz;
            default:
                return c.powderJasper;
        }
    }

    /** Die Menge hinter dem "x"; fehlt sie, ist es eins */
    private static long amount(String digits) {
        if (digits == null || digits.isEmpty()) return 1L;
        try {
            return Long.parseLong(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    /** Die kurze Zeile: "+ 537 Gemstone Powder", mit der Farbe der Belohnung; ohne Menge "+ Wishing Compass" */
    static ChatVerdict compact(String reward) {
        Matcher m = ANY_REWARD.matcher(reward);
        if (!m.matches()) return null;
        String name = m.group(1);
        String amount = m.group(2);
        String head = amount == null ? "§a+§r " : "§a+ §b" + amount + "§r ";
        return ChatVerdict.replace(head + colourOf(name) + name);
    }

    /** Die Farbe, in der Hypixel die Belohnung schreibt; im Klartext ist sie weg, deshalb nach dem Namen */
    static String colourOf(String name) {
        if (name.contains("Goblin Egg")) {
            if (name.startsWith("Green ")) return "§a";
            if (name.startsWith("Yellow ")) return "§e";
            if (name.startsWith("Red ")) return "§c";
            if (name.startsWith("Blue ")) return "§3";
            return "§9";
        }
        if (name.endsWith(" Gemstone")) {
            if (name.contains(" Rough ")) return "§f";
            if (name.contains(" Flawed ")) return "§a";
            if (name.contains(" Fine ")) return "§9";
            if (name.contains(" Flawless ")) return "§5";
        }
        if (name.startsWith("Gemstone Powder") || name.endsWith("Essence")) return "§d";
        if (name.startsWith("Mithril Powder")) return "§2";
        switch (name) {
            case "Ascension Rope":
            case "FTX 3070":
            case "Synthetic Heart":
            case "Control Switch":
            case "Robotron Reflector":
            case "Electron Transmitter":
            case "Superlite Motor":
                return "§9";
            case "Wishing Compass":
            case "Oil Barrel":
            case "Sludge Juice":
            case "Yoggie":
                return "§a";
            case "Prehistoric Egg":
                return "§f";
            case "Pickonimbus 2000":
            case "Treasurite":
                return "§5";
            case "Jungle Heart":
                return "§6";
            default:
                return "§f";
        }
    }
}
