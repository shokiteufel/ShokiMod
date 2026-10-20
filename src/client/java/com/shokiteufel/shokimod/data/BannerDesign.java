package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Banner, wie es der Sandbox baut: jede Besonderheit ist eine Einstellung.
 *
 * Frueher gab es zwanzig feste Stile, jeder ein eigenes Stueck Zeichencode. Jetzt
 * gibt es ein Modell, und die alten Stile sind Vorlagen darin - wer "Side card"
 * mag, aber gruen und oben links, kopiert die Vorlage und dreht an drei Reglern.
 *
 * Alles hier ist reine Ablage. Gezeichnet wird in DropBanner, bearbeitet in
 * BannerDesignScreen, verschoben im HUD-Editor.
 */
public class BannerDesign {

    /** Wo das Banner haengt */
    public enum Anchor {
        FREE("Free (X/Y)"),
        BAND("Band across screen"),
        TOP("Top edge"),
        HOTBAR("Above hotbar"),
        RIGHT_EDGE("Right edge"),
        CENTER("Screen centre");

        public final String label;

        Anchor(String label) {
            this.label = label;
        }
    }

    public enum Background {
        NONE("None"),
        BOX("Dark box"),
        FILL("Coloured fill"),
        SPLIT("Split dark/colour"),
        /** Von der Akzentfarbe ins Dunkle - weich statt als harte Kante */
        GRADIENT("Fading colour"),
        /**
         * Wie die Titel des Spiels (/title): hinter jeder Zeile ein eigener Streifen in der Abdunklung,
         * die in den Spieleinstellungen steht, die Schrift mit Schatten, und das Ausblenden dauert eine
         * halbe Sekunde. Form, Rahmen und Akzent gelten dort nicht.
         */
        TITLE("Like the game's title");

        public final String label;

        Background(String label) {
            this.label = label;
        }
    }

    /**
     * Der Umriss von Hintergrund und Rahmen.
     *
     * Gezeichnet wird zeilenweise: Fuer alles ausser dem Rechteck gibt es im Spiel
     * keinen Fuellbefehl, also bekommt jede Bildzeile ihre eigene Breite. Das kostet
     * so viel wie hundert Rechtecke - also nichts, was man merkt - und braucht keine
     * eigenen Bilddateien, die mit jedem Bannermass neu skaliert werden muessten.
     */
    public enum Shape {
        RECTANGLE("Rectangle"),
        ROUNDED("Rounded corners"),
        PILL("Pill (round ends)"),
        OVAL("Oval"),
        DIAMOND("Diamond"),
        /** Die Ecken abgeschnitten statt gerundet - kantig, aber nicht rechteckig */
        CUT("Cut corners");

        public final String label;

        Shape(String label) {
            this.label = label;
        }
    }

    public enum Frame {
        NONE("None"),
        SINGLE("Single"),
        DOUBLE("Double"),
        THICK("Thick"),
        /** Nur die vier Ecken - das Banner wirkt gefasst, ohne eingesperrt zu sein */
        CORNERS("Corner brackets");

        public final String label;

        Frame(String label) {
            this.label = label;
        }
    }

    public enum Accent {
        NONE("None"),
        LEFT_BAR("Bar on the left"),
        UNDERLINE("Underline"),
        SWEEP("Underline, sweeping in"),
        EDGES("Lines top and bottom"),
        DOT("Dot before the text"),
        DIVIDER("Line between headline and value");

        public final String label;

        Accent(String label) {
            this.label = label;
        }
    }

    public enum TextColour {
        ACCENT("Accent colour"),
        WHITE("White"),
        DARK("Dark"),
        GREY("Grey");

        public final String label;

        TextColour(String label) {
            this.label = label;
        }
    }

    public enum TextEffect {
        PLAIN("Plain"),
        SHADOW("Shadow"),
        OUTLINE("Outline"),
        GLOW("Glow");

        public final String label;

        TextEffect(String label) {
            this.label = label;
        }
    }

    /** Wo die Zeilen im Banner stehen */
    public enum Align {
        CENTER("Centred"),
        LEFT("Left"),
        RIGHT("Right");

        public final String label;

        Align(String label) {
            this.label = label;
        }
    }

