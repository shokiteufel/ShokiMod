package com.shokiteufel.shokimod.gui;

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

/**
 * Welche verzauberten Buecher markiert werden - eines je Zeile.
 *
 * Die Liste sind die Buecher, die schon im eigenen Inventar lagen, und "Corruption I" von Feesh. Wer eines
 * markieren will, das noch nicht dabei war, tippt den Namen unten ein (genau wie im Spiel, mit Stufe: "Frail VI").
 * Aufbau und Bedienung wie bei den anderen Auswahlfenstern: Suchfeld, Seiten, "Done".
 */
public class TrashBooksScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 200;
    private static final int FORGET_WIDTH = 60;
    private static final int LIST_TOP = 74;

    private final Screen parent;
    private static String filter = "";
    private static boolean onlyMarked = false;
    private static String typed = "";
    private static int page = 0;

    public TrashBooksScreen(Screen parent) {
        super(Component.literal("Trash books"));
        this.parent = parent;
    }

    private List<String> visible() {
        String suche = filter.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : TrashBooks.known()) {
            if (!suche.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(suche)) continue;
            if (onlyMarked && !TrashBooks.isMarked(name)) continue;
            out.add(name);
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
        // Was gerade im Inventar liegt, soll schon da stehen, wenn das Fenster aufgeht
        TrashBooks.scan(Minecraft.getInstance());
        page = Math.clamp(page, 0, pageCount() - 1);

        List<String> liste = visible();
        int gridWidth = NAME_WIDTH + 4 + FORGET_WIDTH;
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
                }).bounds(left + NAME_WIDTH + 4, 34, FORGET_WIDTH, 20).build());

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < liste.size(); i++) {
            String name = liste.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            boolean an = TrashBooks.isMarked(name);

            Button toggle = Button.builder(
                    Component.literal((an ? "☑ " : "☐ ") + name)
                            .withStyle(an ? ChatFormatting.RED : ChatFormatting.GRAY),
                    button -> {
                        TrashBooks.toggle(name);
                        rebuild();
                    }).bounds(left, y, NAME_WIDTH, 20).build();
            toggle.setTooltip(Tooltip.create(Component.literal(an
                    ? "Marked: this book gets a red background in your inventory."
                    : "Not marked.")));
            addRenderableWidget(toggle);

            Button forget = Button.builder(Component.literal("Forget").withStyle(ChatFormatting.DARK_GRAY),
                    button -> {
                        TrashBooks.forget(name);
                        rebuild();
                    }).bounds(left + NAME_WIDTH + 4, y, FORGET_WIDTH, 20).build();
            forget.setTooltip(Tooltip.create(Component.literal(
                    "Takes it off the list. It comes back, unmarked, when it shows up in your inventory again.")));
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
        EditBox add = new EditBox(font, left, height - 28, NAME_WIDTH - 64, 20, Component.literal("Add a book"));
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
        }).bounds(left + NAME_WIDTH - 60, height - 28, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 60, height - 28, 60, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;

        graphics.centeredText(font, Component.literal("Trash books").withStyle(ChatFormatting.GOLD),
                centerX, 14, 0xFFFFAA00);
        graphics.centeredText(font, Component.literal(
                        TrashBooks.markedCount() + " of " + TrashBooks.known().size()
                                + " marked  ·  only slots of your own inventory get the red background")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 60, 0xFF888888);

        if (visible().isEmpty()) {
            graphics.centeredText(font, Component.literal(TrashBooks.known().isEmpty()
                            ? "No books seen yet. Carry some, or type a name below."
                            : "Nothing found for \"" + filter + "\"")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 44, 0xFFAAAAAA);
        }
    }
}
