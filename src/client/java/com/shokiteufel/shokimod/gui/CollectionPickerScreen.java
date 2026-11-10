package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.CollectionData;
import com.shokiteufel.shokimod.util.CollectionData.Collection;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welche Collections der Tracker fuehrt: Mehrfachauswahl zum Anhaken.
 *
 * Die Liste kommt aus Hypixels eigener Aufstellung, geordnet nach Kategorie. Ein
 * Suchfeld daneben, weil neunzig Eintraege auf keine Seite passen.
 */
public class CollectionPickerScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int COLUMNS = 3;
    private static final int COLUMN_WIDTH = 160;
    private static final int LIST_TOP = 62;

    private final Screen parent;
    private String filter = "";
    private int page;

    public CollectionPickerScreen(Screen parent) {
        super(Component.literal("Collections"));
        this.parent = parent;
    }

    private static List<String> selected() {
        List<String> list = ModConfig.INSTANCE.collections.tracker.selected;
        if (list == null) ModConfig.INSTANCE.collections.tracker.selected = list = new ArrayList<>();
        return list;
    }

    /**
     * Die gefilterte Liste, einmal gebaut und gehalten (CollectionData.all() legt und sortiert bei jedem Aufruf eine neue
     * Liste an, und das Fenster fragt mehrmals je Bild danach).
     *
     * Ihre Eingaben: der Suchtext (aendert sich nur im Suchfeld, dessen Antwort {@link #rebuild()} ruft - das verwirft sie in
     * {@link #init()}) und die Liste von Hypixel, die im Hintergrund nachgeladen wird. Dafuer gibt es keinen Versionszaehler;
     * neu gebaut wird deshalb, wenn sich die Zahl der Eintraege der Quelle aendert (erste Antwort), und sonst nach
     * spaetestens einer Sekunde (eine neue Antwort mit gleich vielen, aber anderen Eintraegen). Der Aufruf von all() haelt
     * nebenbei den Nachlade-Takt der Quelle am Laufen, den vorher jedes Bild besorgte.
     */
    private List<Collection> sichtbar;
    private int sichtbarQuelle = -1;
    private long sichtbarStand;
    private static final long LISTE_MILLIS = 1_000L;
    /** "3 selected" und die Ladehinweise - neu gebaut nur, wenn sich Zahl der Haken oder Ladestand aendern */
    private final FensterText.Gemerkt notiz = new FensterText.Gemerkt();

    private List<Collection> visible() {
        long jetzt = System.currentTimeMillis();
        if (sichtbar == null || CollectionData.FEED.size() != sichtbarQuelle || jetzt - sichtbarStand >= LISTE_MILLIS) {
            listeBauen(jetzt);
        }
        return sichtbar;
    }

    private void listeBauen(long jetzt) {
        List<Collection> out = new ArrayList<>();
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        for (Collection collection : CollectionData.all()) {
            if (needle.isEmpty() || collection.name().toLowerCase(Locale.ROOT).contains(needle)
                    || collection.category().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(collection);
            }
        }
        sichtbar = out;
        // Die Zahl erst nach all() lesen: der erste Aufruf kann die Platte lesen und die Quelle damit fuellen
        sichtbarQuelle = CollectionData.FEED.size();
        sichtbarStand = jetzt;
    }

    /** Der zusammengesetzte Text des Bildes: gebaut nur, wenn sich seine Quellen geaendert haben */
    private void texteAktualisieren() {
        boolean fertig = CollectionData.ready();
        int gewaehlt = selected().size();
        long schluessel = gewaehlt * 2L + (fertig ? 1L : 0L);
        if (!notiz.gilt(schluessel)) {
            String text = fertig
                    ? (gewaehlt == 0 ? "Nothing selected - everything that comes in is counted"
                                     : gewaehlt + " selected")
                    : "Loading the list from Hypixel...";
            notiz.setze(schluessel, Component.literal(text).withStyle(
                    gewaehlt == 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        }
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int perPage() {
        return rowsPerPage() * COLUMNS;
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        CollectionData.prefetch();
        // Jeder Neuaufbau (Suchtext, Seite, Haken) liest die Liste frisch, wie vorher
        sichtbar = null;
        page = Math.min(page, pageCount() - 1);

        List<Collection> items = visible();
        int gridWidth = COLUMNS * (COLUMN_WIDTH + 4) - 4;
        int left = width / 2 - gridWidth / 2;

        EditBox search = new EditBox(font, left, 34, 200, 20, Component.literal("Search"));
        search.setValue(filter);
        search.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        int start = page * perPage();
        for (int i = 0; i < perPage() && start + i < items.size(); i++) {
            Collection collection = items.get(start + i);
            int x = left + (i % COLUMNS) * (COLUMN_WIDTH + 4);
            int y = LIST_TOP + (i / COLUMNS) * ROW_HEIGHT;
            Button row = Button.builder(label(collection), button -> {
                toggle(collection.id());
                button.setMessage(label(collection));
            }).bounds(x, y, COLUMN_WIDTH, 20).build();
            addRenderableWidget(row);
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Clear all"), button -> {
            selected().clear();
            rebuild();
        }).bounds(left, y, 90, 20).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 98, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 122, y, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 90, y, 90, 20).build());
    }

    private static Component label(Collection collection) {
        boolean on = selected().contains(collection.id());
        return Component.literal((on ? "☑ " : "☐ ") + collection.name())
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private void toggle(String id) {
        if (!selected().remove(id)) selected().add(id);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        // Feste Texte kommen aus dem Zwischenspeicher (je Zeichenkette ein geteiltes Component), nicht aus einem neuen Aufbau je Bild
        graphics.centeredText(font, FensterText.text("Collections in the tracker"), centerX, 14, 0xFFFFFFFF);

        texteAktualisieren();
        graphics.centeredText(font, notiz.component(), centerX, height - 46, 0xFFAAAAAA);

        if (pageCount() > 1) {
            // "2 / 5" wird nur neu gebaut, wenn sich Seite oder Seitenzahl aendern
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 58, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
