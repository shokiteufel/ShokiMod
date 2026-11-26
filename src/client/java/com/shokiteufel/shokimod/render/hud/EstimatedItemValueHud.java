package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.EstimatedItemValue;

import java.util.List;

/**
 * Der Kasten zu Estimated Item Value: der Wert des Gegenstands unter der Maus, Posten fuer Posten.
 *
 * Herkunft der Idee: SkyHanni ("Estimated Item Value"). Er steht, solange die Maus auf einem Gegenstand liegt, der mehr wert ist als sein Grundpreis, und je
 * nach Einstellung immer oder nur mit der Taste; im HUD-Editor ({@code /shoki hud}) immer, mit einem Beispiel, damit er sich setzen laesst.
 */
public final class EstimatedItemValueHud {

    static final List<String> SAMPLE = List.of(
            "§aEstimated Item Value:",
            "§7Base item: §6Hyperion §7(§6850M§7)",
            "§7Reforge: §9Withered",
            " §7Stone: §6Wither Blood §7(§6300k§7)",
            "§7Stars: §e5§7/§e5 §7(§635M§7)",
            "§7Enchantments: §6120M",
            " §9Sharpness VI §7(§6150k§7)",
            "§aTotal: §6§l1.1B coins");

    private EstimatedItemValueHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.inventory.itemValue.enabled) return false;
        return EstimatedItemValue.visible() || HudEditorScreen.isOpen();
    }

    public static HudPanel build() {
        List<String> lines = EstimatedItemValue.display();
        if (lines.isEmpty() && HudEditorScreen.isOpen()) lines = SAMPLE;
        HudPanel panel = new HudPanel();
        for (String line : lines) panel.line(line, HudColours.WHITE);
        return panel;
    }
}
