package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.BannerDesign;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.BannerDesign.Accent;
import com.shokiteufel.shokimod.data.BannerDesign.Align;
import com.shokiteufel.shokimod.data.BannerDesign.Anchor;
import com.shokiteufel.shokimod.data.BannerDesign.Animation;
import com.shokiteufel.shokimod.data.BannerDesign.Background;
import com.shokiteufel.shokimod.data.BannerDesign.Frame;
import com.shokiteufel.shokimod.data.BannerDesign.Icon;
import com.shokiteufel.shokimod.data.BannerDesign.Shape;
import com.shokiteufel.shokimod.data.BannerDesign.TextColour;
import com.shokiteufel.shokimod.data.BannerDesign.TextEffect;
import com.shokiteufel.shokimod.util.ItemIcons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Die Einblendung fuer seltene Funde - gezeichnet nach einem {@link BannerDesign}.
 *
 * Ein Zeichenweg fuer alle Banner: das Design sagt, wo es haengt, welchen Umriss es
 * hat, was dahinter liegt, wie die Schrift aussieht und wie es hereinkommt. Die
 * frueheren zwanzig Stile sind Vorlagen desselben Modells, kein eigener Code mehr.
 *
 * Getrennt vom {@link AlertBanner}, der fuer SHINY-Critter und Chatregeln bleibt.
 * Kein Timer: nur ein Zeitstempel, aus dem beim Zeichnen Deckkraft, Lage und
 * Animationsfortschritt folgen. Das Design wird beim Zeichnen gelesen - wer im
 * Sandbox oder Editor dreht, sieht es sofort.
 */
public final class DropBanner {

    private static final long FADE_MILLIS = 900L;
    private static final long SLIDE_MILLIS = 250L;
    private static final long POP_MILLIS = 220L;
    private static final long SWEEP_MILLIS = 350L;
    private static final long TYPE_MILLIS = 450L;
    private static final long FLASH_MILLIS = 160L;
    /**
     * Die vier Abschnitte der Kisten-Animation, als Zeitpunkte vom Anfang gerechnet.
     *
     * Bis CHEST_BURST wackelt die Kiste, dann platzt sie. Ab CHEST_ITEM schiesst das
     * Stueck heraus, ab CHEST_NAME faehrt der Name ein, ab CHEST_VALUE steht der
     * Wert. Die Ueberlappung ist Absicht: Die Kiste vergeht noch, waehrend das Stueck
     * schon kommt - sonst entstuende eine Luecke, in der nichts passiert.
     */
    private static final long CHEST_BURST = 2100L;
    /**
     * Erst nach dem Platzen kommt das Stueck.
     *
     * Frueher lag dieser Zeitpunkt davor - die Ueberlappung sollte eine Luecke
     * vermeiden, zeigte aber den Fund, bevor die Kiste ihn hergab. Das nimmt der
     * ganzen Sache die Pointe. Jetzt beginnt es, waehrend die Kiste vergeht: spaet
     * genug, dass sie zuerst aufgeht, frueh genug, dass nichts stockt.
     */
    private static final long CHEST_ITEM = CHEST_BURST + 90L;
    private static final long CHEST_NAME = CHEST_BURST + 520L;
    private static final long CHEST_VALUE = CHEST_BURST + 880L;
    /** So lange dauert die ganze Vorstellung - vorher darf sie nicht ausblenden */
    private static final long CHEST_TOTAL = CHEST_BURST + 1100L;

    /**
     * So lange wird auf Nachzuegler gewartet, bevor abgespielt wird.
     *
     * Aus einem Bundle kommen die Zeilen dicht hintereinander, aber nicht im selben
     * Augenblick. Ohne diese Pause liefe der erste schon, waehrend die anderen noch
     * eintreffen - und "von billig nach teuer" waere nicht mehr zu halten, weil der
     * erste eben schon laeuft. Ein Fuenftel einer Sekunde faellt niemandem auf.
     */
    private static final long GRACE_MILLIS = 200L;
    /** Mehr als das ueberdeckt den halben Bildschirm */
    private static final int MAX_STACK = 5;
    /** Luft zwischen zwei gestapelten Einblendungen */
    private static final int STACK_GAP = 6;
    /** So viele duerfen hoechstens anstehen */
    private static final int MAX_WAITING = 20;

    /** Eine Einblendung mit allem, was sie braucht - frueher waren das statische Felder */
    private static final class Shown {
        BannerDesign design;
        String headline = "";
        String worth = "";
        String tierLabel = "";
        int tint = 0xFFFFFF;
        ItemStack icon;
        long displayMillis = 3000L;
        /** Wonach gereiht wird. Bei allem ausser Beute egal */
        double value;
        /** 0 heisst: wartet noch */
        long startedAt;
        /** Ob beim Anfangen der grosse Auftritt dazugehoert */
        boolean withEffects = true;
    }

