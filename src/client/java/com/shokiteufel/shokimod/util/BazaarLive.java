package com.shokiteufel.shokimod.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/**
 * Die Bazaar-Preise, direkt von Hypixel und so frisch wie sie zu haben sind.
 *
 * <p><b>Warum das ueberhaupt geht:</b> Der Bazaar ist dreieinhalb Megabyte gross -
 * zu viel, um ihn dauernd zu holen. Komprimiert sind es aber nur fuenfhundert
 * Kilobyte, und Hypixel liefert die Komprimierung auf Wunsch. Der Java-Client fragt
 * sie nicht von selbst an, deshalb steht sie hier ausdruecklich im Kopf und wird
 * hier ausgepackt.
 *
 * <p><b>Warum nicht dauernd:</b> Fuenfhundert Kilobyte alle zwanzig Sekunden waeren
 * neunzig Megabyte in der Stunde - fuer Zahlen, die niemand ansieht. Geholt wird
 * deshalb nur, solange jemand hinsieht: Das Fusions-Fenster meldet sich an, und mit
 * dem Schliessen hoert es wieder auf. Wer eine Viertelstunde in der Liste blaettert,
 * verbraucht rund zwanzig Megabyte; wer sie nie oeffnet, gar nichts.
 *
 * <p><b>Warum zwanzig Sekunden:</b> Gemessen. Hypixel setzt den Stand in genau
 * diesem Takt neu - haeufiger zu fragen bringt dieselben Zahlen noch einmal.
 *
 * <p>Solange nichts hiervon vorliegt, gelten die Preise aus der GitHub-Datei. Sie
 * sind bis zu fuenf Minuten alt, dafuer immer da.
 */
public final class BazaarLive {

    private static final String URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    /** Der gemessene Takt, in dem Hypixel den Bazaar neu setzt */
    private static final long GAP_MILLIS = 15_000L;
    /** Ohne frischen Stand gilt wieder, was von GitHub kam */
    private static final long STALE_MILLIS = 90_000L;
    /** So lange nach dem letzten Hinsehen wird noch geholt */
    private static final long INTEREST_MILLIS = 30_000L;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Gson GSON = new Gson();

    /** Kennung des Shards -> sofort kaufen / sofort verkaufen */
    private static volatile Map<String, long[]> prices = Map.of();
    private static volatile long fetchedAt = 0L;
    private static volatile long interestedAt = 0L;
    private static volatile long attemptedAt = 0L;
    private static volatile String lastError = null;
    private static volatile int fetchCount = 0;
    private static volatile long bytes = 0L;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    private BazaarLive() {
    }

    /**
     * "Jemand sieht gerade hin."
     *
     * Vom Fusions-Fenster bei jedem Bild gerufen. Solange das geschieht, wird geholt;
     * danach laeuft es von selbst aus.
     */
    public static void wanted() {
        interestedAt = System.currentTimeMillis();
        tick();
    }

    /** Holt nach, wenn Bedarf besteht und der letzte Stand alt genug ist */
    private static void tick() {
        long now = System.currentTimeMillis();
        if (now - interestedAt > INTEREST_MILLIS) return;
        if (now - attemptedAt < GAP_MILLIS) return;
        if (!fetching.compareAndSet(false, true)) return;

        attemptedAt = now;
        Thread worker = new Thread(BazaarLive::fetch, "ShokiMod bazaar live");
        worker.setDaemon(true);
        worker.start();
    }

    /** Der frische Preis, oder null - dann gilt der Stand von GitHub */
    public static long[] priceOf(String bazaarId) {
        if (!fresh()) return null;
        return prices.get(bazaarId);
    }

    /** Liegt ein brauchbar frischer Stand vor? */
    public static boolean fresh() {
        return fetchedAt > 0 && System.currentTimeMillis() - fetchedAt < STALE_MILLIS;
    }

