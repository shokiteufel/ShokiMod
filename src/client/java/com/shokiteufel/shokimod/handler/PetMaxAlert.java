package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.PetProfitData;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Die Einblendung, wenn ein Pet seine Hoechststufe erreicht.
 *
 * Nachgebaut aus Feesh von MoonTheSadFisher (Apache 2.0, Sleepy-Panda/Feesh), dort
 * features/alerts/PetLevelUpAlert.kt: ein Titel "X is maxed" mit der Stufe darunter,
 * dazu ein Ton und wahlweise eine Zeile, was das Hochziehen eingebracht hat.
 *
 * <p>Ein Unterschied bleibt. Feesh meldet bei Stufe 100 und 200 gleichermassen; hier
 * zaehlt die Hoechststufe des jeweiligen Pets, und die liegt bei Golden, Jade und Rose
 * Dragon bei 200. Ein Golden Dragon auf 100 ist nicht fertig, sondern halb fertig -
 * "is maxed" waere dort schlicht falsch. Welche Stufe die letzte ist, steht in
 * denselben Zahlen, aus denen auch der Pet-Gewinn rechnet.
 */
public final class PetMaxAlert {

    private PetMaxAlert() {
    }

    private static ModConfig.PetMaxCategory cfg() {
        return ModConfig.INSTANCE.chat.petMax;
    }

    /**
     * Ein Pet ist fertig.
     *
     * @param petId   Kennung wie GOLDEN_DRAGON
     * @param rarity  COMMON bis MYTHIC - sie faerbt den Namen
     * @param level   die erreichte Stufe
     */
    public static void show(String petId, String rarity, int level) {
        if (petId == null || !cfg().enabled) return;

        String name = SkyBlockItems.readableName(petId);
        int farbe = SkyBlockItems.rarityColour(rarity) & 0xFFFFFF;

        if (cfg().banner) {
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(cfg().bannerDesign),
                    name + " is maxed", "Level " + level, rarity, farbe, ItemStack.EMPTY);
        }
        if (cfg().chatLine) say(Component.literal(name).withStyle(style -> style.withColor(farbe).withBold(true))
                .append(Component.literal(" is maxed - Level " + level).withStyle(ChatFormatting.WHITE)));

        String ton = cfg().sound;
        if (ton != null && !ton.isBlank()) {
            CustomSoundPlayer.play(ton, AlertVolume.factor(), PetMaxAlert.class);
        }
        if (cfg().showPrice) sayPrice(petId, rarity, level, name, farbe);

        ShokiMod.LOGGER.info("[PetMax] {} reached level {}", petId, level);
    }

    /**
     * Was das Hochziehen eingebracht hat.
     *
     * Feesh zieht dafuer zwei Preise aus dem Auktionshaus und nennt beide. Hier kommen
     * sie aus derselben Auswertung, die auch /shoki petprofit fuellt - der Preis des
     * fertigen Tiers und der des frisch geschluepften, und dazwischen liegt der Gewinn.
     * Fehlt einer der beiden, steht das da, statt eine Zahl zu erfinden.
     */
    private static void sayPrice(String petId, String rarity, int level, String name, int farbe) {
        long fertig = PetProfitData.maxedPrice(petId, rarity, level);
        if (fertig <= 0) {
            say(Component.literal(name).withStyle(style -> style.withColor(farbe))
                    .append(Component.literal(" - no price on the auction house right now.")
                            .withStyle(ChatFormatting.GRAY)));
            return;
        }
        long frisch = PetProfitData.freshPrice(petId, rarity);
        Component zeile = Component.literal("Worth ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(ItemValue.format(fertig)).withStyle(ChatFormatting.GOLD));
        if (frisch > 0 && frisch < fertig) {
            zeile = ((net.minecraft.network.chat.MutableComponent) zeile)
                    .append(Component.literal(", levelling it brought ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(ItemValue.format(fertig - frisch)).withStyle(ChatFormatting.GREEN));
        }
        say(((net.minecraft.network.chat.MutableComponent) zeile)
                .append(Component.literal(".").withStyle(ChatFormatting.YELLOW)));
    }

    private static void say(Component text) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                .withStyle(ChatFormatting.DARK_AQUA).append(text));
    }

    /** Der Knopf in den Einstellungen: einmal vorfuehren */
    public static void test() {
        boolean vorher = cfg().enabled;
        cfg().enabled = true;
        show("GOLDEN_DRAGON", "LEGENDARY", 200);
        cfg().enabled = vorher;
    }

    /** Die Seltenheit, wie sie in der Kennung steht - fuer den Testknopf */
    static String rarityName(int tier) {
        return switch (tier) {
            case 0 -> "COMMON";
            case 1 -> "UNCOMMON";
            case 2 -> "RARE";
            case 3 -> "EPIC";
            case 5 -> "MYTHIC";
            default -> "LEGENDARY";
        };
    }

    static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
