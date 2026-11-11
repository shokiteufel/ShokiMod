package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.data.ModConfig.LeapOrder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Rad ("Wheel" bei NoammAddons, {@code WheelLeapRenderer}, CC0): fuenf Stuecke eines Rings, alle fuenf Spieler samt dem eigenen - so bleibt
 * die Reihenfolge gleich, wenn man die Klasse wechselt. Die Klassen haben feste Stuecke; wer keinen Platz findet, rueckt in ein freies.
 */
final class SprungRad extends SprungDarstellung {

    static final SprungRad INSTANZ = new SprungRad();

    private static final int PLAETZE = 5;
    private static final double STUECK = Math.PI * 2.0 / PLAETZE;
    private static final float AUSSEN = 130f;
    private static final float INNEN = AUSSEN * 0.45f;
    private static final float TOTER_WINKEL = 5f;
    private static final int KOPF = 32;
    private static final int TRENNER = 0x28FFFFFF;
    private static final int TOT = 0xFFFF5555;

    private SprungRad() {
    }

    @Override
    void zeichnen(GuiGraphicsExtractor g, Font font, int guiBreite, int guiHoehe, double mausX, double mausY) {
        float s = SprungZeichnen.faktor(guiBreite, guiHoehe);
        float nutzer = SprungMenue.cfg().scale / 100f * 2.0f;
        float mitteX = guiBreite / s / nutzer / 2f;
        float mitteY = guiHoehe / s / nutzer / 2f;

        g.pose().pushMatrix();
        g.pose().scale(s, s);
        SprungZeichnen.rechteck(g, 0, 0, guiBreite / s, guiHoehe / s, 0x96000000);
        g.pose().scale(nutzer, nutzer);

        SprungSpieler[] plaetze = plaetze();
        SprungSpieler ich = SprungTeam.ich();
        Integer zeiger = platz(mausX / s / nutzer - mitteX, mausY / s / nutzer - mitteY);
        SprungSpieler unterZeiger = null;

        for (int i = 0; i < PLAETZE; i++) {
            SprungSpieler spieler = plaetze[i];
            if (spieler == null) continue;
            boolean selbst = spieler == ich;
            int menuePlatz = SprungMenue.platzVon(spieler);
            if (!selbst && menuePlatz == -1) continue;

            boolean zeigt = !selbst && zeiger != null && i == zeiger;
            if (zeigt) unterZeiger = spieler;

            int farbe = SprungMenue.kastenFarbe(spieler, zeigt);
            double winkel = -Math.PI / 2.0 + i * STUECK;
            SprungZeichnen.ringstueck(g, mitteX, mitteY, INNEN, AUSSEN, winkel - STUECK / 2.0, winkel + STUECK / 2.0,
                    SprungFarben.mitDeckkraft(farbe, 190));
            spielerImStueck(g, font, spieler, winkel, mitteX, mitteY, selbst);
        }

        for (int i = 0; i < PLAETZE; i++) {
            double winkel = -Math.PI / 2.0 - STUECK / 2.0 + i * STUECK;
            float cos = (float) Math.cos(winkel);
            float sin = (float) Math.sin(winkel);
            SprungZeichnen.linie(g, mitteX + cos * (INNEN - 1f), mitteY + sin * (INNEN - 1f),
                    mitteX + cos * (AUSSEN + 1f), mitteY + sin * (AUSSEN + 1f), TRENNER, 2f);
        }

        if (unterZeiger != null) {
            int klasse = 0xFF000000 | unterZeiger.klasse.farbe;
            SprungZeichnen.mitte(g, font, unterZeiger.name, mitteX, mitteY - 9f, klasse, 1f);
            SprungZeichnen.mitte(g, font, unterZeiger.tot ? "DEAD" : unterZeiger.klasse.anzeige, mitteX, mitteY + 2f,
                    unterZeiger.tot ? TOT : klasse, 0.8f);
        }
        g.pose().popMatrix();
    }