    /** Wie alt der frische Stand ist, in Sekunden. -1 heisst: keiner da */
    public static int ageSeconds() {
        if (fetchedAt == 0) return -1;
        return (int) ((System.currentTimeMillis() - fetchedAt) / 1000L);
    }

    private static void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .header("User-Agent", "ShokiMod")
                    // Ohne diesen Kopf schickt Hypixel dreieinhalb Megabyte statt
                    // fuenfhundert Kilobyte. Der Java-Client fragt ihn nicht von
                    // selbst an und packt auch nicht selbst aus
                    .header("Accept-Encoding", "gzip")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                lastError = "HTTP " + response.statusCode();
                return;
            }
            bytes = response.body().length;
            String körper = unpack(response, response.body());
            JsonObject root = GSON.fromJson(körper, JsonObject.class);
            if (root == null) {
                lastError = "unreadable answer";
                return;
            }
            JsonObject produkte = root.getAsJsonObject("products");
            if (produkte == null || produkte.isEmpty()) {
                lastError = "no products";
                return;
            }

            // Nur die Shards behalten. Zweitausend Produkte kommen an, dreihundert
            // davon werden gebraucht - der Rest belegte nur Speicher
            Map<String, long[]> frisch = new HashMap<>(512);
            for (String id : produkte.keySet()) {
                if (!id.startsWith("SHARD_")) continue;
                JsonObject eintrag = produkte.getAsJsonObject(id);
                if (eintrag == null) continue;
                JsonObject stand = eintrag.getAsJsonObject("quick_status");
                if (stand == null) continue;
                long kaufen = Math.round(stand.get("buyPrice") == null ? 0
                        : stand.get("buyPrice").getAsDouble());
                long verkaufen = Math.round(stand.get("sellPrice") == null ? 0
                        : stand.get("sellPrice").getAsDouble());
                frisch.put(id, new long[]{kaufen, verkaufen});
            }
            if (frisch.isEmpty()) {
                lastError = "no shards in the answer";
                return;
            }

            prices = Map.copyOf(frisch);
            fetchedAt = System.currentTimeMillis();
            fetchCount++;
            lastError = null;
        } catch (IOException | RuntimeException e) {
            lastError = e.toString();
            ShokiMod.LOGGER.warn("[ShokiMod] Live bazaar failed: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    /**
     * Auspacken, falls komprimiert geliefert wurde.
     *
     * Manche Zwischenstationen packen von sich aus aus und lassen den Hinweis stehen,
     * andere lassen beides unberuehrt. Entschieden wird deshalb nach dem, was wirklich
     * ankommt: Eine gzip-Sendung beginnt immer mit denselben zwei Bytes.
     */
    private static String unpack(HttpResponse<byte[]> response, byte[] roh) throws IOException {
        boolean gepackt = roh.length > 2
                && (roh[0] & 0xFF) == 0x1F && (roh[1] & 0xFF) == 0x8B;
        if (!gepackt) return new String(roh, StandardCharsets.UTF_8);
        try (GZIPInputStream aus = new GZIPInputStream(new ByteArrayInputStream(roh));
             InputStreamReader leser = new InputStreamReader(aus, StandardCharsets.UTF_8)) {
            StringBuilder out = new StringBuilder(4 * 1024 * 1024);
            char[] puffer = new char[64 * 1024];
            int gelesen;
            while ((gelesen = leser.read(puffer)) > 0) out.append(puffer, 0, gelesen);
            return out.toString();
        }
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        if (fetchCount == 0) {
            return "live bazaar: not used yet"
                    + (lastError == null ? "" : ", last error: " + lastError);
        }
        return "live bazaar: " + prices.size() + " shards, " + fetchCount + " fetches, "
                + (bytes / 1024) + " KB each, " + ageSeconds() + "s old"
                + (fresh() ? "" : " (STALE - falling back to the GitHub prices)")
                + (lastError == null ? "" : ", last error: " + lastError);
    }
}
