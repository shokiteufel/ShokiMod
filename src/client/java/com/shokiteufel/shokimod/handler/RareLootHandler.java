package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.RareLootCategory;
import com.shokiteufel.shokimod.data.ModConfig.RareLootCategory.Tier;
import com.shokiteufel.shokimod.data.RareLootParser;
import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.render.ShokiModToast;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.ItemIcons;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.CollectionData;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.Value;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Seltene Funde aus dem Chat: Stufen-Alarm und Teilen in Party oder Gilde.
 *
 * Hypixel meldet jeden seltenen Fund als "RARE DROP!"-Zeile, gefangene Shards als
 * "CHARM!". Daraus wird der Fund gelesen, sein Wert im Basar oder Auktionshaus
 * nachgeschlagen, und dann greift die hoechste Stufe, deren Schwelle er erreicht -
 * mit ihrer eigenen Reaktion. Wer 50M findet, bekommt den 50M-Alarm und nicht
 * zusaetzlich die beiden darunter.
 *
 * Das Teilen schickt eine Meldung nach eigener Vorlage in die Party und auf Wunsch
 * in die Gilde, ab einer eigenen Schwelle. Kommt sie als "Party > ..." zurueck,
 * wird sie nicht noch einmal gelesen - sonst teilte die Mod ihre eigene Meldung.
 *
 * Jede Entscheidung wird festgehalten - im Log und in einem kurzen Gedaechtnis,
 * das der Diagnoseknopf in eine Datei schreibt. Der Tester sieht das Spiel, nicht
 * den Code; die Datei sagt ihm, was die Mod gesehen und warum sie geschwiegen hat.
 *
 * Nachbau von Skysofts Rare Drop Titles und Rare Loot Sharing (LGPL-3.0,
 * Akinsoft), mit drei Stufen statt einer.
 */
public final class RareLootHandler {

    private static final long TOAST_MILLIS = 5000L;
    /** So lange nach "LOOT SHARE You received ..." gilt der naechste Fund als geteilt */
    private static final long LOOTSHARE_WINDOW_MILLIS = 2000L;
    /** So viele Entscheidungen bleiben fuer den Bericht im Gedaechtnis */
    private static final int REMEMBERED_EVENTS = 40;
    private static final String DIAGNOSTICS_FILE = "shokimod-diagnostics.txt";

    /** Die Vorlage, wenn das Feld leer ist - Skysofts Wortlaut */
    public static final String DEFAULT_SHARE_TEMPLATE = "{prefix} {item} {mf} {value}";

    /** Je Stufe eine Farbe, damit man schon am Banner sieht, welche es war */
    private static final int[] TIER_COLOURS = {0x55FF55, 0xFFD700, 0xFF55FF};

