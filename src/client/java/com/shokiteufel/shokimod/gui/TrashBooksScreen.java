package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.BookMark;
import com.shokiteufel.shokimod.handler.TrashBooks;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Welche verzauberten Buecher markiert werden - eines je Zeile.
 *
 * Die Liste sind die Buecher, die schon im eigenen Inventar lagen, und "Corruption I" von Feesh. Wer eines
 * markieren will, das noch nicht dabei war, tippt den Namen unten ein (genau wie im Spiel, mit Stufe: "Frail VI").
 * Aufbau und Bedienung wie bei den anderen Auswahlfenstern: Suchfeld, Seiten, "Done".
 */
public class TrashBooksScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 176;
    private static final int RED_WIDTH = 42;
    private static final int GREEN_WIDTH = 50;
    private static final int FORGET_WIDTH = 56;
    private static final int LIST_TOP = 74;

    private final Screen parent;
    private static String filter = "";
    private static boolean onlyMarked = false;
    private static String typed = "";
    private static int page = 0;

    /** So oft wird das Inventar hoechstens nach Buechern abgesucht (Stueck holen, Beschreibung lesen) - nicht in jedem Bild */
    private static final long BUECHER_NANOS = 250_000_000L;

    /**
     * Die gefilterte Liste, einmal gebaut und gehalten. TrashBooks.known() baut und sortiert bei jedem Aufruf eine neue Liste, dazu kamen
     * Suchtext klein schreiben und je Eintrag den Namen klein schreiben - und visible() steckt auch in pageCount(), das das Fenster mehrfach
     * je Bild fragt.
     *
     * Ihre Eingaben: Suchtext, "Marked/All" und die Markierungen aendern sich ueber die Knoepfe dieses Fensters, und jede dieser Aktionen endet
     * in {@link #rebuild()}, das {@link #init()} neu laufen laesst - dort wird die Liste verworfen. Die Liste der bekannten Buecher waechst aber
     * auch im Hintergrund: TrashBooks.tick merkt alle 20 Ticks neue Buecher aus dem Inventar (nur Hinzufuegen in dieselbe Tabelle). Das zeigt
     * sich an der Groesse der Tabelle (oder daran, dass es eine andere ist, wenn die Einstellungen neu geladen wurden), also wird die Liste
     * auch dann neu gebaut.
     */
    private List<String> sichtbar;
    private Map<String, BookMark> sichtbarQuelle;
    private int sichtbarGroesse = -1;
    /** Gezaehlt zusammen mit der Liste: rote, gruene und alle bekannten Buecher */
    private int rot;
    private int gruen;
    private int bekannt;

    /** Wie viele Buecher im Inventar liegen und wie viele davon lesbar sind - hoechstens alle 250 ms gelesen, nicht in jedem Bild */
    private int[] buecher = {0, 0};
    private long buecherGelesen;
    private boolean buecherDa;

    // Gemerkte Texte: gebaut nur, wenn sich ihre Quellen aendern, nicht in jedem Bild
    /** "In your inventory: ..." - neu gebaut nur, wenn sich eine der beiden Zahlen aendert */
    private final FensterText.Gemerkt inventar = new FensterText.Gemerkt();
    private int inventarFarbe = 0xFF888888;
    /** "2 red, 1 green of 14 books ..." - neu gebaut nur, wenn sich eine der drei Zahlen aendert */
    private final FensterText.Gemerkt zaehler = new FensterText.Gemerkt();
    /** "Nothing found for ..." - neu gebaut nur, wenn sich der Suchtext aendert */
    private final FensterText.Gemerkt nichtsGefunden = new FensterText.Gemerkt();

    public TrashBooksScreen(Screen parent) {
        super(Component.literal("Trash books"));
        this.parent = parent;
    }

    private List<String> visible() {
        Map<String, BookMark> marks = ModConfig.INSTANCE.fishing.books.marks;
        if (sichtbar == null || marks != sichtbarQuelle || marks.size() != sichtbarGroesse) listeBauen(marks);
        return sichtbar;
    }

    private void listeBauen(Map<String, BookMark> marks) {
        String suche = filter.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        List<String> alle = TrashBooks.known();
        for (String name : alle) {
            if (!suche.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(suche)) continue;
            if (onlyMarked && TrashBooks.markOf(name) == BookMark.NEUTRAL) continue;
            out.add(name);
        }
        sichtbar = out;
        sichtbarQuelle = marks;
        sichtbarGroesse = marks.size();
        bekannt = alle.size();
        rot = TrashBooks.count(BookMark.RED);
        gruen = TrashBooks.count(BookMark.GREEN);
    }

    /** Das Inventar nach Buechern absuchen - hoechstens alle 250 ms; dazwischen gilt das Ergebnis von vorhin */
    private void buecherLesen() {
        long jetzt = System.nanoTime();
        if (buecherDa && jetzt - buecherGelesen < BUECHER_NANOS) return;
        buecher = TrashBooks.inventoryBooks(Minecraft.getInstance());
        buecherGelesen = jetzt;
        buecherDa = true;
    }

    /**
     * Die zusammengesetzten Texte des Bildes: gebaut nur, wenn sich ihre Quellen (Zahlen, Suchtext) geaendert haben. Die Zahlen der Liste
     * (rot, gruen, bekannt) kommen mit der Liste aus visible().
     */
    private void texteAktualisieren() {
        boolean leer = visible().isEmpty();
        if (ModConfig.INSTANCE.fishing.books.enabled) {
            buecherLesen();
            int gesamt = buecher[0];
            int lesbar = buecher[1];
            long schluessel = gesamt * 1000L + lesbar;
            if (!inventar.gilt(schluessel)) {
                inventar.setze(schluessel, Component.literal("In your inventory: " + gesamt + " enchanted book"
                                + (gesamt == 1 ? "" : "s") + ", " + lesbar + " readable")
                        .withStyle(gesamt > lesbar ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
                inventarFarbe = gesamt > lesbar ? 0xFFFF5555 : 0xFF888888;
            }
        }
        long zahlen = (rot * 4096L + gruen) * 4096L + bekannt;
        if (!zaehler.gilt(zahlen)) {
            zaehler.setze(zahlen, Component.literal(
                            rot + " red, " + gruen + " green of " + bekannt
                                    + " books  ·  only slots of your own inventory are coloured")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (leer && bekannt > 0 && !nichtsGefunden.gilt(0L, filter)) {
            nichtsGefunden.setze(0L, filter, Component.literal("Nothing found for \"" + filter + "\"")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        // Was gerade im Inventar liegt, soll schon da stehen, wenn das Fenster aufgeht
        TrashBooks.scan(Minecraft.getInstance());
        // Jeder Neuaufbau heisst: Suche, Schalter oder eine Markierung haben sich geaendert - die Liste neu bauen lassen
        sichtbar = null;
        page = Math.clamp(page, 0, pageCount() - 1);

        List<String> liste = visible();
        int gridWidth = NAME_WIDTH + 4 + RED_WIDTH + 4 + GREEN_WIDTH + 4 + FORGET_WIDTH;
        int left = width / 2 - gridWidth / 2;

        EditBox search = new EditBox(font, left, 34, NAME_WIDTH, 20, Component.literal("Search"));
        search.setValue(filter);
        search.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(
                Component.literal(onlyMarked ? "Marked" : "All").withStyle(ChatFormatting.AQUA),
                button -> {
                    onlyMarked = !onlyMarked;
                    page = 0;
                    rebuild();
                }).bounds(left + NAME_WIDTH + 4, 34, RED_WIDTH + 4 + GREEN_WIDTH, 20).build());

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            String name = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            BookMark mark = TrashBooks.markOf(name);

            // Der Name zeigt, wie das Buch steht, und ein Klick darauf geht weiter: neutral, rot, gruen
            Button state = Button.builder(
                    Component.literal((mark == BookMark.NEUTRAL ? "☐ " : "☑ ") + name)
                            .withStyle(mark == BookMark.RED ? ChatFormatting.RED
                                    : mark == BookMark.GREEN ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                    button -> {
                        TrashBooks.cycle(name);
                        rebuild();
                    }).bounds(left, y, NAME_WIDTH, 20).build();
            state.setTooltip(Tooltip.create(Component.literal(switch (mark) {
                case RED -> "Red: a book to get rid of. Click for green.";
                case GREEN -> "Green: a good book, highlighted. Click for neutral.";
                default -> "Neutral: left alone. Click for red.";
            })));
            addRenderableWidget(state);

            Button red = Button.builder(Component.literal("Red")
                            .withStyle(mark == BookMark.RED ? ChatFormatting.RED : ChatFormatting.DARK_GRAY),
                    button -> {
                        TrashBooks.pick(name, BookMark.RED);
                        rebuild();
                    }).bounds(left + NAME_WIDTH + 4, y, RED_WIDTH, 20).build();
            red.setTooltip(Tooltip.create(Component.literal("Red background in your inventory: a book to get rid of.")));
            addRenderableWidget(red);

            Button green = Button.builder(Component.literal("Green")
                            .withStyle(mark == BookMark.GREEN ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
                    button -> {
                        TrashBooks.pick(name, BookMark.GREEN);
                        rebuild();
                    }).bounds(left + NAME_WIDTH + 4 + RED_WIDTH + 4, y, GREEN_WIDTH, 20).build();
            green.setTooltip(Tooltip.create(Component.literal("Green background in your inventory: a good book, easy to spot.")));
            addRenderableWidget(green);

            Button forget = Button.builder(Component.literal("Forget").withStyle(ChatFormatting.DARK_GRAY),
                    button -> {
                        TrashBooks.forget(name);
                        rebuild();
                    }).bounds(left + NAME_WIDTH + 4 + RED_WIDTH + 4 + GREEN_WIDTH + 4, y, FORGET_WIDTH, 20).build();
            forget.setTooltip(Tooltip.create(Component.literal(
                    "Takes it off the list. It comes back, neutral, when it shows up in your inventory again.")));
            addRenderableWidget(forget);
        }

        if (pageCount() > 1) {
            int mitte = width / 2;
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(mitte - 60, height - 50, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(mitte + 40, height - 50, 20, 20).build());
        }

        // Ein Buch von Hand eintragen: genau wie im Spiel, mit Stufe
        EditBox add = new EditBox(font, left, height - 28, gridWidth - 128, 20, Component.literal("Add a book"));
        add.setValue(typed);
        add.setHint(Component.literal("Add a book, e.g. Frail VI").withStyle(ChatFormatting.DARK_GRAY));
        add.setMaxLength(60);
        add.setResponder(value -> typed = value);
        addRenderableWidget(add);

        addRenderableWidget(Button.builder(Component.literal("Add"), button -> {
            if (TrashBooks.add(typed) != null) {
                typed = "";
                filter = "";
                rebuild();
            }
        }).bounds(left + gridWidth - 124, height - 28, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 60, height - 28, 60, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;

        // Feste Texte kommen aus dem Zwischenspeicher (je Zeichenkette ein geteiltes Component); Zahlen und Suchtext liegen fertig in gemerkten
        // Feldern und werden nur neu gebaut, wenn sich ihre Quellen aendern
        graphics.centeredText(font, FensterText.gold("Trash books"), centerX, 14, 0xFFFFAA00);
        texteAktualisieren();
        // Eine stille Funktion sieht aus wie eine kaputte: Hier steht, woran es liegt
        if (!ModConfig.INSTANCE.fishing.books.enabled) {
            graphics.centeredText(font, FensterText.rot("Switched off - turn it on under Fishing > Trash books > Enabled"),
                    centerX, 24, 0xFFFF5555);
        } else {
            graphics.centeredText(font, inventar.component(), centerX, 24, inventarFarbe);
        }
        graphics.centeredText(font, zaehler.component(), centerX, 60, 0xFF888888);

        if (visible().isEmpty()) {
            graphics.centeredText(font, bekannt == 0
                            ? FensterText.grau("No books seen yet. Carry some, or type a name below.")
                            : nichtsGefunden.component(),
                    centerX, LIST_TOP + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 44, 0xFFAAAAAA);
        }
    }
}