    /** Eingetroffen, aber noch nicht angefangen - in der Reihenfolge des Eintreffens */
    private static final java.util.List<Shown> waiting = new java.util.ArrayList<>();
    /** Laeuft gerade auf dem Bild */
    private static final java.util.List<Shown> running = new java.util.ArrayList<>();
    private static long lastArrival = 0L;

    private DropBanner() {
    }

    /**
     * Blendet ein Banner ein.
     *
     * @param chosen das Design; null nimmt die erste Vorlage
     * @param rgb    die Farbe der Stufe - gilt, wenn das Design keine eigene hat
     */
    public static void show(BannerDesign chosen, String headlineText, String worthText, String tierText,
                            int rgb, ItemStack iconStack) {
        show(chosen, headlineText, worthText, tierText, rgb, iconStack, 0d);
    }

    /**
     * Dasselbe, mit dem Wert des Fundes - danach wird gereiht.
     *
     * Eingereiht statt gezeigt: Was daraus wird, entscheidet sich einen Augenblick
     * spaeter in {@link #start}, wenn feststeht, ob noch etwas nachkommt.
     */
    public static void show(BannerDesign chosen, String headlineText, String worthText, String tierText,
                            int rgb, ItemStack iconStack, double value) {
        Shown s = new Shown();
        s.design = chosen == null ? BannerDesign.presets().get(0) : chosen;
        s.headline = headlineText == null ? "" : headlineText;
        s.worth = worthText == null ? "" : worthText;
        s.tierLabel = tierText == null ? "" : tierText;
        s.tint = rgb & 0xFFFFFF;
        s.icon = iconStack;
        s.displayMillis = Math.max(s.design.durationMillis, FADE_MILLIS + 300L);
        // Eine Kiste, die aufspringt, braucht ihre Zeit. Bliebe die uebliche Dauer
        // stehen, waere sie ausgeblendet, bevor der Wert erscheint
        if (s.design.animation == Animation.CHEST) {
            s.displayMillis = Math.max(s.displayMillis, CHEST_TOTAL + FADE_MILLIS + 600L);
        }
        s.value = value;
        waiting.add(s);
        // Wer so lange ansteht, dass niemand mehr weiss wofuer, hilft keinem mehr
        while (waiting.size() > MAX_WAITING) waiting.remove(0);
        lastArrival = System.currentTimeMillis();
    }

    /**
     * Was aus den Wartenden wird - die eine Stelle, an der die Einstellung zaehlt.
     *
     * Gereiht wird hier und nicht beim Eintreffen: Erst wenn eine Weile nichts mehr
     * kam, steht fest, was zusammengehoert.
     */
    private static void start(long now) {
        ModConfig.BannerCategory.MultiDrop wie = ModConfig.INSTANCE.chat.banner.multiDrop;
        switch (wie) {
            case NEWEST -> {
                Shown neuestes = waiting.get(waiting.size() - 1);
                neuestes.startedAt = now;
                running.clear();
                running.add(neuestes);
                waiting.clear();
                effects(neuestes);
            }
            case STACKED -> {
                waiting.sort(java.util.Comparator.comparingDouble(s -> s.value));
                // Nur die entnehmen, die auch wirklich anfangen: Wer nicht mehr auf den
                // Bildschirm passt, bleibt stehen und rueckt nach, sobald oben einer
                // ablaeuft. Ein pauschales Leeren haette ihn verschluckt
                java.util.Iterator<Shown> es = waiting.iterator();
                Shown reichstes = null;
                while (es.hasNext() && running.size() < MAX_STACK) {
                    Shown s = es.next();
                    s.startedAt = now;
                    running.add(s);
                    es.remove();
                    if (reichstes == null || s.value > reichstes.value) reichstes = s;
                }
                // Alle auf einmal heisst: ein Auftritt, und zwar der des wertvollsten.
                // Fuenf Totems uebereinander waeren kein Auftritt, sondern ein Gewitter
                effects(reichstes);
            }
            // Einer nach dem anderen: Der naechste kommt erst, wenn der laufende durch ist
            default -> {
                if (!running.isEmpty()) return;
                waiting.sort(wie == ModConfig.BannerCategory.MultiDrop.RICH_FIRST
                        ? java.util.Comparator.comparingDouble((Shown s) -> s.value).reversed()
                        : java.util.Comparator.comparingDouble(s -> s.value));
                Shown naechstes = waiting.remove(0);
                naechstes.startedAt = now;
                running.add(naechstes);
                effects(naechstes);
            }
        }
    }

    private static void effects(Shown s) {
        if (s != null && s.withEffects) SpecialEffects.play(s.design, s.icon);
    }

    /**
     * Sandbox und Befehl: ein Beispiel im gewuenschten Design, laenger als im Spiel.
     *
     * Ohne den grossen Auftritt: Die Vorschau laeuft bei jedem Reglerzug neu an, und
     * ein Stueck, das bei jedem Millimeter ueber den Bildschirm fliegt, macht das
     * Einstellen unmoeglich. Wer ihn sehen will, nimmt {@link #previewWithEffects}.
     */
    public static void preview(BannerDesign chosen) {
        preview(chosen, false);
    }

