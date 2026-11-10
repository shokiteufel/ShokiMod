package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.render.hud.HudPanel;
import com.shokiteufel.shokimod.render.hud.PetHud;
import com.shokiteufel.shokimod.render.hud.PetHud.Part;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Der Baukasten fuer den Pet-Kasten: was erscheint, in welcher Reihenfolge, wie gross.
 *
 * Links die Teile mit Pfeilen zum Verschieben und einem Schalter zum An- und Abwaehlen,
 * rechts eine Vorschau, die sich sofort mitaendert. Wer sortiert, soll nicht raten
 * muessen, wie es hinterher aussieht - deshalb steht die Vorschau daneben und wird aus
 * demselben Bauplan gezeichnet wie der Kasten im Spiel.
 */
public class PetHudBuilderScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 46;
    private static final int LIST_WIDTH = 210;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;

    private final Screen parent;

    public PetHudBuilderScreen(Screen parent) {
        super(Component.literal("Pet display"));
        this.parent = parent;
    }

    private static ModConfig.PetHudCategory cfg() {
        return ModConfig.INSTANCE.hud.pet;
    }

    /**
     * Die Vorschau, einmal gebaut und gehalten (vorher baute das Bild in jedem Bild einen neuen Kasten mit allen Zeilen).
     *
     * Eingaben: die Einstellungen dieses Fensters - jede endet in {@link #rebuild()} und damit in {@link #init()}, das die Vorschau verwirft -
     * und die Pet-Daten ({@code PetState}: Name, Stufe, Fortschritt, Bild), die im Hintergrund aus Chat und Tab-Liste kommen und keinen
     * Versionszaehler haben. Dafuer gilt die Obergrenze von einer Sekunde.
     */
    private HudPanel vorschauCache;
    private long vorschauAt;
    private static final long VORSCHAU_MILLIS = 1_000L;

    private HudPanel vorschau() {
        long now = System.currentTimeMillis();
        if (vorschauCache == null || now - vorschauAt >= VORSCHAU_MILLIS) {
            vorschauCache = PetHud.build();
            vorschauAt = now;
        }
        return vorschauCache;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        // Jeder Neuaufbau heisst: eine Einstellung hat sich geaendert - die Vorschau neu bauen lassen
        vorschauCache = null;
        ModConfig.PetHudCategory c = cfg();
        List<Part> teile = PetHud.order(c);
        int left = width / 2 - LIST_WIDTH - 12;

        for (int i = 0; i < teile.size(); i++) {
            Part part = teile.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            boolean an = part.enabled(c);

            addRenderableWidget(Button.builder(
                    Component.literal((an ? "☑ " : "☐ ") + part.label)
                            .withStyle(an ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY),
                    b -> {
                        part.toggle(c);
                        rebuild();
                    }).bounds(left, y, LIST_WIDTH - 46, 20).build());

            int index = i;
            Button hoch = Button.builder(Component.literal("▲"), b -> {
                move(teile, index, index - 1);
                rebuild();
            }).bounds(left + LIST_WIDTH - 44, y, 20, 20).build();
            hoch.active = i > 0;
            addRenderableWidget(hoch);

            Button runter = Button.builder(Component.literal("▼"), b -> {
                move(teile, index, index + 1);
                rebuild();
            }).bounds(left + LIST_WIDTH - 22, y, 20, 20).build();
            runter.active = i < teile.size() - 1;
            addRenderableWidget(runter);
        }

        int unten = LIST_TOP + teile.size() * ROW_HEIGHT + 8;

        addRenderableWidget(Button.builder(
                Component.literal(c.sameLine ? "Layout: side by side" : "Layout: below each other"),
                b -> {
                    c.sameLine = !c.sameLine;
                    rebuild();
                }).bounds(left, unten, LIST_WIDTH, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(c.showLabels ? "Labels: on" : "Labels: off"), b -> {
                    c.showLabels = !c.showLabels;
                    rebuild();
                }).bounds(left, unten + 24, LIST_WIDTH / 2 - 2, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Icon: " + c.iconPlace.label), b -> {
                    ModConfig.IconPlace[] alle = ModConfig.IconPlace.values();
                    c.iconPlace = alle[(c.iconPlace.ordinal() + 1) % alle.length];
                    rebuild();
                }).bounds(left + LIST_WIDTH / 2 + 2, unten + 24, LIST_WIDTH / 2 - 2, 20).build());

        // Groesse in Schritten von einem Zehntel - feiner braucht es niemand
        addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            c.hudScale = clamp(Math.round((c.hudScale - 0.1f) * 10f) / 10f);
            rebuild();
        }).bounds(left, unten + 48, 20, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(String.format(Locale.US, "Size: %.1fx", c.hudScale)), b -> {
                    c.hudScale = 1.0f;
                    rebuild();
                }).bounds(left + 24, unten + 48, LIST_WIDTH - 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            c.hudScale = clamp(Math.round((c.hudScale + 0.1f) * 10f) / 10f);
            rebuild();
        }).bounds(left + LIST_WIDTH - 20, unten + 48, 20, 20).build());

        // Die Bildgroesse getrennt von der Textgroesse
        addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            c.iconScale = clamp(Math.round((c.iconScale - 0.1f) * 10f) / 10f);
            rebuild();
        }).bounds(left, unten + 72, 20, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal(String.format(Locale.US, "Icon size: %.1fx", c.iconScale)), b -> {
                    c.iconScale = 1.0f;
                    rebuild();
                }).bounds(left + 24, unten + 72, LIST_WIDTH - 48, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            c.iconScale = clamp(Math.round((c.iconScale + 0.1f) * 10f) / 10f);
            rebuild();
        }).bounds(left + LIST_WIDTH - 20, unten + 72, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Move on screen"), b -> {
            if (minecraft != null) minecraft.setScreenAndShow(new HudEditorScreen(this, false));
        }).bounds(left, unten + 96, LIST_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left, height - 28, LIST_WIDTH, 20).build());
    }

    /** Ein Teil an eine andere Stelle setzen und die Reihenfolge festhalten */
    private void move(List<Part> teile, int from, int to) {
        if (to < 0 || to >= teile.size()) return;
        Part part = teile.remove(from);
        teile.add(to, part);
        PetHud.saveOrder(cfg(), teile);
    }

    private static float clamp(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Feste Texte: je Zeichenkette einmal gebaut und geteilt, nicht in jedem Bild
        graphics.centeredText(font, FensterText.gold("Pet display"), width / 2, 14, 0xFFFFAA00);
        graphics.centeredText(font, FensterText.dunkelgrau("Order the parts, pick what shows, set the size"), width / 2, 28, 0xFF888888);

        // Die Vorschau kommt aus demselben Bauplan wie der Kasten im Spiel; sie liegt fertig vor und wird nur neu gebaut, wenn sich etwas aendert
        int previewLeft = width / 2 + 12;
        graphics.text(font, "Preview", previewLeft, LIST_TOP - 14, 0xFF888888, false);
        HudPanel vorschau = vorschau();
        float scale = cfg().hudScale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(previewLeft, LIST_TOP);
        graphics.pose().scale(scale, scale);
        vorschau.render(graphics, font, 0, 0);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
