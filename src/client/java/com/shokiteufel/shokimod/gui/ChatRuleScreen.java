package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ChatRule;
import com.shokiteufel.shokimod.data.ChatRuleShare;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.List;

/** Liste der Chatregeln. Bearbeitet wird eine Regel im ChatRuleEditScreen. */
public class ChatRuleScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 392;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LIST_TOP = 48;

    private final Screen parent;
    private int page;
    /** Was die letzte Aktion ergeben hat - im Bildschirm, denn der Chat liegt hinter dem Fenster */
    private Component status;

    public ChatRuleScreen(Screen parent) {
        super(Component.literal("Chat Rules"));
        this.parent = parent;
    }

    private List<ChatRule> rules() {
        return ModConfig.INSTANCE.chat.chatRules;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (rules().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        int left = width / 2 - ROW_WIDTH / 2;
        int rows = rowsPerPage();
        int start = page * rows;
        List<ChatRule> list = rules();

        for (int i = 0; i < rows && start + i < list.size(); i++) {
            ChatRule rule = list.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            addRenderableWidget(Button.builder(onOff(rule.enabled), button -> {
                rule.enabled = !rule.enabled;
                button.setMessage(onOff(rule.enabled));
            }).bounds(left, y, 40, WIDGET_HEIGHT).build());

            boolean needsFilter = rule.filter == null || rule.filter.isBlank();
            Button edit = Button.builder(Component.literal(
                    (needsFilter ? "§c⚠ §r" : "") + rule.label() + "  §8" + summary(rule)),
                    button -> minecraft.setScreenAndShow(new ChatRuleEditScreen(this, rule)))
                    .bounds(left + 44, y, 228, WIDGET_HEIGHT).build();
            edit.setTooltip(Tooltip.create(needsFilter
                    ? Component.literal("No filter yet - the rule never triggers")
                            .withStyle(ChatFormatting.RED)
                    : Component.literal("Filter: " + rule.filter)));
            addRenderableWidget(edit);

            Button share = Button.builder(Component.literal("↗").withStyle(ChatFormatting.AQUA),
                    button -> share(List.of(rule), "\"" + shortLabel(rule) + "\""))
                    .bounds(left + 276, y, 20, WIDGET_HEIGHT).build();
            share.active = !rule.isUntouched();
            share.setTooltip(Tooltip.create(Component.literal(
                    "Copies this rule as a code you can paste anywhere.")));
            addRenderableWidget(share);

            // Die Reihenfolge entscheidet, welche Regel zuerst zuschlaegt - und damit,
            // welche eine andere aussperren kann
            int index = start + i;
            Button up = Button.builder(Component.literal("▲"), button -> {
                swap(index, index - 1);
                rebuild();
            }).bounds(left + 300, y, 20, WIDGET_HEIGHT).build();
            up.active = index > 0;
            addRenderableWidget(up);

            Button down = Button.builder(Component.literal("▼"), button -> {
                swap(index, index + 1);
                rebuild();
            }).bounds(left + 324, y, 20, WIDGET_HEIGHT).build();
            down.active = index < list.size() - 1;
            addRenderableWidget(down);

            addRenderableWidget(Button.builder(
                    Component.literal("✕").withStyle(ChatFormatting.RED), button -> {
                        rules().remove(rule);
                        rebuild();
                    }).bounds(left + 352, y, 20, WIDGET_HEIGHT).build());
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("New rule"), button -> {
            ChatRule fresh = new ChatRule();
            rules().add(fresh);
            minecraft.setScreenAndShow(new ChatRuleEditScreen(this, fresh));
        }).bounds(left, y, 90, WIDGET_HEIGHT).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 96, y, 20, WIDGET_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 120, y, 20, WIDGET_HEIGHT).build());
        }

        // Weitergeben und Holen laufen ueber die Zwischenablage, weil die von jedem Fenster aus
        // erreichbar ist - Discord, Chat, Notizzettel. Wie beim Banner-Design
        Button shareAll = Button.builder(Component.literal("Share all").withStyle(ChatFormatting.AQUA),
                button -> shareAll()).bounds(left + 148, y, 78, WIDGET_HEIGHT).build();
        shareAll.active = rules().stream().anyMatch(rule -> !rule.isUntouched());
        shareAll.setTooltip(Tooltip.create(Component.literal(
                "Copies every rule in the list as one code you can paste anywhere.")));
        addRenderableWidget(shareAll);

        Button importButton = Button.builder(Component.literal("Import").withStyle(ChatFormatting.GREEN),
                button -> importRules()).bounds(left + 230, y, 78, WIDGET_HEIGHT).build();
        importButton.setTooltip(Tooltip.create(Component.literal(
                "Reads a rule code from your clipboard and adds the rules at the end of the list. "
                        + "They arrive switched off, so look them over before you turn them on.")));
        addRenderableWidget(importButton);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + ROW_WIDTH - 80, y, 80, WIDGET_HEIGHT).build());
    }

    private void shareAll() {
        long count = rules().stream().filter(rule -> !rule.isUntouched()).count();
        share(rules(), count + (count == 1 ? " rule" : " rules"));
    }

    private void share(List<ChatRule> toShare, String what) {
        try {
            String code = ChatRuleShare.encode(toShare);
            minecraft.keyboardHandler.setClipboard(code);
            say("Copied " + what + " (" + code.length() + " characters) to your clipboard", ChatFormatting.GREEN);
        } catch (ChatRuleShare.ShareException e) {
            say(e.getMessage(), ChatFormatting.RED);
        }
    }

    /**
     * Regeln aus der Zwischenablage ans Ende der Liste.
     *
     * Angehaengt wird immer - ein Code soll nie still ersetzen, woran jemand gearbeitet hat.
     * Ausgeschaltet kommen sie an (ChatRuleShare.decode), denn eine Regel kann Chatzeilen
     * verschlucken. Eine Sounddatei, die es hier nicht gibt, wird gemeldet: die Regel schweigt
     * dann an dieser Stelle, und man soll nicht raten muessen, warum.
     */
    private void importRules() {
        try {
            List<ChatRule> incoming = ChatRuleShare.decode(minecraft.keyboardHandler.getClipboard());
            int firstNew = rules().size();
            rules().addAll(incoming);
            // Auf die Seite, auf der die erste neue Regel steht - nicht ans Ende, wo nur die letzte zu sehen waere
            page = firstNew / rowsPerPage();

            long missing = incoming.stream().filter(rule -> !rule.soundFile.isBlank()
                    && !Files.isRegularFile(CustomSoundPlayer.SOUND_DIRECTORY.resolve(rule.soundFile))).count();
            String text = "Added " + incoming.size() + (incoming.size() == 1 ? " rule" : " rules")
                    + ", switched off";
            if (missing > 0) text += " - sound file missing for " + missing;
            say(text, missing > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
            rebuild();
        } catch (ChatRuleShare.ShareException e) {
            say(e.getMessage(), ChatFormatting.RED);
        }
    }

    private void say(String text, ChatFormatting colour) {
        status = Component.literal(text).withStyle(colour);
    }

    /** Der Name fuer die Rueckmeldung - ein langer soll die Zeile nicht ueber den Rand schieben */
    private static String shortLabel(ChatRule rule) {
        String label = rule.label();
        return label.length() > 24 ? label.substring(0, 23) + "…" : label;
    }

    /** Zwei Regeln tauschen. Ausserhalb der Liste passiert nichts */
    private void swap(int from, int to) {
        List<ChatRule> list = rules();
        if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
        list.add(to, list.remove(from));
    }

    /** Kurzzeichen, welche Reaktionen die Regel auslöst */
    private static String summary(ChatRule rule) {
        StringBuilder out = new StringBuilder();
        if (rule.hideMessage) out.append("hide ");
        if (!rule.replacement.isBlank()) out.append("replace ");
        if (!rule.actionBar.isBlank()) out.append("bar ");
        if (!rule.announcement.isBlank()) out.append("banner ");
        if (!rule.toast.isBlank()) out.append("toast ");
        if (!rule.soundId.isBlank()) out.append("sound ");
        if (!rule.soundFile.isBlank()) out.append("file ");
        return out.isEmpty() ? "no output" : out.toString().trim();
    }

    private static Component onOff(boolean on) {
        return on ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.DARK_GRAY);
    }

    void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        graphics.centeredText(font, this.title, centerX, 16, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "React to words in chat  ·  the order decides who goes first")
                .withStyle(ChatFormatting.DARK_GRAY), centerX, 30, 0xFF808080);

        if (rules().isEmpty()) {
            graphics.centeredText(font, Component.literal("No rules yet - add one below.")
                    .withStyle(ChatFormatting.GRAY), centerX, LIST_TOP + 12, 0xFFAAAAAA);
        }
        if (status != null) {
            graphics.centeredText(font, status, centerX, height - 58, 0xFFFFFFFF);
        }
        if (pageCount() > 1) {
            graphics.centeredText(font,
                    Component.literal((page + 1) + " / " + pageCount()).withStyle(ChatFormatting.GRAY),
                    centerX, height - 46, 0xFFAAAAAA);
        }
    }

    /**
     * Hier wird gespeichert, nicht in removed(): removed() laeuft bei jedem Bildschirmwechsel,
     * also auch beim Sprung in den Editor - eine gerade angelegte Regel waere damit weg, bevor
     * man das erste Zeichen tippen kann.
     */
    @Override
    public void onClose() {
        rules().removeIf(ChatRule::isUntouched);
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