    /** Dasselbe mit Auftritt - fuer den Knopf, der ihn ausdruecklich zeigen soll */
    public static void previewWithEffects(BannerDesign chosen) {
        preview(chosen, true);
    }

    private static void preview(BannerDesign chosen, boolean mitAuftritt) {
        // Die Vorschau steht fuer sich - was noch wartet, hat hier nichts zu suchen
        waiting.clear();
        running.clear();
        show(chosen, "3x Ghost Shard", "10.5k", "Tier 1", 0xFFD700, ItemIcons.stackFor("SHARD_GHOST"));
        Shown s = waiting.get(0);
        s.displayMillis = Math.max(s.displayMillis, 4500L);
        s.startedAt = System.currentTimeMillis();
        s.withEffects = mitAuftritt;
        running.add(s);
        waiting.clear();
        if (mitAuftritt) effects(s);
    }

    /**
     * Der Sandbox: die Vorschau bleibt stehen, solange das Fenster offen ist.
     *
     * Kurz bevor sie ausblenden wuerde, faengt sie von vorn an - so laufen auch die
     * Animationen immer wieder, und man sieht, was man gerade eingestellt hat.
     */
    public static void keepPreview(BannerDesign chosen) {
        Shown s = running.isEmpty() ? null : running.get(0);
        if (s == null || s.design != chosen
                || System.currentTimeMillis() - s.startedAt > s.displayMillis - FADE_MILLIS) {
            preview(chosen, false);
        }
    }

    public static boolean visible() {
        return !running.isEmpty();
    }

    /**
     * Zeichnet, was gerade laeuft - und laesst nachruecken, was wartet.
     *
     * Gestapelt wird nach unten, in der Reihenfolge, in der {@link #start} sie gereiht
     * hat: Die erste sitzt an der Stelle, an der frueher die einzige sass, jede
     * weitere darunter.
     */
    public static void render(GuiGraphicsExtractor g) {
        long now = System.currentTimeMillis();
        running.removeIf(s -> now - s.startedAt > s.displayMillis);
        if (!waiting.isEmpty() && now - lastArrival >= GRACE_MILLIS) start(now);
        if (running.isEmpty()) return;

        int offset = 0;
        for (Shown s : running) {
            offset += renderOne(g, s, offset) + STACK_GAP;
        }
    }

