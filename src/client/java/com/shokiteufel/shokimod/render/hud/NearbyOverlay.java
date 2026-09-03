package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.BannerDesignScreen;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.HuntingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Die Kaesten ueber einem offenen Fenster.
 *
 * Sobald ein Fenster offen ist, legt Minecraft einen dunklen Schleier ueber alles,
 * was vorher gezeichnet wurde - Welt und HUD gleichermassen. Ein Kasten aus dem
 * normalen HUD-Durchgang liegt darunter und wirkt dann grau, obwohl seine Schrift
 * weiss ist. Deshalb werden hier alle Kaesten noch einmal darueber gezeichnet.
 *
 * Bewusst eine gewoehnliche Klasse und kein Mixin: die beiden Mixins - einer fuers
 * Zeichnen, einer fuer den Klick - rufen von hier aus auf. Mixin-Klassen sollten
 * sich nicht gegenseitig aufrufen, ihre Inhalte werden ja in die Zielklassen
 * verschoben.
 */
public final class NearbyOverlay {

    private NearbyOverlay() {
    }

    public static boolean shown() {
        return ModConfig.INSTANCE.mobVisuals.masterEnabled && ModConfig.INSTANCE.mobVisuals.showNearbyHud
                && !(Minecraft.getInstance().screen instanceof HudEditorScreen);
    }

    /**
     * Ist ueberhaupt ein Kasten eingeschaltet?
     *
     * Diese Frage steht vor jedem Bild mit offenem Fenster. Sind alle Kaesten aus,
     * endet der Durchgang hier, ohne die Zeichenroutine anzustossen.
     */
    public static boolean anyPanel() {
        for (SafariHud.Panel panel : SafariHud.panels()) {
            if (panel.visible()) return true;
        }
        return false;
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        // Im Einrichtungsfenster nicht: dort werden die Kaesten schon selbst gezeichnet,
        // an der Stelle, an die man sie gerade zieht
        if (Minecraft.getInstance().screen instanceof HudEditorScreen
                || Minecraft.getInstance().screen instanceof BannerDesignScreen) return;
        // Dieselbe Auswahl wie beim Spielen - was eingeschaltet ist und hierher gehoert
        SafariHud.render(graphics);
    }

    /** Gibt es bei offenem Fenster ueberhaupt etwas Anklickbares? */
    public static boolean anyClickable() {
        return shown() || SafariHud.Panel.HUNTING.visible();
    }

    /** Sitzt der Zeiger auf einer Zeile? Dann handeln und den Klick schlucken */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (Minecraft.getInstance().screen instanceof HudEditorScreen) return false;

        if (shown()) {
            SafariHud.Panel panel = SafariHud.Panel.NEARBY;
            HudPanel content = NearbyHud.build();
            int row = content.rowAt(Minecraft.getInstance().font,
                    SafariHud.originX(panel), SafariHud.originY(panel), panel.scale(), mouseX, mouseY);
            if (row >= 0 && NearbyHud.click(row)) return true;
        }

        if (SafariHud.Panel.HUNTING.visible()) {
            SafariHud.Panel panel = SafariHud.Panel.HUNTING;
            // Derselbe gepufferte Kasten, der gerade gezeichnet wird - sonst passen die Zeilen nicht
            HudPanel content = panel.build();
            int row = content.rowAt(Minecraft.getInstance().font,
                    SafariHud.originX(panel), SafariHud.originY(panel), panel.scale(), mouseX, mouseY);
            // Die Reset-Zeile ist immer die letzte
            if (row >= 0 && row == content.rowCount() - 1) {
                HuntingTracker.reset();
                SafariHud.invalidate(panel);
                return true;
            }
        }
        return false;
    }
}
