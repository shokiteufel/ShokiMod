package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.render.ShinyAlert;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.session.SessionManager;
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
            NestTracker.reset();
            SessionManager.onWorldChange();
            RareLootHandler.reset();
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            // Die Action Bar ist keine Chatzeile, dort greifen keine Regeln
            if (overlay) return true;

            String msg = message.getString();
            String unformattedMsg = msg.replaceAll("§[0-9a-fk-or]", "");

            // Die Lauf-Mitschrift liest nur mit und aendert an der Zeile nichts
            SessionManager.onChatMessage(msg);
            // Seltene Funde ebenso: bewerten und melden, die Zeile bleibt
            RareLootHandler.onChatMessage(unformattedMsg);
            // Der Shiny-Ruf eines anderen: nur hoeren, nichts aendern
            ShinyAlert.onChatMessage(unformattedMsg);
            // Der Hunting Tracker zaehlt dieselben Shard-Zeilen mit
            HuntingTracker.onChatMessage(unformattedMsg);

            // Ein "false" blendet die Originalzeile aus
            return ChatRuleHandler.handleMessage(message, msg, unformattedMsg);
        });
    }
}
