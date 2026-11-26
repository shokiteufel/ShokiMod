package com.shokiteufel.shokimod.chat;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Was mit einer Chatzeile geschieht: stehen lassen, ausblenden oder durch eine oder mehrere eigene Zeilen ersetzen.
 *
 * Reine Daten, ohne Minecraft: Die Filter liefern das hier, und erst {@link ChatFilters} baut daraus die Zeilen fuer den Chat. So lassen sich die
 * Filter ohne Spiel pruefen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code SkyHanniChatEvent}: {@code blockedReason} und {@code replaceComponent}). Von dort stammt die Regel, dass
 * ein Filter eine Zeile entweder ausblendet oder durch eine andere ersetzt. Die Umsetzung ist eigene Arbeit: Java, ein reiner Wert statt eines
 * Ereignisses, das man veraendert.
 */
public record ChatVerdict(Kind kind, List<Line> lines) {

    public enum Kind {
        /** Die Zeile bleibt */
        SHOW,
        /** Die Zeile wird nicht angezeigt */
        HIDE,
        /** Die Zeile wird nicht angezeigt, an ihrer Stelle stehen die Zeilen des Urteils */
        REPLACE
    }

    /**
     * Eine eigene Zeile. {@code text} darf Farbcodes ({@code §}) enthalten; {@code command} ist ein Befehl mit Schraegstrich, der beim Klick laeuft
     * (oder null), {@code hover} der Text beim Zeigen (oder null).
     *
     * Chat (Teil 2): Eine Zeile kann auch fertig als {@code component} kommen (mit allen Stilen, Klick und Text beim Zeigen, etwa die umgeschriebene
     * Zeile des Servers); dann sind {@code text}, {@code command} und {@code hover} null und die Komponente wird so geschrieben, wie sie ist.
     */
    public record Line(String text, String command, String hover, Component component) {

        public Line(String text, String command, String hover) {
            this(text, command, hover, null);
        }

        public static Line of(String text) {
            return new Line(text, null, null, null);
        }

        /** Eine fertige Komponente */
        public static Line ofComponent(Component component) {
            return new Line(null, null, null, component);
        }

        /** Der Text mit Farbcodes - auch bei einer fertigen Komponente (fuer die Tests und das Protokoll) */
        public String legacyText() {
            return component == null ? text : StyledText.of(component).legacy();
        }
    }

    public static final ChatVerdict SHOW = new ChatVerdict(Kind.SHOW, List.of());
    public static final ChatVerdict HIDE = new ChatVerdict(Kind.HIDE, List.of());

    public static ChatVerdict replace(Line... lines) {
        return new ChatVerdict(Kind.REPLACE, List.of(lines));
    }

    public static ChatVerdict replace(String text) {
        return replace(Line.of(text));
    }

    /** Wird die Originalzeile nicht angezeigt? */
    public boolean removesOriginal() {
        return kind != Kind.SHOW;
    }
}
