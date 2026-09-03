package com.shokiteufel.shokimod.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Kopf-Texturen der Jagd-Shards.
 *
 * Ein Shard ist im Spiel ein Kopf mit dem Gesicht des Mobs. Hypixels Item-Liste und
 * das Server-Pack kennen die Shards nicht; das NEU-Repo auf GitHub schon - dort
 * heisst der Ghost-Shard aber nach seinem Attribut, ATTRIBUTE_SHARD_VEIL, nicht
 * nach dem Mob. Die Bruecke schlaegt eine Tabelle im selben Repo, die zu jeder
 * Basar-Kennung (SHARD_GHOST) den NEU-Namen nennt.
 *
 * Zwei Schritte also: die Tabelle einmal am Tag, dann je Shard beim ersten Bedarf
 * seine Datei mit der Textur. Beides landet auf der Platte und wird danach nicht
 * mehr geholt. Alles laeuft in eigenen Threads; wer eine Textur abfragt, bekommt
 * sofort eine Antwort - notfalls "noch nicht da".
 */
public final class ShardIcons {

    private static final String REPO = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/";
    private static final String TEXTURE_DIR = "shard-heads";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Gson GSON = new Gson();

    /** SkullOwner.Properties.textures[0].Value im alten NBT-Text des Repos */
    private static final Pattern TEXTURE_VALUE = Pattern.compile("Value:\"([A-Za-z0-9+/=]+)\"");

    /** Basar-Kennung auf NEU-Namen, aus constants/attribute_shards.json */
    public static final RemoteMap<String> INDEX = new RemoteMap<>(
            "shard index",
            REPO + "constants/attribute_shards.json",
            "shard-index.json",
            24 * 60 * 60 * 1000L,
            ShardIcons::parseIndex);

    private static final Map<String, String> textures = new ConcurrentHashMap<>();
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private ShardIcons() {
    }

    /**
     * Die Base64-Textur eines Shards, oder null wenn sie noch nicht da ist.
     *
     * Der erste Aufruf stoesst das Laden an; ein spaeterer bekommt sie dann.
     */
    public static String texture(String bazaarId) {
        if (bazaarId == null || !bazaarId.startsWith("SHARD_")) return null;

        String cached = textures.get(bazaarId);
        if (cached != null) return cached;
        if (missing.contains(bazaarId)) return null;

        String fromDisk = readDisk(bazaarId);
        if (fromDisk != null) {
            textures.put(bazaarId, fromDisk);
            return fromDisk;
        }

        String neuName = INDEX.get(bazaarId);
        if (neuName == null) return null;
        if (loading.add(bazaarId)) {
            Thread worker = new Thread(() -> fetchTexture(bazaarId, neuName), "ShokiMod shard head " + bazaarId);
            worker.setDaemon(true);
            worker.start();
        }
        return null;
    }

    private static void fetchTexture(String bazaarId, String neuName) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(REPO + "items/" + neuName + ".json"))
                    .header("User-Agent", "ShokiMod")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                ShokiMod.LOGGER.warn("[ShokiMod] No repo entry for {} ({}): HTTP {}", bazaarId, neuName, response.statusCode());
                missing.add(bazaarId);
                return;
            }

            JsonObject item = GSON.fromJson(response.body(), JsonObject.class);
            String nbt = item != null && item.has("nbttag") ? item.get("nbttag").getAsString() : "";
            Matcher matcher = TEXTURE_VALUE.matcher(nbt);
            if (!matcher.find()) {
                ShokiMod.LOGGER.warn("[ShokiMod] Repo entry for {} carries no head texture.", bazaarId);
                missing.add(bazaarId);
                return;
            }

            String texture = matcher.group(1);
            textures.put(bazaarId, texture);
            writeDisk(bazaarId, texture);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not load head texture for {}: {}", bazaarId, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            loading.remove(bazaarId);
        }
    }

    /** attributes[].bazaarName -> attributes[].internalName */
    private static Map<String, String> parseIndex(JsonObject root) {
        Map<String, String> out = new HashMap<>();
        if (!root.has("attributes") || !root.get("attributes").isJsonArray()) return out;

        JsonArray attributes = root.getAsJsonArray("attributes");
        for (JsonElement element : attributes) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("bazaarName") || !entry.has("internalName")) continue;

            String bazaar = entry.get("bazaarName").getAsString();
            String internal = entry.get("internalName").getAsString();
            if (!bazaar.isBlank() && !internal.isBlank()) out.put(bazaar, internal);
        }
        return out;
    }

    private static Path diskFile(String bazaarId) {
        return ModPaths.configDir().resolve(TEXTURE_DIR).resolve(bazaarId + ".txt");
    }

    private static String readDisk(String bazaarId) {
        Path file = diskFile(bazaarId);
        if (!Files.exists(file)) return null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).trim();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeDisk(String bazaarId, String texture) {
        try {
            Path file = diskFile(bazaarId);
            Files.createDirectories(file.getParent());
            Files.writeString(file, texture, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not store head texture for {}: {}", bazaarId, e.toString());
        }
    }
}