    public enum Icon {
        NONE("No icon"),
        MIDDLE("Between headline and value"),
        LEFT("Left of the text"),
        RIGHT("Right of the text");

        public final String label;

        Icon(String label) {
            this.label = label;
        }
    }

    /**
     * Die Funken, die neben dem Banner aufsteigen.
     *
     * Abgelegt wird der Name aus dem Spiel, nicht der Typ selbst: Diese Klasse ist
     * reine Ablage und soll nichts aus Minecraft kennen - sonst wandert beim Teilen
     * eines Designs am Ende halb Minecraft durch die Zwischenablage. Aufgeloest wird
     * er beim Abspielen; kennt das Spiel den Namen einmal nicht mehr, bleibt es eben
     * still, statt abzustuerzen.
     */
    public enum Particles {
        NONE("None", ""),
        TOTEM("Totem burst", "totem_of_undying"),
        HAPPY("Green sparkles", "happy_villager"),
        CRIT("Crit sparks", "crit"),
        ENCHANT("Enchanting runes", "enchant"),
        PORTAL("Portal swirl", "portal"),
        FLAME("Flames", "flame"),
        SOUL("Soul fire", "soul_fire_flame"),
        END_ROD("End rod", "end_rod"),
        GLOW("Glow", "glow"),
        HEART("Hearts", "heart"),
        NOTE("Notes", "note"),
        FIREWORK("Firework sparks", "firework"),
        SCRAPE("Scrape", "scrape"),
        TRIAL("Trial spawner flash", "trial_spawner_detection"),
        DRAGON("Dragon breath", "dragon_breath"),
        CHERRY("Cherry petals", "cherry_leaves");

        public final String label;
        /** Die Kennung im Spiel, ohne "minecraft:" */
        public final String particle;

        Particles(String label, String particle) {
            this.label = label;
            this.particle = particle;
        }
    }

    /**
     * Die Truhen, die aufspringen koennen.
     *
     * Alle aus dem Spiel, keine gemalten: Ein bekanntes Ding wirkt vertrauter, und
     * die Bilder kommen ohne eigene Dateien aus.
     */
    public enum Chest {
        ENDER("Ender Chest", "ender_chest"),
        NORMAL("Chest", "chest"),
        TRAPPED("Trapped Chest", "trapped_chest"),
        BARREL("Barrel", "barrel"),
        SHULKER("Shulker Box", "shulker_box"),
        PURPLE_SHULKER("Purple Shulker Box", "purple_shulker_box"),
        GOLD("Gold Block", "gold_block"),
        DIAMOND("Diamond Block", "diamond_block"),
        BEACON("Beacon", "beacon"),
        DRAGON_EGG("Dragon Egg", "dragon_egg");

        public final String label;
        /** Die Kennung im Spiel - daraus wird das Bild geholt */
        public final String item;

        Chest(String label, String item) {
            this.label = label;
            this.item = item;
        }
    }

    public enum Animation {
        NONE("None"),
        SLIDE_RIGHT("Slide in from the right"),
        SLIDE_LEFT("Slide in from the left"),
        DROP("Drop from the top"),
        RISE("Rise from the bottom"),
        POP("Pop"),
        TYPEWRITER("Typewriter"),
        FLASH("Flash"),
        /**
         * Die grosse Nummer: eine Kiste, die aufspringt.
         *
         * Vier Abschnitte nacheinander - die Kiste wackelt und platzt, das Stueck
         * schiesst heraus und faellt auf seine Groesse zurueck, der Name faehrt von
         * links ein, zuletzt der Wert. Zusammen knapp zwei Sekunden; dafuer bleibt
         * sie laenger stehen als die anderen, sonst waere sie vorbei, bevor der Preis
         * steht.
         */
        CHEST("Chest opening");

        public final String label;

        Animation(String label) {
            this.label = label;
        }
    }

    @Expose public String name = "New banner";

    @Expose public Anchor anchor = Anchor.FREE;
    @Expose public float x = 0.5f;
    @Expose public float y = 0.3f;
    @Expose public float scale = 1.0f;
    /** Luft zwischen Text und Rand, in Bildpunkten */
    @Expose public int padding = 10;

