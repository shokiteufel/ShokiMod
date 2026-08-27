package com.shokiteufel.shokimod.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void shokimod$setXpos(double xpos);

    @Accessor("ypos")
    void shokimod$setYpos(double ypos);
}
