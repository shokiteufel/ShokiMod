package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.handler.ChatPeek;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat > Peek Chat, zweiter Teil: Solange die Taste gehalten wird, rechnet der Chat mit der Hoehe des offenen Chats statt mit der des geschlossenen.
 *
 * {@code ChatComponent.getHeight()} (privat, {@code ()I}) liefert die Hoehe nach {@code isChatFocused()}: offen die eingestellte Hoehe des offenen Chats, sonst die
 * des geschlossenen (weniger Zeilen). Ohne diesen Einsprung zeigte der Vordergrund-Modus nur so viele Zeilen wie der geschlossene Chat. Es gibt daneben eine
 * statische {@code getHeight(double)}; das Ziel nennt deshalb die ganze Beschreibung. Per javap in den Jars von 26.3 nachgesehen.
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}, {@code require = 0}): Passt die Stelle nicht, fehlt nur die Hoehe, und Peek Chat zeigt weniger Zeilen.
 */
@Mixin(ChatComponent.class)
public abstract class ChatPeekHeightMixin {

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$peekHeight(CallbackInfoReturnable<Integer> cir) {
        if (ChatPeek.peek()) cir.setReturnValue(ChatPeek.focusedHeight());
    }
}
