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
import com.shokiteufel.shokimod.gui.CollectionPickerScreen;
import com.shokiteufel.shokimod.handler.CakeReminder;
import com.shokiteufel.shokimod.handler.CollectionTracker;
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
        // Wer die Mod schon vor 1.2.11 hatte, hat das Feld leer gespeichert. Der Gist ist die
        // oeffentliche Quelle und soll auch in alten Dateien ohne Zutun greifen
        if (INSTANCE.guild.events.fallbackUrl == null || INSTANCE.guild.events.fallbackUrl.isBlank()) {
            INSTANCE.guild.events.fallbackUrl = GuildEventsCategory.DEFAULT_FALLBACK_URL;
        }
        if (INSTANCE.fishing == null) INSTANCE.fishing = new FishingCategory();
        if (INSTANCE.collections == null) INSTANCE.collections = new CollectionsCategory();
        if (INSTANCE.collections.tracker == null) INSTANCE.collections.tracker = new CollectionTrackerCategory();
        CollectionTrackerCategory collectionTracker = INSTANCE.collections.tracker;
        if (collectionTracker.selected == null) collectionTracker.selected = new ArrayList<>();
        if (collectionTracker.gains == null) collectionTracker.gains = new HashMap<>();
        if (collectionTracker.values == null) collectionTracker.values = new HashMap<>();
        if (collectionTracker.totals == null) collectionTracker.totals = new HashMap<>();
        if (collectionTracker.sinceRead == null) collectionTracker.sinceRead = new HashMap<>();
        if (collectionTracker.totalExact == null) collectionTracker.totalExact = new HashMap<>();
        if (collectionTracker.priceMode == null) collectionTracker.priceMode = ItemValue.PriceMode.INSTANT_SELL;
        if (collectionTracker.lineOrder == null) collectionTracker.lineOrder = CollectionTrackerCategory.LineOrder.TOTAL_GAINED_HOUR;
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
        INSTANCE.chat.rareLoot.openTier4Sound = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new SoundPickerScreen(
                        Minecraft.getInstance().screen,
                        () -> INSTANCE.chat.rareLoot.tier4Sound,
                        picked -> INSTANCE.chat.rareLoot.tier4Sound = picked,
                        1.0f)));
        INSTANCE.chat.rareLoot.testTier4 = () -> RareLootHandler.test(4);
        INSTANCE.chat.rareLoot.openDiagnostics = () -> Minecraft.getInstance().execute(RareLootHandler::writeDiagnostics);
        INSTANCE.chat.testAlertVolume = () -> Minecraft.getInstance().execute(AlertVolume::test);
        INSTANCE.hunting.tracker.resetTracker = () -> Minecraft.getInstance().execute(HuntingTracker::reset);
        INSTANCE.guild.events.testBanner = () -> Minecraft.getInstance().execute(GuildEvents::testBanner);
        INSTANCE.collections.tracker.resetTracker = () -> Minecraft.getInstance().execute(CollectionTracker::reset);
        INSTANCE.collections.tracker.openPicker = () -> Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().setScreen(new CollectionPickerScreen(Minecraft.getInstance().screen)));
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
        INSTANCE.hunting.fusion.open = () -> Minecraft.getInstance().execute(() -> {
            // Das Holen anstossen, bevor das Fenster aufgeht - sonst steht dort
            // im ersten Moment nur "Loading"
            com.shokiteufel.shokimod.util.ShardProfitData.prefetch();
            Minecraft.getInstance().setScreen(
                    new com.shokiteufel.shokimod.gui.ShardProfitScreen(Minecraft.getInstance().screen));
        });

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

    @Expose
    @Category(name = "Collections", desc = "What your collections gain while you play.")
    public CollectionsCategory collections = new CollectionsCategory();

    @Expose
    @Category(name = "Mining", desc = "Helpers for the mining islands: commissions, your pickaxe ability and the Sky Mall buff of the day.")
    public MiningCategory mining = new MiningCategory();

    /**
     * Der Tageszaehler, wie ihn GanKura hatte.
     *
     * Gezaehlt wird die Weltzeit geteilt durch 24000. Das alte GanKura fing die Zahl per
     * Mixin direkt vom Server ab; diesen Mixin gibt es hier nicht mehr, deshalb kommt sie
     * aus der Welt des Clients. In der Praxis ist das dieselbe Zahl - Hypixel haelt beide
     * gleich - nur direkt nach dem Betreten kann sie einen Augenblick hinterherhinken.
     */
    public static class DayHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "The day counter on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Where", desc = "Mining islands only, or everywhere in SkyBlock. Areas in the HUD editor override this.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.EVERYWHERE;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.28f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;
    }

    /** Wo das Bild des Pets sitzt */
    public enum IconPlace {
        LEFT("In front"), RIGHT("Behind"), OWN_LINE("Own line");

        public final String label;

        IconPlace(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class PetHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "The active pet with its level and progress. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Where", desc = "Everywhere in SkyBlock, or only on mining islands. Areas in the HUD editor override this.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.EVERYWHERE;

        @Expose
        @ConfigOption(name = "Icon", desc = "The pet's item image, read from the pet menu.")
        @ConfigEditorBoolean
        public boolean showIcon = true;

        @Expose
        @ConfigOption(name = "Pet name", desc = "The name, in the colour of its rarity.")
        @ConfigEditorBoolean
        public boolean showName = true;

        @Expose
        @ConfigOption(name = "Pet level", desc = "The level in brackets, as the game writes it: [Lvl 100].")
        @ConfigEditorBoolean
        public boolean showLevel = true;

        @Expose
        @ConfigOption(name = "Overflow level", desc = "Level plus the levels beyond the maximum, added up: a level 200 dragon with 332 extra shows [532]. Hypixel does not show these - they are a calculation.")
        @ConfigEditorBoolean
        public boolean showOverflowLevel = true;

        @Expose
        @ConfigOption(name = "Overflow XP", desc = "The experience collected beyond the maximum level.")
        @ConfigEditorBoolean
        public boolean showOverflowXp = true;

        @Expose
        @ConfigOption(name = "Held item", desc = "The item the pet carries, if it has one.")
        @ConfigEditorBoolean
        public boolean showHeldItem = false;

        @Expose
        @ConfigOption(name = "Progress bar", desc = "How far it is to the next level. Only shown below the maximum level.")
        @ConfigEditorBoolean
        public boolean showProgress = true;

        /**
         * In welcher Reihenfolge die Teile erscheinen. Die Namen stehen in
         * PetHud.Part; was hier fehlt, wird hinten angehaengt, damit ein neues Teil
         * nach einem Update nicht unsichtbar bleibt.
         */
        @Expose
        public List<String> order = new ArrayList<>();

        @Expose
        @ConfigOption(name = "Side by side", desc = "All parts in one line, or each on its own line below the other.")
        @ConfigEditorBoolean
        public boolean sameLine = true;

        @Expose
        @ConfigOption(name = "Labels", desc = "The words in front: Pet:, Level:, Name:. Off shows only the values.")
        @ConfigEditorBoolean
        public boolean showLabels = true;

        @Expose
        @ConfigOption(name = "Announce overflow", desc = "Writes a line into your own chat when the pet gains an overflow level. Nobody else sees it.")
        @ConfigEditorBoolean
        public boolean announceOverflow = true;

        @Expose
        @ConfigOption(name = "Icon position", desc = "Where the pet image sits: in front of the first line, behind it, or on a line of its own.")
        @ConfigEditorDropdown
        public IconPlace iconPlace = IconPlace.LEFT;

        @Expose
        @ConfigOption(name = "Icon size", desc = "Only the pet image, without changing the text. 1.0 is the usual sixteen pixels.")
        @ConfigEditorSlider(minValue = 0.5f, maxValue = 3.0f, minStep = 0.1f)
        public float iconScale = 1.0f;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.40f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;
    }

    public static class PerformanceHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "Frames, server ticks and ping on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Show FPS", desc = "Frames per second, as Minecraft counts them.")
        @ConfigEditorBoolean
        public boolean showFps = true;

        @Expose
        @ConfigOption(name = "Show TPS", desc = "How fast the server is running. Measured from how often it sends its clock - twenty is full speed.")
        @ConfigEditorBoolean
        public boolean showTps = true;

        @Expose
        @ConfigOption(name = "Show ping", desc = "Round trip to the server, from the player list.")
        @ConfigEditorBoolean
        public boolean showPing = true;

        @Expose
        @ConfigOption(name = "Where", desc = "Everywhere in SkyBlock, or only on mining islands. Areas in the HUD editor override this.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.EVERYWHERE;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.52f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;
    }

    public static class MiningCategory {

        @Expose
        @Category(name = "Mining HUD", desc = "One panel with what matters underground: commissions, the pickaxe ability with its cooldown, the Sky Mall buff. The numbers come from the tab list.")
        public MiningHudCategory hud = new MiningHudCategory();
    }

    /** Welche gefrorenen Leichen im Mining-HUD stehen sollen */
    public enum CorpseFilter {
        OFF("Off"),
        LAPIS_ONLY("Lapis only"),
        ALL("Lapis, Umber, Tungsten");

        private final String label;

        CorpseFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Ab wie vielen Lapis-Leichen die Party benachrichtigt wird */
    public enum CorpseCall {
        OFF("Off"),
        FROM_TWO("From 2 Lapis"),
        FROM_THREE("From 3 Lapis");

        private final String label;

        CorpseCall(String label) {
            this.label = label;
        }

        /** Wie viele Lapis-Leichen es mindestens braucht. 0 heisst: nie rufen */
        public int threshold() {
            return switch (this) {
                case OFF -> 0;
                case FROM_TWO -> 2;
                case FROM_THREE -> 3;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wo ein Kasten erscheinen darf - dieselbe Wahl, die auch SkyHanni beim Sky Mall bietet */
    public enum HudVisibility {
        MINING_ISLANDS("Mining islands"),
        EVERYWHERE("Everywhere");

        private final String label;

        HudVisibility(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Das Mining-HUD.
     *
     * Die Werte stammen aus der Tab-Liste, und die fuehrt sie nur auf den Mining-Inseln.
     * Deshalb bleibt der zuletzt gelesene Stand stehen, wenn man die Insel verlaesst -
     * sonst waere die Einstellung "Everywhere" wertlos, weil der Kasten dort leer bliebe.
     */
    public static class MiningHudCategory {

        @Expose
        @ConfigOption(name = "Show panel", desc = "The mining panel on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = false;

        @Expose
        @ConfigOption(name = "Show where", desc = "Mining islands: only in the Dwarven Mines, Crystal Hollows, Glacite Mineshafts and the Gold Mine. Everywhere: keeps showing the last numbers wherever you are.")
        @ConfigEditorDropdown
        public HudVisibility visibility = HudVisibility.MINING_ISLANDS;

        @Expose
        @ConfigOption(name = "Commissions", desc = "The running commissions with their progress.")
        @ConfigEditorBoolean
        public boolean showCommissions = true;

        @Expose
        @ConfigOption(name = "Ability name", desc = "Which pickaxe ability is equipped.")
        @ConfigEditorBoolean
        public boolean showAbility = true;

        @Expose
        @ConfigOption(name = "Ability cooldown", desc = "How long until the pickaxe ability is ready again.")
        @ConfigEditorBoolean
        public boolean showCooldown = true;

        @Expose
        @ConfigOption(name = "Sky Mall", desc = "The Sky Mall buff of the day. Read from the chat message that announces it, so it appears once the day rolls over.")
        @ConfigEditorBoolean
        public boolean showSkyMall = true;

        @Expose
        @ConfigOption(name = "Frozen corpses", desc = "The frozen corpses of the mineshaft you are in, and how many of each are still unlooted. Only mineshafts carry them. Vanguard is never listed.")
        @ConfigEditorDropdown
        public CorpseFilter corpses = CorpseFilter.OFF;

        @Expose
        @ConfigOption(name = "Ready alert", desc = "Shows a banner the moment the pickaxe ability comes off cooldown, so you do not have to watch the panel.")
        @ConfigEditorBoolean
        public boolean readyAlert = false;

        @Expose
        @ConfigOption(name = "Tell the party", desc = "Writes the corpses of a mineshaft into the party chat, once per shaft, as soon as they show up. Only fires when there are at least this many Lapis corpses.")
        @ConfigEditorDropdown
        public CorpseCall corpseCall = CorpseCall.OFF;

        @Expose
        public float hudX = 0.02f;
        @Expose
        public float hudY = 0.35f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudOpacity = 0.5f;

        /** Der zuletzt gelesene Stand, damit der Kasten ausserhalb der Minen nicht leer wird */
        @Expose
        public List<String> lastCommissions = new ArrayList<>();
        @Expose
        public String lastAbility = "";
        @Expose
        public String lastSkyMall = "";
    }

    public static class CollectionsCategory {

        @ConfigOption(name = "Collections", desc = "The tracker lives in the sub tab on the left.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @Category(name = "Tracker", desc = "Counts what your sacks collect, in collection units: total, gained, per hour and worth.")
        public CollectionTrackerCategory tracker = new CollectionTrackerCategory();
    }

    /**
     * Der Collection-Tracker. Gezaehlt wird aus dem Sack-Hinweis im Chat; verzauberte
     * Items zaehlen mit ihrem Bauplan (ein Enchanted Helix Log ist 160 Helix Log).
     */
    public static class CollectionTrackerCategory {

        @Expose
        @ConfigOption(name = "Enabled", desc = "Read the sack messages and count collections. Off means nothing is read or counted.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @ConfigOption(name = "Collections", desc = "Which collections the panel lists. Nothing chosen: everything that comes in.")
        @ConfigEditorButton(buttonText = "Choose")
        public transient Runnable openPicker = () -> {
        };

        @Expose
        @ConfigOption(name = "Show panel", desc = "The tracker panel on screen. Move it with /shoki hud.")
        @ConfigEditorBoolean
        public boolean showHud = true;

        @Expose
        @ConfigOption(name = "Timer", desc = "Track time for the per hour numbers. Off counts only the amounts.")
        @ConfigEditorBoolean
        public boolean timerEnabled = true;

        @Expose
        @ConfigOption(name = "Show worth", desc = "Also show what the collected items are worth on the bazaar.")
        @ConfigEditorBoolean
        public boolean showValue = true;

        @Expose
        @ConfigOption(name = "Price", desc = "Instant Sell is what selling right now pays; Sell Order is what a listed order brings once it fills.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode priceMode = ItemValue.PriceMode.INSTANT_SELL;

        @Expose
        @ConfigOption(name = "Show total", desc = "The collection you already have. Open the collections menu once so the mod can read it.")
        @ConfigEditorBoolean
        public boolean showTotal = true;

        @Expose
        @ConfigOption(name = "Show gained", desc = "What came in since the last reset.")
        @ConfigEditorBoolean
        public boolean showGained = true;

        @Expose
        @ConfigOption(name = "Show per hour", desc = "The pace, from the tracked time.")
        @ConfigEditorBoolean
        public boolean showPerHour = true;

        @Expose
        @ConfigOption(name = "Line order", desc = "In which order Total, Gained and Per hour stand under each collection.")
        @ConfigEditorDropdown
        public LineOrder lineOrder = LineOrder.TOTAL_GAINED_HOUR;

        /** Die drei Zeilen unter einer Collection, in jeder moeglichen Reihenfolge */
        public enum LineOrder {
            TOTAL_GAINED_HOUR("Total, Gained, Per hour", "TGH"),
            TOTAL_HOUR_GAINED("Total, Per hour, Gained", "THG"),
            GAINED_TOTAL_HOUR("Gained, Total, Per hour", "GTH"),
            GAINED_HOUR_TOTAL("Gained, Per hour, Total", "GHT"),
            HOUR_TOTAL_GAINED("Per hour, Total, Gained", "HTG"),
            HOUR_GAINED_TOTAL("Per hour, Gained, Total", "HGT");

            public final String label;
            /** T = Total, G = Gained, H = Per hour */
            public final String code;

            LineOrder(String label, String code) {
                this.label = label;
                this.code = code;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        @Expose
        @ConfigOption(name = "Pause after", desc = "Seconds without anything collected before the timer pauses. The idle time is taken off again.")
        @ConfigEditorSlider(minValue = 10f, maxValue = 600f, minStep = 5f)
        public int pauseAfterSeconds = 120;

        @Expose
        @ConfigOption(name = "Rows", desc = "How many collections the panel lists.")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        public int maxRows = 4;

        @ConfigOption(name = "Reset", desc = "Clears the gained amounts and the timer. The totals from the collections menu stay.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetTracker = () -> {
        };

        /** Kennungen der gewaehlten Collections */
        @Expose
        public List<String> selected = new ArrayList<>();
        /** Zuwachs seit dem Reset, in Einheiten der Collection */
        @Expose
        public Map<String, Long> gains = new HashMap<>();
        /** Was die dafuer eingesammelten Items wert waren */
        @Expose
        public Map<String, Double> values = new HashMap<>();
        /** Gesamtstand aus dem Collections-Menue, sobald es einmal offen war */
        @Expose
        public Map<String, Long> totals = new HashMap<>();
        /** Zuwachs seit genau diesem Stand - so waechst Total mit, ohne doppelt zu zaehlen */
        @Expose
        public Map<String, Long> sinceRead = new HashMap<>();
        /** War der abgelesene Stand genau? Auf Co-op-Profilen ist die eigene Zeile gerundet */
        @Expose
        public Map<String, Boolean> totalExact = new HashMap<>();
        @Expose
        public long uptimeMillis = 0L;
        @Expose
        public long startedAt = 0L;

        @Expose
        public float hudX = 0.75f;
        @Expose
        public float hudY = 0.12f;
        @Expose
        public float hudScale = 1.0f;
        @Expose
        public float hudAlpha = 1.0f;
    }

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

        /** Der Gist, den der Bot schreibt - Voreinstellung, damit es ohne Zutun laeuft */
        public static final String DEFAULT_FALLBACK_URL =
                "https://gist.githubusercontent.com/shokiteufel/645fc250482ee832e484a56e328db125"
                        + "/raw/shokimod-events.json";

        @Expose
        @ConfigOption(name = "Primary URL", desc = "The bot on ShokiTeufel's PC, e.g. https://xyz.trycloudflare.com/shokimod/events.json. Asked first. Empty: skipped.")
        @ConfigEditorText
        public String primaryUrl = "";

        @Expose
        @ConfigOption(name = "Fallback URL", desc = "Raw URL of the GitHub Gist the bot keeps updated. Asked when the primary does not answer. Empty: skipped.")
        @ConfigEditorText
        // Voreingestellt, damit die Events ohne jede Einrichtung ankommen: der Gist steht
        // oeffentlich und wird vom Bot im Zehn-Minuten-Takt geschrieben
        public String fallbackUrl = DEFAULT_FALLBACK_URL;

        @Expose
        @ConfigOption(name = "Shared key", desc = "The same word as shokimod_key in the bot's config.json. Sent to the primary URL as X-ShokiMod-Key.")
        @ConfigEditorText
        public String sharedKey = "";

        @Expose
        @ConfigOption(name = "Follow the bot", desc = "Ask exactly when the bot has fresh numbers: every ten minutes at xx:x4:50, ten seconds before Discord shows them. Off: the interval below.")
        @ConfigEditorBoolean
        public boolean syncToBot = true;

        @Expose
        @ConfigOption(name = "Refresh seconds", desc = "Only without Follow the bot: how often to ask.")
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

        @Expose
        @Category(name = "Fusions", desc = "Which shard fusions pay off right now, from the bazaar. Open the list with /shoki shardprofit.")
        public FusionCategory fusion = new FusionCategory();
    }

    /**
     * Die Fusions-Liste: welche Shards zusammengelegt etwas abwerfen.
     *
     * Die Rezepte stammen von SkyShards (MIT), die Preise vom Bazaar; gerechnet wird
     * auf GitHub, weil es rund 130.000 Kombinationen sind.
     */
    public static class FusionCategory {

        /**
         * Wie die Zutaten beschafft werden.
         *
         * Der Unterschied ist kein Rundungsfehler: Bei derselben Fusion liegen zwischen
         * dem teuersten und dem guenstigsten Weg schon einmal zwei Millionen. Wer sofort
         * kauft, zahlt den Preis der offenen Angebote; wer einen Auftrag stellt, zahlt
         * weniger und wartet, bis jemand ihn bedient.
         */
        public enum BuyMode {
            INSTANT_BUY("Instant Buy"),
            BUY_ORDER("Buy Order");

            private final String label;

            BuyMode(String label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return label;
            }
        }

        @ConfigOption(name = "Fusions", desc = "Cost and profit per fusion, priced on the bazaar. The two settings below are independent: buying the ingredients right away and selling the result through an order is a perfectly ordinary choice.")
        @ConfigEditorInfoText
        public transient String about = "";

        @Expose
        @ConfigOption(name = "Ingredients", desc = "How you get the two shards you fuse. Instant Buy takes them from the open offers right now; Buy Order costs less and waits until someone fills it.")
        @ConfigEditorDropdown
        public BuyMode ingredientMode = BuyMode.INSTANT_BUY;

        @Expose
        @ConfigOption(name = "Result", desc = "How you turn the fused shard into coins. Instant Sell pays right now; Sell Order brings more once it fills.")
        @ConfigEditorDropdown
        public ItemValue.PriceMode resultMode = ItemValue.PriceMode.INSTANT_SELL;

        @ConfigOption(name = "Open the list", desc = "Same as /shoki shardprofit.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable open = () -> {
        };
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
        @ConfigOption(name = "Editor shows", desc = "In /shoki hud: all panels, so you can place them before switching them on - or only the ones that are on right now.")
        @ConfigEditorBoolean
        public boolean editorShowsAll = true;

        @Expose
        @Category(name = "Day", desc = "A small panel with the SkyBlock day count, like the one GanKura had.")
        public DayHudCategory day = new DayHudCategory();

        @Expose
        @Category(name = "Pet", desc = "The active pet: name, level and how far it is to the next one. The progress comes from the pet menu, so it updates whenever you open it.")
        public PetHudCategory pet = new PetHudCategory();

        @Expose
        @Category(name = "Performance", desc = "Frames per second, how fast the server is running, and the ping - each one on its own.")
        public PerformanceHudCategory performance = new PerformanceHudCategory();

        /**
         * Je Kasten die Gebiete, in denen er erscheinen darf. Leere oder fehlende Liste
         * heisst: die eingebaute Vorgabe gilt (Safari-Kaesten nur auf der Safari, der Rest
         * ueberall). Eingestellt wird das im HUD-Editor, Knopf "Areas".
         *
         * Bewusst eine Tabelle statt je Kasten ein Feld: neue Kaesten brauchen dann keine
         * neue Einstellung, sie stehen sofort mit drin.
         */
        @Expose
        public Map<String, List<String>> panelAreas = new HashMap<>();

        /** Die Liste eines Kastens, immer vorhanden - zum Bearbeiten im Waehler */
        public List<String> areasFor(String panel) {
            if (panelAreas == null) panelAreas = new HashMap<>();
            return panelAreas.computeIfAbsent(panel, key -> new ArrayList<>());
        }

        /**
         * Wann ein Kasten zu sehen ist: draussen, bei offenem Fenster, oder immer.
         *
         * Gedacht fuer Kaesten, die nur beim Blick ins Inventar interessieren - eine
         * Aufstellung, die im Kampf nur Platz wegnimmt, gehoert nicht dauernd aufs Bild.
         */
        public enum HudWhen {
            ALWAYS("Always"), WORLD("Outside only"), INVENTORY("Inventory only");

            public final String label;

            HudWhen(String label) {
                this.label = label;
            }
        }

        /** Je Kasten, wann er erscheinen darf. Fehlt der Eintrag, gilt ALWAYS */
        @Expose
        public Map<String, String> panelWhen = new HashMap<>();

        public HudWhen whenFor(String panel) {
            if (panelWhen == null) panelWhen = new HashMap<>();
            String stored = panelWhen.get(panel);
            if (stored == null) return HudWhen.ALWAYS;
            try {
                return HudWhen.valueOf(stored);
            } catch (IllegalArgumentException e) {
                return HudWhen.ALWAYS;
            }
        }

        public void setWhen(String panel, HudWhen when) {
            if (panelWhen == null) panelWhen = new HashMap<>();
            if (when == HudWhen.ALWAYS) panelWhen.remove(panel);
            else panelWhen.put(panel, when.name());
        }

        /** Darf der Kasten hier erscheinen? Null heisst: keine eigene Wahl getroffen */
        public Boolean allowsHere(String panel, String area) {
            if (panelAreas == null) return null;
            List<String> chosen = panelAreas.get(panel);
            if (chosen == null || chosen.isEmpty()) return null;
            if (area == null || area.isBlank()) return false;
            for (String entry : chosen) {
                if (entry.equalsIgnoreCase(area)) return true;
            }
            return false;
        }

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

        /**
         * Was geschieht, wenn mehrere Funde gleichzeitig kommen.
         *
         * Aus einem Nucleus-Bundle fallen vier, fuenf Sachen auf einmal. Frueher hielt
         * die Einblendung nur einen einzigen Zustand: Jeder neue Fund ueberschrieb den
         * laufenden, und uebrig blieb, wen die Reihenfolge zufaellig zuletzt brachte -
         * nicht etwa der wertvollste.
         */
        public enum MultiDrop {
            CHEAP_FIRST("One by one, cheap first"),
            RICH_FIRST("One by one, rich first"),
            STACKED("All at once, under each other"),
            NEWEST("Only the newest");

            public final String label;

            MultiDrop(String label) {
                this.label = label;
            }
        }

        @Expose
        @ConfigOption(name = "Several at once", desc = "What happens when a bundle drops more than one thing worth showing: one banner after the other, all of them under each other, or only the newest.")
        @ConfigEditorDropdown
        public MultiDrop multiDrop = MultiDrop.CHEAP_FIRST;

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
         * Ob der Bazaar dauernd frisch geholt wird oder nur beim Hinsehen.
         *
         * Hypixel setzt den Bazaar alle zwanzig Sekunden neu, und komprimiert sind es
         * vierhundertachtzig Kilobyte - alle fuenfzehn Sekunden macht das etwa
         * hundert Megabyte in der Stunde. Fuer die Profit-Fenster lohnt sich das,
         * solange man hineinsieht; rund um die Uhr fuer jeden Fund im Chat ist es
         * viel Verkehr fuer wenig: In zehn Minuten bewegt sich ein Bazaar-Preis
         * meist um Prozente, und ob ein Fund 41 oder 42 Millionen wert ist, aendert
         * an der Meldung nichts.
         *
         * Wer die Genauigkeit trotzdem will, schaltet es hier ein.
         */
        @Expose
        @ConfigOption(name = "Live bazaar prices", desc = "Fetches the bazaar straight from Hypixel every 15 seconds instead of every 10 minutes - for everything, not just the profit screens. Costs about 100 MB per hour. Off means the profit screens still do it while they are open.")
        @ConfigEditorBoolean
        public boolean liveBazaarAlways = false;


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

        /**
         * Nur zum Auf- und Zuklappen. MoulConfig haelt den Zustand selbst und schreibt
         * nie in dieses Feld - ein Schalter darf deshalb nicht am Kopf haengen
         */
        @ConfigOption(name = "Tier 4", desc = "The top tier. From the threshold below up; a drop that reaches it fires only this one.")
        @ConfigEditorAccordion(id = 25)
        public transient boolean tier4Folder = false;

        @Expose
        @ConfigOption(name = "Enabled", desc = "Off means this tier never fires, even if the drop reaches its threshold.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Enabled = false;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Coins. Short forms work: 500k, 5M, 1.2B. Counted is the whole drop, so 3x counts three times.")
        @ConfigEditorText
        @ConfigAccordionId(id = 25)
        public String tier4Threshold = "250M";

        @Expose
        @ConfigOption(name = "Banner", desc = "Large text across the screen.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Banner = true;

        @Expose
        @ConfigOption(name = "Banner", desc = "Name of the banner design this tier shows. Pick or build one in Alerts > Banner > Sandbox, where Use for Tier sets this for you.")
        @ConfigEditorText
        @ConfigAccordionId(id = 25)
        public String tier4Design = "Classic band";

        @Expose
        @ConfigOption(name = "Toast", desc = "Small box in the top right corner.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Toast = true;

        @Expose
        @ConfigOption(name = "Chat line", desc = "Writes the drop and its value into your chat.")
        @ConfigEditorBoolean
        @ConfigAccordionId(id = 25)
        public boolean tier4Chat = false;

        @ConfigOption(name = "Sound", desc = "Your own file from config/shokimod/sounds. Leave empty for silence.")
        @ConfigEditorButton(buttonText = "Pick")
        @ConfigAccordionId(id = 25)
        public transient Runnable openTier4Sound = () -> {
        };

        @Expose
        public String tier4Sound = "";

        @ConfigOption(name = "Test", desc = "Fires this tier once with a sample drop, so you can see and hear what you set.")
        @ConfigEditorButton(buttonText = "Test")
        @ConfigAccordionId(id = 25)
        public transient Runnable testTier4 = () -> {
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
                case 3 -> new Tier(3, tier3Enabled, tier3Threshold, tier3Banner, tier3Toast, tier3Chat, tier3Sound, tier3Design);
                default -> new Tier(4, tier4Enabled, tier4Threshold, tier4Banner, tier4Toast, tier4Chat, tier4Sound, tier4Design);
            };
        }

        public List<Tier> tiers() {
            return List.of(tier(1), tier(2), tier(3), tier(4));
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