    /** Eine einzelne Einblendung. Gibt ihre Hoehe zurueck, damit die naechste darunter passt */
    private static int renderOne(GuiGraphicsExtractor g, Shown banner, int offsetY) {
        if (banner.design == null) return 0;

        long age = System.currentTimeMillis() - banner.startedAt;
        long displayMillis = banner.displayMillis;

        float alpha = 1.0f;
        long fadeStart = displayMillis - FADE_MILLIS;
        if (age > fadeStart) alpha = 1.0f - (age - fadeStart) / (float) FADE_MILLIS;

        Font font = Minecraft.getInstance().font;
        int width = g.guiWidth();
        int height = g.guiHeight();
        BannerDesign d = banner.design;
        int colour = parseColour(d.colour, banner.tint);
        float scale = clamp(d.scale, 0.3f, 4.0f);
        Shape shape = d.shape == null ? Shape.RECTANGLE : d.shape;
        Align align = d.align == null ? Align.CENTER : d.align;
        int padding = Math.clamp(d.padding, 0, 60);

        // ---- Texte und Groessen ----
        //
        // Die Schnitte stehen als Steuerzeichen vor dem Text. Das Spiel liest sie beim
        // Zeichnen und beim Messen gleichermassen - fett ist je Zeichen einen Punkt
        // breiter, und genau diese Breite braucht der Kasten
        String headStyle = style(d.headlineBold, d.headlineItalic, d.headlineUnderline, d.headlineStrike);
        String valueStyle = style(d.valueBold, d.valueItalic, d.valueUnderline, d.valueStrike);
        String head = headStyle + d.prefix + banner.headline + d.suffix;
        String value = d.showValue && !banner.worth.isEmpty()
                ? valueStyle + d.valuePrefix + banner.worth + d.valueSuffix
                : "";
        String tier = d.showTier ? banner.tierLabel : "";
        float hs = clamp(d.headlineSize, 0.5f, 6.0f) * scale;
        float vs = clamp(d.valueSize, 0.5f, 6.0f) * scale;
        float ts = Math.max(0.8f, scale);

        // ---- Die Kiste, wenn sie dran ist ----
        //
        // Gezeichnet wird sie vor allem anderen und ausserhalb des Kastens: Sie
        // gehoert nicht zum Text, sondern geht ihm voraus.
        boolean kiste = d.animation == Animation.CHEST;
        float itemAuf = 1.0f;
        float nameAuf = 1.0f;
        boolean zeigeWert = true;
        if (kiste) {
            // Vor dem Platzen nur die Kiste, danach das Stueck
            if (age < CHEST_ITEM) {
                itemAuf = 0f;
            } else {
                // Herausschiessen und zurueckfallen: erst ueber die Zielgroesse
                // hinaus, dann darauf einpendeln
                float t = Math.min(1.0f, (age - CHEST_ITEM) / 320f);
                itemAuf = t < 0.6f ? t / 0.6f * 1.35f : 1.35f - (t - 0.6f) / 0.4f * 0.35f;
            }
            nameAuf = age < CHEST_NAME ? 0f
                    : Math.min(1.0f, (age - CHEST_NAME) / 260f);
            zeigeWert = age >= CHEST_VALUE;
        }

        float pop = 1.0f;
        if (d.animation == Animation.POP) {
            float progress = Math.min(1.0f, age / (float) POP_MILLIS);
            pop = progress < 0.6f ? 0.4f + progress : 1.3f - (progress - 0.6f) * 0.75f;
        }
        String shownHead = head;
        boolean typing = false;
        if (d.animation == Animation.TYPEWRITER) {
            // Nur der sichtbare Text wird gekuerzt, die Steuerzeichen bleiben stehen -
            // sonst faellt der Schnitt mitten im Tippen weg
            String rumpf = head.substring(headStyle.length());
            float progress = Math.min(1.0f, age / (float) TYPE_MILLIS);
            int shown = Math.min(rumpf.length(), Math.round(rumpf.length() * progress));
            shownHead = headStyle + rumpf.substring(0, shown) + (progress < 1.0f ? "_" : "");
            typing = progress < 1.0f;
        }

        int headW = (int) (font.width(head) * hs * pop);
        int headH = (int) (10 * hs * pop);
        int valueW = value.isEmpty() ? 0 : (int) (font.width(value) * vs);
        int valueH = value.isEmpty() ? 0 : (int) (10 * vs);
        int tierW = tier.isEmpty() ? 0 : (int) (font.width(tier) * ts);
        int tierH = tier.isEmpty() ? 0 : (int) (10 * ts);
        int iconSize = d.icon == Icon.NONE || banner.icon == null ? 0 : (int) (16 * clamp(d.iconScale, 0.5f, 6.0f) * scale);
        int gap = (int) (4 * scale);
        boolean iconBeside = (d.icon == Icon.LEFT || d.icon == Icon.RIGHT) && iconSize > 0;

        // Der Textblock: Ueberschrift, (Bild), Wert, Stufe untereinander
        int textW = Math.max(headW, Math.max(valueW, tierW));
        int textH = headH + (valueH > 0 ? gap + valueH : 0) + (tierH > 0 ? gap + tierH : 0);
        int blockW;
        int blockH;
        if (d.icon == Icon.MIDDLE && iconSize > 0) {
            blockW = Math.max(textW, iconSize);
            blockH = textH + gap + iconSize;
        } else if (iconBeside) {
            blockW = iconSize + gap * 2 + textW;
            blockH = Math.max(textH, iconSize);
        } else {
            blockW = textW;
            blockH = textH;
        }
        int accentPad = d.accent == Accent.LEFT_BAR ? (int) (6 * scale) : d.accent == Accent.DOT ? (int) (8 * scale) : 0;
        int boxW = blockW + padding * 2 + accentPad;
        int boxH = blockH + padding * 2;

        // ---- Lage ----
        //
        // Ueber die ganze Breite gibt es keine Rundung: Ein Oval, das den Bildschirm
        // ausfuellt, ist keine Form mehr, sondern ein Zufall
        boolean fullWidth = d.anchor == Anchor.BAND || d.anchor == Anchor.TOP;
        Shape umriss = fullWidth ? Shape.RECTANGLE : shape;

        // Runde Formen brauchen mehr Luft als ein Rechteck: In der Ecke eines Ovals
        // ist kein Platz, und ohne Zuschlag stuende der Text ueber dem Rand
        if (umriss == Shape.OVAL || umriss == Shape.DIAMOND) {
            boxW = (int) (boxW * 1.45f);
            boxH = (int) (boxH * 1.35f);
        }

        int cx;
        int cy;
        switch (d.anchor) {
            case CENTER -> {
                cx = width / 2;
                cy = height / 2;
            }
            case HOTBAR -> {
                cx = width / 2;
                cy = (int) (height * 0.62f);
            }
            case BAND -> {
                cx = width / 2;
                cy = height / 4 + boxH / 2;
            }
            case TOP -> {
                cx = width / 2;
                cy = boxH / 2 + 4;
            }
            case RIGHT_EDGE -> {
                cx = width - boxW / 2;
                cy = height / 3 + boxH / 2;
            }
            default -> {
                cx = (int) (width * clamp(d.x, 0f, 1f));
                cy = (int) (height * clamp(d.y, 0f, 1f));
            }
        }

        // Gestapelt: jede weitere sitzt unter der vorigen
        cy += offsetY;

        float slide = Math.min(1.0f, age / (float) SLIDE_MILLIS);
        if (d.animation == Animation.SLIDE_RIGHT) cx += (int) ((1.0f - slide) * (width - cx + boxW));
        if (d.animation == Animation.SLIDE_LEFT) cx -= (int) ((1.0f - slide) * (cx + boxW));
        if (d.animation == Animation.DROP) cy -= (int) ((1.0f - slide) * (cy + boxH));
        if (d.animation == Animation.RISE) cy += (int) ((1.0f - slide) * (height - cy + boxH));

        int left = fullWidth ? 0 : cx - boxW / 2;
        int right = fullWidth ? width : cx + boxW / 2;
        int top = cy - boxH / 2;
        int bottom = cy + boxH / 2;
        int radius = Math.clamp(d.cornerRadius, 0, 64);

        // ---- Hintergrund und Rahmen ----
        if (d.animation == Animation.FLASH && age < FLASH_MILLIS) {
            g.fill(0, 0, width, height, argb((1.0f - age / (float) FLASH_MILLIS) * 0.45f, colour));
        }
        float bgAlpha = alpha * clamp(d.backgroundAlpha, 0f, 1f);
        switch (d.background) {
            case BOX -> fillShape(g, umriss, left, top, right, bottom, radius,
                    argb(bgAlpha, 0x101010), argb(bgAlpha, 0x101010));
            case FILL -> fillShape(g, umriss, left, top, right, bottom, radius,
                    argb(bgAlpha, colour), argb(bgAlpha, colour));
            case GRADIENT -> fillShape(g, umriss, left, top, right, bottom, radius,
                    argb(bgAlpha, colour), argb(bgAlpha, 0x101010));
            case SPLIT -> {
                int mid = left + (right - left) * 3 / 5;
                fillShape(g, umriss, left, top, right, bottom, radius,
                        argb(bgAlpha, 0x101010), argb(bgAlpha, 0x101010));
                // Die rechte Haelfte liegt darueber und wird auf die Form beschnitten,
                // damit sie bei einem Oval nicht ueber den Rand steht
                fillShapeClipped(g, umriss, left, top, right, bottom, radius, mid, right,
                        argb(bgAlpha, colour), argb(bgAlpha, colour));
            }
            default -> {
            }
        }
        int frameColour = argb(alpha, colour);
        switch (d.frame) {
            case SINGLE -> strokeShape(g, umriss, left, top, right, bottom, radius, 2, frameColour);
            case THICK -> strokeShape(g, umriss, left, top, right, bottom, radius,
                    Math.max(3, (int) (4 * scale)), frameColour);
            case DOUBLE -> {
                strokeShape(g, umriss, left, top, right, bottom, radius, 1, frameColour);
                strokeShape(g, umriss, left + 4, top + 4, right - 4, bottom - 4,
                        Math.max(0, radius - 2), 1, argb(alpha, 0xFFFFFF));
            }
            case CORNERS -> corners(g, left, top, right, bottom,
                    Math.max(6, (int) (10 * scale)), Math.max(1, (int) (2 * scale)), frameColour);
            default -> {
            }
        }
        if (d.accent == Accent.EDGES) {
            g.fill(left, top, right, top + 1, argb(alpha, colour));
            g.fill(left, bottom - 1, right, bottom, argb(alpha, colour));
        }
        if (d.accent == Accent.LEFT_BAR) g.fill(left, top, left + (int) (3 * scale) + 1, bottom, argb(alpha, colour));

        // Die Kiste: wackelt, waechst, platzt. Gezeichnet ueber dem Kasten, damit
        // sie nicht hinter dessen Hintergrund verschwindet
        if (kiste && age < CHEST_BURST + 220L) {
            drawChest(g, d.chest, cx, cy - boxH / 4, scale, age, alpha);
        }

        // ---- Inhalt ----
        int contentLeft = left + (boxW - blockW - accentPad) / 2 + accentPad + ((right - left) - boxW) / 2;
        int textLeft = contentLeft;
        int textTop = top + (boxH - blockH) / 2;
        if (iconBeside) {
            int gezeigt = Math.round(iconSize * itemAuf);
            int iconLeft = d.icon == Icon.LEFT ? contentLeft : contentLeft + textW + gap * 2;
            if (gezeigt > 0) {
                drawIcon(g, banner.icon, iconLeft + (iconSize - gezeigt) / 2,
                        top + (boxH - gezeigt) / 2, gezeigt);
            }
            textLeft = d.icon == Icon.LEFT ? contentLeft + iconSize + gap * 2 : contentLeft;
            textTop = top + (boxH - textH) / 2;
        }
        int headCentre = lineCentre(align, textLeft, textW, headW);
        if (d.accent == Accent.DOT) {
            int dot = Math.max(3, (int) (4 * scale));
            g.fill(textLeft - accentPad, textTop + headH / 2 - dot / 2, textLeft - accentPad + dot, textTop + headH / 2 + dot / 2, argb(alpha, colour));
        }

        int y = textTop;
        if (nameAuf > 0f) {
            // Von links hereinfahren und dabei aufklaren. Bei allen anderen
            // Animationen ist nameAuf eins, also aendert sich dort nichts
            int versatz = Math.round((1.0f - nameAuf) * -60 * scale);
            drawText(g, font, shownHead, headCentre + versatz, y, hs * pop,
                    textColour(d.headlineColour, colour), d.textEffect,
                    alpha * nameAuf, colour);
        }
        y += headH;

        if (d.accent == Accent.UNDERLINE || d.accent == Accent.SWEEP) {
            int half = headW / 2 + (int) (6 * scale);
            if (d.accent == Accent.SWEEP) half = (int) (half * Math.min(1.0f, age / (float) SWEEP_MILLIS));
            g.fill(headCentre - half, y + 1, headCentre + half, y + 1 + Math.max(1, (int) (2 * scale)), argb(alpha, colour));
        }
        if (d.icon == Icon.MIDDLE && iconSize > 0) {
            y += gap;
            // Bei der Kiste schiesst das Stueck heraus, statt einfach dazustehen
            int gezeigt = Math.round(iconSize * itemAuf);
            if (gezeigt > 0) {
                drawIcon(g, banner.icon, lineCentre(align, textLeft, textW, iconSize) - gezeigt / 2,
                        y + (iconSize - gezeigt) / 2, gezeigt);
            }
            y += iconSize;
        }
        if (valueH > 0 && !typing && zeigeWert) {
            y += gap;
            if (d.accent == Accent.DIVIDER) {
                int half = textW / 2 + (int) (6 * scale);
                int mitte = textLeft + textW / 2;
                g.fill(mitte - half, y - gap / 2, mitte + half, y - gap / 2 + 1, argb(alpha, colour));
            }
            int valueX = lineCentre(align, textLeft, textW, valueW);
            if (d.background == Background.SPLIT) valueX = left + (right - left) * 4 / 5;
            drawText(g, font, value, valueX, y, vs, textColour(d.valueColour, colour), d.textEffect, alpha, colour);
            y += valueH;
        }
        if (tierH > 0) {
            y += gap;
            drawText(g, font, tier, lineCentre(align, textLeft, textW, tierW), y, ts,
                    0xAAAAAA, TextEffect.PLAIN, alpha, colour);
        }
        return boxH;
    }

