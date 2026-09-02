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
 * Eine Nachschlagetabelle aus dem Netz: Schluessel auf Wert.
 *
 * Basarpreise, niedrigste BINs und Item-Namen unterscheiden sich nur in Adresse
 * und Antwortform - alles andere ist gleich: nur holen, solange jemand fragt; nie
 * im Tick warten; den letzten Stand auf der Platte behalten, damit direkt nach dem
 * Einloggen schon etwas dasteht. Deshalb eine Klasse, drei Instanzen.
 *
 * Geholt wird in einem eigenen Thread. Wer nachschlaegt, stoesst das Nachladen an,
 * bekommt aber sofort eine Antwort - notfalls "unbekannt".
 *
 * Was zuletzt geschah, steht in {@link #status()}: der Tester sieht das Spiel, nicht
 * den Code, und soll trotzdem sagen koennen, ob die Liste da war.
 */
public final class RemoteMap<V> {

    /** Nach einem Fehlschlag nicht sofort wieder anklopfen */
    private static final long RETRY_MILLIS = 2 * 60 * 1000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Gson GSON = new Gson();

    private final String name;
    private final String endpoint;
    private final String cacheFile;
    private final long refreshMillis;
    /** Macht aus der Antwort die Tabelle. Leer heisst: nichts Brauchbares */
    private final Function<JsonObject, Map<String, V>> parser;

    private final Map<String, V> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean fetching = new AtomicBoolean(false);
    private volatile long attemptedAt = 0L;
    private volatile long succeededAt = 0L;
    private volatile String lastError = null;
    private volatile String origin = "nothing loaded";
    private volatile boolean cacheRead = false;

    public RemoteMap(String name, String endpoint, String cacheFile, long refreshMillis,
                     Function<JsonObject, Map<String, V>> parser) {
        this.name = name;
        this.endpoint = endpoint;
        this.cacheFile = cacheFile;
        this.refreshMillis = refreshMillis;
        this.parser = parser;
    }

    /** Der Wert zu einem Schluessel, oder null wenn die Tabelle ihn nicht fuehrt */
    public V get(String key) {
        if (key == null || key.isBlank()) return null;
        prefetch();
        return entries.get(key);
    }

    public boolean ready() {
        return !entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /** Alle Schluessel, zum Abgleich ungenauer Kennungen. Nicht veraenderbar */
    public java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(entries.keySet());
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
        long age = now - attemptedAt;
        if (age < (entries.isEmpty() ? RETRY_MILLIS : refreshMillis)) return;
        if (!fetching.compareAndSet(false, true)) return;

        // Der Zeitstempel wird sofort gesetzt, nicht erst nach der Antwort: sonst
        // liefe bei jedem gescheiterten Versuch gleich der naechste hinterher
        attemptedAt = now;
        Thread worker = new Thread(this::fetch, "ShokiMod " + name);
        worker.setDaemon(true);
        worker.start();
    }

    /** Eine Zeile fuer den Diagnosebericht: was da ist, woher, und was zuletzt schiefging */
    public String status() {
        StringBuilder out = new StringBuilder(name).append(": ");
        if (entries.isEmpty()) {
            out.append("EMPTY");
        } else {
            out.append(entries.size()).append(" entries from ").append(origin);
        }
        if (succeededAt > 0) out.append(", last success ").append(ago(succeededAt));
        if (attemptedAt > 0) out.append(", last attempt ").append(ago(attemptedAt));
        if (lastError != null) out.append(", last error: ").append(lastError);
        return out.toString();
    }

    private static String ago(long at) {
        long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1000L);
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return (seconds / 60) + "min ago";
        return (seconds / 3600) + "h ago";
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
                fail("HTTP " + response.statusCode());
                return;
            }

            if (parse(response.body(), "network")) {
                succeededAt = System.currentTimeMillis();
                lastError = null;
                writeCache(response.body());
            } else {
                fail("response had no usable entries");
            }
        } catch (IOException | RuntimeException e) {
            fail(e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    private void fail(String reason) {
        lastError = reason;
        ShokiMod.LOGGER.warn("[ShokiMod] Could not load {}: {}", name, reason);
    }

    private boolean parse(String body, String from) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return false;

        Map<String, V> parsed = parser.apply(root);
        if (parsed == null || parsed.isEmpty()) return false;

        entries.putAll(parsed);
        origin = from;
        return true;
    }

    private void readCache() {
        Path file = ModPaths.configDir().resolve(cacheFile);
        if (!Files.exists(file)) return;

        try {
            parse(Files.readString(file, StandardCharsets.UTF_8), "disk cache");
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not read cached {}: {}", name, e.toString());
        }
    }

    private void writeCache(String body) {
        try {
            Files.writeString(ModPaths.configDir().resolve(cacheFile), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[ShokiMod] Could not store {}: {}", name, e.toString());
        }
    }
}
