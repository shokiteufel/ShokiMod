package com.shokiteufel.shokimod.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Chat (Teil 4) > Translator > Translate On Click: macht eine Chatzeile anklickbar. Der Klick fuehrt {@code /shoki translateline <Nummer>} aus, der Text beim Zeigen
 * sagt "Click to translate!".
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code TranslatorCommand.replaceChatComponent}): Dort bekommt die Zeile eines Spielers den Klick und den Text "Click to
 * translate!". Die Umsetzung ist eigene Arbeit: Java; der Befehl nennt eine Nummer ({@link TranslateLines}) statt des Textes, und eine Zeile, die schon einen eigenen
 * Klick traegt (eine andere Funktion, ein Link), bleibt, wie sie ist - hier wird nichts ueberschrieben.
 */
final class TranslateOnClick {

    /** Der Befehl des Klicks, ohne Nummer */
    static final String COMMAND = "/shoki translateline ";

    private TranslateOnClick() {
    }

    /** Der Befehl, der die Zeile mit dieser Nummer uebersetzt */
    static String commandFor(int id) {
        return COMMAND + id;
    }

    /**
     * Die Zeile mit dem Klick auf jedem Stueck. Ein Stueck, das schon einen Text beim Zeigen hat, behaelt ihn.
     *
     * @return die neue Zeile, oder null, wenn die Zeile schon einen Klick traegt
     */
    static StyledText wrap(StyledText line, int id) {
        if (line.isEmpty() || line.firstClick() != null) return null;
        ClickEvent click = new ClickEvent.RunCommand(commandFor(id));
        HoverEvent hover = new HoverEvent.ShowText(Component.literal("Click to translate!").withStyle(ChatFormatting.AQUA));
        return line.restyled(style -> {
            net.minecraft.network.chat.Style s = style.withClickEvent(click);
            return style.getHoverEvent() == null ? s.withHoverEvent(hover) : s;
        });
    }
}
