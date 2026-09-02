package com.shokiteufel.shokimod.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * Die SkyBlock-Kennung eines Items - aus dem Item selbst, nicht aus dem Chat.
 *
 * Hypixel legt sie in denselben Zusatzdaten ab, die auch SkyHanni, Feesh und
 * SkyOcean auslesen: das Component CUSTOM_DATA traegt den alten NBT-Block
 * ExtraAttributes, und darin steht unter "id" die Kennung - HYPERION,
 * ENCHANTED_DIAMOND und so weiter.
 *
 * Der Chat nennt Namen, keine Kennungen: er faerbt sie ein, kuerzt sie, uebersetzt
 * Mengen in Worte. Am Item steht die Kennung unveraendert, egal wie die Meldung
 * dazu aussieht - und auch dann, wenn es gar keine Meldung gibt.
 *
 * Geliefert wird die Kennung, unter der der Basar das Item fuehrt. Fuer die
 * meisten Items ist das die Kennung am Item. Verzauberte Buecher sind die
 * Ausnahme: am Item steht ENCHANTED_BOOK, im Basar ENCHANTMENT_FLASH_1 - die
 * Verzauberung ist die Ware, nicht das Buch.
 */
public final class SkyblockItem {

    private static final String EXTRA_ATTRIBUTES = "ExtraAttributes";
    private static final String ID = "id";
    private static final String ENCHANTMENTS = "enchantments";
    private static final String ENCHANTED_BOOK = "ENCHANTED_BOOK";
    private static final String ENCHANTMENT_PREFIX = "ENCHANTMENT_";

    private SkyblockItem() {
    }

    /**
     * Die Kennung eines Stapels, so wie der Basar sie fuehrt.
     *
     * Ohne SkyBlock-Kennung bleibt der Name aus der Vanilla-Registry - fuer normale
     * Bloecke ist das die richtige Antwort, und der Basar fuehrt sie unter genau
     * diesem Namen in Grossbuchstaben.
     */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag root = data.copyTag();
            CompoundTag extra = root.getCompound(EXTRA_ATTRIBUTES).orElse(root);

            String id = extra.getString(ID).orElse("");
            if (!id.isBlank()) return ENCHANTED_BOOK.equals(id) ? bookId(extra) : id;
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(Locale.ROOT);
    }

    /**
     * Ein Buch mit genau einer Verzauberung heisst im Basar nach ihr.
     *
     * "flash" auf Stufe 1 wird zu ENCHANTMENT_FLASH_1 - derselbe Weg, den SkyHanni
     * geht. Buecher mit mehreren Verzauberungen handelt der Basar nicht; die
     * bleiben ENCHANTED_BOOK und finden keinen Preis, was auch stimmt.
     */
    private static String bookId(CompoundTag extra) {
        CompoundTag enchantments = extra.getCompound(ENCHANTMENTS).orElse(null);
        if (enchantments == null || enchantments.size() != 1) return ENCHANTED_BOOK;

        String name = enchantments.keySet().iterator().next();
        int level = enchantments.getIntOr(name, 0);
        if (name.isBlank() || level <= 0) return ENCHANTED_BOOK;

        return ENCHANTMENT_PREFIX + name.toUpperCase(Locale.ROOT) + "_" + level;
    }
}
