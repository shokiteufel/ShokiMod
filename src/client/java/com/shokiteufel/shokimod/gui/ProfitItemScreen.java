package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.ProfitTracker;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Die Liste des Profit-Trackers: was gefunden wurde, was davon im Kasten steht und
 * wie jedes Stueck zu Geld gemacht wird.
 *
 * Eine Zeile je Fund. Links der Name mit Haken - ein Klick nimmt ihn heraus oder
 * holt ihn herein, je nachdem, welche Betriebsart eingestellt ist. Rechts daneben
 * die Verkaufsart: Default, Insta, Order, NPC, Custom, im Kreis. Default heisst,
 * dass das Item der Voreinstellung des Kastens folgt; sie steht oben, damit man sie
 * sieht, bevor man vierzig Items einzeln einstellt.
 *
 * Bei Custom erscheint ein Feld fuer den eigenen Preis je Stueck. Kurzformen sind
 * erlaubt - 550k und 1.2m werden gelesen wie ueberall sonst in dieser Mod. Bleibt
 * das Feld leer, gilt weiter der Marktpreis; so steht nach einem versehentlichen
 * Klick keine Null im Kasten.
 *
 * Aufgelistet wird, was seit dem Reset tatsaechlich angekommen ist. Eine Liste
 * aller 5.000 SkyBlock-Items waere nutzlos: man richtet sie fuer den Lauf ein, den
 * man gerade macht.
 */
