package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shokiteufel.shokimod.ShokiMod;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Die neueste Fassung holen und beim naechsten Start einsetzen.
 *
 * Der Weg ist so gewaehlt, dass nie zwei Jars derselben Mod nebeneinander liegen -
 * Fabric bricht dann naemlich beim Start ab. Deshalb laedt der Befehl die neue Datei
 * unter einer Endung, die Fabric nicht anfasst, und getauscht wird erst beim
 * Beenden des Spiels: Die alte Datei wird zu ".jar.old" umbenannt, die neue bekommt
 * ihren Namen. Geht das Umbenennen schief - Windows haelt die laufende Jar fest -,
 * bleibt die alte Fassung liegen und die Meldung sagt, was zu tun ist.
 *
 * Nichts davon passiert von selbst: Es laeuft nur auf "/shoki update".
 */
public final class ModUpdater {

    private static final String RELEASES = "https://api.github.com/repos/shokiteufel/ShokiMod/releases?per_page=30";
    /** Die heruntergeladene Datei, bis sie beim Beenden eingesetzt wird */
    private static final String PENDING = "shokimod-update.jar.part";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private static volatile boolean running = false;

    private ModUpdater() {
    }

    /** Die laufende Fassung, etwa "1.8.12+26.1.x" */
    public static String current() {
        return FabricLoader.getInstance().getModContainer("shokimod")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    /** Woran man die passende Datei erkennt: "+26.1.x" oder "+26.2" */
    static String branch(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? "" : version.substring(plus);
    }

    /** Nur die Zahlen vor dem Pluszeichen, zum Vergleichen */
    static int[] numbers(String version) {
        String head = version.split("\\+")[0];
        String[] teile = head.split("\\.");
        int[] out = new int[Math.max(3, teile.length)];
        for (int i = 0; i < teile.length; i++) {
            try {
                out[i] = Integer.parseInt(teile[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** Ist a neuer als b? */
    static boolean newer(String a, String b) {
        int[] links = numbers(a);
        int[] rechts = numbers(b);
        for (int i = 0; i < Math.max(links.length, rechts.length); i++) {
            int l = i < links.length ? links[i] : 0;
            int r = i < rechts.length ? rechts[i] : 0;
            if (l != r) return l > r;
        }
        return false;
    }

    /** Eine gefundene Fassung: wie sie heisst, wie die Datei heisst und wo sie liegt */
    public record Release(String version, String fileName, String url) {
    }

    /**
     * Die neueste Fassung fuer diesen Zweig aus den Releases.
     *
     * Gesucht wird nicht "das neueste Release", sondern die neueste Datei mit der
     * passenden Endung: Es gibt zwei Zweige, und das oberste Release kann das des
     * anderen sein.
     */
    static Release pick(JsonArray releases, String branch) {
        Release best = null;
        for (JsonElement element : releases) {
            if (!element.isJsonObject()) continue;
            JsonObject release = element.getAsJsonObject();
            if (release.has("draft") && release.get("draft").getAsBoolean()) continue;

            JsonElement assets = release.get("assets");
            if (assets == null || !assets.isJsonArray()) continue;
            for (JsonElement assetElement : assets.getAsJsonArray()) {
                JsonObject asset = assetElement.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (!name.endsWith(".jar") || name.contains("-sources")) continue;
                if (!branch.isEmpty() && !name.contains(branch + ".jar")) continue;

                String version = name.replace("shokimod-", "").replace(".jar", "");
                if (best == null || newer(version, best.version())) {
                    best = new Release(version, name, asset.get("browser_download_url").getAsString());
                }
            }
        }
        return best;
    }

    /** Der Befehl: nachsehen, holen, Bescheid sagen */
    public static void run() {
        if (running) {
            say("An update is already running.", ChatFormatting.GRAY);
            return;
        }
        running = true;
        say("Looking for a newer version...", ChatFormatting.GRAY);

        Thread worker = new Thread(ModUpdater::work, "shokimod-update");
        worker.setDaemon(true);
        worker.start();
    }

    private static void work() {
        try {
            String current = current();
            String branch = branch(current);
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

            HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ShokiMod/" + current)
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                say("GitHub answered " + response.statusCode() + " - try again later.", ChatFormatting.RED);
                return;
            }

            JsonElement json;
            try (InputStream in = response.body()) {
                json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            if (!json.isJsonArray()) {
                say("GitHub sent something unexpected.", ChatFormatting.RED);
                return;
            }

            Release newest = pick(json.getAsJsonArray(), branch);
            if (newest == null) {
                say("No file found for this Minecraft version (" + branch + ").", ChatFormatting.RED);
                return;
            }
            if (!newer(newest.version(), current)) {
                say("You are on the newest version (" + current + ").", ChatFormatting.GREEN);
                return;
            }

            say("Downloading " + newest.version() + "...", ChatFormatting.GRAY);
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
            Path pending = mods.resolve(PENDING);
            Files.createDirectories(mods);

            HttpRequest file = HttpRequest.newBuilder(URI.create(newest.url()))
                    .header("User-Agent", "ShokiMod/" + current)
                    .timeout(Duration.ofMinutes(2))
                    .build();
            HttpResponse<Path> download = client.send(file,
                    HttpResponse.BodyHandlers.ofFile(pending, java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                            java.nio.file.StandardOpenOption.WRITE));
            if (download.statusCode() != 200 || !Files.isRegularFile(pending) || Files.size(pending) < 10_000) {
                say("Download failed - nothing was changed.", ChatFormatting.RED);
                Files.deleteIfExists(pending);
                return;
            }

            // Der Name der neuen Datei wird daneben abgelegt, damit das Einsetzen beim
            // Beenden ohne zweite Abfrage auskommt
            Files.writeString(mods.resolve(PENDING + ".name"), newest.fileName(), StandardCharsets.UTF_8);
            ShokiMod.LOGGER.info("[Update] {} downloaded to {}", newest.fileName(), pending);
            say("Version " + newest.version() + " is ready. It replaces the old one when you close the game.",
                    ChatFormatting.GREEN);
        } catch (IOException | InterruptedException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Update] failed: {}", e.toString());
            say("Update failed: " + e.getClass().getSimpleName() + " - nothing was changed.", ChatFormatting.RED);
        } finally {
            running = false;
        }
    }

    /**
     * Beim Beenden: die alte Datei beiseite, die neue an ihren Platz.
     *
     * Erst wird die laufende Jar umbenannt - solange sie im Ordner liegt, wuerde Fabric
     * beim naechsten Start zwei Fassungen derselben Mod finden und abbrechen. Klappt das
     * Umbenennen nicht, bleibt die neue Datei unter ihrer Zwischen-Endung liegen; dann
     * ist nichts kaputt, nur nichts passiert.
     */
    public static void swapOnExit() {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        Path pending = mods.resolve(PENDING);
        Path nameFile = mods.resolve(PENDING + ".name");
        if (!Files.isRegularFile(pending) || !Files.isRegularFile(nameFile)) return;

        try {
            String name = Files.readString(nameFile, StandardCharsets.UTF_8).trim();
            if (name.isEmpty() || !name.endsWith(".jar")) return;

            Path running = ownJar(mods);
            if (running != null) {
                Path beiseite = mods.resolve(running.getFileName() + ".old");
                Files.deleteIfExists(beiseite);
                Files.move(running, beiseite, StandardCopyOption.ATOMIC_MOVE);
                ShokiMod.LOGGER.info("[Update] old file moved to {}", beiseite.getFileName());
            }
            Files.move(pending, mods.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(nameFile);
            ShokiMod.LOGGER.info("[Update] {} is in place", name);
        } catch (IOException | RuntimeException e) {
            ShokiMod.LOGGER.warn("[Update] could not put the new file in place: {}", e.toString());
        }
    }

    /** Die Jar, aus der diese Mod laeuft - oder null, wenn sie nicht im mods-Ordner liegt */
    static Path ownJar(Path mods) {
        try (var files = Files.list(mods)) {
            List<Path> jars = files.filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.startsWith("shokimod-") && name.endsWith(".jar");
            }).toList();
            return jars.size() == 1 ? jars.get(0) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void say(String text, ChatFormatting colour) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.player == null) return;
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ")
                    .withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(text).withStyle(colour)));
        });
    }
}
