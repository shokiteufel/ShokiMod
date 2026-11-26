package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.handler.SeaCreatureCocoon;
import com.shokiteufel.shokimod.util.SeaCreatures;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Welcher Meeresbewohner bei einem Cocoon einen Titel und eine Zeile im Gruppenchat bekommt - einer je Zeile.
 *
 * Herkunft der Idee: SkyHanni ("Custom Cocoon Settings", die Listen "Warn when cocooned" und "Share cocoon to party"). Hier stehen beide
 * Schalter in einem Fenster nebeneinander: Title (der Banner und der Ton) und Share (die Zeile an die Gruppe). Was nicht ausdruecklich gesetzt
 * ist, folgt der Voreinstellung - die seltenen melden sich, die uebrigen nicht. Die Hauptschalter unter Fishing > Cocoon settings muessen
 * zusaetzlich an sein.
 *
 * Alles, was im Bild steht, wird beim Aufbau ({@link #init()}) gebaut und gehalten; das Zeichnen liest nur.
 */
public class CocoonScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int GAP = 4;
    private static final int LIST_TOP = 66;
    private static final int NAME_WIDTH = 150;
    private static final int COLUMN_WIDTH = 74;

    private final Screen parent;
    /** Der Suchbegriff ueberdauert das Schliessen - man sucht selten nur einmal */
    private static String filter = "";
    private static int page = 0;

    private List<SeaCreatures.Creature> sichtbar;
    /** Die Namen der Zeilen dieser Seite, in ihren Farben - beim Aufbau gebaut */
    private final List<Component> zeilenNamen = new ArrayList<>();
    private int nameX, titleX, shareX, gridWidth;
    /** "Nothing found for ..." - neu gebaut nur, wenn sich der Suchtext aendert */
    private final FensterText.Gemerkt nichtsGefunden = new FensterText.Gemerkt();

    public CocoonScreen(Screen parent) {
        super(Component.literal("Cocoon creatures"));
        this.parent = parent;
    }

    private List<SeaCreatures.Creature> visible() {
        if (sichtbar == null) {
            String suche = filter.trim().toLowerCase(Locale.ROOT);
            List<SeaCreatures.Creature> out = new ArrayList<>();
            for (SeaCreatures.Creature c : SeaCreatures.all()) {
                if (suche.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(suche)) out.add(c);
            }
            sichtbar = out;
        }
        return sichtbar;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 56 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static Component titleLabel(boolean on) {
        return FensterText.format(on ? "Title ☑" : "Title ☐", on ? ChatFormatting.RED : ChatFormatting.DARK_GRAY);
    }

    private static Component shareLabel(boolean on) {
        return FensterText.format(on ? "Share ☑" : "Share ☐", on ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
    }

    @Override
    protected void init() {
        sichtbar = null;
        zeilenNamen.clear();
        page = Math.clamp(page, 0, pageCount() - 1);

        gridWidth = NAME_WIDTH + 2 * COLUMN_WIDTH + 2 * GAP;
        int left = width / 2 - gridWidth / 2;
        nameX = left;
        titleX = nameX + NAME_WIDTH + GAP;
        shareX = titleX + COLUMN_WIDTH + GAP;

        EditBox search = new EditBox(font, nameX, 34, NAME_WIDTH, 20, FensterText.text("Search"));
        search.setValue(filter);
        search.setHint(FensterText.dunkelgrau("Search"));
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        // Alles an, je Spalte: bei neunzig Zeilen spart das viel Klicken
        addRenderableWidget(Button.builder(FensterText.gruen("All on"), button -> {
            for (SeaCreatures.Creature c : visible()) {
                if (!SeaCreatureCocoon.wantsWarn(c)) SeaCreatureCocoon.toggleWarn(c);
            }
            rebuild();
        }).bounds(titleX, 34, COLUMN_WIDTH, 20).build());
        addRenderableWidget(Button.builder(FensterText.gruen("All on"), button -> {
            for (SeaCreatures.Creature c : visible()) {
                if (!SeaCreatureCocoon.wantsShare(c)) SeaCreatureCocoon.toggleShare(c);
            }
            rebuild();
        }).bounds(shareX, 34, COLUMN_WIDTH, 20).build());

        List<SeaCreatures.Creature> liste = visible();
        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            SeaCreatures.Creature c = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            zeilenNamen.add(Component.literal(c.name()).withStyle(style -> style.withColor(c.colour())));

            Button title = Button.builder(titleLabel(SeaCreatureCocoon.wantsWarn(c)), button -> {
                SeaCreatureCocoon.toggleWarn(c);
                rebuild();
            }).bounds(titleX, y, COLUMN_WIDTH, 20).build();
            title.setTooltip(Tooltip.create(FensterText.text(
                    "Whether a cocooned " + c.name() + " gets a title and a sound. "
                            + "\"Send title\" under Fishing > Cocoon settings has to be on as well.")));
            addRenderableWidget(title);

            Button share = Button.builder(shareLabel(SeaCreatureCocoon.wantsShare(c)), button -> {
                SeaCreatureCocoon.toggleShare(c);
                rebuild();
            }).bounds(shareX, y, COLUMN_WIDTH, 20).build();
            share.setTooltip(Tooltip.create(FensterText.text(
                    "Whether a cocooned " + c.name() + " is written into the party chat. "
                            + "\"Share in party chat\" under Fishing > Cocoon settings has to be on as well. Off by default for common ones: this talks to other people.")));
            addRenderableWidget(share);
        }
        if (liste.isEmpty() && !nichtsGefunden.gilt(0L, filter)) {
            nichtsGefunden.setze(0L, filter, FensterText.grau("Nothing found for \"" + filter + "\""));
        }

        if (pageCount() > 1) {
            int mitte = width / 2;
            addRenderableWidget(Button.builder(FensterText.text("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(mitte - 60, height - 50, 20, 20).build());
            addRenderableWidget(Button.builder(FensterText.text("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(mitte + 40, height - 50, 20, 20).build());
        }

        addRenderableWidget(Button.builder(FensterText.text("Done"), button -> onClose())
                .bounds(left + gridWidth - 80, height - 28, 80, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, FensterText.gold("Cocoon creatures"), centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, FensterText.dunkelgrau(
                "Title: banner and sound  ·  Share: tell your party  ·  both also need their main switch"), centerX, 24, 0xFF888888);

        for (int i = 0; i < zeilenNamen.size(); i++) {
            graphics.text(font, zeilenNamen.get(i), nameX + 4, LIST_TOP + i * ROW_HEIGHT + 6, 0xFFFFFFFF, true);
        }
        if (zeilenNamen.isEmpty()) {
            graphics.centeredText(font, nichtsGefunden.component(), centerX, LIST_TOP + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 44, 0xFFAAAAAA);
        }
    }
}
