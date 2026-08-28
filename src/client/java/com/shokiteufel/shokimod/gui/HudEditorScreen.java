package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.hud.HudPanel;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Die beiden Safari-Kaesten an die gewuenschte Stelle ziehen.
 *
 * Ziehen verschiebt, Scrollen ueber einem Kasten aendert seine Groesse. Gezeichnet
 * werden sie mit Beispielinhalt, damit sich auch ausserhalb der Safari etwas einrichten
 * laesst - sonst muesste man zum Einrichten erst hinfahren.
 */
public class HudEditorScreen extends Screen {

    private final Screen parent;
    private final Map<SafariHud.Panel, HudPanel> content = new EnumMap<>(SafariHud.Panel.class);

    private SafariHud.Panel dragging = null;
    private int grabX = 0;
    private int grabY = 0;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Move the Safari panels"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildContent();

        addRenderableWidget(Button.builder(Component.literal("Reset positions"), button -> {
            SafariHud.Panel.PROGRESS.setPosition(0.01f, 0.02f);
            SafariHud.Panel.PROGRESS.setScale(1.0f);
            SafariHud.Panel.MISSING.setPosition(0.75f, 0.02f);
            SafariHud.Panel.MISSING.setScale(1.0f);
            SafariHud.Panel.CONTEST.setPosition(0.4f, 0.02f);
            SafariHud.Panel.CONTEST.setScale(1.0f);
            SafariHud.Panel.NEARBY.setPosition(0.01f, 0.35f);
            SafariHud.Panel.NEARBY.setScale(1.0f);
        }).bounds(width / 2 - 105, height - 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 + 5, height - 30, 100, 20).build());
    }

    /** Der Inhalt aendert sich waehrend des Einrichtens nicht - einmal bauen genuegt */
    private void rebuildContent() {
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            content.put(panel, panel.build());
        }
    }

    private boolean isOver(SafariHud.Panel panel, double mouseX, double mouseY) {
        HudPanel built = content.get(panel);
        if (built == null || built.isEmpty()) return false;

        int left = SafariHud.originX(panel);
        int top = SafariHud.originY(panel);
        return mouseX >= left && mouseX <= left + SafariHud.scaledWidth(panel, built, font)
                && mouseY >= top && mouseY <= top + SafariHud.scaledHeight(panel, built);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, event.x(), event.y())) continue;
            dragging = panel;
            grabX = (int) event.x() - SafariHud.originX(panel);
            grabY = (int) event.y() - SafariHud.originY(panel);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) return super.mouseDragged(event, dragX, dragY);

        // In Anteilen speichern, damit die Lage jede Aufloesung ueberlebt
        float x = (float) (event.x() - grabX) / width;
        float y = (float) (event.y() - grabY) / height;
        dragging.setPosition(Math.clamp(x, 0f, 0.98f), Math.clamp(y, 0f, 0.98f));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            if (!isOver(panel, mouseX, mouseY)) continue;
            panel.setScale(panel.scale() + (float) scrollY * 0.1f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Kein Abdunkeln, kein Weichzeichner.
     *
     * Beides wuerde hinter die halbdurchsichtigen Kaesten geraten und sie blasser
     * wirken lassen, als sie im Spiel aussehen - man wuerde also etwas einrichten,
     * das man so nie zu sehen bekommt.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        for (SafariHud.Panel panel : SafariHud.Panel.values()) {
            HudPanel built = content.get(panel);
            if (built == null || built.isEmpty()) continue;

            // Der Rahmen zeigt den Fangbereich, auch wenn der Kasten selbst schmal ist
            int left = SafariHud.originX(panel);
            int top = SafariHud.originY(panel);
            int right = left + SafariHud.scaledWidth(panel, built, font);
            int bottom = top + SafariHud.scaledHeight(panel, built);
            int colour = isOver(panel, mouseX, mouseY) ? 0xFFFFAA00 : 0x60FFFFFF;
            graphics.fill(left - 1, top - 1, right + 1, top, colour);
            graphics.fill(left - 1, bottom, right + 1, bottom + 1, colour);
            graphics.fill(left - 1, top, left, bottom, colour);
            graphics.fill(right, top, right + 1, bottom, colour);

            SafariHud.draw(graphics, font, panel, built);
        }

        graphics.centeredText(font, this.title, width / 2, 12, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Drag to move, scroll over a panel to resize")
                .withStyle(ChatFormatting.GRAY), width / 2, 26, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
