package com.shokiteufel.shokimod.util;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;

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
    private record Recipe(String item, String texture, int overflowLevel, double overflowXp,
                          boolean fromActive) {

        Recipe(String item, String texture) {
            this(item, texture, 0, 0d, false);
        }

        Recipe withOverflow(int level, double xp) {
            return new Recipe(item, texture, level, xp, fromActive);
        }

        Recipe asActive(boolean aktiv) {
            return new Recipe(item, texture, overflowLevel, overflowXp, aktiv);
        }

        /** Nur das Bild - fuer den Vergleich, ob sich am Bild etwas geaendert hat */
        boolean sameImage(Recipe other) {
            return other != null && item.equals(other.item()) && texture.equals(other.texture());
        }
    }

    /** Die letzte Aufstiegszeile, die die Mod selbst in den Chat geschrieben hat - sie laeuft durch dieselben Ereignisse wie die des Servers */
    private static volatile String lastAnnounced = "";

    /** Ist das die Zeile, die die Mod selbst geschrieben hat, und nicht eine von Hypixel? Fuer das Protokoll */
    public static boolean isOwnAnnouncement(String plainLine) {
        return plainLine != null && plainLine.equals(lastAnnounced);
    }

    private static final Map<String, Recipe> recipes = new ConcurrentHashMap<>();
    private static final Map<String, ItemStack> built = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile boolean dirty = false;

    private PetIcons() {
    }

    /**
     * Das gemerkte Bild zu diesem Pet, oder ein leerer Gegenstand.
     *
     * Gesucht wird erst unter dem Schluessel des Pets (Name, Skin, Item - siehe PetIdentity), dann ohne das Item: Das
     * Item aendert das Bild nicht. Ein Pet mit Skin nimmt das Bild des Pets ohne Skin nur ersatzweise, solange das Menue es nie gezeigt hat.
     */
    public static ItemStack iconFor(String petKey) {
        if (petKey == null || petKey.isBlank()) return ItemStack.EMPTY;
        load();
        for (String key : PetIdentity.iconKeys(petKey)) {
            ItemStack fertig = built.get(key);
            if (fertig != null) return fertig;

            Recipe recipe = recipes.get(key);
            if (recipe == null) continue;
            ItemStack stack = build(recipe);
            if (!stack.isEmpty()) {
                built.put(key, stack);
                return stack;
            }
        }
        // Ein Pet mit Skin, das das Menue noch nie gezeigt hat (ein Favorit bis 1.19.8, eine Seite, die nie aufgeschlagen
        // wurde): ersatzweise das Bild des Pets ohne Skin. Ein Bild vom richtigen Pet mit womoeglich falschem Skin ist besser
        // als ein Kasten ohne Bild - und sobald das Menue das Pet gezeigt hat, gilt sein eigenes
        String plain = PetIdentity.nameOf(petKey);
        if (PetIdentity.skinOf(petKey) && !plain.isEmpty()) {
            ItemStack fertig = built.get(plain);
            if (fertig != null) return fertig;
            Recipe recipe = recipes.get(plain);
            if (recipe != null) {
                ItemStack stack = build(recipe);
                if (!stack.isEmpty()) {
                    built.put(plain, stack);
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Die gemerkten Schluessel - damit die Zuordnung weiss, welche Pets es schon gibt */
    public static java.util.List<String> knownKeys() {
        load();
        return new java.util.ArrayList<>(recipes.keySet());
    }

    /**
     * Ein im Menue gesehenes Bild behalten.
     *
     * Geschrieben wird nur, wenn sich wirklich etwas geaendert hat - der Blick ins
     * Menue geschieht zweimal je Sekunde, und jedes Mal die Platte anzufassen waere
     * verschwendet.
     */
    public static void remember(String petName, ItemStack stack) {
        remember(petName, stack, false);
    }

    /**
     * Ein Bild behalten - und dabei wissen, ob es vom getragenen Pet stammt.
     *
     * Zwei Pets koennen denselben Namen tragen und trotzdem verschieden aussehen: wer
     * ein Ammonite mit Skin traegt und ein zweites ohne, hat beide unter "Ammonite"
     * stehen. Weil der Name der einzige Schluessel ist, gewinnt sonst das letzte im
     * Menue - und das ist selten das getragene. Deshalb hat das getragene Vorrang und
     * wird von einem gleichnamigen aus der Liste nicht mehr ueberschrieben.
     */
    public static void remember(String petName, ItemStack stack, boolean active) {
        if (petName == null || petName.isBlank() || stack == null || stack.isEmpty()) return;
        load();
        String key = key(petName);
        rememberBase(key, stack);
        Recipe vorher = recipes.get(key);
        // Ein Bild aus der Liste tritt hinter das des getragenen Pets zurueck
        if (!active && vorher != null && vorher.fromActive()) return;

        Recipe recipe = new Recipe(itemId(stack), texture(stack)).asActive(active);
        if (recipe.sameImage(vorher) && vorher != null && vorher.fromActive() == active) return;
        // Was ueber den Ueberschuss bekannt war, bleibt erhalten
        if (vorher != null) recipe = recipe.withOverflow(vorher.overflowLevel(), vorher.overflowXp());

        recipes.put(key, recipe);
        built.put(key, stack.copy());
        dirty = true;
        save();
    }

    /**
     * Das Bild des Pets unter Name und Skin ohne Item festhalten, wenn es dort noch fehlt - damit auch ein anderes Pet
     * dieser Art ohne eigenen Eintrag ein Bild hat. Ein vorhandenes Bild bleibt, der Ueberschuss dort auch.
     */
    private static void rememberBase(String key, ItemStack stack) {
        String base = PetIdentity.baseKey(PetIdentity.nameOf(key), PetIdentity.skinOf(key));
        if (base.equals(key)) return;
        Recipe vorher = recipes.get(base);
        if (vorher != null && !vorher.item().isBlank()) return;
        Recipe recipe = new Recipe(itemId(stack), texture(stack));
        if (vorher != null) recipe = recipe.withOverflow(vorher.overflowLevel(), vorher.overflowXp());
        recipes.put(base, recipe);
        built.put(base, stack.copy());
        dirty = true;
    }

    /**
     * Den zuletzt bekannten Ueberschuss dieses Pets festhalten.
     *
     * Gilt nur fuer Zahlen groesser null: eine Null waere keine Auskunft, sondern das
     * Fehlen einer - und wuerde die gemerkte Zahl zerstoeren.
     *
     * @param petKey   der Schluessel des Pets (Name, Skin, Item) - nicht der blosse Name: Zwei Golden Dragons teilten
     *                 sich sonst eine Zahl, und der Wechsel zwischen ihnen galt als Aufstieg
     * @param petName  der Name fuer die Meldung im Chat
     * @param combined Stufe plus Ueberschuss - dieselbe Zahl, die im Kasten steht.
     *                 Null heisst: unbekannt, dann nennt die Meldung den Ueberschuss
     */
    public static void rememberOverflow(String petKey, String petName, int level, double xp, int combined) {
        if (petKey == null || petKey.isBlank() || level <= 0) return;
        load();
        String key = key(petKey);
        Recipe vorher = recipes.get(key);
        Recipe recipe = (vorher == null ? new Recipe("", "") : vorher).withOverflow(level, xp);
        if (recipe.equals(vorher)) return;

        // Ist die Stufe gestiegen? Gemeldet wird nur, was wirklich dazukommt.
        //
        // Die Bedingung "vorher war schon eine bekannt" ist der Kern: Beim ersten
        // Blick auf ein Pet steht dort noch nichts, und ohne sie meldete die Mod
        // beim Einloggen jedes Mal "Golden Dragon hat Stufe 332 erreicht" - fuer
        // etwas, das seit Wochen so ist
        //
        // Und nur ein Schritt, der einer sein kann: ein bis drei Stufen. Alles darueber ist eine andere Zahl - ein
        // anderes Pet, das hier fuer dasselbe gehalten wurde, oder ein Stand von frueher. Die Erfahrung kostet
        // 1.886.700 je Stufe; Dutzende Stufen kommen nicht zwischen zwei Blicken in die Tab-Liste zusammen
        boolean sprung = vorher != null && vorher.overflowLevel() > 0 && level > vorher.overflowLevel();
        boolean gestiegen = sprung && PetIdentity.plausibleLevelUp(vorher.overflowLevel(), level);
        if (sprung && !gestiegen) {
            PetLog.line("LEVEL", "no message for " + key + ": overflow " + vorher.overflowLevel() + " -> " + level
                    + " is no single step, taken over silently");
        } else if (gestiegen) {
            PetLog.line("LEVEL", key + ": overflow " + vorher.overflowLevel() + " -> " + level + ", total " + combined
                    + (combined > 0 ? ", message" : ", no message (total unknown)"));
        }

        recipes.put(key, recipe);
        dirty = true;
        save();

        // Gemeldet wird nur mit bekannter Gesamtstufe. Fehlt sie, stuende in der Zeile
        // der blosse Ueberschuss - eine andere Zahl als im Kasten, und beim Lesen sucht
        // man erst, was gemeint ist
        if (gestiegen && combined > 0) {
            announceOverflow(petName == null || petName.isBlank() ? PetIdentity.nameOf(key) : petName, combined);
        }
    }

    /**
     * Die Zeile im eigenen Chat - sie geht an niemanden sonst.
     *
     * Geschrieben wird sie ueber den Spieler selbst, nicht ueber den Server: eine
     * Systemnachricht, die nur hier ankommt.
     */
    private static void announceOverflow(String petName, int level) {
        if (!ModConfig.INSTANCE.hud.pet.announceOverflow) return;
        lastAnnounced = "Your " + petName + " leveled up to level " + level + "!";
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null) return;   // ohne laufendes Spiel gibt es niemanden zu benachrichtigen
        client.execute(() -> {
            if (client.player == null) return;
            // Genau wie Hypixels eigene Meldung, die manche Drachen-Skins zeigen:
            // "Your Jade Dragon leveled up to level 569!" - gruen, der Name in der
            // Farbe seiner Seltenheit, die Stufe blau. Wer beide Zeilen sieht, soll
            // nicht zwei verschiedene Sprachen lesen
            client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aYour " + rarityColour() + petName + " §aleveled up to level §9"
                            + level + "§a!"));
        });
    }

    /**
     * Die Farbe, in der Hypixel einen Pet-Namen schreibt.
     *
     * Gelesen wird die Seltenheit des aktiven Pets - gemeldet wird ohnehin nur das,
     * dessen Stufe gerade gestiegen ist. Unbekannt heisst Gold, die Farbe der
     * Legendaeren, bei denen der Ueberschuss am haeufigsten vorkommt.
     */
    private static String rarityColour() {
        String r = com.shokiteufel.shokimod.scanner.PetState.rarity();
        return switch (r == null ? "" : r.toUpperCase(java.util.Locale.ROOT)) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            case "SPECIAL" -> "§c";
            default -> "§6";
        };
    }

    /** Die zuletzt bekannte Ueberschuss-Stufe, oder 0 */
    public static int overflowLevelFor(String petKey) {
        if (petKey == null || petKey.isBlank()) return 0;
        load();
        Recipe recipe = recipes.get(key(petKey));
        return recipe == null ? 0 : recipe.overflowLevel();
    }

    /** Die zuletzt bekannte Ueberschuss-Erfahrung, oder 0 */
    public static double overflowXpFor(String petKey) {
        if (petKey == null || petKey.isBlank()) return 0d;
        load();
        Recipe recipe = recipes.get(key(petKey));
        return recipe == null ? 0d : recipe.overflowXp();
    }

    /** Wie viele Eintraege gemerkt sind - fuer den Diagnosebericht */
    public static int size() {
        load();
        return recipes.size();
    }

    /**
     * Wie viele davon wirklich ein Bild tragen.
     *
     * Nicht jeder Eintrag ist eines: rememberOverflow legt auch dann einen an, wenn
     * nur die Ueberschuss-Stufe bekannt ist. "59 gemerkt" hiess deshalb bisher nicht
     * "59 Bilder" - genau diese Verwechslung soll die Diagnose nicht mehr zulassen.
     */
    public static int withImage() {
        load();
        int n = 0;
        for (Recipe r : recipes.values()) if (!r.item().isBlank()) n++;
        return n;
    }

    /** Was ueber dieses eine Pet gemerkt ist, im Klartext - fuer die Diagnose */
    public static String describe(String petName) {
        if (petName == null || petName.isBlank()) return "no pet name";
        load();
        String key = key(petName);
        Recipe recipe = recipes.get(key);
        if (recipe == null) {
            return "\"" + key + "\" not in the file at all (image searched under "
                    + PetIdentity.iconKeys(key) + ")";
        }
        if (recipe.item().isBlank()) {
            return "\"" + key + "\" has no image, only overflow "
                    + recipe.overflowLevel();
        }
        String kopf = recipe.texture().isBlank() ? "no skin texture"
                : "skin texture " + recipe.texture().length() + " chars";
        return "\"" + key + "\" = " + recipe.item() + ", " + kopf
                + (recipe.fromActive() ? ", from the worn pet" : ", from the menu")
                + ", builds to " + (build(recipe).isEmpty() ? "NOTHING" : "an item");
    }

    /** Ein blosser Name wird gross geschrieben, ein Schluessel mit Skin und Item behaelt sein Item - siehe PetIdentity */
    private static String key(String petName) {
        return PetIdentity.canon(petName);
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
        // Ein Eintrag ohne Material ist kein Bild, sondern ein Platzhalter:
        // rememberOverflow legt einen an, wenn die Ueberschuss-Stufe frueher
        // bekannt ist als das Bild. Ohne diese Schranke ginge er als Kennung
        // in die Registry - mit ungewissem Ausgang
        if (recipe.item().isBlank()) return ItemStack.EMPTY;
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
                boolean aktiv = o.has("active") && o.get("active").getAsBoolean();
                recipes.put(entry.getKey(), new Recipe(item, texture, over, overXp, aktiv));
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
                if (entry.getValue().fromActive()) o.addProperty("active", true);
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
