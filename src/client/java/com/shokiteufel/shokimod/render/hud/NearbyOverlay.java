package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
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
        return ModConfig.INSTANCE.mobVisuals.showNearbyHud
                && !(Minecraft.getInstance().screen instanceof HudEditorScreen);
    }

    public static void draw(GuiGraphicsExtractor graphics) {
        // Im Einrichtungsfenster nicht: dort werden die Kaesten schon selbst gezeichnet,
        // an der Stelle, an die man sie gerade zieht
        if (Minecraft.getInstance().screen instanceof HudEditorScreen) return;
        // Dieselbe Auswahl wie beim Spielen - was eingeschaltet ist und hierher gehoert
        SafariHud.render(graphics);
    }

    /** Sitzt der Zeiger auf einer Zeile? Dann handeln und den Klick schlucken */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (!shown()) return false;

        SafariHud.Panel panel = SafariHud.Panel.NEARBY;
        HudPanel content = NearbyHud.build();
        int row = content.rowAt(Minecraft.getInstance().font,
                SafariHud.originX(panel), SafariHud.originY(panel), panel.scale(), mouseX, mouseY);
        return row >= 0 && NearbyHud.click(row);
    }
}
