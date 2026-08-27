package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Platziert die Kaesten dieser Mod und zeichnet sie.
 *
 * Die Lage wird als Anteil der Bildschirmgroesse gespeichert, nicht in Pixeln - sonst
 * wandert der Kasten beim Wechsel der Aufloesung oder der GUI-Skalierung aus dem Bild.
 */
public final class SafariHud {

    /** Ein Kasten auf dem Bildschirm. Traegt seine eigene Lage und Groesse in der Config */
    public enum Panel {
        PROGRESS, MISSING, CONTEST;

        public boolean visible() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.showProgressHud;
                case MISSING -> c.showMissingHud;
                case CONTEST -> c.showContestHud;
            };
        }

        /**
         * Gehoert der Kasten hierher?
         *
         * Die beiden Safari-Kaesten haben ausserhalb nichts zu sagen. Der Contest laeuft
         * dagegen ueberall in SkyBlock und soll auch ueberall zu sehen sein.
         */
        public boolean showsHere() {
            return this == CONTEST || GameState.Server.isSafari();
        }

        public float x() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudX;
                case MISSING -> c.missingHudX;
                case CONTEST -> c.contestHudX;
            };
        }

        public float y() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudY;
                case MISSING -> c.missingHudY;
                case CONTEST -> c.contestHudY;
            };
        }

        public float scale() {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            return switch (this) {
                case PROGRESS -> c.progressHudScale;
                case MISSING -> c.missingHudScale;
                case CONTEST -> c.contestHudScale;
            };
        }

        public void setPosition(float x, float y) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            switch (this) {
                case PROGRESS -> {
                    c.progressHudX = x;
                    c.progressHudY = y;
                }
                case MISSING -> {
                    c.missingHudX = x;
                    c.missingHudY = y;
                }
                case CONTEST -> {
                    c.contestHudX = x;
                    c.contestHudY = y;
                }
            }
        }

        public void setScale(float scale) {
            ModConfig.SafariCategory c = ModConfig.INSTANCE.safari;
            float clamped = Math.clamp(scale, 0.5f, 3.0f);
            switch (this) {
                case PROGRESS -> c.progressHudScale = clamped;
                case MISSING -> c.missingHudScale = clamped;
                case CONTEST -> c.contestHudScale = clamped;
            }
        }

        public HudPanel build() {
            return switch (this) {
                case PROGRESS -> ProgressHud.build();
                case MISSING -> MissingHud.build();
                case CONTEST -> ContestHud.build();
            };
        }
    }

    private SafariHud() {
    }

    /** Beim Spielen: nur was eingeschaltet ist und an diesen Ort gehoert */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!GameState.Server.isSkyblock()) return;

        for (Panel panel : Panel.values()) {
            if (!panel.visible() || !panel.showsHere()) continue;
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
