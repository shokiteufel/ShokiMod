package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.LootshareTracker;
import com.shokiteufel.shokimod.handler.LootshareTracker.Target;
import com.shokiteufel.shokimod.render.hud.LootshareHud;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Was der Lootshare-Helfer in der Welt zeigt: den Text ueber dem Mob, den Kreis am Boden
 * und das Haeckchen ueber den Koepfen der Gruppe.
 *
 * Der Text wird als Bild gezeichnet, nicht als Text in der Welt - aus demselben Grund wie
 * bei den Namensschildern der Mobs: Der Glow-Nacheffekt uebermalt Welttext, das Bild
 * liegt sicher davor.
 */
public final class LootshareRenderer {

    /** Wie weit ein Text noch gezeichnet wird, in Bloecken */
    private static final double TEXT_RANGE = 80.0;
    /** Der Kreis erst in der Naehe - weit weg ist er ein Ring ohne Aussage */
    private static final double CIRCLE_SHOW_RANGE = 50.0;
    private static final float CIRCLE_RADIUS = 30.0f;
    private static final float CIRCLE_WIDTH = 2.0f;
    private static final int MISSING_ARGB = 0xE6FF5555;
    private static final int READY_ARGB = 0xE655FFFF;
    private static final double REFERENCE_GUI_SCALE = 4.0;

    private LootshareRenderer() {
    }

    private static ModConfig.LootshareCategory cfg() {
        return ModConfig.INSTANCE.fishing.lootshare;
    }

    // ------------------------------------------------------------------ Bild

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, float partial) {
        if (!cfg().enabled || client.level == null || client.player == null) return;

        if (cfg().showLabel) {
            for (Target t : LootshareTracker.targets()) {
                if (t.nameplate() == null || t.nameplate().isRemoved()) continue;
                Vec3 above = t.nameplate().getPosition(partial).add(0, 0.7, 0);
                draw(graphics, client, above, label(t));
            }
        }
        if (cfg().checkmarks && !LootshareTracker.markedPlayers().isEmpty()) {
            for (String name : LootshareTracker.markedPlayers()) {
                for (Player player : client.level.players()) {
                    if (player == client.player || !player.getGameProfile().name().equalsIgnoreCase(name)) continue;
                    // Ueber dem Namensschild des Spielers, nicht davor
                    Vec3 above = player.getPosition(partial).add(0, player.getBbHeight() + 0.85, 0);
                    draw(graphics, client, above, "§b§l✓");
                }
            }
        }
    }

    /** "Lootshare 34%" - rot und durchgestrichen, solange es nicht reicht, gruen mit Haeckchen danach */
    private static String label(Target t) {
        Double percent = t.percent();
        String anteil = percent == null ? "" : " " + LootshareHud.percent(percent);
        return t.eligible()
                ? "§a§lLootshare ✓" + anteil
                : "§c§mLootshare§r§c" + anteil;
    }

    private static void draw(GuiGraphicsExtractor graphics, Minecraft client, Vec3 world, String text) {
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        double distanceSq = cameraPos.distanceToSqr(world);
        // Gleiche Position wie die Kamera zerlegt die Projektion, weit weg ist es Rauschen
        if (distanceSq < 1.0E-4 || distanceSq > TEXT_RANGE * TEXT_RANGE) return;

        Vec3 ndc = client.gameRenderer.projectPointToScreen(world);
        // Hinter der Kamera spiegelt die Projektion das Ziel in den Bildschirm
        if (ndc.z > 1.0) return;

        float x = (float) (ndc.x * 0.5 + 0.5) * client.getWindow().getGuiScaledWidth();
        float y = (float) (0.5 - ndc.y * 0.5) * client.getWindow().getGuiScaledHeight();
        // Unabhaengig von der GUI-Skalierung gleich gross - wie bei den Namensschildern
        float scale = (float) (REFERENCE_GUI_SCALE / client.getWindow().getGuiScale()) * 0.8f;

        Font font = client.font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, -font.lineHeight / 2, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    // ------------------------------------------------------------------ Kreis

    public static void emitGizmos(Minecraft client) {
        if (client.player == null || !cfg().enabled || !cfg().showCircle) return;
        for (Target t : LootshareTracker.targets()) {
            Vec3 pos = t.position();
            if (pos == null || client.player.position().distanceTo(pos) > CIRCLE_SHOW_RANGE) continue;
            // Knapp ueber dem Boden, damit er nicht in ihm flackert
            Vec3 centre = new Vec3(pos.x, pos.y + 0.05, pos.z);
            Gizmos.circle(centre, CIRCLE_RADIUS,
                    GizmoStyle.stroke(t.eligible() ? READY_ARGB : MISSING_ARGB, CIRCLE_WIDTH));
        }
    }
}
