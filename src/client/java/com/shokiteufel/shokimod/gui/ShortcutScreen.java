package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.CommandAlias;
import com.shokiteufel.shokimod.data.FeatureGate;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Zwei Reiter: Tasten, die Befehle schicken, und Aliase, die einen kurzen Befehl durch einen laengeren ersetzen.
 *
 * <b>Tasten:</b> eine Zeile je Taste mit Haken, Taste und Text. Der Haken schaltet die Zeile an und aus. Liegen mehrere auf derselben Taste, gilt nur die mit
 * Haken - wer bei einer anderen den Haken setzt, nimmt ihn der ersten. So lassen sich Varianten aufheben und mit einem Klick wechseln.
 *
 * <b>Aliase</b> (seit 2.5.13, nach dem Vorbild von Skyblockers Shortcuts): eine Zeile je Alias mit Haken, dem kurzen Befehl und dem Befehl, der stattdessen geschickt
 * wird - {@code /g} wird zu {@code /warp garden}. Was man hinter dem Alias tippt, wird angehaengt. Dieselbe Regel mit dem Haken wie bei den Tasten: Dasselbe
 * Alias zweimal, es gilt nur das mit Haken. Die Regel selbst steht in {@link CommandAlias#rewrite}.
 *
 * Ein einziges Fenster ohne Unterfenster: Die Zeilen sind gleich beschreibbar, und gespeichert wird beim Schliessen.
 */
public class ShortcutScreen extends Screen {

    private enum Tab {
        KEYS, ALIASES
    }

    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_HEIGHT = 20;
    private static final int TITLE_Y = 8;
    private static final int TAB_Y = 20;
    private static final int TAB_WIDTH = 120;
    private static final int NOTE_Y = 46;
    private static final int LIST_TOP = 72;
    private static final int CHECK_WIDTH = 20;
    private static final int KEY_WIDTH = 84;
    private static final int DELETE_WIDTH = 20;
    private static final int GAP = 4;
    private static final int COMMAND_WIDTH = 236;
    private static final int ROW_WIDTH = CHECK_WIDTH + GAP + KEY_WIDTH + GAP + COMMAND_WIDTH + GAP + DELETE_WIDTH;

    // Feste Texte: je Zeichenkette ein geteiltes Component aus dem Zwischenspeicher (FensterText), nie je Bild neu gebaut
    private static final String KEYS_NOTE_1 = "Press a key to send a command or a message  ·  one send per press";
    private static final String KEYS_NOTE_2 = "Several on one key: only the ticked one fires. Ticking another switches.";
    private static final String ALIAS_NOTE_1 = "Type the alias in the chat and the command is sent instead  ·  /g becomes /warp garden";
    private static final String ALIAS_NOTE_2 = "What you type behind the alias is added at the end. The same alias twice: only the ticked one counts.";

    // Tastencodes (GLFW), fest und seit jeher dieselben
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;

    private Tab tab = Tab.KEYS;
    private int page;
    /** Die Zeile, die gerade auf ihre Taste wartet (nur im Reiter der Tasten) */
    private Shortcut capturing = null;

    public ShortcutScreen() {
        super(Component.literal("Shortcuts"));
    }

    private List<Shortcut> list() {
        return ModConfig.INSTANCE.shortcuts;
    }

    private List<CommandAlias> aliases() {
        if (ModConfig.INSTANCE.aliases == null) ModConfig.INSTANCE.aliases = new ArrayList<>();
        return ModConfig.INSTANCE.aliases;
    }

    /** Wie viele Zeilen der Reiter hat, der gerade offen ist */
    private int count() {
        return tab == Tab.KEYS ? list().size() : aliases().size();
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 64 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (count() + rowsPerPage() - 1) / rowsPerPage());
    }

    private void switchTab(Tab next) {
        capturing = null;
        tab = next;
        page = 0;
        rebuild();
    }

    @Override
    protected void init() {
        // Die Aliase gehoeren zu 26.3: Mit dem Schalter aus gibt es nur die Tasten, das Fenster sieht aus wie vor 2.4.10
        boolean aliasesOn = FeatureGate.update263();
        if (!aliasesOn && tab == Tab.ALIASES) tab = Tab.KEYS;
        page = Math.min(page, pageCount() - 1);

        int left = width / 2 - ROW_WIDTH / 2;
        int rows = rowsPerPage();
        int start = page * rows;

        // Die zwei Reiter; der offene ist ausgegraut. Ohne Aliase bleibt der eine, in der Mitte
        int tabsLeft = aliasesOn ? width / 2 - TAB_WIDTH - GAP / 2 : width / 2 - TAB_WIDTH / 2;
        Button keysTab = Button.builder(Component.literal("Key shortcuts"), button -> switchTab(Tab.KEYS))
                .bounds(tabsLeft, TAB_Y, TAB_WIDTH, WIDGET_HEIGHT).build();
        keysTab.active = tab != Tab.KEYS;
        keysTab.setTooltip(Tooltip.create(Component.literal("A key sends a command or a message.")));
        addRenderableWidget(keysTab);
        if (aliasesOn) {
            Button aliasTab = Button.builder(Component.literal("Command aliases"), button -> switchTab(Tab.ALIASES))
                    .bounds(tabsLeft + TAB_WIDTH + GAP, TAB_Y, TAB_WIDTH, WIDGET_HEIGHT).build();
            aliasTab.active = tab != Tab.ALIASES;
            aliasTab.setTooltip(Tooltip.create(Component.literal("A short command that is swapped for a longer one when you send it: /g sends /warp garden.")));
            addRenderableWidget(aliasTab);
        }

        if (tab == Tab.KEYS) initKeyRows(left, rows, start);
        else initAliasRows(left, rows, start);

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal(tab == Tab.KEYS ? "New shortcut" : "New alias"), button -> {
            capturing = null;
            if (tab == Tab.KEYS) list().add(new Shortcut());
            else aliases().add(new CommandAlias());
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

    /** Der Reiter der Tasten: Haken, Taste, Befehl, Loeschen */
    private void initKeyRows(int left, int rows, int start) {
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
    }

    /** Der Reiter der Aliase: Haken, kurzer Befehl, Befehl, der stattdessen geschickt wird, Loeschen */
    private void initAliasRows(int left, int rows, int start) {
        List<CommandAlias> all = aliases();
        for (int i = 0; i < rows && start + i < all.size(); i++) {
            CommandAlias entry = all.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left;

            Button check = Button.builder(checkLabel(entry.enabled), button -> {
                entry.enabled = !entry.enabled;
                // Wer anmacht, uebernimmt das Alias - ein anderer Eintrag mit demselben geht aus
                if (entry.enabled) entry.takeOver(all);
                rebuild();
            }).bounds(x, y, CHECK_WIDTH, WIDGET_HEIGHT).build();
            check.setTooltip(Tooltip.create(Component.literal(
                    "On or off. With the same alias twice, only the ticked one counts - ticking another switches.")));
            addRenderableWidget(check);
            x += CHECK_WIDTH + GAP;

            EditBox alias = new EditBox(font, x, y, KEY_WIDTH, WIDGET_HEIGHT, Component.literal("Alias"));
            alias.setMaxLength(48);
            alias.setValue(entry.alias == null ? "" : entry.alias);
            alias.setHint(Component.literal("/alias"));
            alias.setResponder(value -> entry.alias = value);
            alias.setTooltip(Tooltip.create(Component.literal("What you type, for example /g. Capital letters and the slash do not matter.")));
            addRenderableWidget(alias);
            x += KEY_WIDTH + GAP;

            EditBox command = new EditBox(font, x, y, COMMAND_WIDTH, WIDGET_HEIGHT, Component.literal("Command"));
            command.setMaxLength(256);
            command.setValue(entry.command == null ? "" : entry.command);
            command.setHint(Component.literal("/command that is sent instead"));
            command.setResponder(value -> entry.command = value);
            command.setTooltip(Tooltip.create(Component.literal("What is sent instead, for example /warp garden. Anything typed behind the alias goes at the end.")));
            addRenderableWidget(command);
            x += COMMAND_WIDTH + GAP;

            addRenderableWidget(Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED), button -> {
                all.remove(entry);
                rebuild();
            }).bounds(x, y, DELETE_WIDTH, WIDGET_HEIGHT).build());
        }
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
        graphics.centeredText(font, this.title, centerX, TITLE_Y, 0xFFFFFFFF);
        // Feste Texte kommen aus dem Zwischenspeicher (je Zeichenkette ein geteiltes Component), "2 / 5" wird nur bei neuer Seite gebaut
        graphics.centeredText(font, FensterText.dunkelgrau(tab == Tab.KEYS ? KEYS_NOTE_1 : ALIAS_NOTE_1), centerX, NOTE_Y, 0xFF808080);
        graphics.centeredText(font, FensterText.dunkelgrau(tab == Tab.KEYS ? KEYS_NOTE_2 : ALIAS_NOTE_2), centerX, NOTE_Y + 11, 0xFF808080);

        if (count() == 0) {
            graphics.centeredText(font, FensterText.grau(tab == Tab.KEYS ? "No shortcuts yet - add one below." : "No aliases yet - add one below."),
                    centerX, LIST_TOP + 12, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 46, 0xFFAAAAAA);
        }
    }

    /**
     * Gespeichert wird beim Schliessen. Es gibt kein Unterfenster, das removed() vorzeitig ausloesen
     * koennte - dieses Fenster verlaesst man nur, um es ganz zu verlassen. Beide Reiter werden bereinigt, auch der, der nicht offen war.
     */
    @Override
    public void removed() {
        list().removeIf(Shortcut::isUntouched);
        Shortcut.normalize(list());
        aliases().removeIf(CommandAlias::isUntouched);
        CommandAlias.normalize(aliases());
        ModConfig.INSTANCE.saveNow();
        super.removed();
    }
}
