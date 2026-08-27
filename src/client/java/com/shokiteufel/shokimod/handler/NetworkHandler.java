package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.render.ShinyAlert;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Serverwechsel und eingehende Chatnachrichten. */
public class NetworkHandler {

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            GameState.resetAll();
            // Gemeldete Funde gelten nur für die Welt, in der sie gesehen wurden
            ShinyAlert.reset();
            FloorDropHandler.reset();
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            // Die Action Bar ist keine Chatzeile, dort greifen keine Regeln
            if (overlay) return true;

            String msg = message.getString();
            String unformattedMsg = msg.replaceAll("§[0-9a-fk-or]", "");

            // Ein "false" blendet die Originalzeile aus
            return ChatRuleHandler.handleMessage(message, msg, unformattedMsg);
        });
    }
}
