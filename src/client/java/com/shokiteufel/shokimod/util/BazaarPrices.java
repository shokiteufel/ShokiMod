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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Was ein Fund im Basar einbringt.
 *
 * Der Endpunkt ist derselbe, den zehn Mods dieses Profils ohnehin nutzen, und er
 * braucht keinen Schluessel. Gefragt wird nur, solange der Wert-Alarm eingeschaltet
 * ist - ist er aus, geht kein einziger Request hinaus.
 *
 * Genommen wird der Sofortverkaufspreis: das ist, was man fuer einen Fund
 * tatsaechlich bekommt, wenn man ihn ohne Warten abgibt.
 *
 * Geholt wird in einem eigenen Thread. Der Tick wartet nie auf das Netz - bis die
 * erste Antwort da ist, gilt der Stand von der Platte, und ohne den ist der Wert
 * schlicht unbekannt.
 */
public final class BazaarPrices {

    private static final String ENDPOINT = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String CACHE_FILE = "bazaar-prices.json";

    /** Die Preise stehen minutenweise still; oefter zu fragen brächte nichts */
    private static final long REFRESH_MILLIS = 10 * 60 * 1000L;
    /** Nach einem Fehlschlag nicht sofort wieder anklopfen */
    private static final long RETRY_MILLIS = 2 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Gson GSON = new Gson();
    private static final Map<String, Double> prices = new ConcurrentHashMap<>();
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    private static volatile long fetchedAt = 0L;
    private static volatile boolean cacheRead = false;

    private BazaarPrices() {
    }

    /**
     * Der Sofortverkaufspreis eines Items, oder -1 wenn der Basar ihn nicht fuehrt.
     *
     * Der Aufruf loest das Nachladen aus, wartet aber nicht darauf. Wer hier -1
     * bekommt, weiss den Wert eben noch nicht - und nicht, dass er null waere.
     */
    public static double sellPrice(String itemId) {
        if (itemId == null || itemId.isBlank()) return -1;
        ensureFresh();

        Double price = prices.get(itemId);
        return price == null ? -1 : price;
    }

    /**
     * Holt die Preise, bevor der erste Fund sie braucht.
     *
     * Die erste Antwort dauert Sekunden - genau die, in denen der erste Drop nach
     * dem Einloggen faellt. Wer erst beim Fund fragt, hat fuer diesen keinen Preis.
     */
    public static void prefetch() {
        ensureFresh();
    }

    /** Steht ueberhaupt schon etwas bereit? */
    public static boolean ready() {
        return !prices.isEmpty();
    }

    /** Holt nach, wenn der Stand alt genug ist - und immer nur einmal gleichzeitig */
    private static void ensureFresh() {
        if (!cacheRead) {
            cacheRead = true;
            readCache();
        }

        long now = System.currentTimeMillis();
        long age = now - fetchedAt;
        if (age < (prices.isEmpty() ? RETRY_MILLIS : REFRESH_MILLIS)) return;
        if (!fetching.compareAndSet(false, true)) return;

        // Der Zeitstempel wird sofort gesetzt, nicht erst nach der Antwort: sonst
        // liefe bei jedem gescheiterten Versuch gleich der naechste hinterher
        fetchedAt = now;
        Thread worker = new Thread(BazaarPrices::fetch, "ShokiMod Bazaar");
        worker.setDaemon(true);
        worker.start();
    }

    private static void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("User-Agent", "ShokiMod")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                ShokiMod.LOGGER.warn("Bazaar request failed with status {}.", response.statusCode());
                return;
            }

            if (parse(response.body())) writeCache(response.body());
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("Could not read bazaar prices: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    /** Traegt die Preise aus einer Antwort ein. Falsch, wenn nichts Brauchbares drinstand */
    private static boolean parse(String body) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null || !root.has("products")) return false;

        JsonObject products = root.getAsJsonObject("products");
        int found = 0;
        for (String id : products.keySet()) {
            JsonObject product = products.getAsJsonObject(id);
            if (product == null || !product.has("quick_status")) continue;

            JsonObject status = product.getAsJsonObject("quick_status");
            if (status == null || !status.has("sellPrice")) continue;

            double price = status.get("sellPrice").getAsDouble();
            if (price > 0) {
                prices.put(id, price);
                found++;
            }
        }
        return found > 0;
    }

    /**
     * Der letzte Stand von der Platte.
     *
     * Ohne ihn stuenden nach jedem Start ein paar Sekunden lang keine Preise bereit -
     * und genau in die faellt der erste Fund nach dem Einloggen.
     */
    private static void readCache() {
        Path file = ModPaths.configDir().resolve(CACHE_FILE);
        if (!Files.exists(file)) return;

        try {
            parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("Could not read cached bazaar prices: {}", e.toString());
        }
    }

    private static void writeCache(String body) {
        try {
            Files.writeString(ModPaths.configDir().resolve(CACHE_FILE), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("Could not store bazaar prices: {}", e.toString());
        }
    }
}
