package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.garden.PlotGrid;
import com.shokiteufel.shokimod.garden.PlotsState;
import com.shokiteufel.shokimod.garden.ShoppingListText;
import com.shokiteufel.shokimod.gui.HudEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Set;

/**
 * Der Kasten mit der Karte der Plots (Garden > Garden Plots Widget).
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, "Garden Plots Widget"). Was die Karte zeigt und wie sie geklickt wird, regelt {@link PlotGrid}; hier ist der Kasten des
 * HUD-Editors ({@code /shoki hud}) dazu, mit einer Ueberschrift und fuenf Zeilen. Er steht im Garden, im Editor immer (mit ein paar befallenen Plots als Beispiel).
 * Bei offenem Inventar schickt ein Klick auf eine Nummer {@code /plottp}.
 */
public final class GardenPlotsHud {

    /** Welche Plots im HUD-Editor als befallen gezeigt werden, solange man nicht im Garden steht */
    static final Set<Integer> SAMPLE_INFESTED = Set.of(4, 12, 18);
    /** Die Zeile, in der die Ueberschrift steht: die Zeilen der Karte folgen ab der naechsten */
    static final int HEADER_ROWS = 1;

    private GardenPlotsHud() {
    }

    private static boolean inGarden() {
        return ShoppingListText.isGarden(GameState.Server.map);
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.garden.plots.enabled) return false;
        return HudEditorScreen.isOpen() || inGarden();
    }

    /** Auf welchem Plot steht der Spieler, oder -1 */
    static int currentPlot() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return -1;
        return PlotGrid.plotAt(client.player.getX(), client.player.getZ());
    }

    public static HudPanel build() {
        boolean sample = HudEditorScreen.isOpen() && !inGarden();
        Set<Integer> infested = sample ? SAMPLE_INFESTED : PlotsState.infested();
        HudPanel panel = new HudPanel();
        panel.title("Garden Plots", HudColours.GOLD);
        for (String row : PlotGrid.rows(infested, currentPlot())) {
            panel.line(row, HudColours.GRAY);
        }
        if (!infested.isEmpty()) panel.line("§c" + infested.size() + " infested", HudColours.RED);
        return panel;
    }

    /** Ein Klick bei offenem Inventar auf eine Nummer der Karte: dorthin teleportieren und den Klick schlucken */
    public static boolean handleClick(double mouseX, double mouseY) {
        SafariHud.Panel panel = SafariHud.Panel.GARDEN_PLOTS;
        if (HudEditorScreen.isOpen() || !panel.visible() || !panel.showsHere() || !panel.showsNow() || !inGarden()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return false;

        HudPanel content = panel.build();
        int row = content.rowAt(client.font, SafariHud.originX(panel), SafariHud.originY(panel), panel.scale(), mouseX, mouseY) - HEADER_ROWS;
        if (row < 0 || row >= PlotGrid.SIZE) return false;
        double localX = (mouseX - SafariHud.originX(panel)) / panel.scale() - HudPanel.padding();
        int column = PlotGrid.columnAt(localX, client.font.width("00"), client.font.width(" "));
        int plot = PlotGrid.plotInCell(row, column);
        if (plot < 0) return false;

        client.getConnection().sendCommand(PlotGrid.teleportCommand(plot));
        if (ModConfig.INSTANCE.garden.plots.closeScreenOnPlotClick) {
            Screen screen = client.gui.screen();
            if (screen != null) screen.onClose();
        }
        return true;
    }
}