    /**
     * Die Kiste, die aufspringt.
     *
     * Drei Abschnitte in einem: Sie waechst heran, zittert kurz - je naeher das
     * Platzen, desto staerker - und faellt dann auseinander, indem sie sich schnell
     * aufblaeht und verschwindet. Gezeichnet wird eine gewoehnliche Truhe; ein
     * eigenes Bild braucht es dafuer nicht, und ein bekanntes Ding wirkt ohnehin
     * vertrauter als ein gemaltes.
     */
    private static void drawChest(GuiGraphicsExtractor g, BannerDesign.Chest art,
                                  int cx, int cy, float scale, long age, float alpha) {
        // Laenger heranwachsen, passend zur laengeren Spannung davor
        float wachsen = Math.min(1.0f, age / 700f);
        float groesse = 48f * scale * wachsen;
        float deckkraft = alpha;

        if (age >= CHEST_BURST) {
            // Auseinanderfallen: schnell groesser und dabei durchsichtig
            float t = Math.min(1.0f, (age - CHEST_BURST) / 220f);
            groesse = 48f * scale * (1.0f + t * 1.6f);
            deckkraft = alpha * (1.0f - t);
        }
        if (deckkraft <= 0.01f || groesse < 1f) return;

        // Zittern: nimmt zu, je naeher das Platzen kommt
        int ruettel = 0;
        if (age < CHEST_BURST) {
            // Hoch vier statt hoch zwei: Bei zwei Sekunden Anlauf begaenne das
            // Zittern sonst viel zu frueh und waere die halbe Zeit ueber da. So
            // bleibt die Kiste lange ruhig und faengt erst zum Schluss an zu beben
            float naehe = Math.min(1.0f, age / (float) CHEST_BURST);
            float staerke = naehe * naehe * naehe * naehe * 5f * scale;
            ruettel = Math.round((float) Math.sin(age / 26.0) * staerke);
        }

        // Was das Design will. Kennt das Spiel die Kennung nicht - etwa nach einer
        // Umbenennung -, bleibt die Endertruhe als Rueckfall
        net.minecraft.world.item.Item gewaehlt = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(net.minecraft.resources.Identifier.tryParse(
                        "minecraft:" + (art == null ? "ender_chest" : art.item)))
                .orElse(net.minecraft.world.item.Items.ENDER_CHEST);
        ItemStack truhe = new ItemStack(gewaehlt);
        int kante = Math.round(groesse);
        float s = kante / 16f;
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        g.fakeItem(truhe, Math.round((cx + ruettel - kante / 2f) / s),
                Math.round((cy - kante / 2f) / s));
        g.pose().popMatrix();

        // Ein heller Schein im Moment des Platzens
        if (age >= CHEST_BURST - 60 && age < CHEST_BURST + 160) {
            float t = Math.abs(age - CHEST_BURST) / 160f;
            float hell = Math.max(0f, 1.0f - t) * 0.5f * alpha;
            int r = Math.round(groesse * 0.9f);
            g.fill(cx - r, cy - r, cx + r, cy + r, argb(hell, 0xFFFFAA));
        }
    }

