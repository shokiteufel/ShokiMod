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
import java.util.function.UnaryOperator;

/**
 * Der Kasten der Einkaufsliste der Besucher (Garden > Visitor Shopping List).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Visitor Shopping List"). Was in der Liste steht und wo sie steht, regelt {@link ShoppingListText}; hier ist der Kasten des
 * HUD-Editors ({@code /shoki hud}) dazu - im Editor immer, mit einem Beispiel, damit er sich setzen laesst. Er ist anklickbar, solange ein Fenster mit freiem Zeiger
 * offen ist, vor dem die Liste steht: das eigene Inventar, der Chat, das Fenster eines Besuchers und der Basar (bis 2.5.10 nur das Inventar - im Fenster des
 * Besuchers und im Basar, wo man auf die Gegenstaende wartet, ging der Klick ins Leere). Ein Klick auf den Namen eines Besuchers nimmt ihn aus der Summe
 * (durchgestrichen) und wieder hinein, ein Klick auf einen Gegenstand sucht ihn im Basar ({@code /bazaar <Gegenstand>}, die Suche geht gleich auf).
 */
public final class VisitorShoppingHud {

    /** Das Beispiel im HUD-Editor, solange kein Besucher da ist */
    static final List<String> SAMPLE = sample(UnaryOperator.identity());

    /** Das Beispiel; {@code number} formt jede Zahl (Menge, Preis) fuer die Anzeige - schlicht oder fett */
    static List<String> sample(UnaryOperator<String> number) {
        return List.of(
                "§7Visitor Shopping List: §7(§6" + number.apply("125k") + "§7)",
                " §7- §fEnchanted Carrot §ex" + number.apply("160") + " §7(§6" + number.apply("100k") + "§7)",
                " §7- §fEnchanted Cocoa Bean §ex" + number.apply("48") + " §7(§6" + number.apply("25k") + "§7)",
                "",
                "§e2 §7New Visitors:",
                " §7- §fSpaceman",
                " §7- §fJacob");
    }

    private VisitorShoppingHud() {
    }

    /** Soll der Kasten jetzt gezeichnet werden? */
    public static boolean shownNow() {
        if (!ModConfig.INSTANCE.garden.shoppingList.enabled) return false;
        return HudEditorScreen.isOpen() || VisitorWindow.listShownHere();
    }

    /** Die Liste, wie sie jetzt aussieht */
    static ShoppingListText.Built built() {
        boolean showPrice = ModConfig.INSTANCE.garden.shoppingList.showPrice;
        // HUD > Bold numbers: Menge und Preise fett, nur in dieser Anzeige. ItemValue.format selbst bleibt schlicht (es geht auch in Chat und
        // Befehle); der Fettdruck wird hier um das Ergebnis gelegt. "§l" nach dem Farbcode: "§6" + "§l100k", das naechste "§7" beendet es.
        if (!HudNumbers.bold()) {
            return ShoppingListText.build(VisitorBook.INSTANCE, showPrice, VisitorPrices::buyPrice, ItemValue::format);
        }
        return ShoppingListText.build(VisitorBook.INSTANCE, showPrice, VisitorPrices::buyPrice,
                coins -> HudNumbers.bolden(ItemValue.format(coins)), HudNumbers::bolden);
    }

    public static HudPanel build() {
        ShoppingListText.Built built = built();
        List<String> lines = built.isEmpty() && HudEditorScreen.isOpen()
                ? (HudNumbers.bold() ? sample(HudNumbers::bolden) : SAMPLE)
                : built.lines();
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
