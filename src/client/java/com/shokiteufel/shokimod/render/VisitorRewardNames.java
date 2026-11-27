package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.garden.PlotGrid;
import com.shokiteufel.shokimod.garden.ShoppingListText;
import com.shokiteufel.shokimod.garden.VisitorBook;
import com.shokiteufel.shokimod.garden.VisitorReward;
import com.shokiteufel.shokimod.util.TextCache;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Show over Name" der Belohnungswarnung: Die Belohnung steht ueber dem Namen des Besuchers in der Welt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Show over Name", {@code VisitorRewardWarning} und {@code GardenVisitorFeatures}). Von dort stammt die Regel: Der Name eines
 * Besuchers schwebt auf einem unsichtbaren Ruestungsstaender (der Name ohne Farbcodes ist der des Besuchers) im Bereich der Scheune; der Text steht nur, solange man auf dem
 * Plot der Scheune ist, und nur bei Besuchern, in deren Angebot eine gewaehlte Belohnung steht. Die Umsetzung ist eigene Arbeit: Java; die Staender werden alle zehn Ticks
 * gesucht, der Text wird wie die Namensschilder der Mobs ({@link BossNameplateRenderer}) als HUD gezeichnet - ein Punkt ueber dem Staender wird auf den Bildschirm
 * gerechnet -, damit ihn der Umriss-Nacheffekt nicht ueberdeckt. Wie hoch der Text ueber dem Namen steht, ist geschaetzt und im Spiel nicht geprueft.
 */
public final class VisitorRewardNames {

    /** Der Bereich der Scheune: der mittlere Plot, 96 mal 96 Bloecke, ueber die ganze Hoehe */
    private static final AABB BARN = new AABB(-48.0, 0.0, -48.0, 48.0, 256.0, 48.0);
    private static final int SCAN_TICKS = 10;
    /** Wie weit ueber dem Ruestungsstaender der Text steht (die Hoehe des Staenders und der Abstand fuer den Namen darunter), in Bloecken */
    private static final double ABOVE_NAME = 0.9;
    /** Der Text ist etwas kleiner als der Name eines Mobs */
    private static final double TEXT_SIZE = 0.75;
    private static final double REFERENCE_GUI_SCALE = 4.0;
    private static final double NDC_BEHIND_CAMERA_Z = 1.0;

    private static final Map<ArmorStand, String> shown = new LinkedHashMap<>();
    private static int ticks = 0;
    private static boolean failedOnce = false;

    private VisitorRewardNames() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(VisitorRewardNames::tick);
    }

    /** Der Text zu einem Besucher: seine gewaehlten Belohnungen, durch Komma getrennt, in Gold - oder null, wenn er keine hat oder nicht mehr zaehlt */
    static String textFor(VisitorBook.Entry entry) {
        if (entry == null || !entry.active() || entry.warned().isEmpty()) return null;
        List<String> names = new ArrayList<>();
        for (VisitorReward reward : entry.warned()) names.add(reward.displayName());
        return "§6" + String.join("§7, §6", names);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.update263() || !ModConfig.INSTANCE.garden.rewardWarning.showOverName || client.player == null || client.level == null
                || !GameState.Server.isSkyblock() || !ShoppingListText.isGarden(GameState.Server.map)
                || !PlotGrid.onBarnPlot(client.player.getX(), client.player.getZ())) {
            shown.clear();
            ticks = SCAN_TICKS;
            return;
        }
        if (++ticks < SCAN_TICKS) return;
        ticks = 0;
        try {
            find(client);
        } catch (RuntimeException e) {
            shown.clear();
            warnOnce(e);
        }
    }

    /** Die Ruestungsstaender mit dem Namen eines Besuchers, der eine gewaehlte Belohnung bietet */
    private static void find(Minecraft client) {
        shown.clear();
        Map<String, String> texts = new LinkedHashMap<>();
        for (VisitorBook.Entry entry : VisitorBook.INSTANCE.entries()) {
            String text = textFor(entry);
            if (text != null) texts.put(entry.name(), text);
        }
        if (texts.isEmpty()) return;
        for (ArmorStand stand : client.level.getEntitiesOfClass(ArmorStand.class, BARN)) {
            Component name = stand.getCustomName();
            if (name == null) continue;
            String text = texts.get(name.getString().replaceAll("§.", "").trim());
            if (text != null) shown.put(stand, text);
        }
    }

    /** Vom Ende des Bildes gerufen (HudRenderer), neben den Namensschildern der Mobs */
    public static void render(GuiGraphicsExtractor graphics, Minecraft client, float partialTicks) {
        if (shown.isEmpty()) return;
        try {
            draw(graphics, client, partialTicks);
        } catch (RuntimeException e) {
            shown.clear();
            warnOnce(e);
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, Minecraft client, float partialTicks) {
        Font font = client.font;
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        float screenWidth = client.getWindow().getGuiScaledWidth();
        float screenHeight = client.getWindow().getGuiScaledHeight();
        float textScale = (float) (REFERENCE_GUI_SCALE / client.getWindow().getGuiScale() * TEXT_SIZE);

        for (Map.Entry<ArmorStand, String> entry : shown.entrySet()) {
            ArmorStand stand = entry.getKey();
            if (stand.isRemoved()) continue;
            Vec3 above = stand.getPosition(partialTicks).add(0.0, stand.getBbHeight() + ABOVE_NAME, 0.0);
            if (cameraPos.distanceToSqr(above) < 1.0E-4) continue;
            Vec3 ndc = client.gameRenderer.projectPointToScreen(above);
            if (ndc.z > NDC_BEHIND_CAMERA_Z) continue;
            float centerX = (float) (ndc.x * 0.5 + 0.5) * screenWidth;
            float centerY = (float) (0.5 - ndc.y * 0.5) * screenHeight;

            String text = entry.getValue();
            graphics.pose().pushMatrix();
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(textScale, textScale);
            graphics.text(font, text, -TextCache.breite(font, text) / 2, -font.lineHeight, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();
        }
    }

    private static void warnOnce(RuntimeException e) {
        if (failedOnce) return;
        failedOnce = true;
        ShokiMod.LOGGER.warn("[Visitors] Show over Name failed: {}", e.toString());
    }
}
