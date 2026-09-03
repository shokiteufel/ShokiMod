package com.shokiteufel.shokimod.util;

import com.google.gson.Gson;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Welche Collections es gibt, und wie viel ein Item davon einbringt.
 *
 * Zwei Fragen, zwei Quellen:
 *
 * Erstens die Liste der Collections - Hypixels eigene Ressourcen-Liste, ohne Schluessel
 * abrufbar. Sie nennt zu jeder Kennung (HELIX_LOG, INK_SACK:3) den Namen aus dem Spiel
 * ("Helix Log", "Cocoa Beans") und die Kategorie.
 *
 * Zweitens die Umrechnung. In den Sack wandert selten das rohe Item, sondern die
 * verzauberte Fassung: ein Enchanted Helix Log zaehlt 160 Helix Log. Diese Zahl steht
 * nirgends als Zahl - sie steckt im Bauplan. Das NEU-Repo fuehrt zu jedem Item sein
 * Rezept, und daraus faellt sie heraus: fuenfmal 32 Helix Log ergeben ein verzaubertes.
 * Bei mehrfach verzauberten Sachen (Enchanted Diamond Block) geht es rekursiv weiter.
 *
 * Geholt wird nur, was tatsaechlich vorkommt, und das Ergebnis bleibt auf der Platte.
 * Wer eine Stunde Helix-Baeume faellt, verursacht genau eine Anfrage.
 */
public final class CollectionData {

    /** Eine Collection: Anzeigename und Kategorie (FARMING, MINING, ...) */
    public record Collection(String id, String name, String category) {
    }

    /** Was ein Item einbringt: so viele Einheiten dieser Collection */
    public record Yield(String collectionId, long amount) {
    }

    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String NEU_ITEM = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/%s.json";
    private static final String CACHE_FILE = "collection-recipes.json";
    private static final int MAX_DEPTH = 6;

    /** Kennung -> Collection. Gefuellt aus Hypixels Liste */
    private static final Map<String, Collection> collections = new ConcurrentHashMap<>();
    /** Anzeigename (klein) -> Kennung */
    private static final Map<String, String> byName = new ConcurrentHashMap<>();

    /** Item-Kennung -> "COLLECTION_ID:Anzahl". Auf der Platte, damit es nur einmal geholt wird */
    private static final Map<String, String> resolved = new ConcurrentHashMap<>();
    /** Was gerade geholt wird - damit dasselbe Item nicht zweimal gleichzeitig laeuft */
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();
    private static volatile boolean cacheRead = false;
    private static volatile boolean cacheDirty = false;

    public static final RemoteMap<Collection> FEED = new RemoteMap<>(
            "collections",
            "https://api.hypixel.net/v2/resources/skyblock/collections",
            "collections.json",
            24 * 60 * 60 * 1000L,
            CollectionData::parseCollections);

    private CollectionData() {
    }

    public static void prefetch() {
        FEED.prefetch();
    }

    public static boolean ready() {
        return FEED.ready();
    }

