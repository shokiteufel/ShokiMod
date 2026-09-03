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

    public record Event(String id, String name, String label, String reward, long start, long end, String status,
                        List<Row> standings) {
    }

    public record Upcoming(String id, String name, String label, String reward, long start, long end) {
    }

    /** Eine Ankuendigung traegt alles fuers Banner mit: Wertung, Reward, Zeitraum */
    public record Announcement(String id, String kind, String event, String label, String reward,
                               long start, long end, long at) {
    }

    /**
     * {@code events}: alle laufenden Events; {@code event}: das mit dem naechsten Ende, oder null;
     * {@code upcoming}: alle geplanten, der naechste Start zuerst
     */
    public record Feed(long updated, List<Event> events, List<Upcoming> upcoming, List<Row> leaderboard,
                       List<Announcement> announcements, String origin) {

        public Event event() {
            return events.isEmpty() ? null : events.get(0);
        }

        public Upcoming next() {
            return upcoming.isEmpty() ? null : upcoming.get(0);
        }
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
        startDueEvents(client);

        long now = System.currentTimeMillis();
        long wait = current == null && lastError != null ? RETRY_MILLIS : Math.max(15, cfg().refreshSeconds) * 1000L;
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
                    // Ein wechselnder Parameter umgeht den Zwischenspeicher des Gist-Hosts (sonst bis zu 5 Minuten alt)
                    String fresh = fallback + (fallback.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                    pending.set(load(fresh, "", FALLBACK_TIMEOUT, "fallback"));
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

        List<Event> events = new ArrayList<>();
        if (root.has("events") && root.get("events").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("events")) {
                if (element.isJsonObject()) events.add(eventOf(element.getAsJsonObject()));
            }
        } else if (root.has("event") && root.get("event").isJsonObject()) {
            // Aeltere Bot-Staende kennen nur ein Event
            events.add(eventOf(root.getAsJsonObject("event")));
        }
        List<Upcoming> upcoming = new ArrayList<>();
        if (root.has("upcoming") && root.get("upcoming").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("upcoming")) {
                if (element.isJsonObject()) upcoming.add(upcomingOf(element.getAsJsonObject()));
            }
        } else if (root.has("next") && root.get("next").isJsonObject()) {
            upcoming.add(upcomingOf(root.getAsJsonObject("next")));
        }
        List<Announcement> announcements = new ArrayList<>();
        if (root.has("announcements") && root.get("announcements").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("announcements")) {
                if (!element.isJsonObject()) continue;
                JsonObject a = element.getAsJsonObject();
                announcements.add(new Announcement(str(a, "id"), str(a, "kind"), str(a, "event"), str(a, "label"),
                        str(a, "reward"), lng(a, "start"), lng(a, "end"), lng(a, "at")));
            }
        }
        return new Feed(lng(root, "updated"), Collections.unmodifiableList(events), Collections.unmodifiableList(upcoming),
                rows(root.has("leaderboard") ? root.getAsJsonArray("leaderboard") : null, "points"),
                Collections.unmodifiableList(announcements), from);
    }

    private static Upcoming upcomingOf(JsonObject n) {
        return new Upcoming(str(n, "id"), str(n, "name"), str(n, "label"), str(n, "reward"), lng(n, "start"), lng(n, "end"));
    }

    private static Event eventOf(JsonObject e) {
        return new Event(str(e, "id"), str(e, "name"), str(e, "label"), str(e, "reward"), lng(e, "start"),
                lng(e, "end"), str(e, "status"), rows(e.getAsJsonArray("standings"), "score"));
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
            // nur, was noch zu einem laufenden Event gehoert
            if (firstContact && !belongsToRunning(a, feed)) continue;
            showAnnouncement(client, a, feed);
        }
        while (cfg.seenAnnouncements.size() > REMEMBERED_ANNOUNCEMENTS) cfg.seenAnnouncements.remove(0);
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    /**
     * Der Start kommt nicht erst mit der naechsten Abfrage: die Mod kennt die geplanten Events
     * samt Startzeit und zeigt das Banner selbst, sobald die Uhr es sagt. Die Kennung ist dieselbe
     * wie die der Bot-Ankuendigung, deshalb erscheint es spaeter nicht noch einmal.
     */
    private static void startDueEvents(Minecraft client) {
        Feed feed = current;
        if (feed == null || feed.upcoming().isEmpty()) return;
        long nowSeconds = System.currentTimeMillis() / 1000L;
        GuildEventsCategory cfg = cfg();
        boolean changed = false;
        for (Upcoming u : feed.upcoming()) {
            if (u.start() <= 0 || u.start() > nowSeconds || u.id().isEmpty()) continue;
            String id = u.id() + ":start";
            if (cfg.seenAnnouncements.contains(id)) continue;
            cfg.seenAnnouncements.add(id);
            changed = true;
            showAnnouncement(client, new Announcement(id, "start", u.name(), u.label(), u.reward(), u.start(), u.end(), nowSeconds), feed);
            SafariHud.invalidate(SafariHud.Panel.GUILD);
        }
        if (changed) ModConfig.INSTANCE.saveNow();
    }

    private static boolean belongsToRunning(Announcement a, Feed feed) {
        for (Event e : feed.events()) {
            if (a.id().startsWith(e.id() + ":")) return true;
        }
        return false;
    }

    private static Event eventNamed(Feed feed, String name) {
        for (Event e : feed.events()) {
            if (e.name().equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    /**
     * Das grosse Banner: Name in der Ueberschrift, darunter Wertung und Reward, in der
     * dritten Zeile die Dauer. Am Ende stattdessen der eigene Platz.
     */
    private static void showAnnouncement(Minecraft client, Announcement a, Feed feed) {
        GuildEventsCategory cfg = cfg();
        String reward = a.reward().isBlank() ? "" : "  |  Reward: " + a.reward();
        if ("start".equals(a.kind()) && cfg.startBanner) {
            banner("Guild Event: " + a.event(), a.label() + reward, "Duration: " + span(a.start(), a.end()));
        } else if ("end".equals(a.kind()) && cfg.endBanner) {
            Row own = ownRow(client, eventNamed(feed, a.event()));
            String place = own == null ? a.label() : "Your place: #" + own.rank();
            banner("Guild Event ended: " + a.event(), place + reward, "Results in Discord");
        }
    }

    private static void banner(String headline, String line, String tag) {
        BannerDesign design = ModConfig.INSTANCE.chat.banner.designOrDefault(cfg().bannerDesign);
        DropBanner.show(design, headline, line, tag, BANNER_COLOUR, new ItemStack(Items.DRAGON_HEAD));
    }

    /** "5d 2h", "3h 30m", "45m" */
    public static String span(long startSeconds, long endSeconds) {
        long left = Math.max(0, endSeconds - startSeconds);
        long days = left / 86400, hours = (left % 86400) / 3600, minutes = (left % 3600) / 60;
        if (days > 0) return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        if (hours > 0) return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        return Math.max(1, minutes) + "m";
    }

    /** Knopf in den Einstellungen: das Start-Banner mit einem Beispiel */
    public static void testBanner() {
        banner("Guild Event: Kill Ender Dragon", "Mob kills: Dragon  |  Reward: 10M Coins", "Duration: 5d");
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
