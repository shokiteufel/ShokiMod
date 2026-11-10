package com.shokiteufel.shokimod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.shokiteufel.shokimod.handler.IHateDoors;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * "I hate doors" mit Sodium: Sodium baut die Chunk-Netze selbst und liest die Bloecke durch seinen {@code LevelSlice}, nicht durch die Region des Spiels.
 * Wie bei NoammAddons ({@code MixinSodiumLevelSlice}). Ohne Sodium gibt es die Klasse nicht: {@code @Pseudo} laesst den Mixin dann ruhen, und
 * {@code require = 0} sorgt dafuer, dass auch eine andere Fassung von Sodium (andere Methode) nichts zum Absturz bringt.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {

    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), require = 0)
    private BlockState shokimod$tuerGlas(BlockState original, int x, int y, int z) {
        return IHateDoors.renderState(x, y, z, original);
    }
}
