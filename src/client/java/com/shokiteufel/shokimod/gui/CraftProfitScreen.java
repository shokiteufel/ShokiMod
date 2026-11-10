package com.shokiteufel.shokimod.gui;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.BazaarLive;
import com.shokiteufel.shokimod.util.CraftProfitData;
import com.shokiteufel.shokimod.util.CraftProfitData.Row;
import com.shokiteufel.shokimod.util.ItemValue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Was sich zu bauen lohnt - Rezepte, bewertet mit den Preisen von jetzt.
 *
 * Gesucht wird in beiden Richtungen: im Ergebnis und in den Zutaten. Wer "Enchanted
 * String" eintippt, will die dreiundzwanzig Rezepte sehen, die es verbrauchen -
 * nicht nur das eine, das es herstellt.
 *
 * Ein Klick auf eine Zeile oeffnet das Rezept im Spiel, mit dem sich das Ding gleich
 * bauen laesst.
 *
 * Die Preise kommen aus {@link ItemValue} und {@link com.shokiteufel.shokimod.util.BazaarLive}:
 * Bazaar, wo es einen gibt, sonst der Tiefstpreis im Auktionshaus. Was sich gar
 * nicht bewerten laesst, steht unten und sagt das auch.
 */
public class CraftProfitScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    // Breiter geworden mit der Forge: Zeit und Stundenlohn sind zwei Spalten, die
    // es vorher nicht gab, und beide zu quetschen haette die Zahlen unleserlich
    // gemacht, auf die es gerade ankommt
    private static final int LIST_WIDTH = 600;
    private static final int LIMIT = 500;

    /** Ueberdauert das Schliessen - wer weitersucht, faengt nicht von vorn an */
    private static String search = "";
    private static boolean onlyProfitable = true;
    /**
     * Wo das Ergebnis verkauft wird.
     *
     * Nicht dasselbe wie sofort oder per Auftrag - das ist die Frage nach dem Haus.
     * Manches laesst sich an beiden Orten losschlagen, und die Preise gehen weit
     * auseinander: Was im Bazaar zu Tausenden gehandelt wird, bringt im Auktionshaus
     * mitunter das Doppelte, dafuer einzeln und mit Wartezeit.
     */
    private static boolean sellToBazaar = true;
    /**
     * Welche Rezeptart die Liste zeigt.
     *
     * Zwei verschiedene Fragen: Am Handwerkstisch steht man einmal und hat es
     * gleich; in der Forge legt man etwas hin und kommt Stunden spaeter wieder.
     */
    private static CraftProfitData.Kind kind = CraftProfitData.Kind.ALL;
    /** Nach Gewinn je Stunde reihen statt nach Gewinn */
    private static boolean perHour = false;

    private final Screen parent;
    private EditBox searchBox;
    private List<Row> cached;
    /** Wie alt der Live-Stand beim letzten Bild war - fuer das Neuberechnen */
    private int lastLiveAge = -1;
    private int listTop = 74;
    private int page;
    /** Zaehlt mit, wie oft ein neuer Live-Stand eingetroffen ist (das Alter wurde kleiner) - die Zeilentexte haengen an ihm */
    private int liveStand;

    // Gemerkte Texte: gebaut nur, wenn sich ihre Quellen aendern, nicht in jedem Bild
    /** Die graue Statuszeile oben ("12 of 2500 sellable ...") */
    private final FensterText.Gemerkt info = new FensterText.Gemerkt();
    private int infoFarbe = 0xFF888888;
    /** "Nothing found for ..." - gebaut nur, wenn sich der Suchtext aendert */
    private final FensterText.Gemerkt nichtsGefunden = new FensterText.Gemerkt();
    /** Die Forge-Rezepte werden gezaehlt (Schleife ueber alle Rezepte), aber nur, wenn sich die Zahl der Rezepte aendert */
    private int forgeFuerRezepte = -1;
    private int forgeAnzahl;

    /**
     * Die fertig geformten Texte einer Zeile: gekuerzte Namen, Zahlen mit "k/m/b", Farben.
     *
     * Eine Zeile aendert sich nicht, solange es dieselbe {@link Row} ist - Preise, Dauer und Gewinn stehen in ihr. Gerechnet wird also
     * einmal, wenn die Zeile auf die Seite kommt, nicht in jedem Bild.
     */
    private static final class ZeilenText {
        String ergebnis;
        String teile;
        String dauer;
        int dauerFarbe;
        /** false: kein Preis, es steht nur "no price" */
        boolean bewertet;
        String kosten;
        String erloes;
        String gewinn;
        int gewinnFarbe;
        String stundenlohn;
        int stundenFarbe;
        String tag;
        int tagFarbe;

        ZeilenText(Row row, long proTag) {
            ergebnis = cut(row.yield(), 22);
            teile = cut(row.partsText(), 26);
            // Die Wartezeit. Ein Strich heisst: sofort fertig, es gibt keine
            boolean wartet = row.seconds() > 0;
            dauer = wartet ? row.durationText() : "-";
            dauerFarbe = wartet ? 0xFFFFAA00 : 0xFF666666;
            bewertet = row.complete();
            if (bewertet) {
                long gewinnWert = row.profit();
                kosten = ItemValue.format(row.cost());
                erloes = ItemValue.format(row.revenue());
                gewinn = ItemValue.format(gewinnWert);
                gewinnFarbe = gewinnWert > 0 ? 0xFF55FF55 : 0xFFFF5555;
                // Der Stundenlohn steht nur, wo gewartet wird. Was sofort fertig ist,
                // hat keinen - und eine Zahl dahin zu schreiben hiesse, zwei Dinge
                // ohne gemeinsame Einheit nebeneinanderzustellen
                if (wartet) {
                    long proStunde = row.profitPerHour();
                    stundenlohn = ItemValue.format(proStunde);
                    stundenFarbe = proStunde > 0 ? 0xFF55FF55 : 0xFFFF5555;
                } else {
                    stundenlohn = "-";
                    stundenFarbe = 0xFF666666;
                }
            }
            tag = proTag < 0 ? "-" : ItemValue.format(proTag);
            tagFarbe = mengeFarbe(proTag);
        }
    }

    // Die Texte der Zeilen auf der gezeigten Seite. Neu gebaut, wenn sich die Liste (neue Zeilen-Objekte), der Seitenanfang, die Zeilenzahl je
    // Seite, der Live-Zustand der Preise oder der Live-Stand aendert - die Spalte "Sold/day" kommt aus den Live-Preisen und nicht aus der Zeile
    private ZeilenText[] zeilenTexte = new ZeilenText[0];
    private List<Row> zeilenTexteListe;
    private int zeilenTexteStart = -1;
    private int zeilenTexteProSeite = -1;
    private boolean zeilenTexteLive;
    private int zeilenTexteStand = -1;

    // Der Tooltip mit den Materialien: gebaut, wenn der Mauszeiger auf eine andere Zeile (oder die Schrift sich) wechselt, nicht in jedem Bild
    private Row tipZeile;
    private Font tipSchrift;
    private List<String> tipTexte = List.of();
    private int tipBreite;

    public CraftProfitScreen(Screen parent) {
        super(Component.literal("Craft profit"));
        this.parent = parent;
    }

    private static ModConfig.FusionCategory cfg() {
        return ModConfig.INSTANCE.hunting.fusion;
    }

    /** Dieselbe Wahl wie bei den Fusionen - zwei Schalter fuer dasselbe waeren einer zu viel */
    private static boolean instantBuy() {
        return cfg().ingredientMode == ModConfig.FusionCategory.BuyMode.INSTANT_BUY;
    }

    private static boolean instantSell() {
        return cfg().resultMode == ItemValue.PriceMode.INSTANT_SELL;
    }

    private int left() {
        return (width - LIST_WIDTH) / 2;
    }

    private int perPage() {
        return Math.max(1, (height - 60 - listTop) / ROW_HEIGHT);
    }

    private List<Row> visible() {
        if (cached == null) {
            cached = CraftProfitData.select(search, instantBuy(), instantSell(),
                    sellToBazaar, onlyProfitable, kind, perHour,
                    cfg().quickForgePercent, LIMIT);
        }
        return cached;
    }

    private void invalidate() {
        cached = null;
    }

    private int pageCount() {
        return Math.max(1, (visible().size() + perPage() - 1) / perPage());
    }

    /**
     * Die zusammengesetzten Texte des Bildes (Statuszeile, "Nothing found"): gebaut nur, wenn sich ihre Quellen aendern.
     * Der Schluessel fasst alles zusammen, was in die Statuszeile eingeht: Zahl der Zeilen, Rezeptart, Verkaufsort, Live-Zustand mit Alter
     * (nur wo es angezeigt wird), ob gesucht wird, Zahl der Rezepte.
     */
    private void texteAktualisieren(int anzahl, boolean live, int liveAlter) {
        int rezepte = CraftProfitData.recipeCount();
        if (rezepte != forgeFuerRezepte) {
            forgeAnzahl = CraftProfitData.forgeCount();
            forgeFuerRezepte = rezepte;
        }
        boolean gruen = sellToBazaar && live;
        // Das Alter ist nur dort im Schluessel, wo die Zeile es zeigt (Bazaar mit frischem Stand) - sonst wuerde sie jede Sekunde neu gebaut
        int alter = gruen ? Math.clamp(liveAlter, 0, 999_999) : 0;
        long schluessel = anzahl;
        schluessel = schluessel * 8 + kind.ordinal();
        schluessel = schluessel * 2 + (sellToBazaar ? 1 : 0);
        schluessel = schluessel * 2 + (gruen ? 1 : 0);
        schluessel = schluessel * 2 + (search.isBlank() ? 1 : 0);
        schluessel = schluessel * 1_000_000L + alter;
        schluessel = schluessel * 100_000L + (rezepte % 100_000);
        if (!info.gilt(schluessel)) {
            // Wie frisch die Zahlen sind, gehoert daneben. Beim Bazaar sind es Sekunden,
            // beim Auktionshaus Minuten - das ist ein Unterschied, den man kennen sollte
            String stand = sellToBazaar
                    ? (live ? ", live prices " + alter + "s old"
                            : ", bazaar prices up to 10 min old")
                    : ", auction prices up to 10 min old";
            info.setze(schluessel, Component.literal(
                            anzahl + " of "
                            + (kind == CraftProfitData.Kind.FORGE_ONLY
                                    ? forgeAnzahl + " forge recipes"
                                    : String.valueOf(rezepte))
                            + (sellToBazaar ? " sellable on the bazaar"
                                            : " sellable on the auction house")
                            + stand
                            + (search.isBlank() ? " - type an item to narrow it down"
                                                : " - click a row for /recipe"))
                    .withStyle(ChatFormatting.DARK_GRAY));
            infoFarbe = gruen ? 0xFF77DD77 : 0xFF888888;
        }
        if (anzahl == 0 && !search.isBlank() && !nichtsGefunden.gilt(0L, search)) {
            nichtsGefunden.setze(0L, search, Component.literal("Nothing found for \"" + search + "\"")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Die Texte der Zeilen auf der gezeigten Seite: gerechnet, wenn eine andere Seite oder Liste gezeigt wird oder sich die Live-Preise
     * aendern (Sold/day) - sonst kommt dasselbe Feld zurueck wie im Bild davor.
     */
    private void zeilenTexteAktualisieren(List<Row> zeilen, int start, int proSeite, boolean live) {
        if (zeilen == zeilenTexteListe && start == zeilenTexteStart && proSeite == zeilenTexteProSeite
                && live == zeilenTexteLive && liveStand == zeilenTexteStand) {
            return;
        }
        int n = Math.max(0, Math.min(proSeite, zeilen.size() - start));
        ZeilenText[] neu = new ZeilenText[n];
        for (int i = 0; i < n; i++) {
            Row row = zeilen.get(start + i);
            // Der Tagesumsatz. Liegt kein frischer Bazaar-Stand vor, steht hier ein
            // Strich statt einer erfundenen Zahl - der Wochenwert kommt nur mit den
            // Live-Daten, die gespeicherte Datei fuehrt ihn nicht
            neu[i] = new ZeilenText(row, BazaarLive.soldPerDay(row.id()));
        }
        zeilenTexte = neu;
        zeilenTexteListe = zeilen;
        zeilenTexteStart = start;
        zeilenTexteProSeite = proSeite;
        zeilenTexteLive = live;
        zeilenTexteStand = liveStand;
    }

    /**
     * Der Tooltip zu einer Zeile: Was jedes Teil kostet, einzeln und mal Menge. Die Zeile ist unveraenderlich (Preise stehen in ihren Teilen),
     * also genuegt es, neu zu bauen, wenn der Mauszeiger auf eine andere Zeile wechselt oder die Schrift eine andere ist (Breite).
     */
    private void tooltipAktualisieren(Row row) {
        if (row == tipZeile && font == tipSchrift) return;
        List<String> lines = new ArrayList<>();
        lines.add("§6" + row.yield());
        long sum = 0;
        for (CraftProfitData.Part p : row.parts()) {
            if (p.unit() > 0) {
                long total = p.unit() * p.amount();
                sum += total;
                lines.add("§f" + p.amount() + "x " + p.name() + " §7- " + ItemValue.format(p.unit())
                        + " each = §c" + ItemValue.format(total));
            } else {
                lines.add("§f" + p.amount() + "x " + p.name() + " §7- §8no price");
            }
        }
        if (row.parts().size() > 1 && row.complete()) lines.add("§7Total: §c" + ItemValue.format(sum));

        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        tipZeile = row;
        tipSchrift = font;
        tipTexte = lines;
        tipBreite = w;
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        int left = left();
        int sucheY = 44;
        listTop = 82;

        searchBox = new EditBox(font, left, sucheY, 220, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search an item ..."));
        searchBox.setMaxLength(48);
        searchBox.setValue(search);
        searchBox.setResponder(text -> {
            search = text == null ? "" : text.trim();
            page = 0;
            invalidate();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(
                Component.literal(sellToBazaar ? "Sell to BZ" : "Sell to AH")
                        .withStyle(ChatFormatting.AQUA),
                button -> {
                    sellToBazaar = !sellToBazaar;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 226, sucheY, 100, 18).build());

        addRenderableWidget(Button.builder(
                Component.literal((onlyProfitable ? "☑" : "☐") + " Profit"),
                button -> {
                    onlyProfitable = !onlyProfitable;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 330, sucheY, 80, 18).build());

        // Handwerkstisch oder Forge. Wer wissen will, was sich ueber Nacht lohnt,
        // will die zweitausendfuenfhundert Handwerksrezepte nicht dazwischen haben
        addRenderableWidget(Button.builder(
                Component.literal("Type: " + kind.label).withStyle(ChatFormatting.GOLD),
                button -> {
                    CraftProfitData.Kind[] alle = CraftProfitData.Kind.values();
                    kind = alle[(kind.ordinal() + 1) % alle.length];
                    // In der Forge ist der Stundenlohn die Zahl, auf die es ankommt -
                    // also stellt sich die Reihung mit um, statt darauf zu warten,
                    // dass jemand den zweiten Knopf findet
                    perHour = kind == CraftProfitData.Kind.FORGE_ONLY;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 414, sucheY, 86, 18).build());

        Button reihung = Button.builder(
                Component.literal(perHour ? "Sort: Coins/h" : "Sort: Profit")
                        .withStyle(ChatFormatting.AQUA),
                button -> {
                    perHour = !perHour;
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 504, sucheY, 96, 18).build();
        // Der Hinweis gehoert dazu, sonst ist die Liste irrefuehrend: Ganz oben
        // stehen die Rezepte von dreissig Sekunden, und ihr Stundenlohn geht in die
        // Milliarden - aber ihre Teile entstehen anderswo und brauchen selbst Tage.
        // Die Zahl stimmt, sie ist nur keine Verdienstmoeglichkeit
        reihung.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Coins per hour of forge time. The thirty-second "
                        + "recipes lead this list because their parts are forged "
                        + "elsewhere - the number is right, the loop is not.")));
        addRenderableWidget(reihung);

        int unten = height - 28;
        // Die Pfeile links und rechts der Seitenzahl, nicht in der Ecke: Wo man ist
        // und wie man weiterkommt, gehoert zusammen
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

        addRenderableWidget(Button.builder(
                Component.literal("Buy: " + cfg().ingredientMode).withStyle(ChatFormatting.AQUA),
                button -> {
                    cfg().ingredientMode =
                            instantBuy() ? ModConfig.FusionCategory.BuyMode.BUY_ORDER
                                         : ModConfig.FusionCategory.BuyMode.INSTANT_BUY;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 52, unten, 130, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal("Sell: " + cfg().resultMode).withStyle(ChatFormatting.GREEN),
                button -> {
                    cfg().resultMode = instantSell() ? ItemValue.PriceMode.SELL_ORDER
                                                     : ItemValue.PriceMode.INSTANT_SELL;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 186, unten, 130, 20).build());

        // Die eigene Forge laeuft schneller, wenn der Perk sitzt. Der Schritt von
        // fuenf reicht: Es geht darum, dass die angezeigte Zeit ungefaehr die eigene
        // ist, nicht um das letzte Prozent
        addRenderableWidget(Button.builder(
                Component.literal("Quick Forge: "
                                + cfg().quickForgePercent + "%")
                        .withStyle(ChatFormatting.GOLD),
                button -> {
                    int jetzt = cfg().quickForgePercent;
                    cfg().quickForgePercent = jetzt >= 30 ? 0 : jetzt + 5;
                    ModConfig.INSTANCE.saveNow();
                    page = 0;
                    invalidate();
                    rebuild();
                }).bounds(left + 320, unten, 130, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(left + LIST_WIDTH - 80, unten, 80, 20).build());
    }

    /**
     * Ein Klick auf eine Zeile zeigt das Rezept im Spiel.
     *
     * Hypixel kennt dafuer /recipe mit dem Namen des Gegenstands und macht dabei
     * gleich das Handwerksfenster auf, aus dem sich das Ding bauen laesst. Das
     * Fenster schliesst vorher, sonst laege es darueber - dieselbe Regel wie beim
     * Sprung in eine Auktion.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // Erst die Knoepfe fragen: Sonst faengt die Liste einen Klick ab, der einem
        // Schalter galt - und statt der Einstellung ginge ein Rezept auf
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!CraftProfitData.ready()) return false;

        double x = event.x();
        double y = event.y();
        int left = left();
        if (y < listTop - 3 || x < left || x > left + LIST_WIDTH) return false;

        int index = (int) ((y - listTop + 3) / ROW_HEIGHT);
        List<Row> zeilen = visible();
        int start = page * perPage();
        if (index < 0 || index >= perPage() || start + index >= zeilen.size()) return false;

        Row row = zeilen.get(start + index);
        if (minecraft == null || minecraft.player == null) return false;
        minecraft.setScreenAndShow(null);
        // Buecher haben kein Rezept - sie werden am Amboss zusammengelegt. Dort fuehrt
        // der Basar weiter als ein Fenster, das es fuer sie gar nicht gibt
        minecraft.player.connection.sendCommand(
                CraftProfitData.BOOK.equals(row.type())
                        ? "bz " + com.shokiteufel.shokimod.util.BazaarFlips.searchName(row.id())
                        : "recipe " + row.name());
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        boolean behandelt = super.keyPressed(event);
        if (searchBox != null && searchBox.isFocused() && !search.equals(searchBox.getValue().trim())) {
            search = searchBox.getValue().trim();
            invalidate();
            rebuild();
        }
        return behandelt;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Solange jemand hinsieht, werden die Preise direkt bei Hypixel geholt
        BazaarLive.wanted();
        // Frische Preise heissen andere Zahlen - eine festgehaltene Auswahl zeigte
        // sonst den Stand vom Oeffnen, waehrend daneben "3s old" steht
        int alter = BazaarLive.ageSeconds();
        if (alter >= 0 && alter != lastLiveAge) {
            // Ein kleineres Alter als zuvor heisst: ein neuer Stand ist eingetroffen - die Zeilentexte (Sold/day) haengen daran
            if (alter < lastLiveAge) liveStand++;
            lastLiveAge = alter;
            if (alter <= 1) invalidate();
        }
        int left = left();
        int centerX = width / 2;

        graphics.centeredText(font, FensterText.gold("Craft profit"), centerX, 14, 0xFFFFAA00);

        if (!CraftProfitData.ready()) {
            graphics.centeredText(font, FensterText.grau("Loading the recipes ..."), centerX, listTop + 20, 0xFFAAAAAA);
            return;
        }

        List<Row> zeilen = visible();
        boolean live = BazaarLive.fresh();
        // Die Statuszeile ist fertig gebaut und wird nur neu gebaut, wenn sich ihre Zahlen aendern
        texteAktualisieren(zeilen.size(), live, alter);
        graphics.centeredText(font, info.component(), centerX, 28, infoFarbe);

        graphics.text(font, "Craft", left + 4, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Materials", left + 140, listTop - 12, 0xFF888888, false);
        // Die Forge-Zeit. Ohne sie waere ein Gewinn nichtssagend: Zehn Millionen in
        // dreissig Sekunden und zehn Millionen in fuenfzig Stunden sind nicht
        // dasselbe Geschaeft
        graphics.text(font, "Time", left + 300, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Cost", left + 340, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Sells for", left + 396, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Profit", left + 452, listTop - 12, 0xFF888888, false);
        graphics.text(font, "Coins/h", left + 508, listTop - 12, 0xFF888888, false);
        // Wie viele Stueck taeglich weggehen. Der Gewinn allein sagt nicht, ob sich
        // die Ware ueberhaupt bewegt - wer hundert baut und taeglich fuenf verkauft,
        // sitzt lange darauf
        graphics.text(font, "Sold/day", left + 560, listTop - 12, 0xFF888888, false);

        // Die Texte der gezeigten Zeilen liegen fertig vor (gekuerzt, formatiert, mit Farbe) und werden nur neu gebaut, wenn Zeilen,
        // Seite oder Live-Preise wechseln
        int proSeite = perPage();
        int start = page * proSeite;
        zeilenTexteAktualisieren(zeilen, start, proSeite, live);
        ZeilenText[] texte = zeilenTexte;
        for (int i = 0; i < texte.length; i++) {
            ZeilenText t = texte[i];
            int y = listTop + i * ROW_HEIGHT;
            if ((i & 1) == 0) graphics.fill(left, y - 3, left + LIST_WIDTH, y + ROW_HEIGHT - 4, 0x30000000);

            graphics.text(font, t.ergebnis, left + 4, y, 0xFFFFCC66, false);
            graphics.text(font, t.teile, left + 140, y, 0xFFBBBBBB, false);
            graphics.text(font, t.dauer, left + 300, y, t.dauerFarbe, false);

            if (!t.bewertet) {
                // Ohne Preis ist jede Zahl hier eine Behauptung. Lieber ein Strich
                graphics.text(font, "no price", left + 340, y, 0xFF888888, false);
            } else {
                graphics.text(font, t.kosten, left + 340, y, 0xFFFF7777, false);
                graphics.text(font, t.erloes, left + 396, y, 0xFFAAAAFF, false);
                graphics.text(font, t.gewinn, left + 452, y, t.gewinnFarbe, false);
                graphics.text(font, t.stundenlohn, left + 508, y, t.stundenFarbe, false);
            }

            graphics.text(font, t.tag, left + 560, y, t.tagFarbe, false);
        }

        drawMaterialTooltip(graphics, zeilen, mouseX, mouseY);

        if (zeilen.isEmpty()) {
            graphics.centeredText(font, search.isBlank()
                            ? FensterText.grau("Nothing pays off right now - try the other trade route")
                            : nichtsGefunden.component(),
                    centerX, listTop + 20, 0xFFAAAAAA);
        }
        if (pageCount() > 1) {
            // Mittig zwischen den beiden Pfeilen, auf ihrer Hoehe
            graphics.centeredText(font, FensterText.seite(page, pageCount()), centerX, height - 44, 0xFFAAAAAA);
        }
    }

    /**
     * Zeigt auf die Materialien einer Zeile: Was jedes Teil kostet, einzeln und mal Menge.
     *
     * Die Spalte ist abgeschnitten, und eine Summe allein sagt nicht, welches Teil das Rezept
     * teuer macht - genau das will man vor dem Bauen wissen.
     */
    private void drawMaterialTooltip(GuiGraphicsExtractor graphics, List<Row> zeilen, int mouseX, int mouseY) {
        int left = left();
        if (mouseX < left + 140 || mouseX > left + 296 || mouseY < listTop - 3) return;
        int index = (mouseY - listTop + 3) / ROW_HEIGHT;
        int start = page * perPage();
        if (index < 0 || index >= perPage() || start + index >= zeilen.size()) return;

        // Texte und Breite des Tooltips liegen fertig vor und werden nur neu gebaut, wenn der Zeiger auf eine andere Zeile wechselt
        tooltipAktualisieren(zeilen.get(start + index));
        List<String> lines = tipTexte;
        int w = tipBreite;
        int x = mouseX + 10;
        int y = mouseY + 10;
        int h = lines.size() * 10;
        if (x + w + 8 > width) x = Math.max(0, mouseX - w - 14);
        if (y + h + 6 > height) y = Math.max(0, height - h - 6);
        graphics.fill(x - 3, y - 3, x + w + 3, y + h + 1, 0xF0100010);
        graphics.fill(x - 2, y - 2, x + w + 2, y + h, 0xF0200030);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x, y + i * 10, 0xFFFFFFFF, true);
        }
    }

    /**
     * Die Farbe des Tagesumsatzes.
     *
     * Rot heisst: Hier bewegt sich fast nichts, und ein Gewinn auf dem Papier nuetzt
     * wenig, wenn die Ware tagelang liegt. Die Schwellen sind grob und sollen nur
     * den Blick lenken.
     */
    private static int mengeFarbe(long proTag) {
        if (proTag < 0) return 0xFF666666;
        if (proTag < 100) return 0xFFFF5555;
        if (proTag < 1000) return 0xFFFFAA00;
        return 0xFF55FF55;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
