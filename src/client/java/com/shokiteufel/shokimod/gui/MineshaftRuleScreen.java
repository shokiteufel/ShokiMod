package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.MineshaftRule;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.MineshaftState;
import com.shokiteufel.shokimod.util.MineshaftCorpses;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ab wann die Leichen-Marker eines Schachts erscheinen - je Bauplan einzeln.
 *
 * Eine Zeile je Bauplan, darin drei Knoepfe: ab wie vielen Leichen, und ob Umber und
 * Tungsten mitzaehlen. Kein Tippen, keine leeren Felder - klicken, bis die Zahl passt.
 * "off" heisst: dieser Bauplan zeigt seine Stellen immer.
 *
 * Vanguard steht nicht dabei. Es gibt ihn nur in einem Bauplan, und dort ist er kein
 * Kriterium, sondern der Grund, ueberhaupt hinzugehen.
 *
 * Der Gedanke stammt vom SkyblockCollectionTracker (LGPL-2.1), der seine Routen nur in
 * Schaechten mit genug Lapis-Leichen zeigt. Dort steht die Schwelle fest; hier steht
 * sie je Bauplan, denn ein Tungsten-Schacht ist fuer andere Sachen gut als ein
 * Peridot-Schacht, und was sich lohnt, weiss der Spieler besser als die Mod.
 */
