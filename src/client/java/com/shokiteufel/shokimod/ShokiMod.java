package com.shokiteufel.shokimod;

import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.PetProfitData;
import com.shokiteufel.shokimod.util.BundledSounds;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.gui.PetHudBuilderScreen;
import com.shokiteufel.shokimod.gui.PetProfitScreen;
import com.shokiteufel.shokimod.gui.ShokiConfigEditor;
import com.shokiteufel.shokimod.handler.FloorDropHandler;
import com.shokiteufel.shokimod.handler.NetworkHandler;
import com.shokiteufel.shokimod.handler.CakeReminder;
import com.shokiteufel.shokimod.handler.CollectionTracker;
import com.shokiteufel.shokimod.handler.GuildEvents;
import com.shokiteufel.shokimod.handler.HotspotTracker;
import com.shokiteufel.shokimod.handler.HuntingTracker;
import com.shokiteufel.shokimod.handler.RareLootHandler;
import com.shokiteufel.shokimod.render.DropBanner;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import com.shokiteufel.shokimod.scanner.LocationScanner;
import com.shokiteufel.shokimod.scanner.ContestState;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.session.SessionManager;
import com.shokiteufel.shokimod.scanner.TabListScanner;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent;
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent;
import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShokiMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ShokiMod");

    // Das Einstellungsfenster darf nicht aus der Chateingabe heraus aufgehen,
    // sonst schliesst der Chat es sofort wieder. Deshalb erst im naechsten Tick
    private static boolean openConfigNextTick = false;
    private static boolean openHudNextTick = false;
    private static boolean openPetProfitNextTick = false;
    private static boolean openShardProfitNextTick = false;
    private static boolean openPetBuilderNextTick = false;


    @Override
    public void onInitializeClient() {
        ModConfig.load();
        BundledSounds.seed();
        NetworkHandler.init();
        // Die Ortsbestimmung ist Voraussetzung für alles Weitere, darum zuerst
        LocationScanner.register();
        TabListScanner.register();
        EntityHighlightManager.register();
        FloorDropHandler.register();
        RareLootHandler.register();
        HuntingTracker.register();
        GuildEvents.register();
        CakeReminder.register();
        HotspotTracker.register();
        CollectionTracker.register();
        NestTracker.register();
        ContestState.register();
        SessionManager.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigNextTick) {
                openConfigNextTick = false;
                openConfigScreen();
            }
            if (openHudNextTick) {
                openHudNextTick = false;
                client.setScreen(new HudEditorScreen(null, false));
            }
            if (openPetProfitNextTick) {
                openPetProfitNextTick = false;
                client.setScreen(new PetProfitScreen(null));
            }
            if (openShardProfitNextTick) {
                openShardProfitNextTick = false;
                client.setScreen(new com.shokiteufel.shokimod.gui.ShardProfitScreen(null));
            }
            if (openPetBuilderNextTick) {
                openPetBuilderNextTick = false;
                client.setScreen(new PetHudBuilderScreen(null));
            }
            // Der Fortschritt des Pets steht nur im Pet-Menue; solange es offen ist,
            // wird er mitgelesen
            com.shokiteufel.shokimod.scanner.PetState.tick(client);
            // Die Umlaufzeit selbst messen - die Spielerliste taugt bei Hypixel nicht
            com.shokiteufel.shokimod.scanner.PerformanceState.tickPing(client);
            // Die Gewinnliste im Hintergrund holen, damit sie beim Oeffnen dasteht
            // und nicht erst beim ersten Blick angefordert wird
            if (com.shokiteufel.shokimod.data.GameState.Server.isSkyblock()) PetProfitData.prefetch();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModConfig.INSTANCE.saveNow();
            LOGGER.info("ShokiMod config saved successfully on exit.");
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("shoki")
                        // /shoki -> Einstellungen
                        .executes(context -> {
                            openConfigNextTick = true;
                            return 1;
                        })
                        // /shoki hud -> die Kaesten verschieben.
                        // "hub" liegt auf derselben Taste daneben und ist als Schreibweise
                        // mit angemeldet, damit der Vertipper nicht ins Leere laeuft
                        .then(ClientCommands.literal("hud").executes(context -> {
                            openHudNextTick = true;
                            return 1;
                        }))
                        .then(ClientCommands.literal("hub").executes(context -> {
                            openHudNextTick = true;
                            return 1;
                        }))
                        // /shoki petprofit -> welche Pets sich zu leveln lohnen.
                        // Die Liste wird nicht hier gerechnet, sondern alle halbe Stunde
                        // auf GitHub; das Holen wird schon hier angestossen, damit beim
                        // Oeffnen im naechsten Tick moeglichst etwas dasteht
                        // /shoki pet -> der Baukasten fuer den Pet-Kasten
                        .then(ClientCommands.literal("pet").executes(context -> {
                            openPetBuilderNextTick = true;
                            return 1;
                        }))
                        .then(ClientCommands.literal("petprofit").executes(context -> {
                            PetProfitData.prefetch();
                            openPetProfitNextTick = true;
                            return 1;
                        }))
                        // /shoki shardprofit -> welche Shard-Fusionen etwas abwerfen.
                        // Auch diese Liste kommt fertig aus dem Netz; das Holen faengt
                        // hier an, damit beim Oeffnen schon etwas dasteht
                        .then(ClientCommands.literal("shardprofit").executes(context -> {
                            com.shokiteufel.shokimod.util.ShardProfitData.prefetch();
                            openShardProfitNextTick = true;
                            return 1;
                        }))
                        // /shoki tab -> die Tab-Liste ins Log schreiben.
                        // Hypixels Zeilen aendern sich mit jedem Update; ohne den Blick
                        // auf die echten Zeilen ist jede Auswertung geraten
                        // /shoki test 7  oder  /shoki test Side card  -> zeigt das Design mit Beispieltext
                        .then(ClientCommands.literal("test")
                                .then(ClientCommands.argument("banner", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String raw = StringArgumentType.getString(context, "banner").trim();
                                            List<BannerDesign> designs = ModConfig.INSTANCE.chat.banner.designs;
                                            BannerDesign chosen = null;
                                            try {
                                                int number = Integer.parseInt(raw.replaceAll("(?i)^b", ""));
                                                if (number >= 1 && number <= designs.size()) chosen = designs.get(number - 1);
                                            } catch (NumberFormatException ignored) {
                                                // dann ist es ein Name
                                            }
                                            if (chosen == null) chosen = ModConfig.INSTANCE.chat.banner.design(raw);
                                            if (chosen == null) {
                                                context.getSource().sendFeedback(Component.literal(
                                                        "Use /shoki test <1-" + designs.size() + "> or a design name."));
                                                return 0;
                                            }
                                            DropBanner.preview(chosen);
                                            context.getSource().sendFeedback(Component.literal("Banner: " + chosen.name));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("tab").executes(context -> {
                            List<String> lines = TabListScanner.lastLines();
                            LOGGER.info("[ShokiMod] Tab list, {} lines:", lines.size());
                            for (int i = 0; i < lines.size(); i++) {
                                LOGGER.info("[ShokiMod]   {}: {}", i, lines.get(i));
                            }
                            // Was der Mining-Leser daraus gemacht hat, gehoert daneben:
                            // sonst sieht man die Zeilen, aber nicht, warum sie nicht ankamen
                            String mining = com.shokiteufel.shokimod.scanner.MiningState.diagnostics();
                            LOGGER.info("[ShokiMod] Mining: {}", mining);
                            context.getSource().sendFeedback(Component.literal(
                                    "Wrote " + lines.size() + " tab lines to the log."));
                            context.getSource().sendFeedback(Component.literal(mining));
                            return 1;
                        }))));

        LOGGER.info("ShokiMod initialized (Mojang Mapping).");
    }

    public static void openConfigScreen() {
        openConfigScreen(null);
    }

    /**
     * Das Einstellungsfenster oeffnen, wahlweise gleich bei einer Funktion.
     *
     * @param suche Text fuer das Suchfeld, etwa "Mining HUD" - null zeigt alles
     */
    public static void openConfigScreen(String suche) {
        try {
            MoulConfigProcessor<ModConfig> processor = MoulConfigProcessor.withDefaults(ModConfig.INSTANCE);
            ConfigProcessorDriver driver = new ConfigProcessorDriver(processor);
            driver.processConfig(ModConfig.INSTANCE);

            ShokiConfigEditor editor = new ShokiConfigEditor(processor);
            editor.preset(suche);
            GuiElementComponent editorComponent = new GuiElementComponent(editor);
            GuiContext guiContext = new GuiContext(editorComponent);

            MoulConfigScreenComponent configScreen = new MoulConfigScreenComponent(
                    Component.literal("ShokiMod Configuration"),
                    guiContext,
                    Minecraft.getInstance().screen
            );

            Minecraft.getInstance().setScreen(configScreen);
        } catch (Exception e) {
            LOGGER.error("Failed to open MoulConfig screen!", e);
        }
    }
}
