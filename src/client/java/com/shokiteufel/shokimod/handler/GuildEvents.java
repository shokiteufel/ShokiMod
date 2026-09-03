package com.shokiteufel.shokimod.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.GuildEventsCategory;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.render.hud.SafariHud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Die Gilden-Events des ShokiTeufelBot im Spiel.
 *
 * Der Bot legt seinen Stand als events.json ab (Aufbau: feed.py im Bot-Projekt). Die
 * Mod holt die Datei alle N Sekunden - erst vom PC des Bot-Betreibers (mit dem
 * gemeinsamen Schluessel im Header), dann vom Gist als Reserve. Antwortet keiner,
 * bleibt der letzte Stand stehen.
 *
 * Aus der Datei kommen drei Dinge: das laufende Event mit allen Plaetzen (der Kasten
 * zeigt die Top 3 und den eigenen Platz), das Gesamt-Leaderboard, und Ankuendigungen.
 * Jede Ankuendigung hat eine Kennung; die Mod merkt sich, welche sie schon gezeigt hat,
 * und zeigt jede genau einmal als Banner - so kommt der Event-Start auch dann an,
 * wenn man erst Stunden spaeter einloggt.
 *
 * Geholt wird in einem eigenen Thread; im Tick wird nur das fertige Ergebnis
 * uebernommen. Aus heisst: kein Request, kein Thread, kein Kasten.
 */
public final class GuildEvents {

    private static final Gson GSON = new Gson();
    private static final Duration PRIMARY_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration FALLBACK_TIMEOUT = Duration.ofSeconds(6);
    private static final long RETRY_MILLIS = 30_000L;
    private static final int REMEMBERED_ANNOUNCEMENTS = 60;
    private static final int BANNER_COLOUR = 0x55FFFF;

    public record Row(String ign, double score, int rank) {
    }

    public record Event(String id, String name, String label, long start, long end, String status, List<Row> standings) {
    }

    public record Upcoming(String id, String name, String label, long start, long end) {
    }

    public record Announcement(String id, String kind, String event, long at) {
    }

    public record Feed(long updated, Event event, Upcoming next, List<Row> leaderboard,
                       List<Announcement> announcements, String origin) {
    }

    private static final AtomicReference<Feed> pending = new AtomicReference<>(null);
    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static volatile Feed current = null;
    private static volatile long attemptedAt = 0L;
    private static volatile long succeededAt = 0L;
    private static volatile String lastError = null;
    private static volatile String origin = "nothing loaded";

