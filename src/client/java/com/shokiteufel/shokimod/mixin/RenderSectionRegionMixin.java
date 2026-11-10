package com.shokiteufel.shokimod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.shokiteufel.shokimod.handler.IHateDoors;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * "I hate doors" (siehe {@link IHateDoors}): Der Zeichner eines Chunk-Abschnitts liest die Bloecke durch diese Region. Die Tuer-Bloecke werden
 * hier gegen Glas getauscht - nur fuer das Netz, das gezeichnet wird; die Welt bleibt, wie sie ist. Wie bei NoammAddons ({@code MixinRenderSectionRegion}),
 * mit {@code ModifyReturnValue}: Das wird fuer jeden gelesenen Block gerufen, ein Rueckgabe-Objekt je Aufruf (wie bei einem Rueckruf mit Abbruch) waere zu teuer.
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {

    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState shokimod$tuerGlas(BlockState original, BlockPos pos) {
        return IHateDoors.renderState(pos.getX(), pos.getY(), pos.getZ(), original);
    }
}