    // ---- Formen ----

    /**
     * Wie breit die Form in dieser Bildzeile ist.
     *
     * Zeilenweise, weil das Spiel nur Rechtecke fuellen kann: Ein Oval ist nichts
     * anderes als hundert verschieden breite Rechtecke uebereinander. Das kostet so
     * viel wie hundert Rechtecke - also nichts - und kommt ohne eigene Bilddateien
     * aus, die bei jeder Bannergroesse neu gestreckt werden muessten.
     *
     * @param out nimmt Anfang und Ende auf, gemessen vom linken Rand
     * @return falsch, wenn diese Zeile gar nichts traegt - oben und unten am Oval
     */
    private static boolean span(Shape shape, int w, int h, int y, int radius, int[] out) {
        out[0] = 0;
        out[1] = w;
        if (w <= 0 || h <= 0 || y < 0 || y >= h) return false;
        if (shape == Shape.OVAL || shape == Shape.DIAMOND) {
            double b = h / 2.0;
            double a = w / 2.0;
            double dy = Math.abs((y + 0.5) - b);
            double halb = shape == Shape.OVAL
                    ? a * Math.sqrt(Math.max(0.0, 1.0 - (dy * dy) / (b * b)))
                    : a * (1.0 - dy / b);
            if (halb <= 0.5) return false;
            out[0] = (int) Math.round(a - halb);
            out[1] = (int) Math.round(a + halb);
        } else if (shape == Shape.PILL || shape == Shape.ROUNDED || shape == Shape.CUT) {
            int r = shape == Shape.PILL ? h / 2 : Math.max(0, Math.min(radius, Math.min(w, h) / 2));
            int dy = Math.min(y, h - 1 - y);
            if (dy < r) {
                int einzug;
                if (shape == Shape.CUT) {
                    einzug = r - dy;
                } else {
                    double k = r - dy - 0.5;
                    einzug = (int) Math.round(r - Math.sqrt(Math.max(0.0, (double) r * r - k * k)));
                }
                out[0] = einzug;
                out[1] = w - einzug;
            }
        }
        return out[1] > out[0];
    }