    private static void spielerImStueck(GuiGraphicsExtractor g, Font font, SprungSpieler spieler, double winkel, float mitteX, float mitteY,
                                        boolean selbst) {
        float radius = (INNEN + AUSSEN) / 2f;
        int kx = (int) (mitteX + (float) Math.cos(winkel) * radius - KOPF / 2f);
        int ky = (int) (mitteY + (float) Math.sin(winkel) * radius - KOPF / 2f - 5f);
        int klasse = 0xFF000000 | spieler.klasse.farbe;
        SprungZeichnen.kopf(g, spieler.haut, kx, ky, KOPF);
        SprungZeichnen.rahmen(g, kx, ky, KOPF, KOPF, klasse, 1);
        SprungZeichnen.mitte(g, font, selbst ? "You" : spieler.name, kx + KOPF / 2f, ky + KOPF + 3f, selbst ? 0xFFAAAAAA : klasse, 0.75f);
    }

    @Override
    Integer index(int guiBreite, int guiHoehe, double mausX, double mausY) {
        float s = SprungZeichnen.faktor(guiBreite, guiHoehe);
        float nutzer = SprungMenue.cfg().scale / 100f * 2.0f;
        double mitteX = guiBreite / s / nutzer / 2.0;
        double mitteY = guiHoehe / s / nutzer / 2.0;
        Integer stueck = platz(mausX / s / nutzer - mitteX, mausY / s / nutzer - mitteY);
        if (stueck == null) return null;
        SprungSpieler spieler = plaetze()[stueck];
        if (spieler == null || spieler == SprungTeam.ich()) return null;
        int p = SprungMenue.platzVon(spieler);
        return p == -1 ? null : p;
    }

    /** Welches Stueck des Rads liegt in (dx, dy) von der Mitte? Null in der toten Zone um die Mitte */
    static Integer platz(double dx, double dy) {
        if (Math.hypot(dx, dy) <= TOTER_WINKEL) return null;
        return Math.floorMod((int) Math.floor((Math.atan2(dy, dx) + Math.PI / 2.0 + STUECK / 2.0) / STUECK), PLAETZE);
    }

    /** Die fuenf Plaetze des Rads mit den Spielern des Teams ({@code wheelSlots}) */
    static SprungSpieler[] plaetze() {
        return plaetze(SprungTeam.alle(), SprungMenue.cfg().sorting, SprungMenue.eigeneKlasse(), SprungMenue.sortierDaten());
    }

    /**
     * Die Plaetze: Bei A-Z Class und Odin hat jede Klasse ihr festes Stueck (Archer oben, dann im Uhrzeigersinn), wer dort keinen Platz hat, rueckt
     * in das erste freie; sonst gilt die Sortierung des Menues, die ersten fuenf.
     */
    static SprungSpieler[] plaetze(List<SprungSpieler> team, LeapOrder sortierung, SprungKlasse eigene, SprungMenue.Sortierdaten daten) {
        SprungSpieler[] plaetze = new SprungSpieler[PLAETZE];
        if (sortierung != LeapOrder.CLASS && sortierung != LeapOrder.ODIN) {
            List<SprungSpieler> sortiert = SprungSortierung.sortieren(team, sortierung, eigene, daten.typ(), daten.reihenfolge());
            for (int i = 0; i < PLAETZE && i < sortiert.size(); i++) plaetze[i] = sortiert.get(i);
            return plaetze;
        }

        List<SprungSpieler> uebrige = new ArrayList<>();
        List<SprungSpieler> nachName = new ArrayList<>(team);
        nachName.sort((a, b) -> a.name.compareTo(b.name));
        for (SprungSpieler spieler : nachName) {
            int index = spieler.klasse.ordinal();
            if (index < PLAETZE && plaetze[index] == null) plaetze[index] = spieler;
            else uebrige.add(spieler);
        }
        for (SprungSpieler spieler : uebrige) {
            int frei = -1;
            for (int i = 0; i < PLAETZE; i++) {
                if (plaetze[i] == null) {
                    frei = i;
                    break;
                }
            }
            if (frei == -1) break;
            plaetze[frei] = spieler;
        }
        return plaetze;
    }

    /** Fuer die Taste des Platzes {@code slot} (0 bis 3): welcher Platz des Menues liegt an dieser Stelle des Rads (ohne einen selbst)? */
    static Integer tastenPlatz(int slot) {
        SprungSpieler ich = SprungTeam.ich();
        int n = 0;
        for (SprungSpieler spieler : plaetze()) {
            if (spieler == null || spieler == ich) continue;
            if (n++ == slot) {
                int p = SprungMenue.platzVon(spieler);
                return p == -1 ? null : p;
            }
        }
        return null;
    }
}