    @Expose public Background background = Background.BOX;
    /** Deckkraft des Hintergrunds, 0 bis 1 */
    @Expose public float backgroundAlpha = 0.75f;
    @Expose public Shape shape = Shape.RECTANGLE;
    /** Wie stark die Ecken gerundet oder geschnitten sind. Nur fuer ROUNDED und CUT */
    @Expose public int cornerRadius = 8;
    @Expose public Frame frame = Frame.NONE;
    @Expose public Accent accent = Accent.NONE;

    /** Schriftgroessen als Vielfache der Standardschrift */
    @Expose public float headlineSize = 2.0f;
    @Expose public float valueSize = 1.2f;
    @Expose public TextColour headlineColour = TextColour.ACCENT;
    @Expose public TextColour valueColour = TextColour.WHITE;
    @Expose public TextEffect textEffect = TextEffect.SHADOW;
    @Expose public Align align = Align.CENTER;

    /**
     * Schriftschnitte, wie das Spiel sie kennt.
     *
     * Minecraft kennt keine zweite Schriftart, aber vier Schnitte: fett, kursiv,
     * unterstrichen, durchgestrichen. Sie stehen als Steuerzeichen vor dem Text und
     * werden beim Messen der Breite mitgezaehlt - fett ist je Zeichen einen Punkt
     * breiter, und ohne diese Zaehlung saesse der Kasten daneben.
     */
    @Expose public boolean headlineBold = false;
    @Expose public boolean headlineItalic = false;
    @Expose public boolean headlineUnderline = false;
    @Expose public boolean headlineStrike = false;
    @Expose public boolean valueBold = false;
    @Expose public boolean valueItalic = false;
    @Expose public boolean valueUnderline = false;
    @Expose public boolean valueStrike = false;

    /** Vor und hinter der Ueberschrift, etwa "[ " und " ]" */
    @Expose public String prefix = "";
    @Expose public String suffix = "";
    /**
     * Was links und rechts vom Wert steht.
     *
     * Die Klammern standen frueher fest im Code, der den Fund meldet - wer sie nicht
     * wollte, konnte nichts machen. Hier sind sie nur noch die Vorgabe.
     */
    @Expose public String valuePrefix = "(";
    @Expose public String valueSuffix = ")";
    @Expose public boolean showValue = true;
    @Expose public boolean showTier = true;
    /**
     * Wer es gefangen hat, steht ueber dem Namen statt darunter.
     *
     * Nur beim Titel-Aussehen (Background.TITLE) von Belang: Dort steht der Fangende von Haus aus in der unteren
     * Zeile, wie bei den Titeln des Spiels. Alle anderen Aussehen zeigen ihn ohnehin klein darueber.
     */
    @Expose public boolean nameAbove = false;

    @Expose public Icon icon = Icon.NONE;
    @Expose public float iconScale = 2.0f;

    @Expose public Animation animation = Animation.NONE;
    /** Welche Truhe aufspringt - gilt nur fuer die Chest-Animation */
    @Expose public Chest chest = Chest.ENDER;
    @Expose public int durationMillis = 3000;
    /** Hex wie FFD700. Leer: die Farbe der Stufe */
    @Expose public String colour = "";

    /**
     * Der grosse Auftritt ausserhalb des Banners.
     *
     * Das Stueck fliegt gross ueber den Bildschirm, so wie es der Totem der
     * Unsterblichkeit tut, und Funken steigen um den Spieler auf. Beides gehoert zum
     * Design und nicht zur Stufe: Wer sein Banner weitergibt, gibt den ganzen
     * Auftritt weiter.
     */
    @Expose public boolean itemFlourish = false;
    @Expose public Particles particles = Particles.NONE;
    /** Wie lange die Funken steigen, in Spielschritten (20 je Sekunde) */
    @Expose public int particleTicks = 12;