    /** Die Form fuellen, oben in der einen und unten in der anderen Farbe */
    private static void fillShape(GuiGraphicsExtractor g, Shape shape, int left, int top, int right, int bottom,
                                  int radius, int colourTop, int colourBottom) {
        fillShapeClipped(g, shape, left, top, right, bottom, radius, left, right, colourTop, colourBottom);
    }

    /** Dasselbe, aber nur zwischen zwei senkrechten Schnitten - fuer den geteilten Hintergrund */
    private static void fillShapeClipped(GuiGraphicsExtractor g, Shape shape, int left, int top, int right, int bottom,
                                         int radius, int clipLeft, int clipRight, int colourTop, int colourBottom) {
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return;
        if (shape == Shape.RECTANGLE && colourTop == colourBottom) {
            g.fill(Math.max(left, clipLeft), top, Math.min(right, clipRight), bottom, colourTop);
            return;
        }
        int[] s = new int[2];
        for (int y = 0; y < h; y++) {
            if (!span(shape, w, h, y, radius, s)) continue;
            int x0 = Math.max(left + s[0], clipLeft);
            int x1 = Math.min(left + s[1], clipRight);
            if (x1 <= x0) continue;
            g.fill(x0, top + y, x1, top + y + 1,
                    blend(colourTop, colourBottom, h == 1 ? 0f : y / (float) (h - 1)));
        }
    }

