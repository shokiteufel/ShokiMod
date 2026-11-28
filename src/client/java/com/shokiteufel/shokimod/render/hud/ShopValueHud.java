package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.ShopValues;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.util.ShopValue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Kasten zu NPC Shop Values: neben dem Fenster eines Haendlers eine Liste je Waehrung, die besten Stuecke oben (siehe {@link ShopValue}, {@link ShopValues}).
 *
 * Eigene Funktion, keine der 26.3-Umstellung. Er steht - je nach Einstellung "Position" - dicht links neben dem Fenster (rechter Rand des Kastens am linken Rand des
 * Fensters, oben bei der Oberkante), egal wie gross die GUI ist, oder dort, wo man ihn im HUD-Editor ({@code /shoki hud}) hingesetzt hat. Im HUD-Editor steht er immer,
 * mit einem Beispiel, damit er sich setzen laesst.
 */
public final class ShopValueHud {

    /** Der Abstand zum Fenster */
    private static final int GAP = 4;
    private static final int MARGIN = 2;

    private ShopValueHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.inventory.shopValue.enabled) return false;
        return ShopValues.active() || HudEditorScreen.isOpen();
    }

    /** Das Beispiel im HUD-Editor */
    static List<String> sample(boolean bold) {
        ShopValue.Currency copper = ShopValue.Currency.COPPER;
        return List.of(
                ShopValue.heading(copper),
                ShopValue.line(copper, new ShopValue.Row("Mutant Nether Wart", "§9", 1, 1500, 420), 1, bold),
                ShopValue.line(copper, new ShopValue.Row("Enchanted Seeds", "§a", 16, 300, 310), 2, bold),
                ShopValue.line(copper, new ShopValue.Row("Compost", "§a", 1, 120, 95), 3, bold));
    }

    /** Die Zeilen zu den Listen des offenen Fensters: je Waehrung eine Ueberschrift und die Stuecke, dazwischen eine Leerzeile */
    static List<String> lines(List<ShopValue.Section> sections, boolean bold) {
        List<String> out = new ArrayList<>();
        for (ShopValue.Section section : sections) {
            if (!out.isEmpty()) out.add("");
            out.add(ShopValue.heading(section.currency()));
            if (section.rows().isEmpty()) {
                out.add(ShopValue.NOTHING);
                continue;
            }
            int rank = 1;
            for (ShopValue.Row row : section.rows()) out.add(ShopValue.line(section.currency(), row, rank++, bold));
        }
        return out;
    }

    public static HudPanel build() {
        boolean bold = HudNumbers.bold();
        List<ShopValue.Section> sections = ShopValues.sections();
        List<String> lines = !sections.isEmpty() ? lines(sections, bold) : HudEditorScreen.isOpen() ? sample(bold) : List.of();
        HudPanel panel = new HudPanel();
        for (String line : lines) {
            if (line.isEmpty()) panel.blank();
            else panel.line(line, HudColours.WHITE);
        }
        return panel;
    }

    // ------------------------------------------------------------------ Lage

    /** Steht der Kasten gerade neben dem Fenster, statt an seinem gesetzten Platz? */
    private static AbstractContainerScreen<?> windowToAnchor() {
        if (ModConfig.INSTANCE.inventory.shopValue.position != ModConfig.ShopValuePosition.LEFT_OF_WINDOW || HudEditorScreen.isOpen()) return null;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> container && ShopValues.active() ? container : null;
    }

    /** Die linke Kante des Kastens neben dem Fenster, oder null, wenn der gesetzte Platz gilt */
    public static Integer anchoredX(SafariHud.Panel panel) {
        AbstractContainerScreen<?> window = windowToAnchor();
        if (window == null) return null;
        int width = SafariHud.scaledWidth(panel, panel.build(), Minecraft.getInstance().font);
        return Math.max(MARGIN, ((AbstractContainerScreenAccessor) window).shokimod$leftPos() - width - GAP);
    }

    /** Die obere Kante des Kastens neben dem Fenster (gleich der des Fensters), oder null, wenn der gesetzte Platz gilt */
    public static Integer anchoredY() {
        AbstractContainerScreen<?> window = windowToAnchor();
        if (window == null) return null;
        return Math.max(MARGIN, ((AbstractContainerScreenAccessor) window).shokimod$topPos());
    }
}
