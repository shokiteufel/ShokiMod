package com.shokiteufel.shokimod.util;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.shokiteufel.shokimod.ShokiMod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein Bild fuer eine SkyBlock-Kennung - so, wie Hypixel es zeigt.
 *
 * Hypixel baut seine Items aus wenigen Vanilla-Grundlagen: Plasma ist Papier, Sorrow
 * eine Ghast-Traene, ein Hyperion ein Eisenschwert. Das eigene Aussehen kommt vom
 * Server-Ressourcenpack, und das Item traegt die Komponente item_model, die auf ein
 * Modell darin zeigt: hypixel_skyblock:item/uncategorized/plasma. Ohne diese
 * Komponente zeichnet der Client die Vanilla-Grundlage - also Papier.
 *
 * Diese Klasse liest die Modelle aus dem Pack, das der Client gerade geladen hat,
 * und haengt die Komponente an. Was das Pack nicht kennt, bekommt bei Koepfen die
 * Skin-Textur aus Hypixels Item-Liste, sonst das Vanilla-Material - und wenn auch
 * das fehlt, ein Ersatzbild.
 */
public final class ItemIcons {

    private static final String PACK_NAMESPACE = "hypixel_skyblock";
    private static final String PACK_ITEM_ROOT = "items/item";
    /** So lange gilt die gelesene Modellliste; das Pack kommt erst nach dem Beitritt */
    private static final long INDEX_TTL_MILLIS = 2 * 60 * 1000L;

    private static final Map<String, ItemStack> cache = new ConcurrentHashMap<>();
    private static Map<String, Identifier> modelIndex = new HashMap<>();
    private static long indexBuiltAt = 0L;

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
        // Erst merken, wenn die Quelle da war - sonst bliebe der Ersatz fuer immer
        boolean settled = itemId.startsWith("SHARD_")
                ? ShardIcons.texture(itemId) != null
                : !modelIndex.isEmpty() && ItemNames.FEED.ready();
        if (settled) cache.put(itemId, built.copy());
        return built;
    }

    private static ItemStack build(String itemId) {
        // Jagd-Shards sind Koepfe mit dem Gesicht des Mobs; die Textur kommt aus dem NEU-Repo
        if (itemId.startsWith("SHARD_")) {
            String texture = ShardIcons.texture(itemId);
            if (texture == null) return fallback(itemId);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            applySkin(head, texture);
            return head;
        }

        ItemNames.Info info = ItemNames.info(itemId);
        Identifier model = modelFor(itemId, info);

        Item item = info == null ? null : itemFor(info.material());
        // Das Pack braucht irgendeine Grundlage; Papier ist Hypixels haeufigste
        if (item == null && model != null) item = Items.PAPER;
        if (item == null) return fallback(itemId);

        ItemStack stack = new ItemStack(item);
        if (model != null) {
            // Das Server-Aussehen: derselbe Verweis, den das echte Item traegt
            stack.set(DataComponents.ITEM_MODEL, model);
        } else if (item == Items.PLAYER_HEAD && info != null && info.skin() != null && !info.skin().isBlank()) {
            applySkin(stack, info.skin());
        }

        Integer colour = info == null ? null : parseColour(info.color());
        if (colour != null && item != Items.PLAYER_HEAD) {
            try {
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(colour));
            } catch (RuntimeException ignored) {
                // Nicht jedes Item laesst sich faerben; dann bleibt es ungefaerbt
            }
        }
        return stack;
    }

    /**
     * Das Modell fuer eine Kennung: erst der Verweis, den Hypixel selbst nennt, sonst geraten.
     *
     * Hypixels Item-Liste fuehrt zu jedem Item das Feld item_model - genau den Verweis, den
     * das echte Item traegt. Ueber den Dateinamen im Pack zu gehen ist nur die Notloesung und
     * geht schief, sobald er abweicht: ENCHANTED_RUBY_VEILSHROOM zeigt auf
     * .../resources/ruby_veilshroom, also ohne das "enchanted_". Bisher blieb dann die
     * Papier-Grundlage stehen, und im Banner erschien ein Blatt Papier statt des Pilzes.
     *
     * Verwendet wird der genannte Verweis nur, wenn das Pack ihn auch fuehrt - sonst zeichnet
     * der Client ein fehlendes Modell, was schlimmer aussieht als die Grundlage.
     */
    private static Identifier modelFor(String itemId, ItemNames.Info info) {
        String named = info == null ? null : info.itemModel();
        if (named != null && !named.isBlank()) {
            Identifier parsed = Identifier.tryParse(named.trim());
            if (parsed != null && packKnows(parsed)) return parsed;
        }
        return packModel(itemId);
    }

    /** Kennt der Client dieses Modell? Vanilla immer, alles andere nur aus dem Pack. */
    private static boolean packKnows(Identifier model) {
        // Ein Teil der Verweise zeigt auf Vanilla (minecraft:bamboo) - die sind immer da
        if ("minecraft".equals(model.getNamespace())) return true;
        refreshIndex();
        if (modelIndex.isEmpty()) return false;
        String path = model.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return modelIndex.containsKey(name);
    }

    /**
     * Das Modell aus dem Server-Pack fuer eine Kennung, oder null.
     *
     * Das Pack legt je Item eine Definition unter items/item/<kategorie>/<name>.json
     * ab; der Name ist die Kennung in Kleinbuchstaben. Die Kategorie laesst sich
     * nicht raten, deshalb wird die Liste aus dem geladenen Pack gelesen - alle paar
     * Minuten neu, weil das Pack erst nach dem Beitritt zu Hypixel da ist.
     */
    private static Identifier packModel(String itemId) {
        refreshIndex();
        return modelIndex.get(itemId.toLowerCase(Locale.ROOT));
    }

    private static synchronized void refreshIndex() {
        long now = System.currentTimeMillis();
        if (now - indexBuiltAt < INDEX_TTL_MILLIS && !modelIndex.isEmpty()) return;
        indexBuiltAt = now;

        Map<String, Identifier> fresh = new HashMap<>();
        try {
            Map<Identifier, ?> found = Minecraft.getInstance().getResourceManager().listResources(PACK_ITEM_ROOT,
                    id -> PACK_NAMESPACE.equals(id.getNamespace()) && id.getPath().endsWith(".json"));
            for (Identifier file : found.keySet()) {
                // items/item/uncategorized/plasma.json -> item/uncategorized/plasma
                String path = file.getPath();
                String modelPath = path.substring("items/".length(), path.length() - ".json".length());
                String name = modelPath.substring(modelPath.lastIndexOf('/') + 1);
                fresh.putIfAbsent(name, Identifier.fromNamespaceAndPath(PACK_NAMESPACE, modelPath));
            }
        } catch (RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read item models from the server pack: {}", e.toString());
        }

        if (!fresh.isEmpty() || modelIndex.isEmpty()) {
            if (fresh.size() != modelIndex.size()) {
                ShokiMod.LOGGER.info("[ShokiMod] {} item models known from the server pack", fresh.size());
                // Ein neues Pack heisst neue Bilder: die alten Stapel gelten nicht mehr
                cache.clear();
            }
            modelIndex = fresh;
        }
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
