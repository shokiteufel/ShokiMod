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

    /**
     * Der Abstand zum Fenster: Am linken Rand eines Fensters sitzen oft Knoepfe anderer Mods (Schnellzugriffe, 32 Punkte breit, ein paar Punkte vom Fenster entfernt); bei
     * einem Abstand von vier Punkten lagen sie ueber dem Ende der Zeilen und schnitten Zahlen ab (Bild des Users, Fear Mongerer, 28.11.).
     */
    private static final int GAP = 42;
    private static final int MARGIN = 2;
    /** Kleiner als das macht man den Kasten nicht, um ihn neben das Fenster zu bekommen */
    private static final float MIN_FIT = 0.35f;

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

    /** Die Zeilen des Kastens und, gleich lang, der Platz im Fenster zu jeder (-1: Ueberschrift, Leerzeile, Hinweis) */
    record Built(List<String> lines, List<Integer> slots) {
    }

    /** Die Zeilen zu den Listen des offenen Fensters: je Waehrung eine Ueberschrift und die Stuecke, dazwischen eine Leerzeile - mit dem Platz jedes Stuecks im Fenster */
    static Built built(List<ShopValue.Section> sections, boolean bold) {
        List<String> out = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (ShopValue.Section section : sections) {
            if (!out.isEmpty()) {
                out.add("");
                slots.add(-1);
            }
            out.add(ShopValue.heading(section.currency()));
            slots.add(-1);
            if (section.rows().isEmpty()) {
                out.add(ShopValue.NOTHING);
                slots.add(-1);
                continue;
            }
            int rank = 1;
            for (ShopValue.Row row : section.rows()) {
                out.add(ShopValue.line(section.currency(), row, rank++, bold));
                slots.add(row.slot());
            }
        }
        return new Built(out, slots);
    }

    static List<String> lines(List<ShopValue.Section> sections, boolean bold) {
        return built(sections, bold).lines();
    }

    /** Die Plaetze zu den Zeilen des Kastens, wie er zuletzt gebaut wurde - ueber sie findet der Zeiger sein Stueck */
    private static volatile List<Integer> slotAt = List.of();

    public static HudPanel build() {
        boolean bold = HudNumbers.bold();
        List<ShopValue.Section> sections = ShopValues.sections();
        List<String> lines;
        if (!sections.isEmpty()) {
            Built built = built(sections, bold);
            lines = built.lines();
            slotAt = built.slots();
        } else {
            lines = HudEditorScreen.isOpen() ? sample(bold) : List.of();
            slotAt = List.of();
        }
        HudPanel panel = new HudPanel();
        for (String line : lines) {
            if (line.isEmpty()) panel.blank();
            else panel.line(line, HudColours.WHITE);
        }
        return panel;
    }

    // ------------------------------------------------------------------ Zeiger

    /**
     * In jedem Bild gerufen (nach dem Zeichnen der Kaesten): Liegt der Zeiger auf der Zeile eines Stuecks, merkt sich {@link ShopValues}, welches Platz es hat; im naechsten
     * Bild leuchtet es im Fenster auf. Ein Bild Verzug sieht man nicht. Ohne Fenster eines Haendlers kostet der Aufruf eine Abfrage.
     */
    public static void updateHover() {
        if (!ShopValues.active()) {
            ShopValues.setHoverSlot(-1);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        SafariHud.Panel panel = SafariHud.Panel.SHOP_VALUE;
        if (client.gui.screen() == null || client.getWindow() == null || !panel.visible() || !panel.showsHere() || !panel.showsNow()) {
            ShopValues.setHoverSlot(-1);
            return;
        }
        double mouseX = client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth();
        double mouseY = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight();
        HudPanel content = panel.build();
        int row = content.rowAt(client.font, SafariHud.originX(panel), SafariHud.originY(panel), effectiveScale(panel), mouseX, mouseY);
        List<Integer> slots = slotAt;
        ShopValues.setHoverSlot(row >= 0 && row < slots.size() ? slots.get(row) : -1);
    }

    // ------------------------------------------------------------------ Lage

    /** Steht der Kasten gerade neben dem Fenster, statt an seinem gesetzten Platz? */
    private static AbstractContainerScreen<?> windowToAnchor() {
        if (ModConfig.INSTANCE.inventory.shopValue.position != ModConfig.ShopValuePosition.LEFT_OF_WINDOW || HudEditorScreen.isOpen()) return null;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> container && ShopValues.active() ? container : null;
    }

    /**
     * Rein: Die Groesse des Kastens bei {@code natural} Punkten Breite (mit der gewaehlten Groesse) und {@code room} Punkten Platz links vom Fenster. Passt er, bleibt es die
     * gewaehlte Groesse; sonst wird er so viel kleiner, dass er passt - aber nicht unter {@link #MIN_FIT} (und nie groesser als gewaehlt).
     */
    static float fitted(float chosen, int natural, int room) {
        if (natural <= 0 || natural <= room) return chosen;
        float fit = chosen * Math.max(room, 0) / natural;
        return Math.max(Math.min(chosen, MIN_FIT), fit);
    }

    /**
     * Die Groesse, in der der Kasten gezeichnet wird: die gewaehlte; steht er neben dem Fenster und ist zu breit fuer den Platz links davon (mit den laengeren Zeilen und
     * einer grossen GUI geschieht das), kleiner - so liegt er nie auf dem Fenster.
     */
    public static float effectiveScale(SafariHud.Panel panel) {
        float chosen = panel.scale();
        AbstractContainerScreen<?> window = windowToAnchor();
        if (window == null) return chosen;
        int natural = Math.round(panel.build().width(Minecraft.getInstance().font) * chosen);
        int room = ((AbstractContainerScreenAccessor) window).shokimod$leftPos() - GAP - MARGIN;
        return fitted(chosen, natural, room);
    }

    /** Die linke Kante des Kastens neben dem Fenster, oder null, wenn der gesetzte Platz gilt */
    public static Integer anchoredX(SafariHud.Panel panel) {
        AbstractContainerScreen<?> window = windowToAnchor();
        if (window == null) return null;
        int width = Math.round(panel.build().width(Minecraft.getInstance().font) * effectiveScale(panel));
        return Math.max(MARGIN, ((AbstractContainerScreenAccessor) window).shokimod$leftPos() - width - GAP);
    }

    /** Die obere Kante des Kastens neben dem Fenster (gleich der des Fensters), oder null, wenn der gesetzte Platz gilt */
    public static Integer anchoredY() {
        AbstractContainerScreen<?> window = windowToAnchor();
        if (window == null) return null;
        return Math.max(MARGIN, ((AbstractContainerScreenAccessor) window).shokimod$topPos());
    }
}
