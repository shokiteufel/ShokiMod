package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.MistFloor;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.List;
import java.util.Locale;

/**
 * Der Boden der Mist (Dwarven Mines): Hypixel legt dort weisses Glas (Bloecke "white_stained_glass" in zwei Lagen, dazwischen Barrieren;
 * Mitschnitt Schiggy 09.11.). Auf Wunsch zeigt der Client es grau oder schwarz - die weissen Ghosts sind sonst schwer zu sehen.
 *
 * Gemacht wird das im Client: In den geladenen Chunks um den Spieler wird das weisse Glas durch graues oder schwarzes ersetzt; der Server
 * erfaehrt nichts davon, und die Kollision ist dieselbe (Glas bleibt Glas). Schickt der Server den Block neu, ersetzt der naechste Durchgang
 * ihn wieder. Es zaehlt nur in der Mist (Seitenleiste "The Mist"); jeder ersetzte Platz wird gemerkt, damit "White" alles zurueckgibt.
 */
public final class MistBoden {

    /** Ein Durchgang je Sekunde: das Glas liegt fest, und ein neu geschickter Block darf ruhig eine Sekunde weiss bleiben */
    private static final int TAKT = 20;
    /** Wie weit um den Spieler gesucht wird, in Chunks - Sichtweite des Servers, mehr wird nicht geschickt */
    private static final int RADIUS = 8;

    /** Chunk (ChunkPos.asLong) -> Plaetze (BlockPos.asLong), an denen weisses Glas ersetzt wurde */
    private static final Long2ObjectOpenHashMap<LongOpenHashSet> ersetzt = new Long2ObjectOpenHashMap<>();
    private static Object letzteWelt;
    private static int ticks;
    private static int geloggt = -1;

