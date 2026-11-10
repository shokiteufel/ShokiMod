package com.shokiteufel.shokimod.dungeonkarte;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.FunnyMapCategory;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import com.shokiteufel.shokimod.util.TextCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import static com.shokiteufel.shokimod.dungeonkarte.KartenOption.*;

/**
 * Zeichnet die Funny Map: Raeume und Tueren in den Farben der Einstellungen, darin Haekchen, Geheimnisse oder Namen, darueber die Spieler
 * mit ihren Koepfen. Die Leinwand ist 128 Punkte breit wie die Karte von Hypixel und NoammAddons; mit dessen Lage und Groesse legt sie sich
 * deckend ueber seine Karte, und mit seinen Einstellungen sieht sie so aus wie seine ({@link KartenEinstellungen}).
 *
 * <p>Was sich nur bei einer Aenderung veraendert, wird nur dann gerechnet und gemalt: Untergrund, Rand, Raeume und Tueren sind ein Bild
 * ({@link KartenBild}, neu gemalt bei einer Aenderung des Rasters, der Einstellungen oder der Verschiebung); Lage der Namen, Breiten und Zeilen
 * stehen je Raumliste fertig da ({@link RaumAnzeige}); die Verschiebung des Rasters und die Zeile unter der Karte gelten, bis sich das Raster
 * aendert. Je Bild bleiben die Zeichenaufrufe fuer Haekchen, Texte und Spieler - sie liegen auf der Karte und wandern oder wechseln mit dem Spiel.
 */
public final class FunnyKarteHud {

    private static final int GRUEN = 0xFF55FF55;
    private static final int WEISS = 0xFFFFFFFF;
    private static final int ROT = 0xFFFF0000;
    private static final int GRAU = 0xFFAAAAAA;

    /** Haekchen und Pfeil: die Bilder von NoammAddons (CC0), je Haekchen ein Zeichenaufruf statt zehn kleiner Flaechen */
    private static final Identifier HAKEN_GRUEN = bild("checkmarks/green_check.png");
    private static final Identifier HAKEN_WEISS = bild("checkmarks/white_check.png");
    private static final Identifier KREUZ = bild("checkmarks/cross.png");
    private static final Identifier FRAGE = bild("checkmarks/question.png");
    private static final Identifier PFEIL = bild("marker.png");

    private static Identifier bild(String pfad) {
        return Identifier.fromNamespaceAndPath("shokimod", "textures/gui/dungeonmap/" + pfad);
    }
    /** Mit "Show Extra Info Under Map" ist die Karte 140 Punkte hoch, wie bei Noamm mit seiner Zeile unter der Karte */
    private static final int HOEHE = 128;
    private static final int HOEHE_MIT_ZEILE = 140;