    public BannerDesign copy() {
        // Feld fuer Feld, damit eine Kopie wirklich eine ist. Frueher standen hier
        // nur die Felder von damals: Truhe, Klammern um den Wert und alles Spaetere
        // fielen beim Kopieren still auf die Vorgabe zurueck
        BannerDesign d = new BannerDesign();
        d.name = name;
        d.anchor = anchor;
        d.x = x;
        d.y = y;
        d.scale = scale;
        d.padding = padding;
        d.background = background;
        d.backgroundAlpha = backgroundAlpha;
        d.shape = shape;
        d.cornerRadius = cornerRadius;
        d.frame = frame;
        d.accent = accent;
        d.headlineSize = headlineSize;
        d.valueSize = valueSize;
        d.headlineColour = headlineColour;
        d.valueColour = valueColour;
        d.textEffect = textEffect;
        d.align = align;
        d.headlineBold = headlineBold;
        d.headlineItalic = headlineItalic;
        d.headlineUnderline = headlineUnderline;
        d.headlineStrike = headlineStrike;
        d.valueBold = valueBold;
        d.valueItalic = valueItalic;
        d.valueUnderline = valueUnderline;
        d.valueStrike = valueStrike;
        d.prefix = prefix;
        d.suffix = suffix;
        d.valuePrefix = valuePrefix;
        d.valueSuffix = valueSuffix;
        d.showValue = showValue;
        d.showTier = showTier;
        d.nameAbove = nameAbove;
        d.icon = icon;
        d.iconScale = iconScale;
        d.animation = animation;
        d.chest = chest;
        d.durationMillis = durationMillis;
        d.colour = colour;
        d.itemFlourish = itemFlourish;
        d.particles = particles;
        d.particleTicks = particleTicks;
        return d;
    }

    /** Gson laesst fehlende oder unbekannte Werte als null stehen - dann gilt der Standard */
    public void repair() {
        BannerDesign def = new BannerDesign();
        if (name == null || name.isBlank()) name = def.name;
        if (anchor == null) anchor = def.anchor;
        if (background == null) background = def.background;
        if (shape == null) shape = def.shape;
        if (frame == null) frame = def.frame;
        if (accent == null) accent = def.accent;
        if (headlineColour == null) headlineColour = def.headlineColour;
        if (valueColour == null) valueColour = def.valueColour;
        if (textEffect == null) textEffect = def.textEffect;
        if (align == null) align = def.align;
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        if (valuePrefix == null) valuePrefix = "";
        if (valueSuffix == null) valueSuffix = "";
        if (icon == null) icon = def.icon;
        if (animation == null) animation = def.animation;
        if (chest == null) chest = def.chest;
        if (particles == null) particles = def.particles;
        if (colour == null) colour = "";
        if (Float.isNaN(x)) x = def.x;
        if (Float.isNaN(y)) y = def.y;
        if (Float.isNaN(scale) || scale <= 0) scale = def.scale;
        if (Float.isNaN(backgroundAlpha)) backgroundAlpha = def.backgroundAlpha;
        if (Float.isNaN(headlineSize) || headlineSize <= 0) headlineSize = def.headlineSize;
        if (Float.isNaN(valueSize) || valueSize <= 0) valueSize = def.valueSize;
        if (Float.isNaN(iconScale) || iconScale <= 0) iconScale = def.iconScale;
        if (durationMillis < 1000) durationMillis = def.durationMillis;
        if (padding < 0 || padding > 60) padding = def.padding;
        if (cornerRadius < 0 || cornerRadius > 64) cornerRadius = def.cornerRadius;
        if (particleTicks < 1 || particleTicks > 100) particleTicks = def.particleTicks;
    }

    // ---- Die bisherigen Stile als Vorlagen ----

    private static BannerDesign preset(String name, Anchor anchor, Background bg, float bgAlpha, Frame frame, Accent accent,
                                       float headline, float value, TextColour hc, TextColour vc, TextEffect effect,
                                       String prefix, String suffix, Icon icon, Animation animation) {
        BannerDesign d = new BannerDesign();
        d.name = name;
        d.anchor = anchor;
        d.background = bg;
        d.backgroundAlpha = bgAlpha;
        d.frame = frame;
        d.accent = accent;
        d.headlineSize = headline;
        d.valueSize = value;
        d.headlineColour = hc;
        d.valueColour = vc;
        d.textEffect = effect;
        d.prefix = prefix;
        d.suffix = suffix;
        d.icon = icon;
        d.animation = animation;
        return d;
    }

