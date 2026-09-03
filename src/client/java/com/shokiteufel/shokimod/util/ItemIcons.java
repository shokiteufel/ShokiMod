package com.shokiteufel.shokimod.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein Bild fuer eine SkyBlock-Kennung.
 *
 * Die Mod fuehrt kein Item-Repo mit. Was sie hat, ist Hypixels Item-Liste: die
 * nennt je Item das Material - bei Koepfen dazu die Skin-Textur, bei Lederruestung
 * die Farbe. Daraus laesst sich das Bild bauen, das auch das Spiel zeigen wuerde.
 * Was die Liste nicht kennt (Shards, Buecher), bekommt ein passendes Ersatzbild.
 *
 * Die Liste nutzt noch die alten Materialnamen von 1.8 ("SKULL_ITEM", "RAW_FISH").
 * Die haeufigsten werden uebersetzt; der Rest wird kleingeschrieben probiert.
 */
public final class ItemIcons {

    private static final Map<String, ItemStack> cache = new ConcurrentHashMap<>();

    /** Alte Materialnamen, die nicht einfach kleingeschrieben zum heutigen Item werden */
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("SKULL_ITEM", "player_head"),
            Map.entry("INK_SACK", "ink_sac"),
            Map.entry("RAW_FISH", "cod"),
            Map.entry("EMPTY_MAP", "map"),
            Map.entry("WOOD_SWORD", "wooden_sword"),
            Map.entry("WOOD_AXE", "wooden_axe"),
            Map.entry("WOOD_PICKAXE", "wooden_pickaxe"),
            Map.entry("WOOD_HOE", "wooden_hoe"),
            Map.entry("WOOD_SPADE", "wooden_shovel"),
            Map.entry("GOLD_SWORD", "golden_sword"),
            Map.entry("GOLD_AXE", "golden_axe"),
            Map.entry("GOLD_PICKAXE", "golden_pickaxe"),
            Map.entry("GOLD_HOE", "golden_hoe"),
            Map.entry("GOLD_SPADE", "golden_shovel"),
            Map.entry("GOLD_HELMET", "golden_helmet"),
            Map.entry("GOLD_CHESTPLATE", "golden_chestplate"),
            Map.entry("GOLD_LEGGINGS", "golden_leggings"),
            Map.entry("GOLD_BOOTS", "golden_boots"),
            Map.entry("IRON_SPADE", "iron_shovel"),
            Map.entry("DIAMOND_SPADE", "diamond_shovel"),
            Map.entry("STONE_SPADE", "stone_shovel"),
            Map.entry("LEASH", "lead"),
            Map.entry("FIREBALL", "fire_charge"),
            Map.entry("EYE_OF_ENDER", "ender_eye"),
            Map.entry("EXP_BOTTLE", "experience_bottle"),
            Map.entry("SULPHUR", "gunpowder"),
            Map.entry("SNOW_BALL", "snowball"),
            Map.entry("BOOK_AND_QUILL", "writable_book"),
            Map.entry("NETHER_STALK", "nether_wart"),
            Map.entry("SPECKLED_MELON", "glistering_melon_slice"),
            Map.entry("GRILLED_PORK", "cooked_porkchop"),
            Map.entry("PORK", "porkchop"),
            Map.entry("RAW_BEEF", "beef"),
            Map.entry("RAW_CHICKEN", "chicken"),
            Map.entry("CARROT_ITEM", "carrot"),
            Map.entry("POTATO_ITEM", "potato"),
            Map.entry("SEEDS", "wheat_seeds"),
            Map.entry("MELON", "melon_slice"),
            Map.entry("CLAY_BALL", "clay_ball"),
            Map.entry("WATCH", "clock"),
            Map.entry("SKULL", "skeleton_skull"),
            Map.entry("LOG", "oak_log"),
            Map.entry("WOOD", "oak_planks"),
            Map.entry("SAPLING", "oak_sapling"),
            Map.entry("ENCHANTED_BOOK", "enchanted_book"),
            Map.entry("PRISMARINE_SHARD", "prismarine_shard"));

    private ItemIcons() {
    }

    /** Ein Bild fuer die Kennung - immer eines, notfalls ein Ersatz */
    public static ItemStack stackFor(String itemId) {
        if (itemId == null || itemId.isBlank()) return new ItemStack(Items.NETHER_STAR);
        ItemStack cached = cache.get(itemId);
        if (cached != null) return cached.copy();

        ItemStack built = build(itemId);
        // Erst merken, wenn die Liste da war - sonst bliebe der Ersatz fuer immer
        if (ItemNames.info(itemId) != null || !ItemNames.FEED.ready()) {
            if (ItemNames.FEED.ready()) cache.put(itemId, built.copy());
        }
        return built;
    }

    private static ItemStack build(String itemId) {
        ItemNames.Info info = ItemNames.info(itemId);
        if (info == null) return fallback(itemId);

        Item item = itemFor(info.material());
        if (item == null) return fallback(itemId);

        ItemStack stack = new ItemStack(item);
        if (item == Items.PLAYER_HEAD && info.skin() != null && !info.skin().isBlank()) {
            applySkin(stack, info.skin());
        }
        Integer colour = parseColour(info.color());
        if (colour != null && item != Items.PLAYER_HEAD) {
            try {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour));
            } catch (RuntimeException ignored) {
                // Nicht jedes Item laesst sich faerben; dann bleibt es ungefaerbt
            }
        }
        return stack;
    }

    private static Item itemFor(String material) {
        if (material == null || material.isBlank()) return null;
        String path = LEGACY.getOrDefault(material, material.toLowerCase(Locale.ROOT));
        try {
            Identifier id = Identifier.parse("minecraft:" + path);
            Item item = BuiltInRegistries.ITEM.getValue(id);
            return item == null || item == Items.AIR ? null : item;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Die Textur eines Kopfes, so wie Hypixel sie liefert: als Base64-Profil-Eigenschaft */
    private static void applySkin(ItemStack stack, String skin) {
        try {
            PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", skin)));
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(skin.getBytes()), "shokimod", properties);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        } catch (RuntimeException e) {
            // Dann eben ein Kopf ohne Gesicht
        }
    }

    /** "255,0,0" wie in der Item-Liste */
    private static Integer parseColour(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.split(",");
        if (parts.length != 3) return null;
        try {
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            return (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ItemStack fallback(String itemId) {
        if (itemId.startsWith("SHARD_")) return new ItemStack(Items.PRISMARINE_SHARD);
        if (itemId.startsWith("ENCHANTMENT_")) return new ItemStack(Items.ENCHANTED_BOOK);
        return new ItemStack(Items.NETHER_STAR);
    }
}