    private FunnyKarteHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!FunnyKarte.sichtbar()) return;
        FunnyMapCategory c = ModConfig.INSTANCE.ikohS.funnyMap;
        KartenEinstellungen e = FunnyKarte.einstellungen();
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        Raster raster = FunnyKarte.raster();

        NoammLage.Lage noamm = c.followNoamm ? FunnyKarte.noamm() : null;
        float x = noamm != null ? noamm.x() : c.x;
        float y = noamm != null ? noamm.y() : c.y;
        float s = noamm != null ? noamm.scale() : c.scale;
        // Mit Noamms Lage gilt auch sein Umrechnungsfaktor: seine Koordinaten stehen in einer Referenzaufloesung von 960 x 540, nicht in GUI-Punkten
        boolean zeile = e.janein(EXTRA_INFO);
        int hoehe = zeile ? HOEHE_MIT_ZEILE : HOEHE;
        float referenz = noamm != null
                ? NoammLage.referenzFaktor(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()) : 1f;

        g.pose().pushMatrix();
        if (referenz != 1f) g.pose().scale(referenz, referenz);
        g.pose().translate(x, y);
        g.pose().scale(s, s);

        // Mit Noamms Lage beginnt das Raster dort, wo es bei ihm beginnt (die Ecke aus der Karte von Hypixel, bei ihm "startCorner"); sonst
        // steht das Belegte in der Mitte der Leinwand
        KartenLeser.Kalibrierung kal = noamm != null ? FunnyKarte.kalibrierung() : null;
        int[] versch = verschiebung(raster, kal);

        // Untergrund, Rand, Raeume und Tueren: ein Bild, nur bei einer Aenderung neu gemalt. Geht das nicht, wie frueher Flaeche fuer Flaeche
        if (!KartenBild.zeichnen(g, raster, versch, e, hoehe)) einzeln(g, raster, versch, e, hoehe);

        inhalt(g, font, raster, versch, e);
        if (c.showPlayers) spieler(g, client, font, versch, e);
        if (zeile) zusatzzeile(g, font, raster, hoehe);

        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ Verschiebung des Rasters

    private static Raster verschRaster;
    private static int verschStand = -1;
    private static int verschKx = Integer.MIN_VALUE;
    private static int verschKy = Integer.MIN_VALUE;
    private static int[] verschWert = {0, 0};

    /** Wo das Raster auf der Leinwand beginnt - gerechnet nur, wenn sich das Raster oder die Ecke aus der Karte aendert, nicht in jedem Bild */
    private static int[] verschiebung(Raster raster, KartenLeser.Kalibrierung kal) {
        int kx = kal == null ? Integer.MIN_VALUE : kal.startX();
        int ky = kal == null ? Integer.MIN_VALUE : kal.startY();
        if (raster != verschRaster || raster.stand() != verschStand || kx != verschKx || ky != verschKy) {
            verschRaster = raster;
            verschStand = raster.stand();
            verschKx = kx;
            verschKy = ky;
            verschWert = kal != null
                    ? new int[] {KartenGeometrie.noammEcke(kx), KartenGeometrie.noammEcke(ky)}
                    : KartenGeometrie.verschiebung(KartenGeometrie.begrenzung(raster));
        }
        return verschWert;
    }

    // ------------------------------------------------------------------ Raeume und Tueren (Rueckfall: einzeln gezeichnet)

    /** Der Weg ohne Kartenbild: Untergrund, Rand und jede belegte Kachel als eigene Flaeche - nur wenn die Textur nicht zu haben war */
    private static void einzeln(GuiGraphicsExtractor g, Raster raster, int[] versch, KartenEinstellungen e, int hoehe) {
        // Untergrund und Rand: Farbe und Dicke nach den Einstellungen ("Map Background Color", "Map Border Color", "Border Thickness")
        int rand = e.farbe(BORDER_COLOR);
        int dicke = Math.max(1, e.ganz(BORDER_WIDTH));
        g.fill(0, 0, KartenGeometrie.LEINWAND, hoehe, e.farbe(BACKGROUND));
        g.fill(0, 0, KartenGeometrie.LEINWAND, dicke, rand);
        g.fill(0, hoehe - dicke, KartenGeometrie.LEINWAND, hoehe, rand);
        g.fill(0, 0, dicke, hoehe, rand);
        g.fill(KartenGeometrie.LEINWAND - dicke, 0, KartenGeometrie.LEINWAND, hoehe, rand);

        int tuerBreite = e.ganz(DOOR_WIDTH);
        for (int i = 0; i < Raster.PLAETZE; i++) {
            Kachel k = raster.get(i);
            if (k == null) continue;
            boolean tuer = k.art == Kachel.Art.TUER;
            int[] r = KartenGeometrie.rechteck(i / Raster.KANTE, i % Raster.KANTE, tuer, tuerBreite);
            int farbe = tuer ? KartenFarben.tuer(raster, i, k, e) : KartenFarben.kachel(k, e);
            g.fill(versch[0] + r[0], versch[1] + r[1], versch[0] + r[0] + r[2], versch[1] + r[1] + r[3], farbe);
        }
    }

    // ------------------------------------------------------------------ Haekchen, Geheimnisse, Namen

    /** Haekchen, Geheimnisse oder Namen je Raum - an der Stelle, die "Center Checkmark/Text" bestimmt */
    private static void inhalt(GuiGraphicsExtractor g, Font font, Raster raster, int[] versch, KartenEinstellungen e) {
        java.util.List<Raster.Raum> raeume = raster.raeume();
        // Die Anzeigedaten gelten, solange die Raumliste dieselbe ist (sie wird nur bei einer Aenderung des Rasters neu gerechnet)
        if (raeume != anzeigenListe || font != anzeigenSchrift) {
            ANZEIGEN.clear();
            anzeigenListe = raeume;
            anzeigenSchrift = font;
        }
        // Die Einstellungen einmal je Bild lesen, nicht je Raum
        int stil = e.ganz(CHECKMARK_STYLE);
        float textMassstab = e.komma(TEXT_SCALE);
        boolean mittig = e.janein(CENTER);
        boolean blutVerbergen = e.janein(HIDE_BLOOD_TEXT);
        float haekchenGroesse = e.komma(CHECKMARK_SCALE);
        boolean unbekanntVerbergen = e.janein(HIDE_UNKNOWN_CHECK);
        boolean begrenzen = e.janein(LIMIT_NAME_SIZE);
        for (int n = 0, anzahl = raeume.size(); n < anzahl; n++) {
            Raster.Raum raum = raeume.get(n);
            RaumDaten daten = raum.daten();
            // Eingang und Fairy tragen keinen Text, wie bei Noamm
            if (daten.typ() == RaumTyp.EINGANG || daten.typ() == RaumTyp.FAIRY) continue;
            if (blutVerbergen && daten.typ() == RaumTyp.BLUT) continue;

            RaumAnzeige a = anzeige(font, raum, daten);
            float mitteX = a.mitteX(raum, versch, mittig);
            float mitteY = a.mitteY;
            int farbe = zustandsfarbe(raum.zustand());

            // Stil 4 ("Secrets + Puzzle Name"): Puzzles und Raeume ohne Geheimnisse zeigen den Namen, alle anderen die Geheimnisse
            int wirksam = stil == 4 ? (daten.typ() == RaumTyp.PUZZLE || daten.geheimnisse() == 0 ? 2 : 1) : stil;
            switch (wirksam) {
                case 0 -> haekchen(g, raum.zustand(), mitteX, mitteY, haekchenGroesse, unbekanntVerbergen);
                case 1 -> {
                    String t = daten.istUnbekannt() ? "?" : daten.geheimnisse() == 0 ? "0" : a.geheimnisse(raum, daten);
                    text(g, font, t, mitteX, mitteY, textMassstab, farbe);
                }
                default -> namen(g, font, a, raum, daten, mitteX, mitteY, farbe, wirksam == 3, textMassstab, begrenzen);
            }
        }
    }

    private static int zustandsfarbe(Zustand z) {
        return switch (z) {
            case GRUEN -> GRUEN;
            case FEHLGESCHLAGEN -> ROT;
            case GERAEUMT -> WEISS;
            default -> GRAU;
        };
    }

    private static void namen(GuiGraphicsExtractor g, Font font, RaumAnzeige a, Raster.Raum raum, RaumDaten daten, float cx, float cy, int farbe,
                              boolean mitGeheimnissen, float grundMassstab, boolean begrenzen) {
        String[] worte = a.worte;
        boolean geheimnisse = mitGeheimnissen && !daten.istUnbekannt() && daten.geheimnisse() > 0;
        String zahlen = geheimnisse ? a.geheimnisse(raum, daten) : "";
        int zeilen = worte.length + (geheimnisse ? 1 : 0);

        // Wie bei Noamm: mit "Limit Room Name Size" schrumpft der Text, bis die breiteste Zeile und alle Zeilen in den Raum passen (nicht unter
        // 0,39, nicht ueber den eingestellten Wert); ohne die Einstellung gilt der eingestellte Wert, auch wenn der Name ueber den Raum ragt
        float massstab = grundMassstab;
        if (begrenzen) {
            int[] platz = a.platz;
            int breit = Math.max(TextCache.breite(font, zahlen), a.breitWorte);
            float hoeheAlle = zeilen * font.lineHeight;
            if (breit > 0 && hoeheAlle > 0) {
                massstab = Math.min(platz[0] / (float) breit, platz[1] / hoeheAlle);
                massstab = Math.max(0.39f, Math.min(grundMassstab, massstab));
            }
        }

        float hoeheGesamt = zeilen * font.lineHeight * massstab;
        float y = cy - hoeheGesamt / 2f;
        for (String w : worte) {
            text(g, font, w, cx, y + font.lineHeight * massstab / 2f, massstab, farbe);
            y += font.lineHeight * massstab;
        }
        if (geheimnisse) text(g, font, zahlen, cx, y + font.lineHeight * massstab / 2f, massstab, farbe);
    }

    /**
     * Was sich an der Anzeige eines Raums nicht aendert, solange Raumliste und Schrift dieselben sind: die Worte seines Namens, ihre breiteste
     * Breite, der Platz des Raums, wo seine Mitte liegt und der Text "3/9" der Geheimnisse (neu nur, wenn sich die Zahl aendert).
     */
    private static final class RaumAnzeige {
        final String[] worte;
        final int breitWorte;
        final int[] platz;
        private final java.util.List<Integer> plaetze;

        // Die Mitte in Leinwandpunkten, gerechnet fuer eine Verschiebung und eine Einstellung
        private int mitteV0 = Integer.MIN_VALUE;
        private int mitteV1 = Integer.MIN_VALUE;
        private boolean mitteMittig;
        private float mitteX;
        float mitteY;

        private int geheimnisWert = -1;
        private String geheimnisText = "";

        RaumAnzeige(Font font, Raster.Raum raum, RaumDaten daten) {
            this.worte = daten.istUnbekannt() ? new String[] {"?"} : daten.name().split(" ");
            int breit = 0;
            for (String w : worte) breit = Math.max(breit, font.width(w));
            this.breitWorte = breit;
            this.platz = platzDesRaums(raum);
            this.plaetze = raum.plaetze();
        }

        /** Die x-Mitte; rechnet die Stelle neu, wenn sich Verschiebung oder Einstellung geaendert haben (y steht danach in {@link #mitteY}) */
        float mitteX(Raster.Raum raum, int[] versch, boolean mittig) {
            if (versch[0] != mitteV0 || versch[1] != mitteV1 || mittig != mitteMittig) {
                int[] stelle = RaumMitte.stelle(plaetze, mittig);
                mitteX = versch[0] + (stelle[0] / 2f) * KartenGeometrie.SCHRITT + KartenGeometrie.RAUM / 2f;
                mitteY = versch[1] + (stelle[1] / 2f) * KartenGeometrie.SCHRITT + KartenGeometrie.RAUM / 2f;
                mitteV0 = versch[0];
                mitteV1 = versch[1];
                mitteMittig = mittig;
            }
            return mitteX;
        }

        /** "3/9": schon gefunden von allen; ein fertiger (gruener) Raum hat alle, einer ohne bekannten Stand null - wie bei Noamm */
        String geheimnisse(Raster.Raum raum, RaumDaten daten) {
            int gesamt = daten.geheimnisse();
            int gefunden = raum.zustand() == Zustand.GRUEN ? gesamt : Math.max(0, FunnyKarte.gefundenIn(raum));
            int wert = Math.min(gefunden, gesamt);
            if (wert != geheimnisWert) {
                geheimnisWert = wert;
                geheimnisText = wert + "/" + gesamt;
            }
            return geheimnisText;
        }
    }

    private static final java.util.IdentityHashMap<Raster.Raum, RaumAnzeige> ANZEIGEN = new java.util.IdentityHashMap<>();
    private static java.util.List<Raster.Raum> anzeigenListe;
    private static Font anzeigenSchrift;

    private static RaumAnzeige anzeige(Font font, Raster.Raum raum, RaumDaten daten) {
        RaumAnzeige a = ANZEIGEN.get(raum);
        if (a == null) {
            a = new RaumAnzeige(font, raum, daten);
            ANZEIGEN.put(raum, a);
        }
        return a;
    }

    /** Breite und Hoehe des Raums in Punkten: das umschliessende Rechteck seiner Plaetze */
    private static int[] platzDesRaums(Raster.Raum raum) {
        int links = Integer.MAX_VALUE;
        int oben = Integer.MAX_VALUE;
        int rechts = Integer.MIN_VALUE;
        int unten = Integer.MIN_VALUE;
        for (int p : raum.plaetze()) {
            int[] r = KartenGeometrie.rechteck(p / Raster.KANTE, p % Raster.KANTE, false);
            links = Math.min(links, r[0]);
            oben = Math.min(oben, r[1]);
            rechts = Math.max(rechts, r[0] + r[2]);
            unten = Math.max(unten, r[1] + r[3]);
        }
        return new int[] {rechts - links, unten - oben};
    }

    /** Text, dessen Mitte bei (cx, cy) liegt, in diesem Massstab */
    private static void text(GuiGraphicsExtractor g, Font font, String s, float cx, float cy, float massstab, int farbe) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy - font.lineHeight * massstab / 2f);
        g.pose().scale(massstab, massstab);
        g.centeredText(font, s, 0, 0, farbe);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ Haekchen

    private static void haekchen(GuiGraphicsExtractor g, Zustand z, float cx, float cy, float groesse, boolean unbekanntVerbergen) {
        Identifier bild = switch (z) {
            case GRUEN -> HAKEN_GRUEN;
            case GERAEUMT -> HAKEN_WEISS;
            case FEHLGESCHLAGEN -> KREUZ;
            case GESCHLOSSEN -> unbekanntVerbergen ? null : FRAGE;
            default -> null;
        };
        if (bild == null) return;
        // Wie bei Noamm: "Map Checkmark Scale" mal zehn Punkte, um die Mitte des Raums
        int s = Math.max(1, Math.round(groesse * 10f));
        g.blit(RenderPipelines.GUI_TEXTURED, bild, Math.round(cx - s / 2f), Math.round(cy - s / 2f), 0f, 0f, s, s, s, s, WEISS);
    }

    // ------------------------------------------------------------------ Spieler

    private static void spieler(GuiGraphicsExtractor g, Minecraft client, Font font, int[] versch, KartenEinstellungen e) {
        if (client.level == null) return;
        for (AbstractClientPlayer p : client.level.players()) {
            // Nur echte Spieler (NPCs tragen keine Version-4-Kennung); man selbst wird zuletzt gezeichnet, damit er oben liegt
            if (p == client.player || p.getUUID().version() != 4) continue;
            kopf(g, font, versch, p, false, client, e);
        }
        if (client.player != null) kopf(g, font, versch, client.player, true, client, e);
    }

    /** Die Namen der Spieler, je Kennung einmal gelesen (das Component des Spielers ist bei jedem Abruf ein neues Objekt) */
    private static final java.util.HashMap<java.util.UUID, String> SPIELERNAMEN = new java.util.HashMap<>();

    private static String spielername(AbstractClientPlayer p) {
        java.util.UUID id = p.getUUID();
        String name = SPIELERNAMEN.get(id);
        if (name == null) {
            if (SPIELERNAMEN.size() >= 64) SPIELERNAMEN.clear();
            name = p.getName().getString();
            SPIELERNAMEN.put(id, name);
        }
        return name;
    }

    /**
     * Ein Spieler auf der Karte, wie bei Noamm: sein Kopf (12 Punkte) in einem Rahmen (14), mit der Blickrichtung nach oben gedreht (Norden ist
     * oben), dazu auf Wunsch sein Name darunter. "Vanilla Head Marker" ersetzt den eigenen Kopf durch den Pfeil der gewoehnlichen Karte.
     */
    private static void kopf(GuiGraphicsExtractor g, Font font, int[] versch, AbstractClientPlayer p, boolean selbst, Minecraft client, KartenEinstellungen e) {
        float px = versch[0] + KartenGeometrie.weltZuLeinwand(p.getX());
        float py = versch[1] + KartenGeometrie.weltZuLeinwand(p.getZ());
        // ausserhalb der Leinwand (Boss-Raum, weit weg) gibt es nichts zu zeigen
        if (px < 0 || py < 0 || px > KartenGeometrie.LEINWAND || py > KartenGeometrie.LEINWAND) return;
        String name = spielername(p);
        Integer klasse = DungeonKlassen.farbe(name);

        double gier = Math.toRadians(p.getYRot() + 180f);
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().rotate((float) gier);
        g.pose().scale(e.komma(HEAD_SCALE), e.komma(HEAD_SCALE));

        if (selbst && e.janein(VANILLA_MARKER)) {
            // Der Pfeil von NoammAddons (7 mal 7, schwarzer Rand, heller Kern), auf 12 Punkte gezogen und in der gewaehlten Farbe getoent
            g.blit(RenderPipelines.GUI_TEXTURED, PFEIL, -6, -6, 0f, 0f, 12, 12, 12, 12, e.farbe(VANILLA_MARKER_COLOR));
        } else {
            int rahmen = e.janein(HEAD_BORDER_CLASS) && klasse != null ? 0xFF000000 | klasse : e.farbe(HEAD_BORDER);
            g.fill(-7, -7, 7, 7, rahmen);
            PlayerFaceExtractor.extractRenderState(g, p.getSkin(), -6, -6, 12);
        }

        int namen = e.ganz(PLAYER_NAMES);
        boolean zeigen = namen == 2 || (namen == 1 && haeltSprung(client));
        if (zeigen) {
            g.pose().rotate((float) -gier);
            g.pose().translate(0f, 8f);
            float ns = e.komma(NAME_SCALE);
            g.pose().scale(ns, ns);
            int farbe = e.janein(NAMES_CLASS) && klasse != null ? 0xFF000000 | klasse : WEISS;
            g.centeredText(font, name, 0, 0, farbe);
        }
        g.pose().popMatrix();
    }

    private static ItemStack sprungStueck;
    private static boolean sprungErgebnis;

    /** Haelt man einen Sprung ("Spirit Leap", "Infinileap", die Faehigkeit des Geistes)? Dann zeigt "Holding Leap" die Namen */
    private static boolean haeltSprung(Minecraft client) {
        if (client.player == null) return false;
        ItemStack hand = client.player.getMainHandItem();
        // Dasselbe Stueck in der Hand: dieselbe Antwort (die Kennung aus den Daten des Stuecks zu lesen kostet jedes Mal)
        if (hand != sprungStueck) {
            String id = ItemChanges.countedId(hand);
            sprungErgebnis = "SPIRIT_LEAP".equals(id) || "INFINITE_SPIRIT_LEAP".equals(id) || "HAUNT_ABILITY".equals(id);
            sprungStueck = hand;
        }
        return sprungErgebnis;
    }

    // ------------------------------------------------------------------ Zeile unter der Karte

    private static Raster zeileRaster;
    private static int zeileStand = -1;
    private static String zeileText = "";

    private static void zusatzzeile(GuiGraphicsExtractor g, Font font, Raster raster, int hoehe) {
        // Die Zeile gilt, bis sich das Raster aendert
        if (raster != zeileRaster || raster.stand() != zeileStand) {
            int[] raeume = raster.zaehleRaeume();
            zeileText = "Secrets " + raster.geheimnisse() + (raeume[1] > 0 ? "  |  ? " + raeume[1] : "");
            zeileRaster = raster;
            zeileStand = raster.stand();
        }
        text(g, font, zeileText, KartenGeometrie.LEINWAND / 2f, KartenGeometrie.LEINWAND + (hoehe - KartenGeometrie.LEINWAND) / 2f, 0.8f, GRAU);
    }
}
