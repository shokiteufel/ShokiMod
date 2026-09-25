package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.MineshaftRule;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.MineshaftState;
import com.shokiteufel.shokimod.util.MineshaftCorpses;

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
 * Wann die Leichen-Marker eines Schachts erscheinen sollen - je Bauplan einzeln.
 *
 * Eine Zeile je Bauplan, dahinter vier Felder: wie viele Lapis, Umber, Tungsten und
 * Vanguard drin sein muessen. Leer oder null heisst "ist mir egal". Wer gar nichts
 * eintraegt, bekommt die Marker ueberall - so wie vorher.
 *
 * Der Gedanke stammt vom SkyblockCollectionTracker (LGPL-2.1), der seine Routen nur
 * in Schaechten mit genug Lapis-Leichen zeigt. Hier steht die Bedingung nicht fest,
 * sondern je Bauplan: Ein Tungsten-Schacht ist fuer andere Sachen gut als ein
 * Peridot-Schacht, und was sich lohnt, weiss der Spieler besser als die Mod.
 */
public class MineshaftRuleScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int NAME_WIDTH = 120;
    private static final int FIELD_WIDTH = 46;
    private static final int GAP = 4;
    private static final int LIST_TOP = 78;

    private final Screen parent;
    private int page;

    public MineshaftRuleScreen(Screen parent) {
        super(Component.literal("Mineshaft"));
        this.parent = parent;
    }

    private static ModConfig.MineshaftCategory cfg() {
        return ModConfig.INSTANCE.mining.mineshaft;
    }

    /**
     * Die Bauplaene, fuer die es Stellen gibt.
     *
     * Gelesen aus derselben Liste, aus der auch die Marker kommen - so steht hier
     * genau das, was der Mod bekannt ist, und nicht eine zweite gepflegte Aufzaehlung
     * daneben. Der Schacht, in dem man gerade steht, kommt nach oben.
     */
    private List<String> shafts() {
        List<String> out = new ArrayList<>();
        for (String key : MineshaftCorpses.FEED.keys()) {
            String type = key.contains("_") ? key.substring(0, key.indexOf('_')) : key;
            if (!out.contains(type)) out.add(type);
        }
        out.sort(String::compareTo);

        String current = MineshaftState.type();
        if (current != null && out.remove(current)) out.add(0, current);
        return out;
    }

    private static MineshaftRule rule(String shaft) {
        return cfg().shaftRules.computeIfAbsent(shaft, key -> new MineshaftRule());
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 70 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (shafts().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    @Override
    protected void init() {
        MineshaftCorpses.prefetch();
        page = Math.min(page, pageCount() - 1);

        List<String> shafts = shafts();
        int gridWidth = NAME_WIDTH + (FIELD_WIDTH + GAP) * MineshaftRule.TYPES.length;
        int left = width / 2 - gridWidth / 2;

        // Die Bedingung gilt fuer alle Zeilen, deshalb steht sie ueber ihnen
        addRenderableWidget(Button.builder(matchLabel(), button -> {
            cfg().shaftRuleAll = !cfg().shaftRuleAll;
            ModConfig.INSTANCE.saveNow();
            button.setMessage(matchLabel());
        }).bounds(left, 46, 150, 20).build());

        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < shafts.size(); i++) {
            String shaft = shafts.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;

            for (int t = 0; t < MineshaftRule.TYPES.length; t++) {
                String type = MineshaftRule.TYPES[t];
                int x = left + NAME_WIDTH + t * (FIELD_WIDTH + GAP);

                EditBox field = new EditBox(font, x, y, FIELD_WIDTH, 20, Component.literal(type));
                int value = rule(shaft).minimum(type);
                field.setValue(value > 0 ? String.valueOf(value) : "");
                field.setHint(Component.literal("-").withStyle(ChatFormatting.DARK_GRAY));
                field.setResponder(text -> rule(shaft).setMinimum(type, number(text)));
                addRenderableWidget(field);
            }
        }

        int y = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Clear all"), button -> {
            cfg().shaftRules.clear();
            ModConfig.INSTANCE.saveNow();
            rebuild();
        }).bounds(left, y, 80, 20).build());

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), button -> {
                page = (page - 1 + pageCount()) % pageCount();
                rebuild();
            }).bounds(left + 84, y, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), button -> {
                page = (page + 1) % pageCount();
                rebuild();
            }).bounds(left + 108, y, 20, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + gridWidth - 60, y, 60, 20).build());
    }

    private static Component matchLabel() {
        boolean all = cfg().shaftRuleAll;
        return Component.literal(all ? "Match: all of them" : "Match: any of them")
                .withStyle(all ? ChatFormatting.AQUA : ChatFormatting.GREEN);
    }

    private static int number(String text) {
        if (text == null || text.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.centeredText(font, Component.literal("Mineshaft - when to show the corpse spots"),
                centerX, 14, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal(
                        "Per layout: how many corpses of each kind it takes. Empty means it does not matter.")
                .withStyle(ChatFormatting.GRAY), centerX, 28, 0xFFAAAAAA);

        List<String> shafts = shafts();
        int gridWidth = NAME_WIDTH + (FIELD_WIDTH + GAP) * MineshaftRule.TYPES.length;
        int left = width / 2 - gridWidth / 2;

        // Die Ueberschriften der vier Spalten
        for (int t = 0; t < MineshaftRule.TYPES.length; t++) {
            int x = left + NAME_WIDTH + t * (FIELD_WIDTH + GAP);
            graphics.text(font, Component.literal(MineshaftRule.TYPES[t]).withStyle(ChatFormatting.GRAY),
                    x, LIST_TOP - 11, 0xFFAAAAAA);
        }

        String current = MineshaftState.type();
        int start = page * rowsPerPage();
        for (int i = 0; i < rowsPerPage() && start + i < shafts.size(); i++) {
            String shaft = shafts.get(start + i);
            boolean here = shaft.equals(current);
            graphics.text(font,
                    Component.literal(name(shaft) + (here ? " (here)" : ""))
                            .withStyle(here ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                    left, LIST_TOP + i * ROW_HEIGHT + 6, 0xFFFFFFFF);
        }

        if (pageCount() > 1) {
            graphics.centeredText(font, Component.literal((page + 1) + " / " + pageCount())
                    .withStyle(ChatFormatting.GRAY), centerX, height - 46, 0xFFAAAAAA);
        }
    }

    /** TUNG wird zu "Tungsten" - die Seitenleiste kuerzt, das Menue nicht */
    private static String name(String shaft) {
        return switch (shaft) {
            case "FAIR" -> "Fairy";
            case "LITT" -> "Little";
            case "TITA" -> "Titanium";
            case "TUNG" -> "Tungsten";
            case "UMBE" -> "Umber";
            case "RUBY" -> "Ruby";
            case "JADE" -> "Jade";
            case "SAPP" -> "Sapphire";
            case "AMBE" -> "Amber";
            case "AMET" -> "Amethyst";
            case "TOPA" -> "Topaz";
            case "JASP" -> "Jasper";
            case "OPAL" -> "Opal";
            case "ONYX" -> "Onyx";
            case "CITR" -> "Citrine";
            case "PERI" -> "Peridot";
            case "AQUA" -> "Aquamarine";
            default -> shaft.charAt(0) + shaft.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
