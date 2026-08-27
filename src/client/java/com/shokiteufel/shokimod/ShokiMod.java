package com.shokiteufel.shokimod;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.FloorDropHandler;
import com.shokiteufel.shokimod.handler.NetworkHandler;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import com.shokiteufel.shokimod.scanner.LocationScanner;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.session.SessionManager;
import com.shokiteufel.shokimod.scanner.TabListScanner;

import io.github.notenoughupdates.moulconfig.gui.GuiContext;
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent;
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShokiMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ShokiMod");

    // Das Einstellungsfenster darf nicht aus der Chateingabe heraus aufgehen,
    // sonst schliesst der Chat es sofort wieder. Deshalb erst im naechsten Tick
    private static boolean openConfigNextTick = false;
    private static boolean openHudNextTick = false;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        NetworkHandler.init();
        // Die Ortsbestimmung ist Voraussetzung für alles Weitere, darum zuerst
        LocationScanner.register();
        TabListScanner.register();
        EntityHighlightManager.register();
        FloorDropHandler.register();
        NestTracker.register();
        SessionManager.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfigNextTick) {
                openConfigNextTick = false;
                openConfigScreen();
            }
            if (openHudNextTick) {
                openHudNextTick = false;
                client.setScreen(new HudEditorScreen(null));
            }
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
                        }))));

        LOGGER.info("ShokiMod initialized (Mojang Mapping).");
    }

    private static void openConfigScreen() {
        try {
            MoulConfigProcessor<ModConfig> processor = MoulConfigProcessor.withDefaults(ModConfig.INSTANCE);
            ConfigProcessorDriver driver = new ConfigProcessorDriver(processor);
            driver.processConfig(ModConfig.INSTANCE);

            MoulConfigEditor<ModConfig> editor = new MoulConfigEditor<>(processor);
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