    private static final Pattern LOOTSHARE_RECEIPT = Pattern.compile(
            "^LOOT SHARE You received(?: .+?)? for assisting (?<player>[A-Za-z0-9_]{1,16})!(?: \\(\\d+\\))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    /** Zeilen anderer Spieler und eigene geteilte Meldungen */
    private static final String[] SKIPPED_PREFIXES = {"Party >", "Guild >", "Co-op >", "From ", "To "};

    /**
     * Solange laeuft ein angefangenes Beutebuendel aus dem Nucleus. Alle Zeilen fallen in
     * derselben Sekunde; die Frist ist nur das Netz fuer den Fall, dass die Schlusszeile
     * ausbleibt - sonst wuerde die naechste Chatzeile Stunden spaeter noch als Fund gelten.
     */
    private static final long BUNDLE_WINDOW_MILLIS = 10_000L;
    /** Und so viele Zeilen hoechstens, falls Hypixel die Schlusszeile einmal aendert */
    private static final int BUNDLE_MAX_LINES = 40;
    private static long bundleUntil = 0L;
    private static int bundleLines = 0;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Deque<String> events = new ArrayDeque<>();
    private static long lastLootShareAt = 0L;

    private RareLootHandler() {
    }

    public static void register() {
        // Die Listen sollen dastehen, bevor der erste Fund faellt. Ist alles aus,
        // wird hier nichts angestossen und nichts geholt
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !GameState.Server.isSkyblock()) return;
            if (!active()) return;
            ItemValue.prefetch();
        });
    }

    private static RareLootCategory cfg() {
        return ModConfig.INSTANCE.chat.rareLoot;
    }

    /** Alarm oder Teilen eingeschaltet? */
    public static boolean active() {
        RareLootCategory cfg = cfg();
        return cfg.enabled || cfg.shareEnabled;
    }

    /** Nach einem Weltwechsel gilt kein Lootshare-Beleg mehr */
    public static void reset() {
        lastLootShareAt = 0L;
        bundleUntil = 0L;
        bundleLines = 0;
    }

    /**
     * @param plain die Chatzeile ohne Farbcodes
     */
    public static void onChatMessage(String plain) {
        if (plain == null || !active()) return;
        if (!GameState.Server.isSkyblock()) return;

        String clean = plain.trim();
        long now = System.currentTimeMillis();

        // Das Beutebuendel aus dem Crystal Nucleus meldet seine Funde nicht einzeln,
        // sondern als Liste unter einer Ueberschrift. Diese Zeilen tragen keines der
        // Kennzeichen ("RARE DROP!", "You dug out"), auf die der Parser sonst hoert.
        if (RareLootParser.isBundleStart(clean)) {
            bundleUntil = now + BUNDLE_WINDOW_MILLIS;
            bundleLines = 0;
            note("nucleus bundle: start");
            return;
        }
        if (bundleUntil > 0L) {
            if (now > bundleUntil || RareLootParser.isBundleEnd(clean) || ++bundleLines > BUNDLE_MAX_LINES) {
                note("nucleus bundle: end after " + bundleLines + " line(s)");
                bundleUntil = 0L;
                bundleLines = 0;
            } else {
                Drop bundled = RareLootParser.parseBundleLine(clean);
                if (bundled != null) {
                    evaluate(bundled, clean, now);
                    return;
                }
                // Ueberschrift oder Leerzeile: das Buendel laeuft weiter, die Zeile ist
                // aber auch fuer die uebrigen Regeln keine - hier ist Schluss
                return;
            }
        }

        for (String prefix : SKIPPED_PREFIXES) {
            if (clean.startsWith(prefix)) return;
        }
        if (LOOTSHARE_RECEIPT.matcher(clean).matches()) {
            lastLootShareAt = now;
            // Sonst nur ein Vermerk fuer den folgenden Fund. Bei Shards aber ist diese Zeile
            // die einzige Meldung - dann zaehlt sie selbst als Fund und laeuft weiter
            if (RareLootParser.parse(clean) == null) return;
        }

        Drop drop = RareLootParser.parse(clean);
        if (drop == null) return;
        evaluate(drop, clean, now);
    }

    /**
     * Einen erkannten Fund bewerten und melden.
     *
     * Steht bewusst fuer sich: Ein Fund aus dem Nucleus-Buendel soll durch dieselben
     * Preise, Stufen und Meldungen laufen wie ein einzeln gemeldeter, damit beide nie
     * auseinanderlaufen koennen.
     */
    private static void evaluate(Drop drop, String clean, long now) {
        RareLootCategory cfg = cfg();
        List<String> candidates = candidatesFor(drop);
        Value value = ItemValue.resolve(candidates, drop.amount(), cfg.shardPriceMode, cfg.bazaarPriceMode);
        boolean lootshare = lastLootShareAt > 0L && now - lastLootShareAt <= LOOTSHARE_WINDOW_MILLIS;

        note("drop \"" + clean + "\" -> name=" + drop.displayName() + " x" + drop.amount()
                + " ids=" + candidates
                + (value == null ? " value=UNKNOWN" : " value=" + ItemValue.format(value.coins())
                + " via " + value.itemId() + "/" + value.source()));

        Minecraft client = Minecraft.getInstance();

        if (cfg.enabled) {
            if (value == null) {
                note("  no alert: no price known for any id");
            } else {
                Tier tier = tierFor(value.coins());
                if (tier == null) {
                    note("  no alert: below every enabled tier");
                } else {
                    note("  alert tier " + tier.number());
                    announce(client, tier, headline(drop), value.coins(), value.itemId());
                }
            }
        }

        if (cfg.shareEnabled) share(client, drop, value, lootshare);
    }

    /**
     * Woher die Kennung kommt: erst Hypixels Item-Liste, dann das Raten aus dem Namen.
     *
     * "Ghostly Boots" heisst GHOST_BOOTS - das steht nur in der Liste. Was die Liste
     * nicht kennt, etwa Buecher und Shards, liefert der Parser aus dem Namen.
     */
    private static List<String> candidatesFor(Drop drop) {
        LinkedHashSet<String> out = new LinkedHashSet<>(ItemNames.idsFor(drop.displayName()));
        for (String candidate : drop.itemIdCandidates()) {
            // "Wither Spectre" ist im Basar SHARD_WITHER_SPECTER: die Produktliste weiss es
            out.add(ItemValue.canonicalShard(candidate));
        }
        return new ArrayList<>(out);
    }

    private static String headline(Drop drop) {
        return drop.amount() > 1 ? drop.amount() + "x " + drop.displayName() : drop.displayName();
    }

    /**
     * Die hoechste Stufe, deren Schwelle der Betrag erreicht - oder null.
     *
     * Hoechste heisst hoechste Schwelle, nicht hoechste Nummer: die Reihenfolge im
     * Menue ist nur eine Anzeige. Eine Stufe ohne gueltige Schwelle oder mit
     * Schalter aus zaehlt nicht mit.
     */
    static Tier tierFor(double coins) {
        Tier best = null;
        double bestThreshold = 0;
        for (Tier tier : cfg().tiers()) {
            if (!tier.enabled()) continue;

            double threshold = ItemValue.parseAmount(tier.threshold());
            if (threshold <= 0 || coins < threshold) continue;
            if (best == null || threshold > bestThreshold) {
                best = tier;
                bestThreshold = threshold;
            }
        }
        return best;
    }

    /**
     * Der Testknopf: feuert eine Stufe einmal mit einem Beispiel.
     *
     * Ohne echten Fund, ohne Preisliste - genau die Reaktion, die eingestellt ist.
     * Der Betrag ist die Schwelle der Stufe selbst, damit man sieht, ab wann sie greift.
     */
    public static void test(int number) {
        Tier tier = cfg().tier(number);
        double threshold = ItemValue.parseAmount(tier.threshold());
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            announce(client, tier, "Test: 3x Ghost Shard", Math.max(threshold, 0), "SHARD_GHOST");
            // Beim Testen soll man sehen, welches Design wirklich gezogen wird - und ob es eine
            // eigene Farbe hat oder die der Stufe nimmt
            BannerDesign used = ModConfig.INSTANCE.chat.banner.designOrDefault(tier.design());
            String wanted = tier.design() == null ? "" : tier.design().trim();
            String colourNote = used.colour == null || used.colour.isBlank() ? "tier colour" : "colour " + used.colour;
            String match = used.name.equalsIgnoreCase(wanted) ? "" : " (no design named " + wanted + " - took the first one)";
            if (client.player != null) client.player.sendSystemMessage(Component.literal(
                    "[ShokiMod] Tier " + number + " banner: " + used.name + ", " + colourNote + match));
        });
    }

    private static void announce(Minecraft client, Tier tier, String headline, double coins, String itemId) {
        String worth = ItemValue.format(coins);
        int colour = TIER_COLOURS[Math.min(Math.max(tier.number() - 1, 0), TIER_COLOURS.length - 1)];

        if (tier.banner()) {
            // Der Wert geht mit: Fallen mehrere auf einmal, reiht die Einblendung danach
            DropBanner.show(ModConfig.INSTANCE.chat.banner.designOrDefault(tier.design()),
                    headline, "(" + worth + ")", "Tier " + tier.number(), colour,
                    ItemIcons.stackFor(itemId), coins);
        }

        if (tier.toast() && client.getToastManager() != null) {
            client.getToastManager().addToast(new ShokiModToast(
                    Component.literal(headline + " (" + worth + ")"), TOAST_MILLIS, null));
        }

        if (tier.chat() && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "§6" + headline + " §e(" + worth + ") §8Tier " + tier.number()));
        }

        String sound = tier.sound();
        if (sound != null && !sound.isBlank()) {
            CustomSoundPlayer.play(sound, AlertVolume.factor(), RareLootHandler.class);
        }
    }

    /**
     * Schickt den Fund in die gewaehlten Kanaele, sobald er die Teil-Schwelle erreicht.
     *
     * Schwelle 0 heisst: jeden Fund teilen, auch ohne bekannten Wert. Sonst braucht
     * es einen Wert - ein Fund ohne Preis ist nicht "wertlos", nur unbekannt, und
     * wird deshalb nicht geteilt.
     */
    private static void share(Minecraft client, Drop drop, Value value, boolean lootshare) {
        RareLootCategory cfg = cfg();
        double threshold = ItemValue.parseAmount(cfg.shareThreshold);
        // Die Vorlage der erreichten Stufe, damit ein 50M-Fund anders klingt als ein 1M-Fund
        Tier reached = value == null ? null : tierFor(value.coins());
        String template = reached == null ? cfg.shareTemplate : cfg.shareTemplateFor(reached.number());
        String message = shareText(drop, value, lootshare, template, cfg.shareMagicFind, cfg.shareValue);

        if (threshold > 0 && value == null) {
            note("  not shared: no price known");
            return;
        }
        if (threshold > 0 && value.coins() < threshold) {
            note("  not shared: below " + cfg.shareThreshold);
            return;
        }
        if (!cfg.shareParty && !cfg.shareGuild) {
            note("  not shared: no channel chosen");
            return;
        }
        if (message.isBlank()) {
            note("  not shared: template produced an empty line");
            return;
        }

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            note("  not shared: no connection");
            return;
        }

        if (cfg.shareParty) {
            note("  sharing to party: " + message);
            connection.sendCommand("pc " + message);
        }
        if (cfg.shareGuild) {
            note("  sharing to guild: " + message);
            connection.sendCommand("gc " + message);
        }
    }

    /**
     * Die Meldung nach Vorlage.
     *
     * Platzhalter: {prefix} wird "RARE DROP!" oder "LOOTSHARE DROP!", {item} der Fund
     * mit Stueckzahl, {name} nur der Name, {amount} nur die Zahl, {mf} der
     * Magic-Find-Zusatz in Klammern, {value} der Wert in Klammern, {coins} der
     * blanke Betrag. Was abgeschaltet oder unbekannt ist, wird leer - und die
     * Luecke geschlossen, damit keine doppelten Leerzeichen bleiben.
     */
    static String shareText(Drop drop, Value value, boolean lootshare, String template,
                            boolean withMagicFind, boolean withValue) {
        String pattern = template == null || template.isBlank() ? DEFAULT_SHARE_TEMPLATE : template;
        String mf = withMagicFind && drop.context() != null && !drop.context().isBlank()
                ? "(" + drop.context() + ")" : "";
        String coins = withValue && value != null ? ItemValue.format(value.coins()) : "";
        String worth = coins.isEmpty() ? "" : "(+" + coins + " coins)";

        String out = pattern
                .replace("{prefix}", lootshare ? "LOOTSHARE DROP!" : "RARE DROP!")
                .replace("{item}", headline(drop))
                .replace("{name}", drop.displayName())
                .replace("{amount}", String.valueOf(drop.amount()))
                .replace("{mf}", mf)
                .replace("{value}", worth)
                .replace("{coins}", coins);
        return MULTI_SPACE.matcher(out).replaceAll(" ").trim();
    }

    /** Ins Log und ins Gedaechtnis - beides, damit der Bericht auch ohne Log etwas sagt */
    private static void note(String text) {
        ShokiMod.LOGGER.info("[RareLoot] {}", text);
        synchronized (events) {
            events.addLast(LocalDateTime.now().format(CLOCK) + " " + text);
            while (events.size() > REMEMBERED_EVENTS) events.removeFirst();
        }
    }

    /**
     * Der Diagnoseknopf: schreibt alles Wissenswerte neben latest.log und oeffnet den Ordner.
     *
     * Version, Ort, Einstellungen, Zustand der drei Listen und die letzten
     * Entscheidungen. Eine Datei, die man einfach weiterschicken kann - der Tester
     * muss nichts suchen und nichts abtippen.
     */
    public static void writeDiagnostics() {
        Path logs = FabricLoader.getInstance().getGameDir().resolve("logs");
        Path file = logs.resolve(DIAGNOSTICS_FILE);
        String report = buildReport(logs);

        try {
            Files.createDirectories(logs);
            Files.writeString(file, report, StandardCharsets.UTF_8);
            ShokiMod.LOGGER.info("[RareLoot] diagnostics written to {}", file);
        } catch (IOException e) {
            ShokiMod.LOGGER.warn("[RareLoot] could not write diagnostics: {}", e.toString());
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("§6[ShokiMod] §eDiagnostics written to §f" + file));
        }
        Util.getPlatform().openPath(logs);
    }

    private static String buildReport(Path logs) {
        RareLootCategory cfg = cfg();
        StringBuilder out = new StringBuilder();
        String version = FabricLoader.getInstance().getModContainer("shokimod")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        out.append("ShokiMod ").append(version).append(" - Rare Loot diagnostics\n");
        out.append("written ").append(LocalDateTime.now()).append('\n');
        out.append("send this file together with ").append(logs.resolve("latest.log")).append("\n\n");

        out.append("[location]\n");
        out.append("skyblock=").append(GameState.Server.isSkyblock())
                .append(" area=").append(GameState.Server.map)
                .append(" server=").append(GameState.Server.id).append("\n\n");

        out.append("[settings]\n");
        out.append("enabled=").append(cfg.enabled)
                .append(" shardPrice=").append(cfg.shardPriceMode)
                .append(" bazaarPrice=").append(cfg.bazaarPriceMode)
                .append(" alertVolume=").append(AlertVolume.factor()).append('\n');
        out.append("tier1Design=").append(cfg.tier1Design)
                .append(" tier2Design=").append(cfg.tier2Design)
                .append(" tier3Design=").append(cfg.tier3Design).append("\n\n");

        out.append("[guild]\n").append(GuildEvents.status()).append("\n\n");

        out.append("[collections]\n").append(CollectionTracker.status()).append("\n")
                .append(CollectionData.status()).append("\n\n");

        out.append("[banners]\n");
        List<BannerDesign> designs = ModConfig.INSTANCE.chat.banner.designs;
        if (designs == null || designs.isEmpty()) out.append("(none)\n");
        else for (BannerDesign d : designs) {
            out.append(d.name).append(": colour=").append(d.colour == null || d.colour.isBlank() ? "tier" : d.colour)
                    .append(" anchor=").append(d.anchor).append(" bg=").append(d.background)
                    .append(" x=").append(d.x).append(" y=").append(d.y).append(" scale=").append(d.scale).append('\n');
        }
        for (Tier tier : cfg.tiers()) {
            out.append("tier").append(tier.number())
                    .append(": enabled=").append(tier.enabled())
                    .append(" threshold=").append(tier.threshold())
                    .append(" (=").append((long) ItemValue.parseAmount(tier.threshold())).append(")")
                    .append(" banner=").append(tier.banner())
                    .append(" banner=").append(tier.design())
                    .append(" toast=").append(tier.toast())
                    .append(" chat=").append(tier.chat())
                    .append(" sound=").append(tier.sound()).append('\n');
        }
        out.append("share: enabled=").append(cfg.shareEnabled)
                .append(" party=").append(cfg.shareParty)
                .append(" guild=").append(cfg.shareGuild)
                .append(" threshold=").append(cfg.shareThreshold)
                .append(" (=").append((long) ItemValue.parseAmount(cfg.shareThreshold)).append(")")
                .append(" mf=").append(cfg.shareMagicFind)
                .append(" value=").append(cfg.shareValue)
                .append(" template=\"").append(cfg.shareTemplate).append("\"")
                .append(" tier1=\"").append(cfg.shareTemplate1).append("\"")
                .append(" tier2=\"").append(cfg.shareTemplate2).append("\"")
                .append(" tier3=\"").append(cfg.shareTemplate3).append("\"\n\n");

        out.append("[performance]\n")
           .append(com.shokiteufel.shokimod.scanner.PerformanceState.status()).append("\n\n");

        out.append("[pets]\n")
           .append(com.shokiteufel.shokimod.scanner.PetState.status()).append("\n")
           .append(com.shokiteufel.shokimod.util.PetProfitData.status()).append("\n\n");

        out.append("[shards]\n")
           .append(com.shokiteufel.shokimod.util.ShardProfitData.status()).append("\n\n");

        out.append("[price lists]\n");
        for (String line : ItemValue.statusLines()) out.append(line).append('\n');
        out.append('\n');

        out.append("[recent decisions, oldest first]\n");
        synchronized (events) {
            if (events.isEmpty()) out.append("(none since start)\n");
            for (String event : events) out.append(event).append('\n');
        }
        return out.toString();
    }
}
