package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.handler.ChatPeek;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Chat > Peek Chat, erster Teil: Der Chat im Spielbild wird im Vordergrund-Modus gezeichnet, solange die Taste gehalten wird.
 *
 * {@code Hud.extractChat(GuiGraphicsExtractor, DeltaTracker)} (privat, ab 26.2 in {@code Hud}) zeichnet den Chat nur, wenn kein Chatfenster offen ist, und ruft dazu
 * {@code ChatComponent.extractRenderState(GuiGraphicsExtractor, Font, int, int, int, DisplayMode, boolean)} mit {@code DisplayMode.BACKGROUND} (nur die Zeilen der
 * letzten Sekunden, verblassend). Hier wird dieses sechste Argument (Stelle 5) durch {@code FOREGROUND} ersetzt: alle Zeilen, ohne Verblassen - so zeichnet der Chat
 * auch im Chatfenster. Ziel und Beschreibung sind per javap in den Jars von 26.3 nachgesehen.
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}, {@code require = 0}): Passt die Stelle in einer anderen Fassung nicht, laeuft die Mod ohne Peek Chat
 * weiter. Die Frage, ob gezeigt wird, ist {@link ChatPeek#peek()} - ohne gewaehlte Taste ein einziger Feldzugriff.
 */
@Mixin(Hud.class)
public abstract class ChatPeekHudMixin {

    @ModifyArg(method = "extractChat(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"),
            index = 5, require = 0)
    private ChatComponent.DisplayMode shokimod$peekMode(ChatComponent.DisplayMode mode) {
        return ChatPeek.peek() ? ChatComponent.DisplayMode.FOREGROUND : mode;
    }
}
