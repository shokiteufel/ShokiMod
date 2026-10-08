package com.shokiteufel.shokimod.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Kopf-Texturen der Pets, wie sie im Inventar liegen: BABY_YETI;3.
 *
 * Ein Pet ist ein Kopf mit dem Gesicht des Tiers. Hypixels Item-Liste fuehrt Pets nicht;
 * das NEU-Repo hat je Pet und Seltenheit eine Datei unter items/BABY_YETI;3.json, in
 * deren NBT die Textur steht. Dasselbe Verfahren wie bei den Jagd-Shards (ShardIcons):
 * beim ersten Bedarf im Hintergrund holen, auf die Platte legen, danach nicht mehr holen.
 *
 * Nicht jede Seltenheit hat im Repo eine eigene Datei. Das Gesicht ist bei allen Stufen
 * dasselbe, also springt die Suche bei einer fehlenden Datei auf die anderen Stufen.
 */
public final class PetHeads {

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
    private static final String TEXTURE_DIR = "pet-heads";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Gson GSON = new Gson();
    private static final Pattern TEXTURE_VALUE = Pattern.compile("Value:\"([A-Za-z0-9+/=]+)\"");
    /** Ein Pet im Inventar: Name in Grossbuchstaben, Semikolon, Seltenheit 0 bis 5 */
    private static final Pattern PET_ID = Pattern.compile("^([A-Z0-9_]+);([0-5])$");

    private static final Map<String, String> textures = new ConcurrentHashMap<>();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private PetHeads() {
    }

    /**
     * Ist das die Kennung eines Pets?
     *
     * Buecher, Runen und Attribut-Shards tragen ebenfalls ein Semikolon, aber mit anderen
     * Vorsaetzen und Endungen - sie fallen hier heraus.
     */
    public static boolean isPet(String itemId) {
        if (itemId == null) return false;
        if (itemId.startsWith("ENCHANTMENT_") || itemId.startsWith("ATTRIBUTE_SHARD_")
                || itemId.startsWith("POTION_") || itemId.contains("_RUNE;")) {
            return false;
        }
        return PET_ID.matcher(itemId).matches();
    }

    /** Der Name ohne Seltenheit, "BABY YETI" statt BABY_YETI;3 - so schluesselt PetIcons */
    public static String nameOf(String itemId) {
        Matcher m = PET_ID.matcher(itemId == null ? "" : itemId);
        return m.matches() ? m.group(1).replace('_', ' ') : "";
    }

    /** Die Base64-Textur oder null, solange sie noch nicht da ist. Der erste Aufruf stoesst das Laden an */
    public static String texture(String petId) {
        if (!isPet(petId)) return null;
        String cached = textures.get(petId);
        if (cached != null) return cached;
        if (missing.contains(petId)) return null;

        String fromDisk = readDisk(petId);
        if (fromDisk != null) {
            textures.put(petId, fromDisk);
            return fromDisk;
        }
        if (loading.add(petId)) {
            Thread worker = new Thread(() -> fetch(petId), "ShokiMod pet head " + petId);
            worker.setDaemon(true);
            worker.start();
        }
        return null;
    }

    /** Ob die Suche abgeschlossen ist, mit oder ohne Fund - sonst bliebe ein Ersatzbild fuer immer haengen */
    public static boolean settled(String petId) {
        return textures.containsKey(petId) || missing.contains(petId);
    }

    private static void fetch(String petId) {
        try {
            Matcher m = PET_ID.matcher(petId);
            if (!m.matches()) return;
            String name = m.group(1);
            int tier = Integer.parseInt(m.group(2));

            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            // Erst die eigene Stufe, dann die uebrigen: das Gesicht ist dasselbe
            int[] order = new int[6];
            order[0] = tier;
            int n = 1;
            for (int t = 0; t < 6; t++) {
                if (t != tier) order[n++] = t;
            }
            for (int t : order) {
                String texture = fetchOne(client, name + ";" + t);
                if (texture != null) {
                    textures.put(petId, texture);
                    writeDisk(petId, texture);
                    return;
                }
            }
            ShokiMod.LOGGER.warn("[ShokiMod] No head texture found for pet {}", petId);
            missing.add(petId);
        } catch (RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not load pet head {}: {}", petId, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            loading.remove(petId);
        }
    }

    private static String fetchOne(HttpClient client, String file) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(REPO + file.replace(";", "%3B") + ".json"))
                    .header("User-Agent", "ShokiMod").timeout(TIMEOUT).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonObject item = GSON.fromJson(response.body(), JsonObject.class);
            String nbt = item != null && item.has("nbttag") ? item.get("nbttag").getAsString() : "";
            Matcher matcher = TEXTURE_VALUE.matcher(nbt);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Path diskFile(String petId) {
        return ModPaths.configDir().resolve(TEXTURE_DIR).resolve(petId.replace(';', '_') + ".txt");
    }

    private static String readDisk(String petId) {
        Path file = diskFile(petId);
        if (!Files.exists(file)) return null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeDisk(String petId, String texture) {
        try {
            Path file = diskFile(petId);
            Files.createDirectories(file.getParent());
            Files.writeString(file, texture, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not store pet head {}: {}", petId, e.toString());
        }
    }
}