public class ProfitItemScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 210;
    private static final int MODE_WIDTH = 64;
    private static final int PRICE_WIDTH = 72;
    private static final int LIST_TOP = 74;

    private final Screen parent;
    private String filter = "";
    private int page;

    public ProfitItemScreen(Screen parent) {
        super(Component.literal("Profit Tracker"));
        this.parent = parent;
    }

    private static ModConfig.ProfitCategory cfg() {
        return ModConfig.INSTANCE.profit;
    }

    /** Alle Funde, wertvollste zuerst - die Reihenfolge des Kastens */
    private List<String> visible() {
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String itemId : ProfitTracker.seen()) {
            if (ProfitTracker.countOf(itemId) <= 0) continue;
            if (!needle.isEmpty()
                    && !ProfitTracker.nameOf(itemId).toLowerCase(Locale.ROOT).contains(needle)
                    && !itemId.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            out.add(itemId);
        }
        out.sort(Comparator.comparingDouble((String id) -> worth(id)).reversed()
                .thenComparing(ProfitTracker::nameOf));
        return out;
    }

    private static double worth(String itemId) {
        double unit = ProfitTracker.unitPrice(itemId);
        return unit > 0 ? unit * ProfitTracker.countOf(itemId) : 0;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        page = Math.min(page, pageCount() - 1);

        List<String> items = visible();
        int gridWidth = NAME_WIDTH + 4 + MODE_WIDTH + 4 + PRICE_WIDTH;
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

        // Die Betriebsart steht neben dem Suchfeld: sie entscheidet, was ein Klick
        // auf eine Zeile ueberhaupt bedeutet
        addRenderableWidget(Button.builder(selectionLabel(), button -> {
            cfg().selection = cfg().selection == ModConfig.ProfitSelection.ALL
                    ? ModConfig.ProfitSelection.PICKED : ModConfig.ProfitSelection.ALL;
            ModConfig.INSTANCE.saveNow();
            rebuild();
        }).bounds(left + NAME_WIDTH + 4, 34, MODE_WIDTH, 20).build());

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < items.size(); i++) {
            String itemId = items.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            addRenderableWidget(Button.builder(rowLabel(itemId), button -> {
                ProfitTracker.toggle(itemId);
                button.setMessage(rowLabel(itemId));
            }).bounds(left, y, NAME_WIDTH, 20).build());

            addRenderableWidget(Button.builder(modeLabel(itemId), button -> {
                ProfitTracker.setMode(itemId, nextMode(itemId));
                button.setMessage(modeLabel(itemId));
                rebuild();
            }).bounds(left + NAME_WIDTH + 4, y, MODE_WIDTH, 20).build());

            // Das Feld gehoert nur zu Custom - sonst stuenden vierzig leere Kaesten da
            if (ProfitTracker.hasOwnMode(itemId) && ProfitTracker.modeOf(itemId) == SellMode.CUSTOM) {
                EditBox price = new EditBox(font, left + NAME_WIDTH + 8 + MODE_WIDTH, y,
                        PRICE_WIDTH, 20, Component.literal("Price"));
                double own = ProfitTracker.customPrice(itemId);
                price.setValue(own > 0 ? String.valueOf((long) own) : "");
                price.setHint(Component.literal("per item").withStyle(ChatFormatting.DARK_GRAY));
                price.setResponder(value -> ProfitTracker.setCustomPrice(itemId, ItemValue.parseAmount(value)));
                addRenderableWidget(price);
            }
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Show all"), button -> {
            if (cfg().selection == ModConfig.ProfitSelection.PICKED) {
                cfg().picked.clear();
                for (String itemId : ProfitTracker.seen()) cfg().picked.add(itemId);
            } else {
                cfg().hidden.clear();
            }
            ModConfig.INSTANCE.saveNow();
            rebuild();
        }).bounds(left, y, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Show none"), button -> {
            if (cfg().selection == ModConfig.ProfitSelection.PICKED) {
                cfg().picked.clear();
            } else {
                cfg().hidden.clear();
                for (String itemId : ProfitTracker.seen()) cfg().hidden.add(itemId);
            }
            ModConfig.INSTANCE.saveNow();
            rebuild();
        }).bounds(left + 84, y, 80, 20).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 168, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 192, y, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 60, y, 60, 20).build());
    }

    private static Component selectionLabel() {
        boolean picked = cfg().selection == ModConfig.ProfitSelection.PICKED;
        return Component.literal(picked ? "Picked" : "All")
                .withStyle(picked ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    /** Haken, Name, Stueckzahl und was der Stapel gerade bringt */
    private Component rowLabel(String itemId) {
        boolean on = ProfitTracker.shown(itemId);
        double value = worth(itemId);
        String text = (on ? "☑ " : "☐ ") + ProfitTracker.nameOf(itemId)
                + " x" + ProfitTracker.countOf(itemId);
        String tail = value > 0 ? "  " + ItemValue.format(value) : "  ?";
        return Component.literal(trimToWidth(text, tail) + tail)
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    /** Lieber den Namen kuerzen als den Preis aus dem Knopf schieben */
    private String trimToWidth(String text, String tail) {
        int room = NAME_WIDTH - 12 - font.width(tail);
        if (font.width(text) <= room) return text;
        StringBuilder shortened = new StringBuilder(text);
        while (shortened.length() > 3 && font.width(shortened + "...") > room) {
            shortened.deleteCharAt(shortened.length() - 1);
        }
        return shortened + "...";
    }

    private static Component modeLabel(String itemId) {
        if (!ProfitTracker.hasOwnMode(itemId)) {
            return Component.literal("Default").withStyle(ChatFormatting.DARK_GRAY);
        }
        return switch (ProfitTracker.modeOf(itemId)) {
            case INSTANT_SELL -> Component.literal("Insta").withStyle(ChatFormatting.GREEN);
            case SELL_ORDER -> Component.literal("Order").withStyle(ChatFormatting.AQUA);
            case NPC_SELL -> Component.literal("NPC").withStyle(ChatFormatting.GOLD);
            case CUSTOM -> Component.literal("Custom").withStyle(ChatFormatting.LIGHT_PURPLE);
        };
    }

    /** Default, Insta, Order, NPC, Custom und wieder von vorn */
    private static SellMode nextMode(String itemId) {
        if (!ProfitTracker.hasOwnMode(itemId)) return SellMode.INSTANT_SELL;
        return switch (ProfitTracker.modeOf(itemId)) {
            case INSTANT_SELL -> SellMode.SELL_ORDER;
            case SELL_ORDER -> SellMode.NPC_SELL;
            case NPC_SELL -> SellMode.CUSTOM;
            case CUSTOM -> null;
        };
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.centeredText(font, Component.literal("Profit Tracker - items"), centerX, 14, 0xFFFFFFFF);

        String note = cfg().selection == ModConfig.ProfitSelection.PICKED
                ? "Only picked: an item shows up once you click it"
                : "Everything but hidden: click an item to take it out";
        graphics.centeredText(font, Component.literal(note).withStyle(ChatFormatting.GRAY),
                centerX, 60, 0xFFAAAAAA);

        String foot = visible().isEmpty()
                ? "Nothing found yet - the tracker fills this list while you play"
                : "Default follows the panel setting: " + ProfitTracker.mode();
        graphics.centeredText(font, Component.literal(foot).withStyle(ChatFormatting.GRAY),
                centerX, height - 46, 0xFFAAAAAA);

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 58, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
