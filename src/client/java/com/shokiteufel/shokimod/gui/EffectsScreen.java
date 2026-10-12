package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EffectEntry;
import com.shokiteufel.shokimod.handler.EffectsTracker;
import com.shokiteufel.shokimod.render.hud.EffectsHud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Die gemerkten Effekte, einer je Zeile: Name, Restzeit, und je Effekt ob er im Kasten steht
 * und ob er Alarm gibt. Gemerkt wird, sobald man /effects oeffnet.
 */
public class EffectsScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 190;
    private static final int TIME_WIDTH = 74;
    private static final int TOGGLE_WIDTH = 62;
    private static final int REMOVE_WIDTH = 20;
    private static final int LIST_TOP = 50;
    private static final int GRID_WIDTH = NAME_WIDTH + TIME_WIDTH + 2 * TOGGLE_WIDTH + REMOVE_WIDTH + 16;

    private final Screen parent;
    private static int page = 0;

    public EffectsScreen(Screen parent) {
        super(Component.literal("Effects"));
        this.parent = parent;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (EffectsTracker.entries().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        page = Math.clamp(page, 0, pageCount() - 1);
        List<EffectEntry> liste = EffectsTracker.entries();
        int left = width / 2 - GRID_WIDTH / 2;

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            EffectEntry e = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left + NAME_WIDTH + TIME_WIDTH + 8;

            addRenderableWidget(Button.builder(
                    Component.literal(e.hud ? "HUD ☑" : "HUD ☐")
                            .withStyle(e.hud ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                    button -> {
                        EffectsTracker.toggleHud(e);
                        rebuild();
                    }).bounds(x, y, TOGGLE_WIDTH, 20).build());
            addRenderableWidget(Button.builder(
                    Component.literal(e.alert ? "Alert ☑" : "Alert ☐")
                            .withStyle(e.alert ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY),
                    button -> {
                        EffectsTracker.toggleAlert(e);
                        rebuild();
                    }).bounds(x + TOGGLE_WIDTH + 4, y, TOGGLE_WIDTH, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED),
                    button -> {
                        EffectsTracker.remove(e);
                        rebuild();
                    }).bounds(x + 2 * TOGGLE_WIDTH + 8, y, REMOVE_WIDTH, 20).build());
        }

        if (pageCount() > 1) {
            int mitte = width / 2;
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(mitte - 60, height - 50, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(mitte + 40, height - 50, 20, 20).build());
        }

        // Oeffnet /effects, damit die Mod es liest - ohne den Befehl selbst zu tippen
        addRenderableWidget(Button.builder(Component.literal("Open /effects"), button -> {
            if (minecraft != null && minecraft.getConnection() != null) {
                minecraft.setScreen(null);
                minecraft.getConnection().sendCommand("effects");
            }
        }).bounds(left, height - 28, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(ModConfig.INSTANCE.effects.enabled ? "Enabled ☑" : "Enabled ☐")
                        .withStyle(ModConfig.INSTANCE.effects.enabled ? ChatFormatting.GREEN : ChatFormatting.RED),
                button -> {
                    ModConfig.INSTANCE.effects.enabled = !ModConfig.INSTANCE.effects.enabled;
                    ModConfig.INSTANCE.saveNow();
                    rebuild();
                }).bounds(left + 116, height - 28, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + GRID_WIDTH - 80, height - 28, 80, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, Component.literal("Effects").withStyle(ChatFormatting.GOLD),
                centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, Component.literal(
                        "Open /effects once - every effect in it is remembered here")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 26, 0xFF888888);
        if (!ModConfig.INSTANCE.effects.enabled) {
            graphics.centeredText(font, Component.literal(
                            "Panel and alerts are off - switch Enabled on below")
                    .withStyle(ChatFormatting.RED), centerX, 38, 0xFFFF5555);
        }

        List<EffectEntry> liste = EffectsTracker.entries();
        if (liste.isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing remembered yet").withStyle(ChatFormatting.GRAY),
                    centerX, LIST_TOP + 20, 0xFFAAAAAA);
            return;
        }
        int left = width / 2 - GRID_WIDTH / 2;
        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            EffectEntry e = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT + 6;
            graphics.text(font, e.name, left, y, 0xFFFFFFFF, false);
            Long rest = EffectsTracker.remaining(e);
            String time = rest == null ? "?" : rest <= 0L ? "off" : EffectsHud.format(rest);
            int colour = rest == null ? 0xFFAAAAAA : rest <= 0L ? 0xFFFF5555 : 0xFF55FF55;
            graphics.text(font, time, left + NAME_WIDTH + 4, y, colour, false);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }
}
