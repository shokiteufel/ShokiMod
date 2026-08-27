package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Platziert die beiden Safari-Kaesten und zeichnet sie.
 *
 * Die Lage wird als Anteil der Bildschirmgroesse gespeichert, nicht in Pixeln - sonst
 * wandert der Kasten beim Wechsel der Aufloesung oder der GUI-Skalierung aus dem Bild.
 */
public final class SafariHud {

    /** Welcher der beiden Kaesten. Traegt seine eigene Lage und Groesse in der Config */
    public enum Panel {
        PROGRESS, MISSING;

        public boolean visible() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return this == PROGRESS ? c.showProgressHud : c.showMissingHud;
        }

        public float x() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return this == PROGRESS ? c.progressHudX : c.missingHudX;
        }

        public float y() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return this == PROGRESS ? c.progressHudY : c.missingHudY;
        }

        public float scale() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return this == PROGRESS ? c.progressHudScale : c.missingHudScale;
        }

        public void setPosition(float x, float y) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            if (this == PROGRESS) {
                c.progressHudX = x;
                c.progressHudY = y;
            } else {
                c.missingHudX = x;
                c.missingHudY = y;
            }
        }

        public void setScale(float scale) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            float clamped = Math.clamp(scale, 0.5f, 3.0f);
            if (this == PROGRESS) {
                c.progressHudScale = clamped;
            } else {
                c.missingHudScale = clamped;
            }
        }

        public HudPanel build() {
            return this == PROGRESS ? ProgressHud.build() : MissingHud.build();
        }
    }

    private SafariHud() {
    }

    /** Beim Spielen: nur in der Safari und nur was eingeschaltet ist */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!GameState.Server.isSafari()) return;

        for (Panel panel : Panel.values()) {
            if (!panel.visible()) continue;
            draw(graphics, client.font, panel, panel.build());
        }
    }

    /** Zeichnet einen Kasten an seiner Stelle, in seiner Groesse */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Panel panel, HudPanel content) {
        if (content.isEmpty()) return;

        float scale = panel.scale();
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX(panel), originY(panel));
        graphics.pose().scale(scale, scale);
        content.render(graphics, font, 0, 0);
        graphics.pose().popMatrix();
    }

    public static int originX(Panel panel) {
        return Math.round(panel.x() * Minecraft.getInstance().getWindow().getGuiScaledWidth());
    }

    public static int originY(Panel panel) {
        return Math.round(panel.y() * Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    /** Groesse des Kastens auf dem Bildschirm, mit Skalierung */
    public static int scaledWidth(Panel panel, HudPanel content, Font font) {
        return Math.round(content.width(font) * panel.scale());
    }

    public static int scaledHeight(Panel panel, HudPanel content) {
        return Math.round(content.height() * panel.scale());
    }
}