    /** Die Vorlagen, in der alten Reihenfolge - Namen bleiben, damit alte Einstellungen passen */
    /** Die schlichte Fang-Vorlage: bis 1.16.14 die Vorgabe des Fang-Alarms, jetzt eine Vorlage unter vielen */
    public static final String PLAIN_CATCH = "Plain catch";
    /** Die Vorlage, die der Fang-Alarm nimmt, solange niemand etwas anderes waehlt */
    public static final String SEA_CREATURE = "Sea creature";
    /**
     * Die Vorlage fuer alles, was frueher ein einfaches Band quer ueber den Bildschirm war (Deployables, Effekte,
     * Chatregeln, Faehigkeit bereit): dasselbe Band, ohne Klammern um die zweite Zeile, vier Sekunden.
     */
    public static final String ALERT_BAND = "Alert band";
    /** Dasselbe sieben Sekunden lang - so lange stand der SHINY-Banner schon immer */
    public static final String ALERT_BAND_LONG = "Alert band long";

    public static List<BannerDesign> presets() {
        List<BannerDesign> out = new ArrayList<>(25);
        out.add(preset("Classic band", Anchor.BAND, Background.BOX, 0.55f, Frame.NONE, Accent.EDGES, 3.0f, 1.6f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Compact strip", Anchor.HOTBAR, Background.BOX, 0.67f, Frame.NONE, Accent.UNDERLINE, 1.5f, 1.5f, TextColour.WHITE, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Big title", Anchor.CENTER, Background.NONE, 0f, Frame.NONE, Accent.NONE, 4.0f, 2.0f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Side card", Anchor.RIGHT_EDGE, Background.BOX, 0.78f, Frame.NONE, Accent.LEFT_BAR, 1.3f, 1.0f, TextColour.WHITE, TextColour.ACCENT, TextEffect.SHADOW, "", "", Icon.NONE, Animation.SLIDE_RIGHT));
        out.add(preset("Top ribbon", Anchor.TOP, Background.FILL, 0.6f, Frame.NONE, Accent.NONE, 2.0f, 1.0f, TextColour.DARK, TextColour.DARK, TextEffect.PLAIN, "", "", Icon.NONE, Animation.DROP));
        out.add(preset("Outlined text", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.4f, 1.2f, TextColour.ACCENT, TextColour.WHITE, TextEffect.OUTLINE, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Boxed", Anchor.FREE, Background.BOX, 0.75f, Frame.SINGLE, Accent.NONE, 2.0f, 1.1f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Two tone", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.DIVIDER, 2.2f, 1.4f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Pop-in", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.6f, 1.2f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.POP));
        out.add(preset("Minimal dot", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.DOT, 1.0f, 1.0f, TextColour.WHITE, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Underline sweep", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.SWEEP, 2.2f, 1.2f, TextColour.WHITE, TextColour.ACCENT, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Double frame", Anchor.FREE, Background.BOX, 0.7f, Frame.DOUBLE, Accent.NONE, 1.9f, 1.1f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Glow", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.6f, 1.2f, TextColour.WHITE, TextColour.ACCENT, TextEffect.GLOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Left bar stack", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.LEFT_BAR, 1.8f, 1.0f, TextColour.WHITE, TextColour.ACCENT, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Small tag", Anchor.FREE, Background.FILL, 0.86f, Frame.NONE, Accent.NONE, 1.1f, 1.1f, TextColour.DARK, TextColour.DARK, TextEffect.PLAIN, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Brackets", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.4f, 1.2f, TextColour.WHITE, TextColour.ACCENT, TextEffect.SHADOW, "[ ", " ]", Icon.NONE, Animation.NONE));
        out.add(preset("Split bar", Anchor.FREE, Background.SPLIT, 0.78f, Frame.NONE, Accent.NONE, 1.5f, 1.5f, TextColour.WHITE, TextColour.DARK, TextEffect.PLAIN, "", "", Icon.NONE, Animation.NONE));
        out.add(preset("Typewriter", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.2f, 1.2f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.TYPEWRITER));
        out.add(preset("Flash", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.4f, 1.2f, TextColour.WHITE, TextColour.ACCENT, TextEffect.SHADOW, "", "", Icon.NONE, Animation.FLASH));
        out.add(preset("Chevrons", Anchor.FREE, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.2f, 1.2f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, ">> ", " <<", Icon.NONE, Animation.NONE));
        out.add(preset("Icon card", Anchor.FREE, Background.BOX, 0.75f, Frame.SINGLE, Accent.NONE, 1.6f, 1.6f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.MIDDLE, Animation.NONE));

        // ---- Die neuen Formen und der grosse Auftritt ----
        //
        // Sie stehen hinten, damit die alten Nummern bleiben, wo sie waren
        BannerDesign pille = preset("Pill", Anchor.FREE, Background.FILL, 0.8f, Frame.NONE, Accent.NONE, 1.6f, 1.2f, TextColour.DARK, TextColour.DARK, TextEffect.PLAIN, "  ", "  ", Icon.NONE, Animation.POP);
        pille.shape = Shape.PILL;
        pille.padding = 14;
        out.add(pille);

        BannerDesign oval = preset("Oval badge", Anchor.CENTER, Background.BOX, 0.8f, Frame.SINGLE, Accent.NONE, 2.0f, 1.2f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.POP);
        oval.shape = Shape.OVAL;
        oval.padding = 22;
        oval.headlineBold = true;
        out.add(oval);

        BannerDesign karte = preset("Rounded card", Anchor.FREE, Background.GRADIENT, 0.85f, Frame.CORNERS, Accent.DIVIDER, 1.8f, 1.2f, TextColour.WHITE, TextColour.ACCENT, TextEffect.SHADOW, "", "", Icon.LEFT, Animation.SLIDE_LEFT);
        karte.shape = Shape.ROUNDED;
        karte.cornerRadius = 10;
        karte.headlineBold = true;
        out.add(karte);

        // Der Farb-Alarm, wie ShokiTeufel ihn am 12.09.2026 gebaut und geteilt hat.
        // Uebernommen sind seine Werte, nicht meine Vorstellung davon
        BannerDesign farbe = preset("Dye alert", Anchor.BAND, Background.NONE, 1.0f, Frame.NONE, Accent.NONE,
                2.62f, 2.46f, TextColour.ACCENT, TextColour.WHITE, TextEffect.PLAIN, "", "", Icon.MIDDLE, Animation.CHEST);
        farbe.shape = Shape.CUT;
        farbe.scale = 1.49f;
        farbe.iconScale = 2.03f;
        farbe.showTier = false;
        farbe.valuePrefix = "";
        farbe.valueSuffix = "";
        farbe.durationMillis = 5021;
        farbe.particles = Particles.FLAME;
        farbe.particleTicks = 53;
        out.add(farbe);

        // Der Fang-Alarm: nur der Name, klar und klein.
        //
        // Die anderen Vorlagen zeigen einen Fund her - Kiste, Funken, grosse Schrift.
        // Beim Angeln taucht alle paar Minuten etwas auf, und dann will man lesen, was
        // da ist, nicht einem Auftritt zusehen. Also keine Animation, kein Bild, kein
        // Hintergrund, und eine Schrift, die neben dem Spiel nicht im Weg steht
        BannerDesign fang = preset(PLAIN_CATCH, Anchor.BAND, Background.NONE, 0f, Frame.NONE,
                Accent.NONE, 2.4f, 1.0f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW,
                "", "", Icon.NONE, Animation.NONE);
        fang.showTier = false;
        fang.valuePrefix = "";
        fang.valueSuffix = "";
        fang.durationMillis = 2500;
        out.add(fang);

        BannerDesign auftritt = preset("Grand reveal", Anchor.CENTER, Background.NONE, 0f, Frame.NONE, Accent.NONE, 2.8f, 1.6f, TextColour.ACCENT, TextColour.WHITE, TextEffect.GLOW, "", "", Icon.MIDDLE, Animation.CHEST);
        auftritt.headlineBold = true;
        auftritt.itemFlourish = true;
        auftritt.particles = Particles.TOTEM;
        auftritt.particleTicks = 20;
        out.add(auftritt);

        // Der Banner von Feesh: Er ist der Titel des Spiels. Der Name vierfach gross und fett in der Farbe der
        // Seltenheit, darunter doppelt gross, wer es gefangen hat; in der Mitte, zwei Sekunden voll und eine
        // halbe Sekunde zum Ausblenden. Die Masse sind die des Spiels (Gui.extractTitle), nicht geschaetzt
        BannerDesign feesh = preset("Feesh", Anchor.CENTER, Background.TITLE, 0f, Frame.NONE, Accent.NONE,
                4.0f, 2.0f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE, Animation.NONE);
        feesh.headlineBold = true;
        feesh.showTier = false;
        feesh.valuePrefix = "";
        feesh.valueSuffix = "";
        feesh.durationMillis = 2500;
        out.add(feesh);

        // Der Fang-Banner, den ShokiTeufel am 19.10.2026 gebaut und als Code geteilt hat: das Aussehen von Feesh (der
        // Titel des Spiels), der Fangende grau und fett ueber dem Namen, hoeher auf dem Bildschirm und mit Drachenatem.
        // Uebernommen sind seine Werte, nicht meine Vorstellung davon. Er ist die Vorgabe des Fang-Alarms
        BannerDesign meer = preset(SEA_CREATURE, Anchor.FREE, Background.TITLE, 0.25797874f, Frame.NONE, Accent.DIVIDER,
                4.0425534f, 1.9242021f, TextColour.ACCENT, TextColour.GREY, TextEffect.SHADOW, "", "", Icon.NONE,
                Animation.NONE);
        meer.x = 0.5f;
        meer.y = 0.3f;
        meer.scale = 0.9965426f;
        meer.padding = 10;
        meer.cornerRadius = 9;
        meer.valueBold = true;
        meer.valuePrefix = "";
        meer.valueSuffix = "";
        meer.showTier = false;
        meer.nameAbove = true;
        meer.iconScale = 1.9800532f;
        meer.durationMillis = 2500;
        meer.particles = Particles.DRAGON;
        meer.particleTicks = 12;
        out.add(meer);

        // Das alte Band, das mit dem Banner-Wechsel (1.16.20) zu einem Design wurde: Alle Meldungen, die bis dahin
        // ein festes Band zeigten, nehmen es von Haus aus und lassen sich im Sandkasten umstellen
        BannerDesign alarm = preset(ALERT_BAND, Anchor.BAND, Background.BOX, 0.55f, Frame.NONE, Accent.EDGES,
                3.0f, 1.6f, TextColour.ACCENT, TextColour.WHITE, TextEffect.SHADOW, "", "", Icon.NONE,
                Animation.NONE);
        alarm.valuePrefix = "";
        alarm.valueSuffix = "";
        alarm.durationMillis = 4000;
        out.add(alarm);
        BannerDesign lang = alarm.copy();
        lang.name = ALERT_BAND_LONG;
        lang.durationMillis = 7000;
        out.add(lang);
        return out;
    }

    /** Der Vorlagenname zum alten Stil-Namen, etwa CHEVRON -> "Chevrons" */
    public static String presetNameFor(String legacyStyle) {
        if (legacyStyle == null) return "Classic band";
        return switch (legacyStyle) {
            case "COMPACT" -> "Compact strip";
            case "TITLE" -> "Big title";
            case "CARD" -> "Side card";
            case "RIBBON" -> "Top ribbon";
            case "OUTLINE" -> "Outlined text";
            case "BOXED" -> "Boxed";
            case "TWO_TONE" -> "Two tone";
            case "POP" -> "Pop-in";
            case "MINIMAL" -> "Minimal dot";
            case "SWEEP" -> "Underline sweep";
            case "DOUBLE_FRAME" -> "Double frame";
            case "GLOW" -> "Glow";
            case "SIDEBAR" -> "Left bar stack";
            case "TAG" -> "Small tag";
            case "BRACKETS" -> "Brackets";
            case "SPLIT" -> "Split bar";
            case "TYPEWRITER" -> "Typewriter";
            case "FLASH" -> "Flash";
            case "CHEVRON" -> "Chevrons";
            case "ICON" -> "Icon card";
            default -> "Classic band";
        };
    }
}
