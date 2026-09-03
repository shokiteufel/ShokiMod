package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.render.ShinyAlert;
import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ModPaths;
import com.shokiteufel.shokimod.gui.ColorPickerScreen;
import com.shokiteufel.shokimod.gui.ChatRuleScreen;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.gui.CustomMobScreen;
import com.shokiteufel.shokimod.gui.MarkerSettingsScreen;
import com.shokiteufel.shokimod.gui.BannerDesignScreen;
import com.shokiteufel.shokimod.gui.SoundPickerScreen;
import com.shokiteufel.shokimod.handler.CakeReminder;
import com.shokiteufel.shokimod.handler.GuildEvents;
import com.shokiteufel.shokimod.handler.HuntingTracker;
import com.shokiteufel.shokimod.handler.RareLootHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

public class ModConfig extends Config {

    // ★修正1: final を外して、ファイルから読み込んだデータで上書きできるようにします
    public static ModConfig INSTANCE = new ModConfig();

    // ★修正2: セーブ＆ロード用のGsonを準備 (@Expose が付いた変数だけを処理する設定)
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    // ★修正3: 保存先を config/shokimod/shokimod_config.properties に変更
    private static File getConfigFile() {
        // "config/shokimod" というフォルダへのパスを作成
        File dir = ModPaths.configDir().toFile();

        // もし "shokimod" フォルダが存在しなければ、新しく作成する
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // そのフォルダの中の "shokimod_config.properties" を指定
        return new File(dir, "shokimod_config.properties");
    }
    // ★修正4: 起動時にファイルを読み込むメソッドを強化
    public static void load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                System.err.println("[ShokiMod] Old config format detected or file corrupted. Overwriting with JSON...");
            }
        }

        // ★超重要: Gsonでデータを読み込むと、transient（保存除外）にしていた「ボタンの処理」が消滅してしまうため、ここで再セットする！
        if (INSTANCE.mobVisuals == null) INSTANCE.mobVisuals = new MobVisualsCategory();
        if (INSTANCE.chat == null) INSTANCE.chat = new ChatRulesCategory();
        if (INSTANCE.safari == null) INSTANCE.safari = new SafariCategory();
        // Der Unterreiter kann in einer Datei von vor 1.1.4 als null stehen
        if (INSTANCE.chat.rareLoot == null) INSTANCE.chat.rareLoot = new RareLootCategory();
        if (INSTANCE.chat.banner == null) INSTANCE.chat.banner = new BannerCategory();
        migrateBanners();
        if (INSTANCE.hunting == null) INSTANCE.hunting = new HuntingCategory();
        if (INSTANCE.guild == null) INSTANCE.guild = new GuildCategory();
        if (INSTANCE.guild.events == null) INSTANCE.guild.events = new GuildEventsCategory();
        if (INSTANCE.guild.events.seenAnnouncements == null) INSTANCE.guild.events.seenAnnouncements = new ArrayList<>();
        if (INSTANCE.fishing == null) INSTANCE.fishing = new FishingCategory();
        if (INSTANCE.fishing.hotspot == null) INSTANCE.fishing.hotspot = new HotspotCategory();
        if (INSTANCE.chat.reminder == null) INSTANCE.chat.reminder = new ReminderCategory();
        if (INSTANCE.chat.reminder.cakes == null) INSTANCE.chat.reminder.cakes = new HashMap<>();
        if (INSTANCE.hunting.tracker == null) INSTANCE.hunting.tracker = new HuntingTrackerCategory();
        if (INSTANCE.hunting.tracker.counts == null) INSTANCE.hunting.tracker.counts = new HashMap<>();
        if (INSTANCE.hunting.tracker.priceMode == null) INSTANCE.hunting.tracker.priceMode = ItemValue.PriceMode.INSTANT_SELL;
        // Der Unterreiter fehlt in Dateien bis 1.1.17; die drei Werte lagen bis dahin eine Ebene hoeher
        MobVisualsCategory visuals = INSTANCE.mobVisuals;
        if (visuals.safari == null) visuals.safari = new MobVisualsCategory.SafariSecretCategory();
        if (visuals.legacyShinyAlert != null) { visuals.safari.shinyAlert = visuals.legacyShinyAlert; visuals.legacyShinyAlert = null; }
        if (visuals.legacyShinyColour != null) { visuals.safari.shinyColour = visuals.legacyShinyColour; visuals.legacyShinyColour = null; }
        if (visuals.legacyHideyhoFinder != null) { visuals.safari.hideyhoFinder = visuals.legacyHideyhoFinder; visuals.legacyHideyhoFinder = null; }
        if (visuals.safari.shinyColour == null) visuals.safari.shinyColour = "FFD700";
        // Wunsch der Tester: der Shiny-Alarm soll bei allen an sein - einmal setzen, danach zaehlt die eigene Wahl
        if (!visuals.safari.defaultOnApplied) {
            visuals.safari.defaultOnApplied = true;
            visuals.safari.shinyAlert = true;
        }
        // Der Shiny-Schalter zog aus dem Safari-Reiter hierher; den alten Stand einmal mitnehmen
        if (!visuals.shinyMoved) {
            visuals.shinyMoved = true;
            visuals.safari.shinyAlert = INSTANCE.safari.shinyAlertEnabled;
            if (INSTANCE.safari.shinyColor != null && !INSTANCE.safari.shinyColor.isBlank()) {
                visuals.safari.shinyColour = INSTANCE.safari.shinyColor;
            }
        }
        // Gson laesst ein unbekanntes Enum-Wort als null stehen - dann gilt der Standard
        if (INSTANCE.chat.rareLoot.shardPriceMode == null) INSTANCE.chat.rareLoot.shardPriceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (INSTANCE.chat.rareLoot.bazaarPriceMode == null) INSTANCE.chat.rareLoot.bazaarPriceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (INSTANCE.chat.rareLoot.shareTemplate == null) INSTANCE.chat.rareLoot.shareTemplate = "{prefix} {item} {mf} {value}";
        if (INSTANCE.chat.rareLoot.shareTemplate1 == null) INSTANCE.chat.rareLoot.shareTemplate1 = "";
        if (INSTANCE.chat.rareLoot.shareTemplate2 == null) INSTANCE.chat.rareLoot.shareTemplate2 = "";
        if (INSTANCE.chat.rareLoot.shareTemplate3 == null) INSTANCE.chat.rareLoot.shareTemplate3 = "";
        if (INSTANCE.safari.shinyCallSoundFile == null) INSTANCE.safari.shinyCallSoundFile = "shiny-alert.mp3";

        adoptLegacyCategory();

        // Das Leuchten stand bis 1.1.0 in der Safari. Wer es dort ausgeschaltet hatte,
        // soll es nach dem Umzug nicht wieder angehen sehen
        if (INSTANCE.safari.glowingHudText != null) {
            INSTANCE.hud.glowingHudText = INSTANCE.safari.glowingHudText;
            INSTANCE.safari.glowingHudText = null;
        }

        INSTANCE.mobVisuals.resetNameplateScale =
                () -> INSTANCE.mobVisuals.nameplateScale = MobVisualsCategory.DEFAULT_NAMEPLATE_SCALE;
        INSTANCE.mobVisuals.openNearbyPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new CustomMobScreen(Minecraft.getInstance().screen, true)));
        INSTANCE.mobVisuals.openCustomManager = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new CustomMobScreen(Minecraft.getInstance().screen, false)));
        INSTANCE.chat.openChatRules = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new ChatRuleScreen(Minecraft.getInstance().screen)));
        INSTANCE.safari.openMarkerSettings = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new MarkerSettingsScreen(Minecraft.getInstance().screen)));
        INSTANCE.hud.openHudEditor = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(
                        new HudEditorScreen(Minecraft.getInstance().screen, false)));
        INSTANCE.safari.openContestSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.safari.contestWarningSound,
                        picked -> INSTANCE.safari.contestWarningSound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.openTier1Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier1Sound,
                        picked -> INSTANCE.chat.rareLoot.tier1Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier1 = () -> RareLootHandler.test(1);
        INSTANCE.chat.rareLoot.openTier2Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier2Sound,
                        picked -> INSTANCE.chat.rareLoot.tier2Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier2 = () -> RareLootHandler.test(2);
        INSTANCE.chat.rareLoot.openTier3Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier3Sound,
                        picked -> INSTANCE.chat.rareLoot.tier3Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier3 = () -> RareLootHandler.test(3);
        INSTANCE.chat.rareLoot.openDiagnostics = () -> Minecraft.getInstance().execute(RareLootHandler::writeDiagnostics);
        INSTANCE.chat.testAlertVolume = () -> Minecraft.getInstance().execute(AlertVolume::test);
        INSTANCE.hunting.tracker.resetTracker = () -> Minecraft.getInstance().execute(HuntingTracker::reset);
        INSTANCE.guild.events.testBanner = () -> Minecraft.getInstance().execute(GuildEvents::testBanner);
        INSTANCE.chat.reminder.testCake = () -> Minecraft.getInstance().execute(CakeReminder::test);
        INSTANCE.chat.reminder.clearCakes = () -> Minecraft.getInstance().execute(CakeReminder::clear);
        INSTANCE.chat.banner.openEditor = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new HudEditorScreen(Minecraft.getInstance().screen, true)));
        INSTANCE.chat.banner.openSandbox = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new BannerDesignScreen(Minecraft.getInstance().screen)));
        INSTANCE.safari.openShinyCallSound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.safari.shinyCallSoundFile,
                        picked -> INSTANCE.safari.shinyCallSoundFile = picked,
                        1.0f)));
        INSTANCE.safari.testShinyCallSound = () -> Minecraft.getInstance().execute(ShinyAlert::testCallSound);

        if (INSTANCE.mobVisuals.customTargets == null) INSTANCE.mobVisuals.customTargets = new ArrayList<>();
        if (INSTANCE.chat.chatRules == null) INSTANCE.chat.chatRules = new ArrayList<>();
        if (INSTANCE.chat.discoveredAreas == null) INSTANCE.chat.discoveredAreas = new ArrayList<>();
        INSTANCE.chat.chatRules.removeIf(r -> r == null);
        INSTANCE.chat.chatRules.forEach(r -> {
            // Regeln aus aelteren Fassungen haben noch keine Kennung und keine Sperrliste
            if (r.id == null || r.id.isBlank()) r.id = java.util.UUID.randomUUID().toString();
            if (r.blocks == null) r.blocks = new ArrayList<>();
            if (r.except == null) r.except = "";
        });
        INSTANCE.mobVisuals.customTargets.removeIf(m -> m == null);
        INSTANCE.mobVisuals.customTargets.forEach(m -> {
            m.pattern = CustomMob.normalize(m.pattern);
            // 後から足したフィールドは旧設定に無いので、読み込み後に補う
            if (m.mode == null) m.mode = CustomMob.Mode.NAME;
            if (m.typeId == null) m.typeId = "";
            if (m.variant == null) m.variant = "";
            if (m.label == null || m.label.isEmpty()) m.label = m.pattern;
            if (m.defaultLabel == null || m.defaultLabel.isEmpty()) m.defaultLabel = m.label;
        });
        INSTANCE.mobVisuals.customTargets.removeIf(m -> m.pattern.isEmpty() && m.typeId.isEmpty());

        adoptOldMinValues();

        INSTANCE.saveNow();
    }

    /**
     * Die Banner-Designs: beim ersten Start die Vorlagen anlegen, alte Einstellungen mitnehmen.
     *
     * Bis 1.1.16 gab es feste Stile mit je einem Ort; jetzt sind es Designs. Ort, Groesse
     * und Farbe je Stil wandern in die gleichnamige Vorlage, die Stufen zeigen auf deren
     * Namen. Danach ist die alte Ablage leer und verschwindet aus der Datei.
     */
    private static void migrateBanners() {
        BannerCategory banner = INSTANCE.chat.banner;
        if (banner.designs == null) banner.designs = new ArrayList<>();
        banner.designs.removeIf(d -> d == null);
        banner.designs.forEach(BannerDesign::repair);

        if (banner.designs.isEmpty()) {
            banner.designs.addAll(BannerDesign.presets());
            if (banner.looks != null) {
                for (Map.Entry<String, BannerCategory.BannerLook> entry : banner.looks.entrySet()) {
                    BannerDesign target = banner.design(BannerDesign.presetNameFor(entry.getKey()));
                    BannerCategory.BannerLook look = entry.getValue();
                    if (target == null || look == null) continue;
                    target.x = look.x;
                    target.y = look.y;
                    target.scale = look.scale;
                    target.colour = look.colour == null ? "" : look.colour;
                }
            }
        }
        banner.looks = null;

        RareLootCategory rare = INSTANCE.chat.rareLoot;
        if (rare.tier1Style != null) { rare.tier1Design = BannerDesign.presetNameFor(rare.tier1Style); rare.tier1Style = null; }
        if (rare.tier2Style != null) { rare.tier2Design = BannerDesign.presetNameFor(rare.tier2Style); rare.tier2Style = null; }
        if (rare.tier3Style != null) { rare.tier3Design = BannerDesign.presetNameFor(rare.tier3Style); rare.tier3Style = null; }
        if (rare.tier1Design == null || rare.tier1Design.isBlank()) rare.tier1Design = "Classic band";
        if (rare.tier2Design == null || rare.tier2Design.isBlank()) rare.tier2Design = "Classic band";
        if (rare.tier3Design == null || rare.tier3Design.isBlank()) rare.tier3Design = "Classic band";
    }

    /**
     * Der Mindestwert aus den Chatregeln wandert einmalig in den Wert-Alarm.
     *
     * Die alte Pruefung las die groesste Zahl aus der Chatzeile; die neue liest den
     * Basarpreis des Items, das tatsaechlich angekommen ist. Uebernommen wird
     * deshalb nur, was sich uebertragen laesst: die hoechste eingestellte Schwelle.
     * Sie ist die vorsichtigste Wahl - der Alarm meldet danach hoechstens seltener
     * als vorher, nicht oefter.
     *
     * Der Filtertext bleibt in seiner Regel: die Regel selbst gilt weiter, nur ohne
     * die Wertgrenze. Wer den Alarm nicht will, schaltet ihn aus - er startet
     * ausgeschaltet nur dann, wenn nie ein Mindestwert gesetzt war.
     */
    private static void adoptOldMinValues() {
        if (INSTANCE.chat.valueAlertMigrated) return;
        INSTANCE.chat.valueAlertMigrated = true;

        double highest = 0;
        for (ChatRule rule : INSTANCE.chat.chatRules) {
            if (rule.minValue == null) continue;
            highest = Math.max(highest, rule.minValue);
            rule.minValue = null;
        }
        if (highest <= 0) return;

        INSTANCE.chat.rareLoot.enabled = true;
        INSTANCE.chat.rareLoot.tier1Threshold = String.valueOf((long) highest);
    }

    /**
     * Bis 1.0.0 lag alles in einer Kategorie "ShokiTeufel". Beim ersten Start danach
     * werden die Werte in die drei neuen Reiter uebernommen und die alte Ablage geleert.
     */
    private static void adoptLegacyCategory() {
        LegacyCustomize old = INSTANCE.customize;
        if (old == null) return;

        MobVisualsCategory visuals = INSTANCE.mobVisuals;
        ChatRulesCategory chat = INSTANCE.chat;
        SafariCategory safari = INSTANCE.safari;

        if (old.customTargets != null && visuals.customTargets.isEmpty()) {
            visuals.customTargets.addAll(old.customTargets);
        }
        if (old.pickRadius != null) visuals.pickRadius = old.pickRadius;
        if (old.debugLogging != null) visuals.debugLogging = old.debugLogging;

        if (old.chatRules != null && chat.chatRules.isEmpty()) chat.chatRules.addAll(old.chatRules);
        if (old.discoveredAreas != null && chat.discoveredAreas.isEmpty()) {
            chat.discoveredAreas.addAll(old.discoveredAreas);
        }

        if (old.shinyAlertEnabled != null) safari.shinyAlertEnabled = old.shinyAlertEnabled;
        if (old.shinyColor != null) safari.shinyColor = old.shinyColor;
        if (old.sparklingEnabled != null) safari.sparklingEnabled = old.sparklingEnabled;
        if (old.sparklingColor != null) safari.sparklingColor = old.sparklingColor;
        if (old.highlightSafariWalls != null) safari.highlightSafariWalls = old.highlightSafariWalls;
        if (old.safariBiomeOnly != null) safari.safariBiomeOnly = old.safariBiomeOnly;
        if (old.wallColor != null) safari.wallColor = old.wallColor;
        if (old.highlightMounds != null) safari.highlightMounds = old.highlightMounds;
        if (old.moundColor != null) safari.moundColor = old.moundColor;

        INSTANCE.customize = null;
    }

    @Override
    public StructuredText getTitle() {
        String version = getModVersion();
        return StructuredText.of("ShokiTeufel (Release: " + version + ")");
    }

    // ★ バージョンを取得するための専用メソッドを追加
    private String getModVersion() {
        // "shokimod" の部分は、あなたの fabric.mod.json に書かれている "id" に合わせてください
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("shokimod");
        if (container.isPresent()) {
            // モジュールのメタデータからバージョンを文字列として取得
            return container.get().getMetadata().getVersion().getFriendlyString();
        }
        return "Unknown"; // 取得に失敗した場合の保険
    }

    // ==========================================
    // ★追加: 消えてしまっていた「セーブ処理」の本体を復活！
    // ==========================================
    @Override
    public void saveNow() {
        try (FileWriter writer = new FileWriter(getConfigFile())) {
            // Gsonを使って、現在の設定をJSON形式でファイルに書き込む
            GSON.toJson(this, writer);
        } catch (Exception e) {
            System.err.println("Failed to save ShokiMod config!");
            e.printStackTrace();
        }
    }

    // ==========================================
    // カテゴリの定義
    // ==========================================
    @Expose
    @Category(name = "HUD", desc = "The panels on screen: where they sit, how big they are and how they look.")
    public HudCategory hud = new HudCategory();

    @Expose
    @Category(name = "Mob Visuals", desc = "Your own mobs and how every marker looks.")
    public MobVisualsCategory mobVisuals = new MobVisualsCategory();

    @Expose
    @Category(name = "Alerts", desc = "React to what happens: words in chat, and what lands in your inventory.")
    public ChatRulesCategory chat = new ChatRulesCategory();

    @Expose
    @Category(name = "Safari", desc = "Markers for the Critter Safari.")
    public SafariCategory safari = new SafariCategory();

    @Expose
    @Category(name = "Hunting", desc = "Shard hunting.")
    public HuntingCategory hunting = new HuntingCategory();

    @Expose
    @Category(name = "Guild", desc = "Guild events from the ShokiTeufelBot: a banner when one starts, the live ranking in a panel.")
    public GuildCategory guild = new GuildCategory();

    @Expose
    @Category(name = "Fishing", desc = "Fishing helpers.")
    public FishingCategory fishing = new FishingCategory();

    public static class FishingCategory {

        @ConfigOption(name = "Fishing", desc = "Everything about fishing lives in the sub tabs on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Hotspot", desc = "Marks fishing hotspots with a circle in the colour of their bonus and warns when the one you fish in vanishes. Ported from SkyOcean.")
        public HotspotCategory hotspot = new HotspotCategory();
    }

    /** Der Hotspot-Kreis aus SkyOcean (Modified MIT, meowdding), nachgebaut mit Minecrafts Gizmos */
    public static class HotspotCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Track hotspots. Off means no scanning, no circles, no particle handling.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Circle surface", desc = "Fill the hotspot area on the water in the colour of its bonus.")
        @ConfigEditorBoolean
        public boolean circleSurface = true;

        @Expose
        @ConfigOption(name = "Circle outline", desc = "Draw the rim of the hotspot.")
        @ConfigEditorBoolean
        public boolean circleOutline = true;

        @Expose
        @ConfigOption(name = "Hide particles", desc = "Hide Hypixel's pink rim particles once the circle knows its size.")
        @ConfigEditorBoolean
        public boolean hideParticles = true;

        @Expose
        @ConfigOption(name = "Surface opacity", desc = "Opacity of the filled area, in percent.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        public int surfaceAlpha = 50;

        @Expose
        @ConfigOption(name = "Outline opacity", desc = "Opacity of the rim, in percent.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 100f, minStep = 5f)
        public int outlineAlpha = 100;

        @Expose
        @ConfigOption(name = "Despawn warning", desc = "Chat line and banner when the hotspot you were fishing in disappears.")
        @ConfigEditorBoolean
        public boolean warning = false;

        @Expose
        @ConfigOption(name = "Warning sound", desc = "A low note with the despawn warning, at the alert volume.")
        @ConfigEditorBoolean
        public boolean warningSound = true;
    }

    public static class GuildCategory {

        @ConfigOption(name = "Guild", desc = "Everything about guild events lives in the sub tab on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Events", desc = "Live ranking of the running guild event and a banner when one starts. The data comes from the ShokiTeufelBot.")
        public GuildEventsCategory events = new GuildEventsCategory();
    }

    /**
     * Die Anbindung an den ShokiTeufelBot.
     *
     * Zwei Adressen, feste Reihenfolge: erst der PC des Bot-Betreibers (mit dem
     * gemeinsamen Schluessel im Header), dann der Gist als Reserve. Beide leer: aus.
     */
    public static class GuildEventsCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Fetch the event feed from the bot. Off means no request at all.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The event panel: name, time left, top places and your own. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @Expose
        @ConfigOption(name = "Top rows", desc = "How many places the panel lists.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        public int topRows = 3;

        @Expose
        @ConfigOption(name = "Start banner", desc = "Banner when an event starts: Guild 'Kill Ender Dragon' Event Start.")
        @ConfigEditorBoolean
        public boolean startBanner = true;

        @Expose
        @ConfigOption(name = "End banner", desc = "Banner when an event ends, with your final place.")
        @ConfigEditorBoolean
        public boolean endBanner = true;

        @Expose
        @ConfigOption(name = "Banner design", desc = "Name of a design from Alerts > Banner.")
        @ConfigEditorText
        public String bannerDesign = "Classic band";

        @ConfigOption(name = "Test banner", desc = "Shows the start banner with an example event.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable testBanner = () -> {
        };

        @Expose
        @ConfigOption(name = "Primary URL", desc = "The bot on ShokiTeufel's PC, e.g. https://xyz.trycloudflare.com/shokimod/events.json. Asked first. Empty: skipped.")
        @ConfigEditorText
        public String primaryUrl = "";

        @Expose
        @ConfigOption(name = "Fallback URL", desc = "Raw URL of the GitHub Gist the bot keeps updated. Asked when the primary does not answer. Empty: skipped.")
        @ConfigEditorText
        public String fallbackUrl = "";

        @Expose
        @ConfigOption(name = "Shared key", desc = "The same word as shokimod_key in the bot's config.json. Sent to the primary URL as X-ShokiMod-Key.")
        @ConfigEditorText
        public String sharedKey = "";

        @Expose
        @ConfigOption(name = "Refresh seconds", desc = "How often to ask. 60 is plenty - the bot itself updates every ten minutes.")
        @ConfigEditorSlider(minValue = 15f, maxValue = 600f, minStep = 5f)
        public int refreshSeconds = 30;

        /** Ankuendigungen, die schon als Banner liefen - jede genau einmal */
        @Expose
        public List<String> seenAnnouncements = new ArrayList<>();

        @Expose
        public float hudX = 0.75f;
        @Expose
        public float hudY = 0.65f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

    public static class HuntingCategory {

        @ConfigOption(name = "Hunting", desc = "Everything about shard hunting lives in the sub tabs on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Hunting Tracker", desc = "Counts every shard you catch and prices the haul on the bazaar: total, per hour, and the top shards.")
        public HuntingTrackerCategory tracker = new HuntingTrackerCategory();
    }

    /**
     * Der Hunting Tracker: nur Shards, bewertet mit dem Basar.
     *
     * Der Zaehlstand liegt hier, damit er einen Neustart ueberlebt - bis zum Reset.
     */
    public static class HuntingTrackerCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Count shards from You caught and CHARM! lines. Off means nothing is counted or priced.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Show panel", desc = "The tracker panel on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @Expose
        @ConfigOption(name = "Timer", desc = "Track hunting time for Profit/h. Off hides Time and Profit/h and counts only the shards.")
        @ConfigEditorBoolean
        public boolean timerEnabled = true;

        @Expose
        @ConfigOption(name = "Price", desc = "Instant Sell is what selling right now pays; Sell Order is what a listed order brings once it fills. The total follows the live price.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode priceMode = ItemValue.PriceMode.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Pause after", desc = "Seconds without a catch before the timer pauses. The idle time since the last catch is taken off again, so Profit/h does not drop while you are away.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 600f, minStep = 5f)
        public int pauseAfterSeconds = 120;

        @Expose
        @ConfigOption(name = "Rows", desc = "How many shards the panel lists, most valuable first.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 15f, minStep = 1f)
        public int maxRows = 8;

        @ConfigOption(name = "Reset", desc = "Clears the count and the timer.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetTracker = () -> {
        };

        // Zaehlstand und Zeit - kein Menuefeld, nur Ablage
        @Expose
        public Map<String, Integer> counts = new HashMap<>();
        @Expose
        public long uptimeMillis = 0L;
        @Expose
        public long startedAt = 0L;

        // Lage des Kastens, gesetzt ueber /shoki hud
        @Expose
        public float hudX = 0.75f;
        @Expose
        public float hudY = 0.4f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }


    /**
     * Bis 1.0.0 lagen die drei Bereiche als Akkordeon in einer Kategorie "ShokiTeufel".
     * Nur zum Uebernehmen alter Einstellungen - traegt keine Anzeige mehr.
     */
    @Expose
    public LegacyCustomize customize = null;

    public static class LegacyCustomize {
        @Expose public List<CustomMob> customTargets = null;
        @Expose public List<ChatRule> chatRules = null;
        @Expose public List<String> discoveredAreas = null;
        @Expose public String pickRadius = null;
        @Expose public Boolean debugLogging = null;
        @Expose public Boolean shinyAlertEnabled = null;
        @Expose public String shinyColor = null;
        @Expose public Boolean sparklingEnabled = null;
        @Expose public String sparklingColor = null;
        @Expose public Boolean highlightSafariWalls = null;
        @Expose public Boolean safariBiomeOnly = null;
        @Expose public String wallColor = null;
        @Expose public Boolean highlightMounds = null;
        @Expose public String moundColor = null;
    }

    /**
     * Was fuer alle Kaesten gilt, unabhaengig davon, was in ihnen steht.
     *
     * Lage, Groesse und Aussehen sind keine Eigenschaft der Safari oder der Mobs -
     * dieselbe Einstellung wirkt auf jeden Kasten. In einem Reiter neben den Inhalten
     * suchte man sie vergeblich, sobald man den falschen Inhalt im Kopf hat.
     */
    public static class HudCategory {

        @ConfigOption(name = "Move Panels", desc = "Drag the panels where you want them.\nScroll over one to resize it. Click a panel and the slider at the bottom sets how transparent its background is.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openHudEditor = () -> {
        };

        @Expose
        @ConfigOption(name = "Glowing text", desc = "Draws the panel text with a dark outline in its own colour, the way Minecraft draws signs written with glow ink.\nOff: plain text with a drop shadow.")
        @ConfigEditorBoolean
        public boolean glowingHudText = true;
    }

    public static class MobVisualsCategory {

        @Expose
        @ConfigOption(name = "Mob Visuals", desc = "The master switch. Off pauses everything in this tab at once - glow, boxes, lines, name plates, the nearby panel, the shiny alert and the Hideyho finder - without losing any setting.")
        @ConfigEditorBoolean
        public boolean masterEnabled = true;
        // ネームプレートの基準サイズ。1.0 でGUIスケール4相当の見え方になる
        public static final float DEFAULT_NAMEPLATE_SCALE = 1.0f;
        public static final double MIN_RADIUS = 16.0;
        public static final double MAX_RADIUS = 1024.0;

        @Expose
        public List<CustomMob> customTargets = new ArrayList<>();

        // ------ Eigene Mobs ------

        @Expose
        @ConfigOption(name = "Mob Name", desc = "Part of the mob name, for example Graveyard Zombie. Level and health in the nametag are ignored automatically.")
        @ConfigEditorText
        public String customInput = "";

        // ボタンは保存対象外なので @Expose を付けず transient にする
        @ConfigOption(name = "Add", desc = "Adds the name above to your own list.")
        @ConfigEditorButton(buttonText = "Add")
        public transient Runnable addCustomInput = () -> {
            String cleaned = CustomMob.cleanPattern(customInput);
            if (cleaned.isEmpty()) return;
            boolean exists = customTargets.stream()
                    .anyMatch(m -> m.pattern.equalsIgnoreCase(cleaned));
            if (!exists) customTargets.add(CustomMob.byName(cleaned, CustomMob.DEFAULT_COLOR));
            customInput = "";
        };

        @ConfigOption(name = "Add Nearby", desc = "Pick from what is around you and keep it permanently.")
        @ConfigEditorButton(buttonText = "Nearby")
        public transient Runnable openNearbyPicker = () -> {
        };

        @ConfigOption(name = "Your Mobs", desc = "Shows your own list. Change name, colour, toggle or remove entries.")
        @ConfigEditorButton(buttonText = "Manage")
        public transient Runnable openCustomManager = () -> {
        };

        @ConfigOption(name = "Remove All", desc = "Empties your own list.")
        @ConfigEditorButton(buttonText = "None")
        public transient Runnable clearCustomTargets = () -> customTargets.clear();

        // MoulConfig のスライダーは数値欄の幅が 55px 固定で 4 桁が入らないため、テキスト入力にする
        @Expose
        @ConfigOption(name = "Search Radius", desc = "How far the pickers look, in blocks (16 - 1024). The server only sends entities inside its own tracking range, so beyond roughly 128 blocks there is usually nothing left to find.")
        @ConfigEditorText
        public String pickRadius = "48";

        // ------ Aussehen der Markierungen ------

        @Expose
        @ConfigOption(name = "Highlight", desc = "Outlines the target mobs with a glow.")
        @ConfigEditorBoolean
        public boolean enableHighlight = true;

        @Expose
        @ConfigOption(name = "Tracer", desc = "Draws a line to the target mobs.")
        @ConfigEditorBoolean
        public boolean enableTracer = true;

        @Expose
        @ConfigOption(name = "Box", desc = "Box around Sparkling critters and the Hideyho finder. Your Mobs use the B button in their own row instead.")
        @ConfigEditorBoolean
        public boolean enableBox = false;

        @Expose
        @ConfigOption(name = "Nameplate", desc = "Shows a nameplate on the target mobs.")
        @ConfigEditorBoolean
        public boolean enableNameplate = true;

        @Expose
        @ConfigOption(name = "Nameplate Size", desc = "Changes nameplate text size.")
        @ConfigEditorSlider(minValue = 0.25f, maxValue = 3.0f, minStep = 0.05f)
        public float nameplateScale = DEFAULT_NAMEPLATE_SCALE;

        @ConfigOption(name = "Reset Nameplate Size", desc = "Reset to default.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetNameplateScale = () -> nameplateScale = DEFAULT_NAMEPLATE_SCALE;

        @Expose
        @ConfigOption(name = "Nameplate Health", desc = "Shows the mob's health under its name.")
        @ConfigEditorBoolean
        public boolean showNameplateHealth = true;

        @Expose
        @ConfigOption(name = "Nearby panel", desc = "Lists the mobs around you while you play.\nA HUD cannot take clicks - press the key to open the same list and add from there.")
        @ConfigEditorBoolean
        public boolean showNearbyHud = false;

        @Expose
        @ConfigOption(name = "Nearby key hint", desc = "Shows which key opens the list, at the bottom of the panel.")
        @ConfigEditorBoolean
        public boolean showNearbyHint = true;

        @Expose
        public float nearbyHudX = 0.01f;
        @Expose
        public float nearbyHudY = 0.35f;
        @Expose
        public float nearbyHudScale = 1.0f;
        @Expose
        public float nearbyHudAlpha = 1.0f;

        /** Die Safari-Helfer als eigener Unterreiter links - verborgen wie der Reiter selbst */
        @Expose
        @Category(name = "Safari", desc = "Safari helpers that live here on purpose: the shiny alert and the Hideyho finder.")
        public SafariSecretCategory safari = new SafariSecretCategory();

        /** Bis 1.1.17 lagen die drei Werte direkt hier; sie werden einmal in den Unterreiter uebernommen */
        @Expose @SerializedName("shinyAlert") public Boolean legacyShinyAlert = null;
        @Expose @SerializedName("shinyColour") public String legacyShinyColour = null;
        @Expose @SerializedName("hideyhoFinder") public Boolean legacyHideyhoFinder = null;

        /** Einmalige Uebernahme des Shiny-Schalters aus dem Safari-Reiter */
        @Expose
        public boolean shinyMoved = false;

        @Expose
        @ConfigOption(name = "Debug Logging", desc = "Writes into the log why a custom mob does or does not glow. Only for troubleshooting.")
        @ConfigEditorBoolean
        public boolean debugLogging = false;

        public static class SafariSecretCategory {

            @Expose
            @ConfigOption(name = "Shiny alert", desc = "Full-screen banner and party call when a Sparkling critter shows up.")
            @ConfigEditorBoolean
            public boolean shinyAlert = true;

            @Expose
            @ConfigOption(name = "Shiny colour", desc = "Hex like FFD700 for the SHINY banner.")
            @ConfigEditorText
            public String shinyColour = "FFD700";

            @Expose
            @ConfigOption(name = "Hideyho finder", desc = "Glow and a line to the nearest Hideyho, like a Your Mobs entry with Highlight and Line - without having to add one.")
            @ConfigEditorBoolean
            public boolean hideyhoFinder = false;

            /** Einmalig: der Shiny-Alarm ist ab 1.1.18 fuer alle an, auch in alten Dateien */
            @Expose
            public boolean defaultOnApplied = false;
        }

        /** Eingaben sind frei, deshalb beim Lesen abfangen */
        public double pickRadiusBlocks() {
            try {
                double v = Double.parseDouble(pickRadius.trim());
                return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, v));
            } catch (RuntimeException e) {
                return 48.0;
            }
        }

        /** Tracer をどのモブに出すか。表示名はそのまま設定画面の選択肢になる */
    }

    public static class ChatRulesCategory {

        @Expose
        public List<ChatRule> chatRules = new ArrayList<>();

        /** Beim Spielen entdeckte Gebietsnamen. Ergaenzt den festen Grundstock in KnownAreas */
        @Expose
        public List<String> discoveredAreas = new ArrayList<>();

        @ConfigOption(name = "Chat Rules", desc = "Add words and choose what happens. Sound files go into config/shokimod/sounds.")
        @ConfigEditorButton(buttonText = "Manage")
        public transient Runnable openChatRules = () -> {
        };

        @Expose
        @ConfigOption(name = "Alert Volume", desc = "One knob for every alert sound: chat rules, rare loot tiers and the contest warning. A rule's own volume is scaled by this.")
        @ConfigEditorSlider(minValue = 0.0f, maxValue = 1.0f, minStep = 0.05f)
        public float alertVolume = 1.0f;

        @ConfigOption(name = "Test Volume", desc = "Plays one of your alert sounds at the volume above. Uses the first rare loot or contest sound you picked, or a note block if none.")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable testAlertVolume = () -> {
        };

        // ==========================================
        // Seltene Funde als eigener Reiter unter Alerts. Gelesen wird Hypixels
        // "RARE DROP!"-Zeile, bewertet im Basar und Auktionshaus, gemeldet in
        // drei Stufen - und auf Wunsch in Party oder Gilde geteilt
        // ==========================================
        @Expose
        @Category(name = "Rare Loot", desc = "Rare drops from chat, priced on the bazaar and auction house. Three tiers with their own reactions, and sharing to your party or guild.")
        public RareLootCategory rareLoot = new RareLootCategory();

        @Expose
        @Category(name = "Banner", desc = "How the drop banners look: try all twenty, then place, size and colour each tier in the HUD editor.")
        public BannerCategory banner = new BannerCategory();

        @Expose
        @Category(name = "Reminder", desc = "Reminders read from chat: expired cakes, with one click to get new ones.")
        public ReminderCategory reminder = new ReminderCategory();

        /**
         * Die uebernommene Schwelle aus den alten Chatregeln.
         *
         * Damit die Uebernahme genau einmal laeuft: ohne die Marke wuerde eine spaeter
         * von Hand geaenderte Schwelle beim naechsten Start wieder ueberschrieben.
         */
        @Expose
        public boolean valueAlertMigrated = false;
    }

    /** Die Banner-Designs: Vorlagen und Eigenbauten, dazu Sandbox und Editor */
    public static class BannerCategory {

        @ConfigOption(name = "Sandbox", desc = "Design your own banner: start from a template, change anything, preview it, assign it to a tier.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openSandbox = () -> {
        };

        @ConfigOption(name = "Editor", desc = "Drag banners and panels into place. Same screen as /shoki hud.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openEditor = () -> {
        };

        /** Alle Designs, die einundzwanzig Vorlagen eingeschlossen. Name ist der Schluessel */
        @Expose
        public List<BannerDesign> designs = new ArrayList<>();

        /** Alte Ablage bis 1.1.16: Ort und Farbe je Stil. Nur noch fuer die Uebernahme */
        @Expose
        public Map<String, BannerLook> looks = null;

        public static class BannerLook {
            @Expose public float x = 0.5f;
            @Expose public float y = 0.3f;
            @Expose public float scale = 1.0f;
            @Expose public String colour = "";
        }

        /** Das Design mit diesem Namen, oder null */
        public BannerDesign design(String name) {
            if (name == null || designs == null) return null;
            for (BannerDesign d : designs) {
                if (d != null && d.name != null && d.name.equalsIgnoreCase(name.trim())) return d;
            }
            return null;
        }

        /** Das Design mit diesem Namen, sonst das erste - ein Fund soll nie ohne Banner bleiben */
        public BannerDesign designOrDefault(String name) {
            BannerDesign found = design(name);
            if (found != null) return found;
            if (designs != null && !designs.isEmpty() && designs.get(0) != null) return designs.get(0);
            return BannerDesign.presets().get(0);
        }
    }

    /**
     * Erinnerungen. Bisher eine: der Kuchen-Alarm aus RiccioFishingUtils, ergaenzt um
     * den anklickbaren Text, der zum Kuchenholen teleportiert.
     */
    public static class ReminderCategory {

        @Expose
        @ConfigOption(name = "Outdated cake alert", desc = "Reads the Yum! line when you eat a cake and warns in chat once its 48 hours are over. Ported from RiccioFishingUtils.")
        @ConfigEditorBoolean
        public boolean cakeAlert = false;

        @Expose
        @ConfigOption(name = "Get Cakes command", desc = "Runs when you click [Get Cakes!] in the reminder.")
        @ConfigEditorText
        public String cakeCommand = "/visit SchiggyMobil";

        @Expose
        @ConfigOption(name = "Repeat minutes", desc = "How often the reminder repeats while a cake stays expired. 0: only once per expired cake.")
        @ConfigEditorSlider(minValue = 0f, maxValue = 60f, minStep = 1f)
        public int cakeRepeatMinutes = 5;

        @Expose
        @ConfigOption(name = "Sound", desc = "A short ping with the reminder, at the alert volume.")
        @ConfigEditorBoolean
        public boolean cakeSound = true;

        @ConfigOption(name = "Test", desc = "Shows the reminder line with the clickable [Get Cakes!].")
        @ConfigEditorButton(buttonText = "Test")
        public transient Runnable testCake = () -> {
        };

        @ConfigOption(name = "Clear cakes", desc = "Forgets every remembered cake - for example after eating on another profile.")
        @ConfigEditorButton(buttonText = "Clear")
        public transient Runnable clearCakes = () -> {
        };

        /** Kuchen und wann er gegessen wurde (Millisekunden) */
        @Expose
        public Map<String, Long> cakes = new HashMap<>();
    }

    /**
     * Seltene Funde aus dem Chat: drei Stufen und das Teilen.
     *
     * Ein Fund loest nur die hoechste Stufe aus, die er erreicht: wer 50M findet,
     * bekommt den 50M-Alarm und nicht zusaetzlich die beiden darunter. Die Stufen
     * duerfen in beliebiger Reihenfolge stehen - gezaehlt wird nach Schwelle, nicht
     * nach Nummer.
     *
     * Die Felder wiederholen sich dreimal, weil MoulConfig nur flache Felder in
     * einer Kategorie kennt. Der Handler sieht davon nichts: {@link #tiers()} liefert
     * ihm die drei Stufen als Werte.
     *
     * Nachbau von Skysofts Rare Drop Titles und Rare Loot Sharing (LGPL-3.0).
     */
    public static class RareLootCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Reads Hypixel's RARE DROP! lines and prices the drop on the bazaar or, failing that, the auction house. Off means no price is ever requested.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Diagnostics", desc = "Writes what the mod saw and decided into logs/shokimod-diagnostics.txt and opens that folder. Send that file together with latest.log when something did not fire.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openDiagnostics = () -> {
        };

        @Expose
        @ConfigOption(name = "Shard price", desc = "Which bazaar price counts for shards (SHARD_...). Instant Sell is what selling right now pays; Sell Order is what a listed order brings once it fills.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode shardPriceMode = ItemValue.PriceMode.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Bazaar price", desc = "Which bazaar price counts for every other bazaar item.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode bazaarPriceMode = ItemValue.PriceMode.INSTANT_SELL;


        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 1", desc = "From the threshold below up. A drop that also clears a higher tier fires only that one.")
        @ConfigEditorAccordion(id = 21)
        public transient boolean tier1Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Enabled = true;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 21)
        public String tier1Threshold = "1M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 21)
        public String tier1Design = "Classic band";

        /** Alte Ablage bis 1.1.16, nur fuer die Uebernahme */
        @Expose
        public String tier1Style = null;


        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 21)
        public boolean tier1Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 21)
        public transient Runnable openTier1Sound = () -> {
        };

        @Expose
        public String tier1Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 21)
        public transient Runnable testTier1 = () -> {
        };

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 2", desc = "From the threshold below up. A drop that also clears a higher tier fires only that one.")
        @ConfigEditorAccordion(id = 22)
        public transient boolean tier2Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Enabled = true;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 22)
        public String tier2Threshold = "25M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 22)
        public String tier2Design = "Classic band";

        /** Alte Ablage bis 1.1.16, nur fuer die Uebernahme */
        @Expose
        public String tier2Style = null;


        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 22)
        public boolean tier2Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 22)
        public transient Runnable openTier2Sound = () -> {
        };

        @Expose
        public String tier2Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 22)
        public transient Runnable testTier2 = () -> {
        };

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 3", desc = "From the threshold below up. A drop that also clears a higher tier fires only that one.")
        @ConfigEditorAccordion(id = 23)
        public transient boolean tier3Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Enabled = true;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 23)
        public String tier3Threshold = "50M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 23)
        public String tier3Design = "Classic band";

        /** Alte Ablage bis 1.1.16, nur fuer die Uebernahme */
        @Expose
        public String tier3Style = null;


        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 23)
        public boolean tier3Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 23)
        public transient Runnable openTier3Sound = () -> {
        };

        @Expose
        public String tier3Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 23)
        public transient Runnable testTier3 = () -> {
        };

        @ConfigOption(name = "Share drops", desc = "Sends valuable drops to your party or guild as RARE DROP! with the value. Your own shared line is never read again.")
        @ConfigEditorAccordion(id = 24)
        public transient boolean shareFolder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Switch the sharing on. Party and Guild below choose where it goes.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareEnabled = false;

        @Expose
        @ConfigOption(name = "Party", desc = "Send as /pc.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareParty = true;

        @Expose
        @ConfigOption(name = "Guild", desc = "Send as /gc.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareGuild = false;

        @Expose
        @ConfigOption(name = "Share from", desc = "Coins. 0 shares every rare drop, even one without a known price.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareThreshold = "1M";

        @Expose
        @ConfigOption(name = "Message", desc = "The line that gets sent. Placeholders: {prefix} = RARE DROP! or LOOTSHARE DROP!, {item} = drop with count, {name}, {amount}, {mf} = Magic Find in brackets, {value} = value in brackets, {coins} = bare value. Empty restores the default.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate = "{prefix} {item} {mf} {value}";

        @Expose
        @ConfigOption(name = "Show Magic Find", desc = "Fill {mf} with the Magic Find from the drop line, e.g. (+471 MF).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareMagicFind = true;

        @Expose
        @ConfigOption(name = "Show value", desc = "Fill {value} and {coins} with the price, e.g. (+121.8k coins).")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 24)
        public boolean shareValue = true;

        @Expose
        @ConfigOption(name = "Message Tier 1", desc = "Used instead of Message when the drop reaches Tier 1. Same placeholders. Empty falls back to Message.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate1 = "";

        @Expose
        @ConfigOption(name = "Message Tier 2", desc = "Used when the drop reaches Tier 2. Empty falls back to Message.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate2 = "";

        @Expose
        @ConfigOption(name = "Message Tier 3", desc = "Used when the drop reaches Tier 3. Empty falls back to Message.")
        @ConfigEditorText
        @ConfigAccordionId(id = 24)
        public String shareTemplate3 = "";

        /** Die Vorlage der erreichten Stufe, sonst die allgemeine */
        public String shareTemplateFor(int tier) {
            String own = switch (tier) {
                case 1 -> shareTemplate1;
                case 2 -> shareTemplate2;
                case 3 -> shareTemplate3;
                default -> null;
            };
            return own == null || own.isBlank() ? shareTemplate : own;
        }

        /** Die Farbe einer Stufe, wenn keine eigene gewaehlt ist: gruen, gold, violett */
        public static int defaultTierColour(int tier) {
            return switch (tier) {
                case 1 -> 0x55FF55;
                case 3 -> 0xFF55FF;
                default -> 0xFFD700;
            };
        }

        /** Der Designname einer Stufe */
        public String designFor(int tier) {
            return switch (tier) { case 1 -> tier1Design; case 2 -> tier2Design; default -> tier3Design; };
        }

        public void setDesign(int tier, String name) {
            switch (tier) { case 1 -> tier1Design = name; case 2 -> tier2Design = name; default -> tier3Design = name; }
        }

        /** Eine Stufe, wie der Handler sie sieht */
        public record Tier(int number, boolean enabled, String threshold, boolean banner,
                           boolean toast, boolean chat, String sound, String design) {
        }

        public Tier tier(int number) {
            return switch (number) {
                case 1 -> new Tier(1, tier1Enabled, tier1Threshold, tier1Banner, tier1Toast, tier1Chat, tier1Sound, tier1Design);
                case 2 -> new Tier(2, tier2Enabled, tier2Threshold, tier2Banner, tier2Toast, tier2Chat, tier2Sound, tier2Design);
                default -> new Tier(3, tier3Enabled, tier3Threshold, tier3Banner, tier3Toast, tier3Chat, tier3Sound, tier3Design);
            };
        }

        public List<Tier> tiers() {
            return List.of(tier(1), tier(2), tier(3));
        }
    }


    public static class SafariCategory {

        @ConfigOption(name = "Marker Settings", desc = "Switch, name and colour side by side, one row per marker.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openMarkerSettings = () -> {
        };

        // ==========================================
        // Lauf-Mitschrift und die Anzeigen
        // ==========================================

        @Expose
        @ConfigOption(name = "Contest panel", desc = "The running contest with its score and remaining time.\nThe clock comes from the day cycle, so it works everywhere - the score only where the tab list carries it.")
        @ConfigEditorBoolean
        public boolean showContestHud = true;

        @Expose
        @ConfigOption(name = "Warn before the end", desc = "Plays a sound 60 seconds before the contest ends.")
        @ConfigEditorBoolean
        public boolean contestWarning = true;

        @ConfigOption(name = "Warning Sound", desc = "Your own file from config/shokimod/sounds.")
        @ConfigEditorButton(buttonText = "Pick")
        public transient Runnable openContestSound = () -> {
        };

        /**
         * Lag bis 1.1.0 hier und steht jetzt im Reiter HUD.
         *
         * Bleibt als leeres Fach stehen, damit ein ausgeschaltetes Leuchten beim
         * Umzug nicht wieder angeht. Ohne Anzeige, nur zum Uebernehmen.
         */
        @Expose
        public Boolean glowingHudText = null;

        @Expose
        @ConfigOption(name = "Progress HUD", desc = "The two Safari panels and what feeds them.")
        @ConfigEditorAccordion(id = 10)
        @ConfigEditorBoolean
        public boolean progressHudFolder = true;

        @Expose
        @ConfigOption(name = "Track runs", desc = "Read the chat to follow a Safari run: what the party caught, and how long it took.\nNothing is sent anywhere; the lines are only read.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean trackRuns = true;

        @Expose
        @ConfigOption(name = "Progress panel", desc = "Run timer, Critterdex for the party and for you, and a bar per biome.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showProgressHud = true;

        @Expose
        @ConfigOption(name = "Missing panel", desc = "What is still uncaught in the biome you are standing in.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showMissingHud = true;

        @Expose
        @ConfigOption(name = "First catch is enough", desc = "Treat a species as done at the first catch.\nOff: species that spawn a fixed number of times per run stay listed until every one is caught.\nQuotas: Gazer 4, Gemzie 3, Troodon 3, Hideyho 1, Wumpa 1, Doomspiral 1")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean firstCatchIsEnough = false;

        @Expose
        @ConfigOption(name = "Per player", desc = "Show who caught how many, under the biome bars. Only with more than one player.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showPerPlayer = true;

        @Expose
        @ConfigOption(name = "Count mounds", desc = "How many Rockmite mounds are standing near you, under the missing list.\nIt counts what is in range, not what is left in the Cavern.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showMoundCount = true;

        @Expose
        @ConfigOption(name = "Count walls", desc = "How many breakable walls are still standing, under the missing list.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showWallCount = true;

        @Expose
        @ConfigOption(name = "Count nests", desc = "How many bee nests are still to punch, under the missing list.\nOnly nests you have come across, not every nest on the map.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 10)
        public boolean showNestCount = true;

        // ==========================================
        // Gemerkter Contest-Stand. Die Tab-Liste fuehrt ihn nur am Ort des Contests,
        // angezeigt werden soll er ueberall - also hier ablegen und fortschreiben
        // ==========================================
        @Expose
        public String contestBracket = "";
        @Expose
        public int contestAmount = 0;
        @Expose
        public String contestNext = "";
        /** Die gelernte Schwelle des naechsten Brackets, nicht der Abstand dorthin */
        @Expose
        public int contestNextThreshold = 0;
        /** SkyBlock-Datum, zu dem dieser Stand gehoert, etwa "Winter 7th" */
        @Expose
        public String contestDate = "";
        /** Tag, an dem schon gewarnt wurde - damit der Ton einmal kommt und nicht dauernd */
        @Expose
        public String contestWarnedDate = "";
        @Expose
        public String contestWarningSound = "";
        /**
         * Wann die laufende Phase endet, als echter Zeitpunkt.
         *
         * Nicht als Restwert: eine Restzeit muesste staendig nachgezogen werden und
         * rastet dabei auf die grobe Quelle ein. Ein fester Endzeitpunkt laesst sich
         * dagegen einfach abziehen, und der Countdown laeuft glatt.
         */
        @Expose
        public long contestEndsAt = 0L;
        /** Ob zu diesem Zeitpunkt der Contest lief oder die Pause dazwischen */
        @Expose
        public boolean contestRunning = true;

        // Lage als Anteil der Bildschirmgroesse, damit sie jede Aufloesung ueberlebt
        @Expose
        public float progressHudX = 0.01f;
        @Expose
        public float progressHudY = 0.02f;
        @Expose
        public float progressHudScale = 1.0f;

        @Expose
        public float missingHudX = 0.75f;
        @Expose
        public float missingHudY = 0.02f;
        @Expose
        public float missingHudScale = 1.0f;

        @Expose
        public float contestHudX = 0.4f;
        @Expose
        public float contestHudY = 0.02f;
        @Expose
        public float contestHudScale = 1.0f;

        // Deckkraft je Kasten, 0.1 bis 1.0. Eingestellt wird sie im Verschiebe-Fenster
        @Expose
        public float progressHudAlpha = 1.0f;
        @Expose
        public float missingHudAlpha = 1.0f;
        @Expose
        public float contestHudAlpha = 1.0f;

        // ==========================================
        @ConfigOption(name = "Shiny party call", desc = "Tell the party when you spot a shiny, and hear it when someone else does.")
        @ConfigEditorAccordion(id = 30)
        public transient boolean shinyShareFolder = false;

        @Expose
        @ConfigOption(name = "Call the party", desc = "When a SHINY banner fires, send OMG!! WHO IS THAT SHINY?! as /pc. The text is fixed so every ShokiMod in the party recognises it.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 30)
        public boolean shinyCallParty = true;

        @Expose
        @ConfigOption(name = "Sound on call", desc = "Play a sound whenever someone else's OMG!! WHO IS THAT SHINY?! shows up in chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 30)
        public boolean shinyCallSound = true;

        @ConfigOption(name = "Call sound", desc = "The file from config/shokimod/sounds. shiny-alert.mp3 comes with the mod and is placed there on first start; pick another if you like.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 30)
        public transient Runnable openShinyCallSound = () -> {
        };

        @Expose
        public String shinyCallSoundFile = "shiny-alert.mp3";

        @ConfigOption(name = "Test", desc = "Plays the call sound once.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 30)
        public transient Runnable testShinyCallSound = () -> {
        };

        // Werte ohne eigene Zeile. Gesetzt wird alles ueber MarkerSettingsScreen
        // ==========================================

        @Expose
        public boolean shinyAlertEnabled = true;
        @Expose
        public String shinyColor = "FFD700";

        @Expose
        public boolean sparklingEnabled = true;
        @Expose
        public String sparklingColor = "FFFF55";

        @Expose
        public boolean highlightSafariWalls = true;
        /**
         * Safari-Marker nur zeigen, wenn man im passenden Biom steht.
         * Betrifft Waende (Cavern / Icy) und Rockmite Mounds (Cavern). Abschaltbar.
         */
        @Expose
        public boolean safariBiomeOnly = true;
        @Expose
        public String wallColor = "55FFFF";

        @Expose
        public boolean highlightMounds = true;
        @Expose
        public String moundColor = "3AB3DA";

        @Expose
        public boolean enableFloorDrops = true;
        @Expose
        public String floorDropColor = "55FFAA";

        @Expose
        public boolean highlightNests = true;
        @Expose
        public String nestColor = "55FF55";

        public int shinyColorRGB() {
            return parseColor(shinyColor, 0xFFD700);
        }

        public int sparklingColorRGB() {
            return parseColor(sparklingColor, 0xFFFF55);
        }

        public int wallColorRGB() {
            return parseColor(wallColor, 0x55FFFF);
        }

        public int moundColorRGB() {
            return parseColor(moundColor, 0x3AB3DA);
        }

        public int floorDropColorRGB() {
            return parseColor(floorDropColor, 0x55FFAA);
        }

        public int nestColorRGB() {
            return parseColor(nestColor, 0x55FF55);
        }

        private static int parseColor(String text, int fallback) {
            try {
                return Integer.parseInt(text.trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (RuntimeException e) {
                return fallback;
            }
        }
    }
}
