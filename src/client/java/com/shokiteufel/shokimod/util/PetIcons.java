package com.shokiteufel.shokimod.util;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.shokiteufel.shokimod.ShokiMod;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die Bilder der Pets, ueber das Spielende hinaus gemerkt.
 *
 * Das Bild eines Pets steht nur in seinem Menue. Wer es einmal geoeffnet hat, soll es
 * nicht wieder oeffnen muessen, bloss damit der Kasten auf dem Bildschirm wieder ein
 * Bild hat - also wird es hier behalten und beim naechsten Start neu aufgebaut.
 *
 * Gespeichert wird nicht der Gegenstand selbst, sondern woraus er besteht: das Material
 * und, bei Koepfen, die Skin-Textur. Das ist wenig, ueberlebt einen Versionswechsel des
 * Spiels und braucht keine Registry - ein vollstaendig eingefrorener Gegenstand waere
 * beides nicht.
 */
public final class PetIcons {

    private static final String FILE = "pet-icons.json";
    private static final Gson GSON = new Gson();

    /**
     * Pet-Name in Grossbuchstaben -> was daraus ein Bild macht, und was zuletzt
     * darueber bekannt war.
     *
     * Die Ueberschuss-Stufe steht nur in der Tab-Liste. Wer sie ausgeblendet hat oder
     * gerade in einem Menue steht, saehe sonst eine Luecke, wo eben noch eine Zahl war -
     * deshalb wird der letzte bekannte Stand behalten.
     */
    private record Recipe(String item, String texture, int overflowLevel, double overflowXp) {

        Recipe(String item, String texture) {
            this(item, texture, 0, 0d);
        }

        Recipe withOverflow(int level, double xp) {
            return new Recipe(item, texture, level, xp);
        }

        /** Nur das Bild - fuer den Vergleich, ob sich am Bild etwas geaendert hat */
        boolean sameImage(Recipe other) {
            return other != null && item.equals(other.item()) && texture.equals(other.texture());
        }
    }

    private static final Map<String, Recipe> recipes = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> built = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile boolean dirty = false;

    private PetIcons() {
    }

    /** Das gemerkte Bild zu diesem Pet, oder ein leerer Gegenstand */
    public static ItemStack iconFor(String petName) {
        if (petName == null || petName.isBlank()) return ItemStack.EMPTY;
        load();
        String key = key(petName);
        ItemStack fertig = built.get(key);
        if (fertig != null) return fertig;

        Recipe recipe = recipes.get(key);
        if (recipe == null) return ItemStack.EMPTY;
        ItemStack stack = build(recipe);
        if (!stack.isEmpty()) built.put(key, stack);
        return stack;
    }

    /**
     * Ein im Menue gesehenes Bild behalten.
     *
     * Geschrieben wird nur, wenn sich wirklich etwas geaendert hat - der Blick ins
     * Menue geschieht zweimal je Sekunde, und jedes Mal die Platte anzufassen waere
     * verschwendet.
     */
    public static void remember(String petName, ItemStack stack) {
        if (petName == null || petName.isBlank() || stack == null || stack.isEmpty()) return;
        load();
        String key = key(petName);
        Recipe vorher = recipes.get(key);
        Recipe recipe = new Recipe(itemId(stack), texture(stack));
        if (recipe.sameImage(vorher)) return;
        // Was ueber den Ueberschuss bekannt war, bleibt erhalten
        if (vorher != null) recipe = recipe.withOverflow(vorher.overflowLevel(), vorher.overflowXp());

        recipes.put(key, recipe);
        built.put(key, stack.copy());
        dirty = true;
        save();
    }

    /**
     * Den zuletzt bekannten Ueberschuss festhalten.
     *
     * Gilt nur fuer Zahlen groesser null: eine Null waere keine Auskunft, sondern das
     * Fehlen einer - und wuerde die gemerkte Zahl zerstoeren.
     */
    public static void rememberOverflow(String petName, int level, double xp) {
        if (petName == null || petName.isBlank() || level <= 0) return;
        load();
        String key = key(petName);
        Recipe vorher = recipes.get(key);
        Recipe recipe = (vorher == null ? new Recipe("", "") : vorher).withOverflow(level, xp);
        if (recipe.equals(vorher)) return;
        recipes.put(key, recipe);
        dirty = true;
        save();
    }

    /** Die zuletzt bekannte Ueberschuss-Stufe, oder 0 */
    public static int overflowLevelFor(String petName) {
        if (petName == null || petName.isBlank()) return 0;
        load();
        Recipe recipe = recipes.get(key(petName));
        return recipe == null ? 0 : recipe.overflowLevel();
    }

    /** Die zuletzt bekannte Ueberschuss-Erfahrung, oder 0 */
    public static double overflowXpFor(String petName) {
        if (petName == null || petName.isBlank()) return 0d;
        load();
        Recipe recipe = recipes.get(key(petName));
        return recipe == null ? 0d : recipe.overflowXp();
    }

    /** Wie viele Bilder gemerkt sind - fuer den Diagnosebericht */
    public static int size() {
        load();
        return recipes.size();
    }

    private static String key(String petName) {
        return petName.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Die Skin-Textur eines Kopfes, oder leer bei allem anderen */
    private static String texture(ItemStack stack) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return "";
        GameProfile gp = profile.partialProfile();
        if (gp == null) return "";
        for (Property property : gp.properties().get("textures")) {
            return property.value();
        }
        return "";
    }

    private static ItemStack build(Recipe recipe) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(recipe.item()))
                .orElse(null);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        if (!recipe.texture().isBlank()) {
            try {
                PropertyMap properties = new PropertyMap(ImmutableMultimap.of(
                        "textures", new Property("textures", recipe.texture())));
                GameProfile profile = new GameProfile(
                        UUID.nameUUIDFromBytes(recipe.texture().getBytes(StandardCharsets.UTF_8)),
                        "shokimod", properties);
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            } catch (RuntimeException e) {
                // Dann eben ein Kopf ohne Gesicht
            }
        }
        return stack;
    }

    private static Path path() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("shokimod").resolve(FILE);
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            Path file = path();
            if (!Files.isRegularFile(file)) return;
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject o = entry.getValue().getAsJsonObject();
                String item = o.has("item") ? o.get("item").getAsString() : "";
                if (item.isBlank() && !o.has("overflow")) continue;
                String texture = o.has("texture") ? o.get("texture").getAsString() : "";
                int over = o.has("overflow") ? o.get("overflow").getAsInt() : 0;
                double overXp = o.has("overflowXp") ? o.get("overflowXp").getAsDouble() : 0d;
                recipes.put(entry.getKey(), new Recipe(item, texture, over, overXp));
            }
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read remembered pet icons: {}", e.toString());
        }
    }

    private static synchronized void save() {
        if (!dirty) return;
        dirty = false;
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Recipe> entry : recipes.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("item", entry.getValue().item());
                if (!entry.getValue().texture().isBlank()) {
                    o.addProperty("texture", entry.getValue().texture());
                }
                if (entry.getValue().overflowLevel() > 0) {
                    o.addProperty("overflow", entry.getValue().overflowLevel());
                    o.addProperty("overflowXp", entry.getValue().overflowXp());
                }
                root.add(entry.getKey(), o);
            }
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not save pet icons: {}", e.toString());
        }
    }
}