    /** Alle Collections, nach Kategorie und Namen geordnet */
    public static List<Collection> all() {
        FEED.prefetch();
        List<Collection> out = new ArrayList<>(collections.values());
        out.sort((a, b) -> {
            int byCategory = a.category().compareTo(b.category());
            return byCategory != 0 ? byCategory : a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }

    public static Collection byId(String id) {
        FEED.prefetch();
        return id == null ? null : collections.get(id);
    }

    /** Der Anzeigename, oder die Kennung wenn die Liste sie nicht kennt */
    public static String nameOf(String id) {
        Collection collection = byId(id);
        return collection == null ? id : collection.name();
    }

    /** Die Kennung zu einem Namen aus dem Spiel, oder null */
    public static String idForName(String displayName) {
        if (displayName == null) return null;
        FEED.prefetch();
        return byName.get(displayName.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, Collection> parseCollections(JsonObject root) {
        Map<String, Collection> out = new LinkedHashMap<>();
        JsonObject categories = root.getAsJsonObject("collections");
        if (categories == null) return out;

        byName.clear();
        for (Map.Entry<String, JsonElement> category : categories.entrySet()) {
            if (!category.getValue().isJsonObject()) continue;
            JsonObject items = category.getValue().getAsJsonObject().getAsJsonObject("items");
            if (items == null) continue;
            for (Map.Entry<String, JsonElement> item : items.entrySet()) {
                if (!item.getValue().isJsonObject()) continue;
                JsonObject body = item.getValue().getAsJsonObject();
                String name = body.has("name") ? body.get("name").getAsString() : item.getKey();
                out.put(item.getKey(), new Collection(item.getKey(), name, category.getKey()));
                byName.put(name.toLowerCase(Locale.ROOT), item.getKey());
            }
        }
        collections.clear();
        collections.putAll(out);
        return out;
    }

    // ---- Umrechnung ueber die Rezepte ----

    /**
     * Was ein Stueck dieses Items fuer die Collection bringt.
     *
     * Null heisst: noch nicht bekannt. Der Bauplan wird dann im Hintergrund geholt; beim
     * naechsten Mal steht die Antwort da. Kein Warten im Tick, keine halbe Zaehlung -
     * lieber einmal nichts als eine falsche Zahl.
     */
    public static Yield yieldOf(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        readCache();

        String id = normalise(itemId);
        if (collections.containsKey(id)) return new Yield(id, 1);

        String cached = resolved.get(id);
        if (cached != null) {
            if (cached.isEmpty()) return null; // schon geholt, ergab nichts
            int split = cached.lastIndexOf(':');
            try {
                return new Yield(cached.substring(0, split), Long.parseLong(cached.substring(split + 1)));
            } catch (RuntimeException e) {
                resolved.remove(id);
            }
        }

        if (pending.add(id)) {
            Thread worker = new Thread(() -> {
                try {
                    Yield found = fetchYield(id, 0);
                    resolved.put(id, found == null ? "" : found.collectionId() + ":" + found.amount());
                    cacheDirty = true;
                    writeCache();
                    if (found != null) {
                        ShokiMod.LOGGER.info("[Collections] {} = {} x {}", id, found.amount(), found.collectionId());
                    }
                } catch (RuntimeException e) {
                    ShokiMod.LOGGER.warn("[Collections] Bauplan von {} nicht lesbar: {}", id, e.toString());
                } finally {
                    pending.remove(id);
                }
            }, "ShokiMod collection recipe");
            worker.setDaemon(true);
            worker.start();
        }
        return null;
    }

    /** Holt den Bauplan und geht ihn hinunter, bis eine Collection erreicht ist */
    private static Yield fetchYield(String itemId, int depth) {
        if (depth >= MAX_DEPTH) return null;
        if (collections.containsKey(itemId)) return new Yield(itemId, 1);

        JsonObject item = fetchItem(itemId);
        if (item == null) return null;

        Map<String, Long> ingredients = new HashMap<>();
        long output = 1;
        collectIngredients(item.getAsJsonObject("recipe"), ingredients);
        if (ingredients.isEmpty() && item.has("recipes") && item.get("recipes").isJsonArray()) {
            for (JsonElement element : item.getAsJsonArray("recipes")) {
                if (!element.isJsonObject()) continue;
                JsonObject recipe = element.getAsJsonObject();
                Map<String, Long> single = new HashMap<>();
                collectIngredients(recipe, single);
                // Das erste brauchbare Rezept genuegt; mehrere Wege enden beim selben Rohstoff
                if (!single.isEmpty()) {
                    ingredients = single;
                    output = outputCount(recipe);
                    break;
                }
            }
        }
        if (ingredients.isEmpty()) return null;

        // Der Rohstoff, von dem am meisten hineingeht, bestimmt die Collection
        Map.Entry<String, Long> main = null;
        for (Map.Entry<String, Long> entry : ingredients.entrySet()) {
            if (main == null || entry.getValue() > main.getValue()) main = entry;
        }
        if (main == null || main.getKey().equals(itemId)) return null;

        Yield base = fetchYield(main.getKey(), depth + 1);
        if (base == null) return null;
        // Ein Bauplan, der zwei Stueck auswirft, bringt je Stueck nur die Haelfte
        long perPiece = Math.max(1, base.amount() * main.getValue() / Math.max(1, output));
        return new Yield(base.collectionId(), perPiece);
    }

    /** Wie viele Stueck ein Bauplan auswirft. Ohne Angabe eines */
    private static long outputCount(JsonObject recipe) {
        try {
            return recipe.has("count") && recipe.get("count").isJsonPrimitive()
                    ? Math.max(1, recipe.get("count").getAsLong()) : 1;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** Die neun Felder eines Bauplans zusammenzaehlen: "HELIX_LOG:32" fuenfmal ergibt 160 */
    private static void collectIngredients(JsonObject recipe, Map<String, Long> out) {
        if (recipe == null) return;
        for (String row : new String[]{"A", "B", "C"}) {
            for (int column = 1; column <= 3; column++) {
                JsonElement slot = recipe.get(row + column);
                if (slot == null || !slot.isJsonPrimitive()) continue;
                String text = slot.getAsString().trim();
                if (text.isEmpty()) continue;
                int split = text.lastIndexOf(':');
                String id = split < 0 ? text : text.substring(0, split);
                long amount = 1;
                if (split >= 0) {
                    try {
                        amount = Long.parseLong(text.substring(split + 1));
                    } catch (NumberFormatException e) {
                        id = text;
                    }
                }
                out.merge(normalise(id), amount, Long::sum);
            }
        }
    }

    /**
     * Das NEU-Repo schreibt Varianten mit Bindestrich (INK_SACK-3), Hypixel mit
     * Doppelpunkt (INK_SACK:3). Dieselbe Sache, zwei Schreibweisen.
     */
    private static String normalise(String id) {
        String out = id.trim().toUpperCase(Locale.ROOT);
        int dash = out.lastIndexOf('-');
        if (dash > 0 && dash < out.length() - 1 && Character.isDigit(out.charAt(dash + 1))) {
            out = out.substring(0, dash) + ":" + out.substring(dash + 1);
        }
        return out;
    }

    private static JsonObject fetchItem(String itemId) {
        String url = String.format(NEU_ITEM, itemId.replace(":", "-"));
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "ShokiMod")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            return GSON.fromJson(response.body(), JsonObject.class);
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ---- Ablage auf der Platte ----

    private static synchronized void readCache() {
        if (cacheRead) return;
        cacheRead = true;
        Path file = ModPaths.configDir().resolve(CACHE_FILE);
        if (!Files.exists(file)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) resolved.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Collections] Rezept-Ablage nicht lesbar: {}", e.toString());
        }
    }

    private static synchronized void writeCache() {
        if (!cacheDirty) return;
        cacheDirty = false;
        try {
            Files.writeString(ModPaths.configDir().resolve(CACHE_FILE), GSON.toJson(resolved), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[Collections] Rezept-Ablage nicht schreibbar: {}", e.toString());
        }
    }

    /** Fuer den Diagnosebericht */
    public static String status() {
        return "collections: " + (FEED.ready() ? collections.size() + " known" : "EMPTY")
                + ", " + resolved.size() + " recipes cached";
    }
}
