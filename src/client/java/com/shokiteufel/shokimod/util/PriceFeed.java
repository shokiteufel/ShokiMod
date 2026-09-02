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
import java.util.function.Function;

/**
 * Eine Preisliste aus dem Netz: Kennung auf Coins.
 *
 * Basar und Lowest BIN unterscheiden sich nur in Adresse und Antwortform - alles
 * andere ist gleich: nur holen, solange jemand fragt; nie im Tick warten; den
 * letzten Stand auf der Platte behalten, damit direkt nach dem Einloggen schon
 * Preise dastehen. Deshalb eine Klasse, zwei Instanzen.
 *
 * Geholt wird in einem eigenen Thread. Wer einen Preis abfragt, stoesst das
 * Nachladen an, bekommt aber sofort eine Antwort - notfalls "unbekannt".
 */
public final class PriceFeed {

    /** Nach einem Fehlschlag nicht sofort wieder anklopfen */
    private static final long RETRY_MILLIS = 2 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Gson GSON = new Gson();

    private final String name;
    private final String endpoint;
    private final String cacheFile;
    private final long refreshMillis;
    /** Macht aus der Antwort die Preisliste. Leer heisst: nichts Brauchbares */
    private final Function<JsonObject, Map<String, Double>> parser;

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final AtomicBoolean fetching = new AtomicBoolean(false);
    private volatile long fetchedAt = 0L;
    private volatile boolean cacheRead = false;

    public PriceFeed(String name, String endpoint, String cacheFile, long refreshMillis,
                     Function<JsonObject, Map<String, Double>> parser) {
        this.name = name;
        this.endpoint = endpoint;
        this.cacheFile = cacheFile;
        this.refreshMillis = refreshMillis;
        this.parser = parser;
    }

    /**
     * Der Preis einer Kennung, oder -1 wenn die Liste sie nicht fuehrt.
     *
     * Wer hier -1 bekommt, weiss den Wert eben noch nicht - und nicht, dass er
     * null waere.
     */
    public double price(String itemId) {
        if (itemId == null || itemId.isBlank()) return -1;
        prefetch();

        Double price = prices.get(itemId);
        return price == null ? -1 : price;
    }

    public boolean ready() {
        return !prices.isEmpty();
    }

    /**
     * Holt nach, wenn der Stand alt genug ist - und immer nur einmal gleichzeitig.
     *
     * Gedacht fuer den Tick: die erste Antwort dauert Sekunden, genau die, in
     * denen der erste Fund nach dem Einloggen faellt.
     */
    public void prefetch() {
        if (!cacheRead) {
            cacheRead = true;
            readCache();
        }

        long now = System.currentTimeMillis();
        long age = now - fetchedAt;
        if (age < (prices.isEmpty() ? RETRY_MILLIS : refreshMillis)) return;
        if (!fetching.compareAndSet(false, true)) return;

        // Der Zeitstempel wird sofort gesetzt, nicht erst nach der Antwort: sonst
        // liefe bei jedem gescheiterten Versuch gleich der naechste hinterher
        fetchedAt = now;
        Thread worker = new Thread(this::fetch, "ShokiMod " + name);
        worker.setDaemon(true);
        worker.start();
    }

    private void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("User-Agent", "ShokiMod")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                ShokiMod.LOGGER.warn("{} request failed with status {}.", name, response.statusCode());
                return;
            }

            if (parse(response.body())) writeCache(response.body());
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("Could not read {}: {}", name, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    private boolean parse(String body) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;

        Map<String, Double> parsed = parser.apply(root);
        if (parsed == null || parsed.isEmpty()) return false;

        prices.putAll(parsed);
        return true;
    }

    private void readCache() {
        Path file = ModPaths.configDir().resolve(cacheFile);
        if (!Files.exists(file)) return;

        try {
            parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("Could not read cached {}: {}", name, e.toString());
        }
    }

    private void writeCache(String body) {
        try {
            Files.writeString(ModPaths.configDir().resolve(cacheFile), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("Could not store {}: {}", name, e.toString());
        }
    }
}