    private GuildEvents() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(GuildEvents::tick);
    }

    private static GuildEventsCategory cfg() {
        return ModConfig.INSTANCE.guild.events;
    }

    /** Der zuletzt geholte Stand, oder null */
    public static Feed current() {
        return current;
    }

    public static boolean everLoaded() {
        return current != null;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.guildEvents()) return;

        Feed fresh = pending.getAndSet(null);
        if (fresh != null) apply(client, fresh);

        long now = System.currentTimeMillis();
        long wait = current == null && lastError != null ? RETRY_MILLIS : Math.max(30, cfg().refreshSeconds) * 1000L;
        if (now - attemptedAt < wait) return;
        if (!fetching.compareAndSet(false, true)) return;
        attemptedAt = now;

        String primary = cfg().primaryUrl == null ? "" : cfg().primaryUrl.trim();
        String fallback = cfg().fallbackUrl == null ? "" : cfg().fallbackUrl.trim();
        String key = cfg().sharedKey == null ? "" : cfg().sharedKey.trim();
        if (primary.isEmpty() && fallback.isEmpty()) {
            fetching.set(false);
            lastError = "no URL configured";
            return;
        }
        Thread worker = new Thread(() -> fetch(primary, fallback, key), "ShokiMod guild events");
        worker.setDaemon(true);
        worker.start();
    }

    /** Erst der PC, dann der Gist - der erste, der antwortet, gewinnt */
    private static void fetch(String primary, String fallback, String key) {
        try {
            String firstError = null;
            if (!primary.isEmpty()) {
                try {
                    pending.set(load(primary, key, PRIMARY_TIMEOUT, "primary"));
                    return;
                } catch (IOException | RuntimeException e) {
                    firstError = "primary: " + e.getMessage();
                }
            }
            if (!fallback.isEmpty()) {
                try {
                    pending.set(load(fallback, "", FALLBACK_TIMEOUT, "fallback"));
                    if (firstError != null) lastError = firstError + " (fallback answered)";
                    return;
                } catch (IOException | RuntimeException e) {
                    lastError = (firstError == null ? "" : firstError + "; ") + "fallback: " + e.getMessage();
                    return;
                }
            }
            lastError = firstError;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            fetching.set(false);
        }
    }

    private static Feed load(String url, String key, Duration timeout, String from) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ShokiMod")
                .timeout(timeout)
                .GET();
        if (!key.isEmpty()) request.header("X-ShokiMod-Key", key);
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        Feed feed = parse(response.body(), from);
        if (feed == null) throw new IOException("no usable JSON");
        return feed;
    }

    /** Liest die Datei tolerant: fehlende Felder sind leer, nicht tot */
    static Feed parse(String body, String from) {
        JsonObject root = GSON.fromJson(body, JsonObject.class);
        if (root == null) return null;

        Event event = null;
        if (root.has("event") && root.get("event").isJsonObject()) {
            JsonObject e = root.getAsJsonObject("event");
            event = new Event(str(e, "id"), str(e, "name"), str(e, "label"), lng(e, "start"), lng(e, "end"),
                    str(e, "status"), rows(e.getAsJsonArray("standings"), "score"));
        }
        Upcoming next = null;
        if (root.has("next") && root.get("next").isJsonObject()) {
            JsonObject n = root.getAsJsonObject("next");
            next = new Upcoming(str(n, "id"), str(n, "name"), str(n, "label"), lng(n, "start"), lng(n, "end"));
        }
        List<Announcement> announcements = new ArrayList<>();
        if (root.has("announcements") && root.get("announcements").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("announcements")) {
                if (!element.isJsonObject()) continue;
                JsonObject a = element.getAsJsonObject();
                announcements.add(new Announcement(str(a, "id"), str(a, "kind"), str(a, "event"), lng(a, "at")));
            }
        }
        return new Feed(lng(root, "updated"), event, next,
                rows(root.has("leaderboard") ? root.getAsJsonArray("leaderboard") : null, "points"),
                Collections.unmodifiableList(announcements), from);
    }

    private static List<Row> rows(JsonArray array, String valueField) {
        List<Row> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject r = element.getAsJsonObject();
            out.add(new Row(str(r, "ign"), dbl(r, valueField), (int) lng(r, "rank")));
        }
        return Collections.unmodifiableList(out);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static long lng(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double dbl(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Im Tick: neuen Stand uebernehmen, ungesehene Ankuendigungen als Banner zeigen */
    private static void apply(Minecraft client, Feed feed) {
        current = feed;
        origin = feed.origin();
        succeededAt = System.currentTimeMillis();
        lastError = null;
        SafariHud.invalidate(SafariHud.Panel.GUILD);

        GuildEventsCategory cfg = cfg();
        if (cfg.seenAnnouncements == null) cfg.seenAnnouncements = new ArrayList<>();
        boolean firstContact = cfg.seenAnnouncements.isEmpty();
        boolean changed = false;
        for (Announcement a : feed.announcements()) {
            if (a.id().isEmpty() || cfg.seenAnnouncements.contains(a.id())) continue;
            cfg.seenAnnouncements.add(a.id());
            changed = true;
            // Beim allerersten Kontakt nicht die ganze Vergangenheit nachspielen -
            // nur, was noch zum laufenden Event gehoert
            if (firstContact && (feed.event() == null || !a.id().startsWith(feed.event().id() + ":"))) continue;
            showAnnouncement(client, a, feed);
        }
        while (cfg.seenAnnouncements.size() > REMEMBERED_ANNOUNCEMENTS) cfg.seenAnnouncements.remove(0);
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    private static void showAnnouncement(Minecraft client, Announcement a, Feed feed) {
        GuildEventsCategory cfg = cfg();
        if ("start".equals(a.kind()) && cfg.startBanner) {
            banner("Guild '" + a.event() + "' Event Start", feed.event() == null ? "" : feed.event().label(), "Guild Event");
        } else if ("end".equals(a.kind()) && cfg.endBanner) {
            Row own = ownRow(client, feed.event());
            String place = own == null ? "" : "Your place: #" + own.rank();
            banner("Guild '" + a.event() + "' Event End", place, "Guild Event");
        }
    }

    private static void banner(String headline, String line, String tag) {
        BannerDesign design = ModConfig.INSTANCE.chat.banner.designOrDefault(cfg().bannerDesign);
        DropBanner.show(design, headline, line, tag, BANNER_COLOUR, new ItemStack(Items.DRAGON_HEAD));
    }

    /** Knopf in den Einstellungen: das Start-Banner mit einem Beispiel */
    public static void testBanner() {
        banner("Guild 'Kill Ender Dragon' Event Start", "Mob kills ender_dragon", "Guild Event");
    }

    /** Der eigene Platz im laufenden Event, oder null wenn nicht dabei */
    public static Row ownRow(Minecraft client, Event event) {
        if (event == null || client.player == null) return null;
        String me = client.getUser() == null ? "" : client.getUser().getName();
        for (Row row : event.standings()) {
            if (row.ign().equalsIgnoreCase(me)) return row;
        }
        return null;
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        StringBuilder out = new StringBuilder("guild events: ");
        if (!FeatureGate.guildEvents()) return out.append("off").toString();
        Feed feed = current;
        if (feed == null) out.append("NO DATA");
        else out.append(feed.event() == null ? "no running event" : "event " + feed.event().name())
                .append(" from ").append(origin)
                .append(", ").append(feed.leaderboard().size()).append(" leaderboard rows");
        if (succeededAt > 0) out.append(", last success ").append((System.currentTimeMillis() - succeededAt) / 1000L).append("s ago");
        if (attemptedAt > 0) out.append(", last attempt ").append((System.currentTimeMillis() - attemptedAt) / 1000L).append("s ago");
        if (lastError != null) out.append(", last error: ").append(lastError);
        return out.toString();
    }
}