    private MistBoden() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MistBoden::tick);
    }

    private static void tick(Minecraft client) {
        if (++ticks % TAKT != 0) return;
        ClientLevel welt = client.level;
        if (welt == null || client.player == null) {
            ersetzt.clear();
            letzteWelt = null;
            return;
        }
        // Eine neue Welt hat neue Chunks: das Gemerkte gilt nicht mehr
        if (welt != letzteWelt) {
            letzteWelt = welt;
            ersetzt.clear();
            geloggt = -1;
        }
        MistFloor soll = ModConfig.INSTANCE.ikohS.mist.floor;
        if (soll == null) soll = MistFloor.WHITE;

        if (soll == MistFloor.WHITE) {
            // Aus: alles zurueck, was ersetzt wurde - auch ausserhalb der Mist, denn die Chunks bleiben geladen
            if (!ersetzt.isEmpty()) zurueck(client, welt);
            return;
        }
        if (!GameState.Server.isSkyblock() || !inMist(client)) return;
        durchgang(client, welt, glas(soll == MistFloor.GRAY ? "gray" : "black").defaultBlockState());
    }

    /**
     * Ein farbiges Glas ueber seinen Namen. 26.1 hat je Farbe ein Feld in {@code Blocks}, 26.2 eine Farbsammlung: nur das Register kennt in
     * beiden Fassungen denselben Weg.
     */
    private static Block glas(String farbe) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + farbe + "_stained_glass"));
    }

    private static Block weiss;
    private static Block grau;
    private static Block schwarz;

    private static void bloeckeHolen() {
        if (weiss != null) return;
        weiss = glas("white");
        grau = glas("gray");
        schwarz = glas("black");
    }

    /** Steht die Seitenleiste auf "The Mist"? */
    static boolean inMist(Minecraft client) {
        List<String> seite = ScoreboardUtils.getSidebarLines(client);
        for (String zeile : seite) {
            if (ScoreboardUtils.stripColor(zeile).toLowerCase(Locale.ROOT).contains("the mist")) return true;
        }
        return false;
    }

    private static boolean istWeiss(BlockState s) {
        bloeckeHolen();
        return s.is(weiss);
    }

    private static boolean istUnsers(BlockState s) {
        bloeckeHolen();
        return s.is(grau) || s.is(schwarz);
    }

    /** Weisses Glas in den Chunks um den Spieler durch {@code ziel} ersetzen; schon Ersetztes auf eine andere Farbe stellen, wenn die gewaehlt wurde */
    private static void durchgang(Minecraft client, ClientLevel welt, BlockState ziel) {
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int neu = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                LevelChunk chunk = welt.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                long schluessel = ChunkPos.pack(cx, cz);
                LongOpenHashSet gemerkt = ersetzt.get(schluessel);
                boolean[] schmutzig = new boolean[chunk.getSections().length];

                // Schon ersetzte Plaetze: auf die jetzt gewaehlte Farbe bringen (oder, vom Server neu geschickt, wieder ersetzen)
                if (gemerkt != null) {
                    for (long p : gemerkt) {
                        pos.set(p);
                        BlockState s = chunk.getBlockState(pos);
                        if (s == ziel || !(istWeiss(s) || istUnsers(s))) continue;
                        if (setzen(chunk, pos, ziel)) schmutzig[chunk.getSectionIndex(pos.getY())] = true;
                    }
                }

                // Neues weisses Glas suchen: nur Abschnitte, deren Palette es ueberhaupt kennt
                LevelChunkSection[] abschnitte = chunk.getSections();
                for (int i = 0; i < abschnitte.length; i++) {
                    LevelChunkSection a = abschnitte[i];
                    if (a == null || a.hasOnlyAir() || !a.maybeHas(MistBoden::istWeiss)) continue;
                    int untenY = welt.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!istWeiss(a.getBlockState(x, y, z))) continue;
                                a.setBlockState(x, y, z, ziel, false);
                                if (gemerkt == null) {
                                    gemerkt = new LongOpenHashSet();
                                    ersetzt.put(schluessel, gemerkt);
                                }
                                gemerkt.add(BlockPos.asLong((cx << 4) + x, untenY + y, (cz << 4) + z));
                                schmutzig[i] = true;
                                neu++;
                            }
                        }
                    }
                }
                neuZeichnen(client, welt, cx, cz, schmutzig);
            }
        }
        if (neu > 0) {
            int summe = 0;
            for (LongOpenHashSet s : ersetzt.values()) summe += s.size();
            if (summe != geloggt) {
                geloggt = summe;
                ShokiMod.LOGGER.info("[Mist] floor: {} glass blocks shown as {} now ({} in all)", neu,
                        ziel.is(grau) ? "gray" : "black", summe);
            }
        }
    }

    /** Alles Ersetzte wieder weiss machen */
    private static void zurueck(Minecraft client, ClientLevel welt) {
        bloeckeHolen();
        BlockState weiss = MistBoden.weiss.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int zurueck = 0;
        for (Long2ObjectOpenHashMap.Entry<LongOpenHashSet> e : ersetzt.long2ObjectEntrySet()) {
            int cx = ChunkPos.getX(e.getLongKey());
            int cz = ChunkPos.getZ(e.getLongKey());
            LevelChunk chunk = welt.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            boolean[] schmutzig = new boolean[chunk.getSections().length];
            for (long p : e.getValue()) {
                pos.set(p);
                if (!istUnsers(chunk.getBlockState(pos))) continue;
                if (setzen(chunk, pos, weiss)) {
                    schmutzig[chunk.getSectionIndex(pos.getY())] = true;
                    zurueck++;
                }
            }
            neuZeichnen(client, welt, cx, cz, schmutzig);
        }
        ersetzt.clear();
        geloggt = -1;
        if (zurueck > 0) ShokiMod.LOGGER.info("[Mist] floor: {} glass blocks white again", zurueck);
    }

    private static boolean setzen(LevelChunk chunk, BlockPos pos, BlockState zustand) {
        int index = chunk.getSectionIndex(pos.getY());
        if (index < 0 || index >= chunk.getSections().length) return false;
        LevelChunkSection a = chunk.getSections()[index];
        if (a == null) return false;
        a.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, zustand, false);
        return true;
    }

    /** Die Abschnitte, in denen sich etwas aenderte, vom Zeichner neu bauen lassen */
    private static void neuZeichnen(Minecraft client, ClientLevel welt, int cx, int cz, boolean[] schmutzig) {
        for (int i = 0; i < schmutzig.length; i++) {
            if (schmutzig[i]) welt.setSectionDirtyWithNeighbors(cx, welt.getSectionYFromSectionIndex(i), cz);
        }
    }
}
