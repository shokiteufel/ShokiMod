package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.CommandAlias;
import com.shokiteufel.shokimod.data.ModConfig;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

/**
 * Aliase beim Abschicken: Wer {@code /g} schickt, bekommt {@code /warp garden}, wenn der zweite Reiter von {@code /shoki shortcut} das so sagt.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code Shortcuts}, dort hakt sich dieselbe Stelle in {@code ClientSendMessageEvents.MODIFY_COMMAND} ein). Die Regel
 * steht rein in {@link CommandAlias#rewrite}; hier ist nur der Haken ins Senden. Ohne Eintrag in der Liste kostet ein Befehl einen Vergleich.
 *
 * <p>Grenzen: Das Ereignis laeuft, nachdem andere Mods den Befehl schon gesehen haben koennen ({@code ALLOW_COMMAND}, zum Beispiel {@code WarpCommands}); ein Alias, dessen
 * Ziel erst von so einer Stelle umgeschrieben wuerde, kommt dort nicht mehr an. Ein Ziel, das ein Client-Befehl einer Mod ist (etwa {@code /shoki ...}), geht an den
 * Server statt in die Mod, weil es nach dem Auswerten der Client-Befehle ersetzt wird.
 */
public final class CommandAliasHandler {

    private CommandAliasHandler() {
    }

    public static void register() {
        ClientSendMessageEvents.MODIFY_COMMAND.register(command -> {
            String sent = rewrite(command);
            if (sent != command && !sent.equals(command)) ShokiMod.LOGGER.info("[Alias] /{} -> /{}", command, sent);
            return sent;
        });
    }

    /** Der Befehl, der wirklich geschickt wird (ohne Schraegstrich) - mit den Eintraegen der Einstellungen */
    static String rewrite(String command) {
        ModConfig config = ModConfig.INSTANCE;
        if (config == null || config.aliases == null || config.aliases.isEmpty()) return command;
        return CommandAlias.rewrite(config.aliases, command);
    }
}
