package com.shokiteufel.shokimod.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.shokiteufel.shokimod.chat.TabCompleteCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Fuer "Chat > Tab Complete": Vor jeder Vorschlagsliste von Brigadier fragt die Mod, ob sie dazu etwas hat.
 *
 * Der Weg ist der von SkyHanni ({@code MixinCommandDispatcher}): Die Chat-Eingabe ruft {@code getCompletionSuggestions(parse, cursor)}; hat
 * {@link TabCompleteCommands#complete} fuer die Eingabe Vorschlaege, ersetzen sie die des Servers, sonst laeuft alles wie sonst. Anders als bei SkyHanni gibt es
 * keine Ereignisse und keine Zwischenlisten - die Mod rechnet die Vorschlaege selbst und braucht dazu weder MixinExtras noch Parameter aus dem Inneren der Methode
 * (nur {@code parse} und {@code cursor}, die beiden Parameter).
 *
 * <p>Diese Datei steht in {@code shokimod-tab.mixins.json}, einer Konfiguration, die nicht zwingend ist: Gibt es die Methode in einer Fassung nicht so, passiert
 * nichts ({@code require = 0}), und nur diese Funktion fehlt - der Spielstart bleibt heil. {@code remap = false}, weil Brigadier kein Minecraft-Code ist und keine
 * Namen umgeschrieben werden.
 */
@Mixin(value = CommandDispatcher.class, remap = false)
public abstract class CommandDispatcherMixin<S> {

    @Inject(method = "getCompletionSuggestions(Lcom/mojang/brigadier/ParseResults;I)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$tabComplete(ParseResults<S> parse, int cursor, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        Suggestions own = TabCompleteCommands.complete(parse.getReader().getString(), cursor);
        if (own != null) cir.setReturnValue(CompletableFuture.completedFuture(own));
    }
}
