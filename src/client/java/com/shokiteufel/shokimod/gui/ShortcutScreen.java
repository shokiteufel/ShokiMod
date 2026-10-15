package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.Shortcut;
import com.shokiteufel.shokimod.handler.ShortcutHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Tasten, die Befehle schicken: eine Zeile je Taste mit Haken, Taste und Text.
 *
 * Der Haken schaltet die Zeile an und aus. Liegen mehrere auf derselben Taste, gilt nur die mit
 * Haken - wer bei einer anderen den Haken setzt, nimmt ihn der ersten. So lassen sich Varianten
 * aufheben und mit einem Klick wechseln.
 *
 * Ein einziges Fenster ohne Unterfenster: Die Zeilen sind gleich beschreibbar, und gespeichert
 * wird beim Schliessen.
 */
public class ShortcutScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LIST_TOP = 52;
    private static final int CHECK_WIDTH = 20;
    private static final int KEY_WIDTH = 84;
    private static final int DELETE_WIDTH = 20;
    private static final int GAP = 4;
    private static final int COMMAND_WIDTH = 236;
    private static final int ROW_WIDTH = CHECK_WIDTH + GAP + KEY_WIDTH + GAP + COMMAND_WIDTH + GAP + DELETE_WIDTH;

    // Tastencodes (GLFW), fest und seit jeher dieselben
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;

    private int page;
    /** Die Zeile, die gerade auf ihre Taste wartet */
    private Shortcut capturing = null;

    public ShortcutScreen() {
        super(Component.literal("Shortcuts"));
    }

    private List<Shortcut> list() {
        return ModConfig.INSTANCE.shortcuts;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 64 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (list().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        int left = width / 2 - ROW_WIDTH / 2;
        int rows = rowsPerPage();
        int start = page * rows;
        List<Shortcut> all = list();

        for (int i = 0; i < rows && start + i < all.size(); i++) {
            Shortcut shortcut = all.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left;

            Button check = Button.builder(checkLabel(shortcut.enabled), button -> {
                capturing = null;
                shortcut.enabled = !shortcut.enabled;
                // Wer anmacht, uebernimmt die Taste - der andere auf ihr geht aus
                if (shortcut.enabled) shortcut.takeOverKey(all);
                rebuild();
            }).bounds(x, y, CHECK_WIDTH, WIDGET_HEIGHT).build();
            check.setTooltip(Tooltip.create(Component.literal(
                    "On or off. With several on one key, only the ticked one fires - ticking another switches.")));
            addRenderableWidget(check);
            x += CHECK_WIDTH + GAP;

            boolean waiting = capturing == shortcut;
            Button key = Button.builder(waiting
                            ? Component.literal("press a key").withStyle(ChatFormatting.YELLOW)
                            : keyLabel(shortcut, all), button -> {
                        capturing = waiting ? null : shortcut;
                        rebuild();
                    }).bounds(x, y, KEY_WIDTH, WIDGET_HEIGHT).build();
            key.setTooltip(Tooltip.create(Component.literal(
                    "Click, then press the key. Esc cancels, Delete clears. A key the game already uses will do both.")));
            addRenderableWidget(key);
            x += KEY_WIDTH + GAP;

            EditBox command = new EditBox(font, x, y, COMMAND_WIDTH, WIDGET_HEIGHT, Component.literal("Command"));
            command.setMaxLength(256);
            command.setValue(shortcut.command == null ? "" : shortcut.command);
            command.setHint(Component.literal("/command or a message"));
            command.setResponder(value -> shortcut.command = value);
            addRenderableWidget(command);
            x += COMMAND_WIDTH + GAP;

            addRenderableWidget(Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED), button -> {
                capturing = null;
                all.remove(shortcut);
                rebuild();
            }).bounds(x, y, DELETE_WIDTH, WIDGET_HEIGHT).build());
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("New shortcut"), button -> {
            capturing = null;
            all.add(new Shortcut());
            page = pageCount() - 1;
            rebuild();
        }).bounds(left, y, 100, WIDGET_HEIGHT).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                capturing = null;
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 108, y, 20, WIDGET_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                capturing = null;
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 132, y, 20, WIDGET_HEIGHT).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + ROW_WIDTH - 90, y, 90, WIDGET_HEIGHT).build());
    }

    private static Component checkLabel(boolean on) {
        return on ? Component.literal("✔").withStyle(ChatFormatting.GREEN) : Component.empty();
    }

    /** Der Name der Taste; teilt sie sich eine mit einer anderen Zeile, steht das dabei */
    private static Component keyLabel(Shortcut shortcut, List<Shortcut> all) {
        String name = ShortcutHandler.keyName(shortcut.key);
        if (shortcut.key == 0) return Component.literal(name).withStyle(ChatFormatting.DARK_GRAY);
        boolean shared = false;
        for (Shortcut other : all) {
            if (other != shortcut && other.key == shortcut.key) shared = true;
        }
        return Component.literal(shared ? name + " §8⇄" : name);
    }

    void rebuild() {
        clearWidgets();
        init();
    }

    /** Waehrend eine Zeile auf ihre Taste wartet, gehoert die naechste Taste ihr - jede, auch Umschalt */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (capturing == null) return super.keyPressed(event);

        Shortcut target = capturing;
        capturing = null;
        int code = event.key();
        if (code == KEY_DELETE || code == KEY_BACKSPACE) {
            target.key = 0;
        } else if (code != KEY_ESCAPE) {
            target.key = code;
            // Wer eine Taste waehlt, auf der schon einer liegt und angeschaltet bleibt, uebernimmt sie
            if (target.enabled) target.takeOverKey(list());
        }
        rebuild();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 14, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "Press a key to send a command or a message  ·  one send per press")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 27, 0xFF808080);
        graphics.centeredText(font, Component.literal(
                        "Several on one key: only the ticked one fires. Ticking another switches.")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 38, 0xFF808080);

        if (list().isEmpty()) {
            graphics.centeredText(font, Component.literal("No shortcuts yet - add one below.")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 12, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font,
                    Component.literal((page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY),
                    centerX, height - 46, 0xFFAAAAAA);
        }
    }

    /**
     * Gespeichert wird beim Schliessen. Es gibt kein Unterfenster, das removed() vorzeitig ausloesen
     * koennte - dieses Fenster verlaesst man nur, um es ganz zu verlassen.
     */
    @Override
    public void removed() {
        list().removeIf(Shortcut::isUntouched);
        Shortcut.normalize(list());
        ModConfig.INSTANCE.saveNow();
        super.removed();
    }
}
