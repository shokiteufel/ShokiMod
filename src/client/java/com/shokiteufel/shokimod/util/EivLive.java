package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig.EivPriceSource;
import com.shokiteufel.shokimod.util.EivData.Reforge;
import com.shokiteufel.shokimod.util.EivData.Upgrade;
import com.shokiteufel.shokimod.util.EivData.ValueData;
import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimated Item Value im Spiel: die Preise, Namen und Tabellen, die der Rechner ({@link EivCalculator}) fragt, aus dem, was die Mod schon hat -
 * Basarpreise und niedrigste BIN des Profit-Trackers ({@link ItemValue}), die Item-Liste ({@link ItemNames}) und die Tabellen aus {@link EivData}.
 *
 * Der Preis folgt SkyHannis Regel: Waehrung zuerst, ein paar feste Preise, dann der Basar nach der gewaehlten Quelle (Sofortkauf, Sofortverkauf), dann
 * die niedrigste BIN, dann der Preis beim NPC.
 */
public final class EivLive implements EivCalculator.Env {

    public static final EivLive INSTANCE = new EivLive();

    private static final Pattern PET_LEVEL = Pattern.compile("\\[Lvl (\\d+)]");

    private EivLive() {
    }

    // ------------------------------------------------------------------ Gegenstand

    /** Der Gegenstand fuer den Rechner, oder null, wenn es kein SkyBlock-Gegenstand ist */
    public static EivCalculator.Subject subjectOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        String raw = tag.getStringOr("id", "");
        if (raw.isEmpty()) return null;
        String id = SkyBlockItems.idOf(stack);
        if (id == null) return null;
        String name = stack.getHoverName().getString();
        String priceId = id;
        if (raw.equals("PET")) {
            Matcher m = PET_LEVEL.matcher(name);
            if (m.find()) priceId = id + "+" + m.group(1);
        }
        return new EivCalculator.Subject(id, raw, priceId, tag, SkyBlockItems.loreOf(stack), name);
    }

    // ------------------------------------------------------------------ Preise

    @Override
    public Double price(String id, EivPriceSource source) {
        if (id == null || id.isEmpty()) return null;
        if (id.equals("SKYBLOCK_COIN")) return 1.0;
        switch (id) {
            case "GEMSTONE_COLLECTION", "JASPER_CRYSTAL", "RUBY_CRYSTAL" -> {
                return 0.0;
            }
            case "WISP_POTION" -> {
                return 20_000.0;
            }
            case "ENCHANTED_HAY_BLOCK" -> {
                return 7_776.0;
            }
            case "TIGHTLY_TIED_HAY_BALE" -> {
                return 1_119_744.0;
            }
            default -> {
                // weiter unten
            }
        }
        if (source != EivPriceSource.NPC_SELL) {
            for (String candidate : SkyBlockItems.priceCandidates(id)) {
                BazaarPrice bazaar = ItemValue.bazaarPrice(candidate);
                if (bazaar != null) {
                    double value = source == EivPriceSource.INSTANT_BUY ? bazaar.sellOrder() : bazaar.instantSell();
                    if (value > 0) return value;
                }
            }
            for (String candidate : SkyBlockItems.priceCandidates(id)) {
                Double bin = ItemValue.LOWEST_BIN.get(candidate);
                if (bin != null && bin > 0) return bin;
            }
            if (id.equals("JACK_O_LANTERN")) {
                Double pumpkin = price("PUMPKIN", source);
                return (pumpkin == null ? 0.0 : pumpkin) + 1;
            }
        }
        // 6,8 fuer manche Spieler; der NPC zahlt 7
        if (id.equals("GOLDEN_CARROT")) return 7.0;
        double npc = ItemNames.npcSellPrice(id);
        return npc > 0 ? npc : null;
    }

    // ------------------------------------------------------------------ Namen

    private static String words(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return out.toString();
    }

    private static String rarityColour(String tier) {
        if (tier == null) return "§f";
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            case "SPECIAL", "VERY_SPECIAL" -> "§c";
            case "SUPREME" -> "§4";
            default -> "§f";
        };
    }

    /** Der Name zu einer Kennung, in der Farbe der Seltenheit; Buecher als "Sharpness VI" in Blau, Ultimates in Rosa, Essenzen in Rosa */
    static String coloured(String id) {
        if (id.equals("SKYBLOCK_COIN")) return "§6Coins";
        if (id.startsWith("ENCHANTMENT_")) {
            String body = id.substring("ENCHANTMENT_".length());
            boolean ultimate = body.startsWith("ULTIMATE_");
            if (ultimate) body = body.substring("ULTIMATE_".length());
            int underscore = body.lastIndexOf('_');
            String level = "";
            if (underscore > 0 && body.substring(underscore + 1).chars().allMatch(Character::isDigit)) {
                level = " " + EivFormat.roman(Integer.parseInt(body.substring(underscore + 1)));
                body = body.substring(0, underscore);
            }
            return (ultimate ? "§d§l" : "§9") + words(body) + level;
        }
        if (id.startsWith("ESSENCE_")) return "§d" + words(id.substring("ESSENCE_".length())) + " Essence";
        String name = ItemNames.displayName(id);
        if (name == null || name.isBlank()) name = SkyBlockItems.readableName(id);
        return rarityColour(ItemNames.tier(id)) + name;
    }

    @Override
    public String name(String id) {
        return coloured(id);
    }

    // ------------------------------------------------------------------ Tabellen

    @Override
    public Reforge reforge(String modifier) {
        return EivData.REFORGES.get(modifier);
    }

    @Override
    public Map<Integer, Upgrade> essence(String id) {
        return EivData.ESSENCE.get(id);
    }

    @Override
    public Set<String> essenceIds() {
        EivData.ESSENCE.prefetch();
        return EivData.ESSENCE.keys();
    }

    @Override
    public boolean hasGemSlotData() {
        EivData.GEM_SLOTS.prefetch();
        return EivData.GEM_SLOTS.ready();
    }

    @Override
    public Map<String, List<String>> gemSlots(String id) {
        return EivData.GEM_SLOTS.get(id);
    }

    @Override
    public ValueData values() {
        return EivData.VALUES.get("data");
    }

    @Override
    public boolean stacking(String enchant) {
        return EivData.STACKING.get(enchant) != null;
    }

    @Override
    public boolean bazaar(String id) {
        return ItemValue.bazaarPrice(id) != null;
    }
}
