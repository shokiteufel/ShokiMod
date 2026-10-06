package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.handler.RareCatchAlert;
import com.shokiteufel.shokimod.util.AlertVolume;
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
 * Welcher Meeresbewohner sich meldet - einer je Zeile.
 *
 * Neunzig Bewohner passen in keine Einstellungsliste: Dort stuenden neunzig Schalter
 * untereinander, und wer einen bestimmten sucht, blaettert. Also dasselbe Fenster wie
 * bei den Waren des Kastens - mit Suchfeld, einer Zeile je Bewohner und den
 * Schaltern nebeneinander.
 *
 * <p>Drei Entscheidungen je Bewohner: ob ein eigener Fang etwas sagt, ob einer aus der
 * Gruppe etwas sagt, und welcher Klang dazu laeuft. Was nicht ausdruecklich gesetzt
 * ist, folgt der Voreinstellung - die sechsundzwanzig seltenen melden sich, die
 * uebrigen nicht.
 */
public class SeaCreatureScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int SOUND_WIDTH = 20;
    private static final int NAME_WIDTH = 190;
    private static final int OWN_WIDTH = 74;
    private static final int PARTY_WIDTH = 74;
    private static final int TEST_WIDTH = 40;
    private static final int LIST_TOP = 74;

    private final Screen parent;
    /** Der Suchbegriff ueberdauert das Schliessen - man sucht selten nur einmal */
    private static String filter = "";
    /** Nur die, die sich melden - wer neunzig Zeilen nicht braucht */
    private static boolean onlyPicked = false;
    private static int page = 0;

    public SeaCreatureScreen(Screen parent) {
        super(Component.literal("Sea creatures"));
        this.parent = parent;
    }

    private List<SeaCreatures.Creature> visible() {
        String suche = filter.trim().toLowerCase(Locale.ROOT);
        List<SeaCreatures.Creature> out = new ArrayList<>();
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            if (!suche.isEmpty() && !c.name().toLowerCase(Locale.ROOT).contains(suche)) continue;
            if (onlyPicked && !RareCatchAlert.wantsOwn(c) && !RareCatchAlert.partyPicked(c)) continue;
            out.add(c);
        }
        return out;
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
        page = Math.clamp(page, 0, pageCount() - 1);

        List<SeaCreatures.Creature> liste = visible();
        int gridWidth = SOUND_WIDTH + 4 + NAME_WIDTH + 4 + OWN_WIDTH + 4 + PARTY_WIDTH + 4 + TEST_WIDTH;
        int left = width / 2 - gridWidth / 2;

        EditBox search = new EditBox(font, left + SOUND_WIDTH + 4, 34, NAME_WIDTH, 20,
                Component.literal("Search"));
        search.setValue(filter);
        search.setHint(Component.literal("Search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(value -> {
            filter = value;
            page = 0;
            rebuild();
        });
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(
                Component.literal(onlyPicked ? "Picked" : "All").withStyle(ChatFormatting.AQUA),
                button -> {
                    onlyPicked = !onlyPicked;
                    page = 0;
                    rebuild();
                }).bounds(left + SOUND_WIDTH + 8 + NAME_WIDTH, 34, OWN_WIDTH, 20).build());

        // Alles an oder alles aus - bei neunzig Zeilen spart das viel Klicken
        addRenderableWidget(Button.builder(Component.literal("All on"), button -> {
            for (SeaCreatures.Creature c : visible()) {
                if (!RareCatchAlert.wantsOwn(c)) RareCatchAlert.toggleOwn(c);
            }
            rebuild();
        }).bounds(left + SOUND_WIDTH + 12 + NAME_WIDTH + OWN_WIDTH, 34, PARTY_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Off"), button -> {
            for (SeaCreatures.Creature c : visible()) {
                if (RareCatchAlert.wantsOwn(c)) RareCatchAlert.toggleOwn(c);
            }
            rebuild();
        }).bounds(left + SOUND_WIDTH + 16 + NAME_WIDTH + OWN_WIDTH + PARTY_WIDTH, 34, TEST_WIDTH, 20).build());

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            SeaCreatures.Creature c = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            Button sound = Button.builder(soundLabel(c), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new SoundPickerScreen(this,
                            () -> RareCatchAlert.soundOf(c),
                            picked -> RareCatchAlert.setSound(c, picked),
                            AlertVolume.factor()));
                }
            }).bounds(left, y, SOUND_WIDTH, 20).build();
            sound.setTooltip(soundTooltip(c));
            addRenderableWidget(sound);

            Button name = Button.builder(nameLabel(c), button -> {
                RareCatchAlert.toggleOwn(c);
                rebuild();
            }).bounds(left + SOUND_WIDTH + 4, y, NAME_WIDTH, 20).build();
            name.setTooltip(Tooltip.create(Component.literal(
                    c.rarity() + (c.rare() ? " - counts as rare" : " - common, off by default"))));
            addRenderableWidget(name);

            addRenderableWidget(Button.builder(ownLabel(c), button -> {
                RareCatchAlert.toggleOwn(c);
                rebuild();
            }).bounds(left + SOUND_WIDTH + 8 + NAME_WIDTH, y, OWN_WIDTH, 20).build());

            Button party = Button.builder(partyLabel(c), button -> {
                RareCatchAlert.toggleParty(c);
                rebuild();
            }).bounds(left + SOUND_WIDTH + 12 + NAME_WIDTH + OWN_WIDTH, y, PARTY_WIDTH, 20).build();
            party.setTooltip(Tooltip.create(Component.literal(
                    "Whether a catch reported by your party says something. "
                            + "The main switch under Fishing > Rare catch has to be on as well.")));
            addRenderableWidget(party);

            Button test = Button.builder(Component.literal("Test").withStyle(ChatFormatting.GRAY),
                    button -> RareCatchAlert.preview(c))
                    .bounds(left + SOUND_WIDTH + 16 + NAME_WIDTH + OWN_WIDTH + PARTY_WIDTH, y,
                            TEST_WIDTH, 20).build();
            test.setTooltip(Tooltip.create(Component.literal("Show this one once, as it would appear.")));
            addRenderableWidget(test);
        }

        int unten = height - 28;
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

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 80, unten, 80, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private Component nameLabel(SeaCreatures.Creature c) {
        boolean an = RareCatchAlert.wantsOwn(c);
        return Component.literal((an ? "☑ " : "☐ ") + c.name())
                .withStyle(style -> style.withColor(an ? c.colour() : 0x777777));
    }

    private Component ownLabel(SeaCreatures.Creature c) {
        boolean an = RareCatchAlert.wantsOwn(c);
        return Component.literal(an ? "Own ☑" : "Own ☐")
                .withStyle(an ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private Component partyLabel(SeaCreatures.Creature c) {
        boolean an = RareCatchAlert.partyPicked(c);
        return Component.literal(an ? "Party ☑" : "Party ☐")
                .withStyle(an ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
    }

    private Component soundLabel(SeaCreatures.Creature c) {
        boolean eigener = !RareCatchAlert.soundOf(c).isBlank();
        return Component.literal("♪").withStyle(eigener ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private Tooltip soundTooltip(SeaCreatures.Creature c) {
        String ton = RareCatchAlert.soundOf(c);
        return Tooltip.create(Component.literal(ton.isBlank()
                ? "No own sound - the one under Fishing > Rare catch plays."
                : "Plays " + ton));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Sea creatures").withStyle(ChatFormatting.GOLD),
                centerX, 14, 0xFFFFAA00);

        List<SeaCreatures.Creature> liste = visible();
        int gewaehlt = 0;
        for (SeaCreatures.Creature c : SeaCreatures.all()) {
            if (RareCatchAlert.wantsOwn(c)) gewaehlt++;
        }
        graphics.centeredText(font, Component.literal(
                        gewaehlt + " of " + SeaCreatures.all().size() + " announce themselves"
                        + " - click a name to switch it, ♪ for its own sound")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 60, 0xFF888888);

        if (liste.isEmpty()) {
            graphics.centeredText(font, Component.literal("Nothing found for \"" + filter + "\"")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }
}
