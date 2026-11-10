package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.GlassColour;
import com.shokiteufel.shokimod.data.ModConfig.IHateDoorsCategory;
import com.shokiteufel.shokimod.dungeonkarte.Raster;
import com.shokiteufel.shokimod.util.DungeonLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "I hate doors": die Tueren des Dungeons (Eingang, Wither, Blut) werden im Client durch Glas ersetzt. Eins zu eins von NoammAddons
 * (CC0, {@code IHateDoors.kt} samt den Mixins {@code MixinRenderSectionRegion} und {@code MixinSodiumLevelSlice}; dort gehoert es zum "cheat"-Bau).
 *
 * <p>Es ist nur die Darstellung: Die Mixins tauschen den Block, den der Zeichner eines Chunk-Abschnitts beim Bauen des Netzes liest
 * ({@code RenderSectionRegion.getBlockState}, bei Sodium {@code LevelSlice.getBlockState}). Die Welt, die Kollision und alles, was der Server
 * sieht, bleiben wie sie sind. Ersetzt wird nur im Dungeon und nicht im Boss-Raum, nur in Hoehe 69 bis 72 und nur dort, wo ein Tuer-Platz des
 * Rasters liegt (jeder zweite Platz in Schritten von 16 Bloecken, ab dem ersten Raum).
 *
 * <p>Damit ein Wechsel (Einstellung, Betreten des Dungeons) sichtbar wird, markiert {@link #tick} die Abschnitte der Tueren zum Neuzeichnen -
 * genau wie bei Noamm nur dort, wo sich der Block wirklich anders darstellt.
 */
public final class IHateDoors {

    /** Der Abstand zwischen zwei Rasterplaetzen: eine halbe Raumgroesse (bei Noamm {@code DungeonScanner.roomSize / 2}) */
    static final int TUER_SCHRITT = Raster.RAUM / 2;
    private static final int UNTEN = 69;
    private static final int OBEN = 72;
    /** Zehn Plaetze in Schritten, dazu die zwei Bloecke einer Tuer hinter dem letzten Platz (Noamm: {@code 10 * doorStep + 2}) */
    private static final int GRENZE = 10 * TUER_SCHRITT + 2;
    /** Hoehe 69 bis 72 liegt im Abschnitt mit dem Index 4 (Hoehe 64 bis 79) */
    private static final int ABSCHNITT_Y = 4;

    private static final Map<Block, BlockState> LEER = Map.of();
    /** Welcher Block als welches Glas gezeichnet wird; wird atomar getauscht, weil die Zeichenfaeden der Chunks mitlesen */
    private static volatile Map<Block, BlockState> ersatz = LEER;

    private static int ticks;
    private static int unterschrift = -1;
    private static Object letzteWelt;
    private static boolean warImDungeon;

    private IHateDoors() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(IHateDoors::tick);
    }

    // ------------------------------------------------------------------ der Hook der Mixins (von den Zeichenfaeden gerufen)

    /**
     * Der Block, den der Zeichner an dieser Stelle sehen soll. Gerufen fuer jeden Block, den der Zeichner liest: darum zuerst die billigen
     * Fragen (ist etwas eingestellt, liegt die Hoehe richtig), dann die Zuordnung des Blocks, zuletzt der Ort.
     */
    public static BlockState renderState(int x, int y, int z, BlockState original) {
        Map<Block, BlockState> karte = ersatz;
        if (karte == LEER) return original;
        if (y < UNTEN || y > OBEN) return original;
        BlockState glas = karte.get(original.getBlock());
        if (glas == null) return original;
        if (!DungeonLauf.imDungeon() || DungeonLauf.imBoss()) return original;
        return tuerPlatz(x, y, z) ? glas : original;
    }

    /**
     * Liegt diese Stelle auf einem Tuer-Platz des Rasters? Die Rechnung von Noamm: von der Ecke des ersten Raums (eins davor) an in Schritten von
     * 16 Bloecken; ein Platz zaehlt als Tuer, wenn Zeile und Spalte zusammen ungerade sind, und die Tuer ist drei Bloecke breit.
     */
    static boolean tuerPlatz(int x, int y, int z) {
        if (y < UNTEN || y > OBEN) return false;
        int gitterX = x - Raster.START + 1;
        int gitterZ = z - Raster.START + 1;
        if (gitterX < 0 || gitterX > GRENZE) return false;
        if (gitterZ < 0 || gitterZ > GRENZE) return false;
        if (gitterX % TUER_SCHRITT > 2) return false;
        if (gitterZ % TUER_SCHRITT > 2) return false;
        return ((gitterX / TUER_SCHRITT + gitterZ / TUER_SCHRITT) & 1) != 0;
    }

    // ------------------------------------------------------------------ Einstellungen und Neuzeichnen

    private static void tick(Minecraft client) {
        if (++ticks % 10 != 0) return;
        ClientLevel welt = client.level;
        if (welt == null || !FeatureGate.iHateDoors()) {
            // Ausgeschaltet: das Glas muss wieder zur Tuer werden - die schon gebauten Abschnitte neu zeichnen lassen (nur wenn man im Dungeon stand)
            Map<Block, BlockState> vorher = ersatz;
            if (vorher != LEER) {
                ersatz = LEER;
                if (welt != null && warImDungeon) tuerenNeuZeichnen(welt, vorher, LEER);
            }
            unterschrift = -1;
            warImDungeon = false;
            return;
        }
        if (welt != letzteWelt) {
            letzteWelt = welt;
            warImDungeon = false;
        }
        IHateDoorsCategory c = ModConfig.INSTANCE.ikohS.dungeon.iHateDoors;
        int neueUnterschrift = unterschrift(c);
        boolean imDungeon = DungeonLauf.imDungeon();
        if (neueUnterschrift != unterschrift) {
            unterschrift = neueUnterschrift;
            Map<Block, BlockState> vorher = ersatz;
            Map<Block, BlockState> nachher = bauen(c);
            ersatz = nachher;
            // Noamm: eine Aenderung der Einstellung markiert die Tueren nur im Dungeon (ausserhalb gibt es keine)
            if (imDungeon) tuerenNeuZeichnen(welt, vorher, nachher);
        } else if (imDungeon && !warImDungeon && ersatz != LEER) {
            // Beim Betreten des Dungeons waren die ersten Chunk-Abschnitte schon gebaut, bevor klar war, dass man im Dungeon steht
            tuerenNeuZeichnen(welt, LEER, ersatz);
        }
        warImDungeon = imDungeon;
    }

    /** Ein Zahlenwert, der sich aendert, sobald sich etwas an den Einstellungen aendert - so wird die Karte nicht in jedem Takt neu gebaut */
    private static int unterschrift(IHateDoorsCategory c) {
        int u = (c.glassEntrance ? 1 : 0) | (c.glassWither ? 2 : 0) | (c.glassBlood ? 4 : 0);
        u = u * 32 + ordinal(c.entranceGlass);
        u = u * 32 + ordinal(c.witherGlass);
        u = u * 32 + ordinal(c.bloodGlass);
        return u;
    }

    private static int ordinal(GlassColour g) {
        return g == null ? GlassColour.DEFAULT.ordinal() : g.ordinal();
    }

    /** Welcher Block als welches Glas gezeichnet wird, nach den Einstellungen (leer, wenn keine Tuerart gewaehlt ist) */
    private static Map<Block, BlockState> bauen(IHateDoorsCategory c) {
        Map<Block, BlockState> karte = new HashMap<>();
        if (c.glassEntrance) karte.put(block("infested_chiseled_stone_bricks"), glas(c.entranceGlass));
        if (c.glassWither) karte.put(block("coal_block"), glas(c.witherGlass));
        if (c.glassBlood) karte.put(block("red_terracotta"), glas(c.bloodGlass));
        return karte.isEmpty() ? LEER : Map.copyOf(karte);
    }

    /** Ein Block ueber seinen Namen: 26.1 hat je Farbe ein Feld in {@code Blocks}, 26.2 eine Farbsammlung - nur das Register kennt in allen Fassungen denselben Weg */
    private static Block block(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + name));
    }

    private static BlockState glas(GlassColour farbe) {
        return block((farbe == null ? GlassColour.DEFAULT : farbe).blockId()).defaultBlockState();
    }

    /**
     * Die Abschnitte der Tueren neu zeichnen lassen, deren Blocks sich in der Darstellung unterscheiden (vorher gegen nachher) - wie
     * {@code invalidateDoors} bei Noamm. Eine Tuer ist drei mal drei Bloecke breit und vier hoch (Hoehe 69 bis 72).
     */
    private static void tuerenNeuZeichnen(ClientLevel welt, Map<Block, BlockState> vorher, Map<Block, BlockState> nachher) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int zeile = 0; zeile <= 10; zeile++) {
            for (int spalte = 0; spalte <= 10; spalte++) {
                if (((zeile + spalte) & 1) == 0) continue;
                int x = Raster.START + spalte * TUER_SCHRITT;
                int z = Raster.START + zeile * TUER_SCHRITT;
                if (welt.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                suchen:
                for (int px = x - 1; px <= x + 1; px++) {
                    for (int py = UNTEN; py <= OBEN; py++) {
                        for (int pz = z - 1; pz <= z + 1; pz++) {
                            Block b = welt.getBlockState(pos.set(px, py, pz)).getBlock();
                            if (Objects.equals(vorher.get(b), nachher.get(b))) continue;
                            welt.setSectionDirtyWithNeighbors(x >> 4, ABSCHNITT_Y, z >> 4);
                            DungeonLog.zaehlen(DungeonLog.Zaehler.TUEREN_NEU_GEZEICHNET);
                            break suchen;
                        }
                    }
                }
            }
        }
    }
}
