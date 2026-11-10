package com.shokiteufel.shokimod.dungeonkarte;

import com.mojang.blaze3d.platform.NativeImage;
import com.shokiteufel.shokimod.ShokiMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Das Bild der Funny Map (Untergrund, Rand, Raeume, Tueren) als eigene Textur. Gemalt wird es nur, wenn sich etwas aendert - das Raster, die
 * Einstellungen, die Hoehe oder die Verschiebung -, danach gibt jedes Bild nur noch einen Zeichenaufruf mit der fertigen Textur ab. Die
 * Haekchen, Texte und Spieler liegen weiter einzeln darueber: Sie aendern sich mit dem Spieler oder sind Schrift.
 *
 * <p>Geht beim Anlegen oder Hochladen etwas schief (eine andere Spielfassung, kein Grafikspeicher), meldet {@link #zeichnen} {@code false}, und die
 * Anzeige zeichnet wie bisher jede Flaeche einzeln - die Karte bleibt sichtbar.
 */
final class KartenBild {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("shokimod", "dynamic/funny_map");

    private static final int[] PIXEL = new int[KartenMaler.BREITE * KartenMaler.HOEHE_MAX];
    private static DynamicTexture textur;
    private static boolean kaputt;

    // Wofuer das Bild gemalt ist
    private static Raster gemalFuer;
    private static int rasterStand = -1;
    private static KartenEinstellungen einstellungen;
    private static int hoehe = -1;
    private static int versch0;
    private static int versch1;
    /** Wie oft gemalt wurde - fuer die Messung, wie selten das ist */
    private static int malungen;

    private KartenBild() {
    }

    /** Zeichnet das Bild an die Stelle (0, 0) des aktuellen Koordinatensystems; {@code false}, wenn es nicht ging (dann bitte einzeln zeichnen) */
    static boolean zeichnen(GuiGraphicsExtractor g, Raster raster, int[] versch, KartenEinstellungen e, int hoeheNeu) {
        if (kaputt) return false;
        try {
            if (textur == null) anlegen();
            if (raster != gemalFuer || raster.stand() != rasterStand || e != einstellungen || hoeheNeu != hoehe
                    || versch[0] != versch0 || versch[1] != versch1) {
                neuMalen(raster, versch, e, hoeheNeu);
            }
            g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0f, 0f, KartenMaler.BREITE, hoeheNeu,
                    KartenMaler.BREITE, KartenMaler.HOEHE_MAX, 0xFFFFFFFF);
            return true;
        } catch (Throwable t) {
            ausgefallen(t);
            return false;
        }
    }

    /** Der Weg bei einer Aenderung: malen, hochladen, die Schluessel merken - er wird nur durchlaufen, wenn sich etwas geaendert hat */
    private static void neuMalen(Raster raster, int[] versch, KartenEinstellungen e, int hoeheNeu) {
        KartenMaler.malen(PIXEL, hoeheNeu, raster, versch, e);
        hochladen();
        gemalFuer = raster;
        rasterStand = raster.stand();
        einstellungen = e;
        hoehe = hoeheNeu;
        versch0 = versch[0];
        versch1 = versch[1];
        malungen++;
        com.shokiteufel.shokimod.util.DungeonLog.zaehlen(com.shokiteufel.shokimod.util.DungeonLog.Zaehler.BILD_GEMALT);
    }

    private static void ausgefallen(Throwable t) {
        kaputt = true;
        ShokiMod.LOGGER.warn("[FunnyMap] the map image could not be used, drawing every tile on its own instead: {}", t.toString());
    }

    private static void anlegen() {
        NativeImage bild = new NativeImage(KartenMaler.BREITE, KartenMaler.HOEHE_MAX, true);
        textur = new DynamicTexture(() -> "ShokiMod Funny Map", bild);
        Minecraft.getInstance().getTextureManager().register(ID, textur);
    }

    private static void hochladen() {
        NativeImage bild = textur.getPixels();
        for (int y = 0; y < KartenMaler.HOEHE_MAX; y++) {
            int zeile = y * KartenMaler.BREITE;
            for (int x = 0; x < KartenMaler.BREITE; x++) bild.setPixel(x, y, PIXEL[zeile + x]);
        }
        textur.upload();
    }

    /** Wie oft das Bild bisher neu gemalt wurde (Pruefung und Messung) */
    static int malungen() {
        return malungen;
    }
}
