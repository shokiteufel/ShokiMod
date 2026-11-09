package com.shokiteufel.shokimod.dungeonkarte;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Die geladene Welt des Spielers als {@link WeltScanner.Welt}. Nur lesen: der Scan fragt Chunks und Bloecke ab, die der Server schon
 * geschickt hat, und fordert nichts an.
 *
 * Die Registernamen der Bloecke werden je Blockart nur einmal ausgerechnet; ein Durchgang fragt je Raumplatz 129 Bloecke.
 */
final class ClientWelt implements WeltScanner.Welt {

    /** Der Hash-Beitrag je Block, ueber alle Durchgaenge gemerkt - Blockarten gibt es nur wenige hundert */
    private static final Map<Block, Integer> TOKEN = new IdentityHashMap<>();
    private static final Map<Block, String> NAME = new IdentityHashMap<>();

    private final ClientLevel welt;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    ClientWelt(ClientLevel welt) {
        this.welt = welt;
    }

    private static String name(Block block) {
        return NAME.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
    }

    @Override
    public boolean chunkGeladen(int x, int z) {
        return welt.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    @Override
    public int hoechsterBlock(int x, int z) {
        for (int y = 256; y >= 0; y--) {
            BlockState s = welt.getBlockState(pos.set(x, y, z));
            if (s.isAir() || "minecraft:gold_block".equals(name(s.getBlock()))) continue;
            return y;
        }
        return 0;
    }

    @Override
    public int kernHash(int x, int z) {
        return KernHash.hashVonTokens(y -> {
            Block block = welt.getBlockState(pos.set(x, y, z)).getBlock();
            Integer t = TOKEN.get(block);
            if (t == null) {
                t = KernHash.token(name(block));
                TOKEN.put(block, t);
            }
            return t;
        });
    }

    @Override
    public String blockName(int x, int y, int z) {
        return name(welt.getBlockState(pos.set(x, y, z)).getBlock());
    }
}
