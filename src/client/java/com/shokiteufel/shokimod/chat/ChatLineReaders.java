package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.FeatureGate;

import net.minecraft.network.chat.Component;

/**
 * Die Leser des Chats aus Teil 3, an einer Stelle: Sound Responses, Current Chat Display und die Listen fuer Tab Complete. Der Chat-Weg ({@code NetworkHandler})
 * ruft sie fuer jede Chatzeile, vor dem Ausblenden - jeder sieht die Zeile, auch wenn ein Filter sie gleich verwirft. Jeder Leser hat seinen Schalter; ist keiner
 * an, kostet eine Zeile drei Feldzugriffe.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1): dort hoeren {@code ChatSoundResponse}, {@code CurrentChatDisplay} und die {@code *Api}-Klassen auf dasselbe Ereignis.
 */
public final class ChatLineReaders {

    private ChatLineReaders() {
    }

    /**
     * Eine Chatzeile der Welt (keine Aktionsleiste).
     *
     * @param message die Zeile als Komponente (mit Klick und Hover)
     * @param plain   dieselbe Zeile ohne Farbcodes
     */
    public static void onChatMessage(Component message, String plain) {
        if (FeatureGate.currentChatDisplay()) CurrentChatDisplay.onChatMessage(plain);
        if (FeatureGate.chatSoundResponses()) ChatSoundResponse.onChatMessage(plain);
        if (FeatureGate.chatRosters()) TabCompleteRosters.onChatMessage(message, plain);
    }
}
