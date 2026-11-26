package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.BazaarHelpers;

/**
 * Der Kasten "Best sell method": was man verliert, wenn man das, was man traegt, sofort verkauft statt ueber ein Angebot.
 *
 * Herkunft der Idee: SkyHanni ("Best Sell Method"). Er steht nur im Fenster eines Basar-Produkts und nur, wenn man davon etwas im Inventar hat; im
 * HUD-Editor ({@code /shoki hud}) immer, mit einem Beispiel, damit er sich setzen laesst.
 */
public final class BazaarSellHud {

    static final String SAMPLE = "§fEnchanted Coal§7 sell difference: §6120k coins";

    private BazaarSellHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.inventory.bazaar.bestSellMethod) return false;
        return BazaarHelpers.sellDifference() != null || HudEditorScreen.isOpen();
    }

    public static HudPanel build() {
        String text = BazaarHelpers.sellDifference();
        if (text == null) text = SAMPLE;
        HudPanel panel = new HudPanel();
        panel.line(text, HudColours.WHITE);
        return panel;
    }
}
