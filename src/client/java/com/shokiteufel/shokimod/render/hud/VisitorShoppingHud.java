package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.garden.ShoppingListText;
import com.shokiteufel.shokimod.garden.VisitorBook;
import com.shokiteufel.shokimod.gui.HudEditorScreen;
import com.shokiteufel.shokimod.handler.VisitorPrices;
import com.shokiteufel.shokimod.handler.VisitorWindow;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Der Kasten der Einkaufsliste der Besucher (Garden > Visitor Shopping List).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Visitor Shopping List"). Was in der Liste steht und wo sie steht, regelt {@link ShoppingListText}; hier ist der Kasten des
 * HUD-Editors ({@code /shoki hud}) dazu - im Editor immer, mit einem Beispiel, damit er sich setzen laesst. Bei offenem Inventar ist er anklickbar: Ein Klick auf den Namen
 * eines Besuchers nimmt ihn aus der Summe (durchgestrichen) und wieder hinein, ein Klick auf einen Gegenstand sucht ihn im Basar.
 */
public final class VisitorShoppingHud {

    /** Das Beispiel im HUD-Editor, solange kein Besucher da ist */
    static final List<String> SAMPLE = List.of(
            "§7Visitor Shopping List: §7(§6125k§7)",
            " §7- §fEnchanted Carrot §ex160 §7(§6100k§7)",
            " §7- §fEnchanted Cocoa Bean §ex48 §7(§625k§7)",
            "",
            "§e2 §7New Visitors:",
            " §7- §fSpaceman",
            " §7- §fJacob");

    private VisitorShoppingHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.garden.shoppingList.enabled) return false;
        return HudEditorScreen.isOpen() || VisitorWindow.listShownHere();
    }

    /** Die Liste, wie sie jetzt aussieht */
    static ShoppingListText.Built built() {
        return ShoppingListText.build(VisitorBook.INSTANCE, ModConfig.INSTANCE.garden.shoppingList.showPrice, VisitorPrices::buyPrice, ItemValue::format);
    }

    public static HudPanel build() {
        ShoppingListText.Built built = built();
        List<String> lines = built.isEmpty() && HudEditorScreen.isOpen() ? SAMPLE : built.lines();
        HudPanel panel = new HudPanel();
        for (String line : lines) {
            if (line.isEmpty()) panel.blank();
            else panel.line(line, HudColours.WHITE);
        }
        return panel;
    }

    /** Ein Klick bei offenem Inventar: auf einem Namen oder einem Gegenstand der Liste? Dann handeln und den Klick schlucken */
    public static boolean handleClick(double mouseX, double mouseY) {
        SafariHud.Panel panel = SafariHud.Panel.VISITOR_LIST;
        if (HudEditorScreen.isOpen() || !panel.visible() || !panel.showsHere() || !panel.showsNow()) return false;
        Minecraft client = Minecraft.getInstance();
        ShoppingListText.Built built = built();
        if (built.isEmpty()) return false;
        HudPanel content = panel.build();
        int row = content.rowAt(client.font, SafariHud.originX(panel), SafariHud.originY(panel), panel.scale(), mouseX, mouseY);
        if (row < 0 || row >= built.lines().size()) return false;

        String visitor = built.visitorAt().get(row);
        if (visitor != null) {
            VisitorBook.INSTANCE.toggleIgnored(visitor);
            SafariHud.invalidate(panel);
            return true;
        }
        String item = built.itemAt().get(row);
        if (item != null && client.getConnection() != null) {
            client.getConnection().sendCommand("bazaar " + item);
            return true;
        }
        return false;
    }
}