    /**
     * Den Umriss nachziehen.
     *
     * Gezeichnet wird die Form einmal gross und einmal um die Stichstaerke kleiner;
     * uebrig bleibt der Ring dazwischen. So stimmt der Rand bei jeder Form, ohne dass
     * fuer Oval, Raute und gerundete Ecke je eine eigene Rechnung dastuende.
     */
    private static void strokeShape(GuiGraphicsExtractor g, Shape shape, int left, int top, int right, int bottom,
                                    int radius, int thick, int colour) {
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return;
        int t = Math.max(1, thick);
        if (shape == Shape.RECTANGLE) {
            frame(g, left, top, right, bottom, t, colour);
            return;
        }
        int[] aussen = new int[2];
        int[] innen = new int[2];
        for (int y = 0; y < h; y++) {
            if (!span(shape, w, h, y, radius, aussen)) continue;
            boolean hatInnen = y >= t && y < h - t
                    && span(shape, w - 2 * t, h - 2 * t, y - t, Math.max(0, radius - t), innen);
            if (!hatInnen) {
                g.fill(left + aussen[0], top + y, left + aussen[1], top + y + 1, colour);
                continue;
            }
            int i0 = Math.max(aussen[0], t + innen[0]);
            int i1 = Math.min(aussen[1], t + innen[1]);
            if (i0 > aussen[0]) g.fill(left + aussen[0], top + y, left + i0, top + y + 1, colour);
            if (i1 < aussen[1]) g.fill(left + i1, top + y, left + aussen[1], top + y + 1, colour);
        }
    }

    /** Vier Winkel in den Ecken - gefasst, aber nicht eingesperrt */
    private static void corners(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
                                int length, int thick, int colour) {
        int l = Math.min(length, Math.min(right - left, bottom - top) / 2);
        g.fill(left, top, left + l, top + thick, colour);
        g.fill(left, top, left + thick, top + l, colour);
        g.fill(right - l, top, right, top + thick, colour);
        g.fill(right - thick, top, right, top + l, colour);
        g.fill(left, bottom - thick, left + l, bottom, colour);
        g.fill(left, bottom - l, left + thick, bottom, colour);
        g.fill(right - l, bottom - thick, right, bottom, colour);
        g.fill(right - thick, bottom - l, right, bottom, colour);
    }

    // ---- Helfer ----

    /** Die Steuerzeichen fuer die gewaehlten Schnitte, etwa "§l§n" */
    private static String style(boolean bold, boolean italic, boolean underline, boolean strike) {
        StringBuilder out = new StringBuilder(8);
        if (bold) out.append("§l");
        if (italic) out.append("§o");
        if (underline) out.append("§n");
        if (strike) out.append("§m");
        return out.toString();
    }

    /** Wo die Mitte dieser Zeile liegt, je nach Ausrichtung */
    private static int lineCentre(Align align, int textLeft, int textW, int lineW) {
        return switch (align) {
            case LEFT -> textLeft + lineW / 2;
            case RIGHT -> textLeft + textW - lineW / 2;
            default -> textLeft + textW / 2;
        };
    }

    private static int textColour(TextColour choice, int accent) {
        return switch (choice) {
            case WHITE -> 0xFFFFFF;
            case DARK -> 0x101010;
            case GREY -> 0xAAAAAA;
            default -> accent;
        };
    }

    private static void drawIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
        if (icon == null) return;
        float s = size / 16f;
        g.pose().pushMatrix();
        g.pose().scale(s, s);
        g.fakeItem(icon, (int) (x / s), (int) (y / s));
        g.pose().popMatrix();
    }

    private static void drawText(GuiGraphicsExtractor g, Font font, String text, int centreX, int y, float size,
                                 int rgb, TextEffect effect, float alpha, int accent) {
        if (text == null || text.isEmpty()) return;
        int colour = argb(alpha, rgb);
        int x = (int) (centreX / size) - font.width(text) / 2;
        int sy = (int) (y / size);

        g.pose().pushMatrix();
        g.pose().scale(size, size);
        switch (effect) {
            case OUTLINE -> {
                int edge = argb(alpha, 0x000000);
                g.text(font, text, x - 1, sy, edge, false);
                g.text(font, text, x + 1, sy, edge, false);
                g.text(font, text, x, sy - 1, edge, false);
                g.text(font, text, x, sy + 1, edge, false);
                g.text(font, text, x, sy, colour, false);
            }
            case GLOW -> {
                int haze = argb(alpha * 0.28f, accent);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if (dx != 0 || dy != 0) g.text(font, text, x + dx, sy + dy, haze, false);
                    }
                }
                g.text(font, text, x, sy, colour, false);
            }
            case SHADOW -> g.text(font, text, x, sy, colour, true);
            default -> g.text(font, text, x, sy, colour, false);
        }
        g.pose().popMatrix();
    }

    private static void frame(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int thick, int colour) {
        g.fill(left, top, right, top + thick, colour);
        g.fill(left, bottom - thick, right, bottom, colour);
        g.fill(left, top, left + thick, bottom, colour);
        g.fill(right - thick, top, right, bottom, colour);
    }

    /** "FFD700" oder "#ffd700" - leer oder unlesbar heisst: Farbe der Stufe */
    public static int parseColour(String text, int fallback) {
        if (text == null) return fallback;
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return fallback;
        try {
            return Integer.parseInt(hex.toUpperCase(Locale.ROOT), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Zwei Farben mischen, mit Deckkraft - fuer den weichen Verlauf */
    private static int blend(int a, int b, float t) {
        if (a == b) return a;
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >> shift) & 0xFF;
            int cb = (b >> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * k) << shift;
        }
        return out;
    }
}
