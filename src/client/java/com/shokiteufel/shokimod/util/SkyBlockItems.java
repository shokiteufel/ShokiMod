package com.shokiteufel.shokimod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welches SkyBlock-Item hier eigentlich liegt.
 *
 * Ein Stapel im Inventar traegt seine Hypixel-Kennung in den eigenen Daten des
 * Items ({@code custom_data}). Dort steht bei jedem SkyBlock-Item ein Feld "id" -
 * "ENCHANTED_DIAMOND", "SHARD_QUEEN_BEE", "DEEP_SEA_ORB". Ein Stein aus der Welt
 * hat kein solches Feld; daran erkennt man, was zu SkyBlock gehoert und was nicht.
 *
 * Fuenf Sorten tragen ihre eigentliche Kennung nicht im Feld "id", sondern
 * daneben - dort steht dann nur die Gattung:
 *
 * <ul>
 *   <li>ENCHANTED_BOOK: das Buch selbst ist wertlos, der Preis haengt an der
 *       Verzauberung. ENCHANTMENT_SHARPNESS_6 statt ENCHANTED_BOOK.</li>
 *   <li>PET: Art und Seltenheit stehen in einem JSON-Text daneben.</li>
 *   <li>RUNE, ATTRIBUTE_SHARD, POTION: Name und Stufe stehen in Unterlisten.</li>
 * </ul>
 *
 * Die Zerlegung entspricht Skysofts SkyBlockItemId (LGPL-3.0, Akinsoft); ohne sie
 * lagen alle Buecher, alle Pets und alle Tranke jeweils auf einem Haufen.
 */
public final class SkyBlockItems {

    private SkyBlockItems() {
    }

    /**
     * Die Kennung eines Stapels, oder null wenn es kein SkyBlock-Item ist.
     *
     * Laeuft einmal je Stapel und Tick - deshalb kein Zerlegen von Text, wo ein
     * Blick in die Daten genuegt.
     */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;

        CompoundTag tag = data.copyTag();
        String id = tag.getStringOr("id", "");
        if (id.isEmpty()) return null;