public class MineshaftRuleScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int NAME_WIDTH = 110;
    private static final int COUNT_WIDTH = 82;
    private static final int TOGGLE_WIDTH = 92;
    private static final int GAP = 4;
    private static final int LIST_TOP = 62;
    /** Mehr als vier Leichen hat kein Schacht; danach faengt die Zahl wieder bei "off" an */
    private static final int MAX_MIN = MineshaftRule.MAX;

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
     * Die Bauplaene, fuer die diese Regel etwas entscheidet.
     *
     * Gelesen aus derselben Liste, aus der auch die Marker kommen - so steht hier genau
     * das, was der Mod bekannt ist, und nicht eine zweite gepflegte Aufzaehlung daneben.
     *
     * Draussen bleiben die Bauplaene ohne Edelstein: Titanium, Tungsten, Umber und
     * Fairy. Die Regel haelt Edelstein-Marker zurueck, und wo keine sind, entscheidet
     * sie nichts - eine Zeile, die nichts tut, ist eine Zeile zu viel. Kommt ein neuer
     * Edelstein-Bauplan dazu, steht er von selbst da.
     *
     * Der Schacht, in dem man gerade steht, kommt nach oben.
     */
    private List<String> shafts() {
        long now = System.currentTimeMillis();
        String current = MineshaftState.type();
        if (shaftsCache == null || MineshaftCorpses.FEED.size() != shaftsFeedSize
                || !Objects.equals(current, shaftsCurrent) || now - shaftsAt >= SHAFTS_MILLIS) {
            shaftsBauen(now, current);
        }
        return shaftsCache;
    }

    // Die Bauplaene samt den Zeilentexten, einmal gebaut und gehalten (vorher je Bild mehrfach: shafts() und pageCount()). Eingaben: die Liste
    // aus dem Netz (kommt nach dem Oeffnen im Hintergrund an - ihre Zahl steht im Schluessel), der Schacht, in dem man steht (Reihenfolge, "(here)"),
    // und sonst spaetestens nach einer Sekunde, falls eine Aktualisierung der Liste dieselbe Zahl hat. Jeder init() baut neu.
    private List<String> shaftsCache;
    private Component[] shaftTexte = new Component[0];
    private int shaftsFeedSize = -1;
    private String shaftsCurrent;
    private long shaftsAt;
    private static final long SHAFTS_MILLIS = 1_000L;

    private void shaftsBauen(long now, String current) {
        List<String> out = new ArrayList<>();
        for (String key : MineshaftCorpses.FEED.keys()) {
            String type = key.contains("_") ? key.substring(0, key.indexOf('_')) : key;
            if (com.shokiteufel.shokimod.util.Gemstones.ofShaft(type) == null) continue;
            if (!out.contains(type)) out.add(type);
        }
        out.sort(String::compareTo);

        if (current != null && out.remove(current)) out.add(0, current);

        // Der Text je Zeile ("Umber (here)", gruen; sonst weiss) steht gleich mit der Liste fertig da
        Component[] texte = new Component[out.size()];
        for (int i = 0; i < texte.length; i++) {
            String shaft = out.get(i);
            boolean here = shaft.equals(current);
            texte[i] = FensterText.format(MineshaftState.readable(shaft) + (here ? " (here)" : ""),
                    here ? ChatFormatting.GREEN : ChatFormatting.WHITE);
        }

        shaftsCache = out;
        shaftTexte = texte;
        shaftsFeedSize = MineshaftCorpses.FEED.size();
        shaftsCurrent = current;
        shaftsAt = now;
    }

    private static MineshaftRule rule(String shaft) {
        return cfg().shaftRules.computeIfAbsent(shaft, key -> new MineshaftRule());
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 60 - LIST_TOP) / ROW_HEIGHT);
    }

    private int pageCount() {
        return Math.max(1, (shafts().size() + rowsPerPage() - 1) / rowsPerPage());
    }

    private int gridWidth() {
        return NAME_WIDTH + COUNT_WIDTH + GAP + (TOGGLE_WIDTH + GAP) * 2;
    }

    @Override
    protected void init() {
        MineshaftCorpses.prefetch();
        // Jeder Neuaufbau (auch nach "Clear all" oder Groessenaenderung): die Liste neu bauen lassen
        shaftsCache = null;
        page = Math.min(page, pageCount() - 1);

        List<String> shafts = shafts();
        int left = width / 2 - gridWidth() / 2;
        int start = page * rowsPerPage();

        for (int i = 0; i < rowsPerPage() && start + i < shafts.size(); i++) {
            String shaft = shafts.get(start + i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int x = left + NAME_WIDTH;

            // Die Zahl: ein Knopf, der weiterzaehlt. Nach der letzten Stufe wieder "off"
            addRenderableWidget(Button.builder(countLabel(shaft), button -> {
                MineshaftRule rule = rule(shaft);
                rule.min = nextStep(rule.min);
                ModConfig.INSTANCE.saveNow();
                button.setMessage(countLabel(shaft));
            }).bounds(x, y, COUNT_WIDTH, 20).build());
            x += COUNT_WIDTH + GAP;

            addRenderableWidget(Button.builder(toggleLabel("Umber", rule(shaft).withUmber), button -> {
                MineshaftRule rule = rule(shaft);
                rule.withUmber = !rule.withUmber;
                ModConfig.INSTANCE.saveNow();
                button.setMessage(toggleLabel("Umber", rule.withUmber));
            }).bounds(x, y, TOGGLE_WIDTH, 20).build());
            x += TOGGLE_WIDTH + GAP;

            addRenderableWidget(Button.builder(toggleLabel("Tungsten", rule(shaft).withTungsten), button -> {
                MineshaftRule rule = rule(shaft);
                rule.withTungsten = !rule.withTungsten;
                ModConfig.INSTANCE.saveNow();
                button.setMessage(toggleLabel("Tungsten", rule.withTungsten));
            }).bounds(x, y, TOGGLE_WIDTH, 20).build());
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
                .bounds(left + gridWidth() - 60, y, 60, 20).build());
    }

    /**
     * "Corpses: off" oder "Corpses: 2" - gruen, sobald eine Schwelle steht.
     *
     * Nicht "Lapis": Mit den Haken daneben zaehlt die Zahl alle angehakten Sorten
     * zusammen, ist also eine Leichenzahl. "Lapis: 3" hat genau diese Verwechslung
     * ausgeloest - zwei Lapis und eine Umber erfuellen sie naemlich auch.
     */
    private static Component countLabel(String shaft) {
        int min = rule(shaft).min;
        if (min == MineshaftRule.NEVER) {
            return Component.literal("Don't show").withStyle(ChatFormatting.RED);
        }
        return Component.literal(min <= 0 ? "Corpses: off" : "Corpses: " + min)
                .withStyle(min <= 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN);
    }

    /**
     * Die naechste Stufe des Knopfes: aus, eins, zwei, drei, vier, "gar nicht", wieder aus.
     *
     * Die Vier ist fest, auch mit angehakten Sorten: Ein Schacht hat nie mehr Leichen.
     * Die Haken sagen nur, welche davon mitzaehlen - Lapis allein, oder Lapis mit den
     * angehakten Sorten zusammen.
     */
    static int nextStep(int min) {
        if (min == MineshaftRule.NEVER) return 0;
        if (min >= MAX_MIN) return MineshaftRule.NEVER;
        return min + 1;
    }

    /** "+ Umber" gruen, wenn es mitzaehlt, sonst dunkel */
    private static Component toggleLabel(String type, boolean on) {
        return Component.literal((on ? "+ " : "- ") + type)
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        // Feste Texte: je Zeichenkette einmal gebaut und geteilt, nicht in jedem Bild
        graphics.centeredText(font, FensterText.text("Mineshaft - when to mark the gemstone veins"), centerX, 16, 0xFFFFFFFF);
        graphics.centeredText(font, FensterText.grau(
                "Per layout: from how many corpses in the shaft on. Lapis always counts; Umber and Tungsten add to the same number when switched on."),
                centerX, 32, 0xFFAAAAAA);

        // Liste und Zeilentexte liegen fertig vor (neu gebaut nur bei Aenderung, siehe shafts())
        List<String> shafts = shafts();
        Component[] texte = shaftTexte;
        int left = width / 2 - gridWidth() / 2;
        int proSeite = rowsPerPage();
        int start = page * proSeite;

        for (int i = 0; i < proSeite && start + i < shafts.size(); i++) {
            graphics.text(font, texte[start + i], left, LIST_TOP + i * ROW_HEIGHT + 6, 0xFFFFFFFF);
        }

        int seiten = Math.max(1, (shafts.size() + proSeite - 1) / proSeite);
        if (seiten > 1) {
            graphics.centeredText(font, FensterText.seite(page, seiten), centerX, height - 46, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        ModConfig.INSTANCE.saveNow();
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