        return switch (id) {
            case "ENCHANTED_BOOK" -> firstNotNull(enchantmentId(tag), id);
            case "PET" -> firstNotNull(petId(tag), id);
            case "RUNE" -> firstNotNull(runeId(tag), id);
            case "ATTRIBUTE_SHARD" -> firstNotNull(attributeShardId(tag), id);
            case "POTION" -> firstNotNull(potionId(tag), id);
            default -> id;
        };
    }

    /**
     * Die Farbe, in der das Spiel den Namen dieses Gegenstands schreibt - oder 0.
     *
     * SkyBlock faerbt jeden Item-Namen nach seiner Seltenheit; ein blauer Name ist
     * RARE, ein lila EPIC. Das ist die genaueste Quelle, die es gibt: sie kommt vom
     * Server selbst und gilt auch fuer Shards, Pets und Farben, die in keiner Liste
     * stehen.
     */
    public static int nameColour(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        return colourOf(stack.getHoverName());
    }

    /** Die erste Farbe, die in einem Text vorkommt - der Name selbst oder eines seiner Stuecke */
    private static int colourOf(Component text) {
        if (text == null) return 0;
        TextColor colour = text.getStyle().getColor();
        if (colour != null) return 0xFF000000 | colour.getValue();
        for (Component sibling : text.getSiblings()) {
            int found = colourOf(sibling);
            if (found != 0) return found;
        }
        return 0;
    }

    /**
     * Die Farbe einer Seltenheit, wie sie im Spiel aussieht.
     *
     * Die Zuordnung ist Hypixels eigene und in jedem Menue zu sehen: gewoehnlich
     * weiss, ungewoehnlich gruen, selten blau, episch lila, legendaer gold.
     */
    public static int rarityColour(String tier) {
        if (tier == null) return 0;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "COMMON" -> 0xFFFFFFFF;
            case "UNCOMMON" -> 0xFF55FF55;
            case "RARE" -> 0xFF5555FF;
            case "EPIC" -> 0xFFAA00AA;
            case "LEGENDARY" -> 0xFFFFAA00;
            case "MYTHIC" -> 0xFFFF55FF;
            case "DIVINE" -> 0xFF55FFFF;
            case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
            case "SUPREME" -> 0xFFAA0000;
            default -> 0;
        };
    }

    /**
     * Die Farbe, in der ein Name in einer Chatzeile steht.
     *
     * Hypixel schreibt die Farbcodes mit in die Zeile. Gesucht wird der letzte Code
     * vor dem Namen - der gilt fuer ihn. Fuer Shards ist das die einzige Gelegenheit,
     * ihre Seltenheit zu erfahren: sie wandern in die Hunting Box und tauchen als
     * Gegenstand nie auf.
     *
     * Gesucht wird die letzte Erwaehnung des Namens, nicht die erste. In der
     * CHARM-Zeile steht er zweimal - "You charmed the Wiki Tiki and received 3 Wiki
     * Tiki Shards!" -, und die erste ist das Tier, nicht der Shard. Dessen Farbe ist
     * die des Mobs, und die stand dann rot im Kasten.
     */
    public static int colourInLine(String formatted, String name) {
        if (formatted == null || name == null || name.isBlank()) return 0;
        int at = formatted.lastIndexOf(name);
        if (at < 0) return 0;

        for (int i = at - 2; i >= 0; i--) {
            if (formatted.charAt(i) != '\u00a7') continue;
            int colour = legacyColour(formatted.charAt(i + 1));
            if (colour != 0) return colour;
        }
        return 0;
    }

    /** Die sechzehn Farbcodes des Spiels */
    private static int legacyColour(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            default -> 0;
        };
    }

    /**
     * Womit man den Preis sucht: die Kennung selbst und, wo es sie gibt, die
     * Schreibweise der anderen Liste.
     *
     * Der Basar fuehrt Attributsplitter ohne Stufe, das Auktionshaus mit. Wer nur
     * eine Schreibweise kennt, findet die Haelfte nicht.
     */
    public static List<String> priceCandidates(String itemId) {
        if (itemId == null || itemId.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(2);
        out.add(itemId);
        int semicolon = itemId.indexOf(';');
        if (semicolon > 0) out.add(itemId.substring(0, semicolon));
        return out;
    }

    /**
     * ENCHANTED_DIAMOND wird zu "Enchanted Diamond".
     *
     * Fuer alles, was Hypixels eigene Item-Liste nicht kennt - und das sind gerade
     * die Sorten, die im Kasten am haeufigsten stehen: Shards, Buecher, Pets und
     * Farben fehlen dort samt und sonders. Sie werden deshalb so geschrieben, wie
     * sie im Spiel heissen: "Queen Bee Shard", nicht "Shard Queen Bee".
     */
    public static String readableName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";

        String rest = itemId;
        String tier = "";
        int semicolon = rest.indexOf(';');
        if (semicolon > 0) {
            tier = rest.substring(semicolon + 1);
            rest = rest.substring(0, semicolon);
        }

        // Ein Buch traegt Verzauberung und Stufe in der Kennung
        if (rest.startsWith("ENCHANTMENT_")) {
            String body = rest.substring("ENCHANTMENT_".length());
            int underscore = body.lastIndexOf('_');
            if (underscore > 0) {
                String level = body.substring(underscore + 1);
                if (level.chars().allMatch(Character::isDigit)) {
                    return words(body.substring(0, underscore)) + " " + level + " Book";
                }
            }
            return words(body) + " Book";
        }
        if (rest.startsWith("ATTRIBUTE_SHARD_")) {
            return words(rest.substring("ATTRIBUTE_SHARD_".length())) + " Shard";
        }
        if (rest.startsWith("SHARD_")) {
            return words(rest.substring("SHARD_".length())) + " Shard";
        }
        if (rest.startsWith("POTION_")) {
            String name = words(rest.substring("POTION_".length())) + " Potion";
            return tier.isEmpty() || "0".equals(tier) ? name : name + " " + tier;
        }
        if (rest.endsWith("_RUNE")) {
            String name = words(rest.substring(0, rest.length() - "_RUNE".length())) + " Rune";
            return tier.isEmpty() ? name : name + " " + tier;
        }

        String name = words(rest);
        if (name.isEmpty()) return itemId;
        // Was hinter dem Semikolon steht, ist bei einem Pet die Seltenheit
        String rarity = rarityName(tier);
        return rarity.isEmpty() ? name : name + " (" + rarity + ")";
    }

    /** GOLDEN_DRAGON wird zu "Golden Dragon" */
    private static String words(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (String part : raw.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return out.toString();
    }

    private static String rarityName(String tier) {
        return switch (tier) {
            case "0" -> "Common";
            case "1" -> "Uncommon";
            case "2" -> "Rare";
            case "3" -> "Epic";
            case "4" -> "Legendary";
            case "5" -> "Mythic";
            default -> "";
        };
    }

    private static String firstNotNull(String preferred, String fallback) {
        return preferred == null ? fallback : preferred;
    }

    /** Ein Buch mit genau einer Verzauberung traegt deren Kennung; Mehrfachbuecher bleiben Buecher */
    private static String enchantmentId(CompoundTag tag) {
        CompoundTag enchantments = tag.getCompoundOrEmpty("enchantments");
        if (enchantments.size() != 1) return null;
        String name = enchantments.keySet().iterator().next();
        int level = enchantments.getInt(name).orElse(0);
        if (level <= 0) return null;
        return "ENCHANTMENT_" + name.toUpperCase(Locale.ROOT) + "_" + level;
    }

    /** {"type":"GOLDEN_DRAGON","tier":"LEGENDARY", ...} wird zu GOLDEN_DRAGON;4 */
    private static String petId(CompoundTag tag) {
        String info = tag.getStringOr("petInfo", "");
        if (info.isEmpty()) return null;
        try {
            JsonObject pet = JsonParser.parseString(info).getAsJsonObject();
            if (!pet.has("type")) return null;
            String type = pet.get("type").getAsString().toUpperCase(Locale.ROOT);
            String tier = pet.has("tier") ? pet.get("tier").getAsString() : "COMMON";
            return type + ";" + tierIndex(tier);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Dieselbe Reihenfolge, die auch die Preislisten benutzen */
    private static int tierIndex(String tier) {
        return switch (tier == null ? "" : tier.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    private static String runeId(CompoundTag tag) {
        CompoundTag runes = tag.getCompoundOrEmpty("runes");
        if (runes.size() != 1) return null;
        String name = runes.keySet().iterator().next();
        int level = runes.getInt(name).orElse(0);
        if (level <= 0) return null;
        return name.toUpperCase(Locale.ROOT) + "_RUNE;" + level;
    }

    private static String attributeShardId(CompoundTag tag) {
        CompoundTag attributes = tag.getCompoundOrEmpty("attributes");
        if (attributes.size() != 1) return null;
        String name = attributes.keySet().iterator().next();
        if (name.isEmpty()) return null;
        return "ATTRIBUTE_SHARD_" + name.toUpperCase(Locale.ROOT) + ";1";
    }

    private static String potionId(CompoundTag tag) {
        int level = tag.getInt("potion_level").orElse(0);
        String name = tag.getStringOr("potion_name", "");
        if (!name.isEmpty()) return "POTION_" + name.replace(' ', '_').toUpperCase(Locale.ROOT) + ";" + level;

        String potion = tag.getStringOr("potion", "");
        if (!potion.isEmpty()) return "POTION_" + potion.toUpperCase(Locale.ROOT) + ";" + level;

        String type = tag.getStringOr("potion_type", "");
        if (!type.isEmpty()) return "POTION_" + type.toUpperCase(Locale.ROOT);
        return "WATER_BOTTLE";
    }
}
