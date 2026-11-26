package com.shokiteufel.shokimod.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Eine Chatzeile als Folge von Stuecken mit je einem Stil (Farbe, Format, Klick, Text beim Zeigen) - der Text ohne Farbcodes steht daneben ({@link #plain()}),
 * damit die Muster gegen den Klartext laufen und trotzdem sagen koennen, welche Farbe an einer Stelle stand.
 *
 * Die Teile sind die der Komponente (jede Aenderung des Stils beginnt ein neues Stueck, zusammengelegt wird nichts): So kann eine Regel fragen, ob
 * eine Zahl am ANFANG eines goldenen Teils steht. Was Hypixel als Zeichen {@code §} in den Text schreibt, wird beim Einlesen zu einem Stil. Beim Umschreiben
 * (ersetzen, einfuegen, anhaengen) behaelt jedes Stueck seinen Stil samt Klick und Text beim Zeigen; {@link #toComponent()} baut daraus die neue Zeile.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatManager}, {@code SkyHanniChatEvent.Modify}, {@code replaceComponent}): Dort wird eine Zeile als Komponente
 * veraendert und ersetzt. Die Umsetzung ist eigene Arbeit: Java, ein reiner Wert ohne Spielzustand (prueft sich ohne Minecraft-Welt), und der Text mit
 * Farbcodes entsteht erst, wenn eine Regel ihn braucht - dann mit {@link #legacy(int, int)} fuer genau den Abschnitt, den ihr Muster getroffen hat.
 */
final class StyledText {

    /** Ein Stueck Text mit seinem Stil */
    record Run(String text, Style style) {
    }

    /** Der Code (ohne das Zeichen davor) zu jedem Format: {@code 'c'} zu ROT, {@code 'l'} zu FETT ... */
    private static final Map<Character, ChatFormatting> BY_CODE = new HashMap<>();

    static {
        for (ChatFormatting f : ChatFormatting.values()) {
            String s = f.toString();
            if (s.length() == 2 && s.charAt(0) == '§') BY_CODE.put(s.charAt(1), f);
        }
    }

    private final List<Run> runs;
    private final String plain;

    private StyledText(List<Run> runs) {
        this.runs = runs;
        StringBuilder sb = new StringBuilder();
        for (Run r : runs) sb.append(r.text());
        this.plain = sb.toString();
    }

    // ---- Einlesen ----

    /** Aus einer Komponente; ein {@code §}-Code im Text eines Teils wird zu einem Stil */
    static StyledText of(Component component) {
        List<Run> out = new ArrayList<>();
        component.visit((Style style, String part) -> {
            if (!part.isEmpty()) parseInto(out, part, style);
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledText(out);
    }

    /** Aus einem Text mit Farbcodes ({@code §a...}) */
    static StyledText ofLegacy(String legacy) {
        List<Run> out = new ArrayList<>();
        if (legacy != null && !legacy.isEmpty()) parseInto(out, legacy, Style.EMPTY);
        return new StyledText(out);
    }

    /** Aus Klartext, ohne Stil */
    static StyledText ofPlain(String plain) {
        List<Run> out = new ArrayList<>();
        if (plain != null && !plain.isEmpty()) out.add(new Run(plain, Style.EMPTY));
        return new StyledText(out);
    }

    private static void parseInto(List<Run> out, String text, Style base) {
        if (text.indexOf('§') < 0) {
            out.add(new Run(text, base));
            return;
        }
        Style style = base;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                ChatFormatting f = BY_CODE.get(Character.toLowerCase(text.charAt(i + 1)));
                if (f != null) {
                    if (buf.length() > 0) {
                        out.add(new Run(buf.toString(), style));
                        buf.setLength(0);
                    }
                    style = apply(style, f, base);
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        if (buf.length() > 0) out.add(new Run(buf.toString(), style));
    }

    /** Wie das Spiel einen Farbcode liest: ein Zuruecksetzen loescht alles, eine Farbe die Formate davor, ein Format kommt dazu */
    private static Style apply(Style style, ChatFormatting f, Style base) {
        if (f == ChatFormatting.RESET) return reset(base);
        TextColor color = TextColor.fromLegacyFormat(f);
        if (color != null) return clearFormats(style).withColor(color);
        switch (f) {
            case OBFUSCATED:
                return style.withObfuscated(true);
            case BOLD:
                return style.withBold(true);
            case STRIKETHROUGH:
                return style.withStrikethrough(true);
            case UNDERLINE:
                return style.withUnderlined(true);
            case ITALIC:
                return style.withItalic(true);
            default:
                return style;
        }
    }

    private static Style clearFormats(Style style) {
        return style.withBold((Boolean) null).withItalic((Boolean) null).withUnderlined((Boolean) null)
                .withStrikethrough((Boolean) null).withObfuscated((Boolean) null);
    }

    /** Zuruecksetzen: Farbe und Formate weg, Klick und Text beim Zeigen bleiben (sie gehoeren dem Teil, nicht dem Code) */
    private static Style reset(Style base) {
        Style r = Style.EMPTY;
        if (base.getClickEvent() != null) r = r.withClickEvent(base.getClickEvent());
        if (base.getHoverEvent() != null) r = r.withHoverEvent(base.getHoverEvent());
        return r;
    }

    // ---- Lesen ----

    String plain() {
        return plain;
    }

    List<Run> runs() {
        return runs;
    }

    boolean isEmpty() {
        return plain.isEmpty();
    }

    /** Der Stil an dieser Stelle des Klartexts; hinter dem Ende der des letzten Stuecks */
    Style styleAt(int index) {
        int pos = 0;
        for (Run r : runs) {
            int end = pos + r.text().length();
            if (index < end) return r.style();
            pos = end;
        }
        return runs.isEmpty() ? Style.EMPTY : runs.get(runs.size() - 1).style();
    }

    /** Beginnt an dieser Stelle des Klartexts ein Stueck? */
    boolean startsRun(int index) {
        int pos = 0;
        for (Run r : runs) {
            if (pos == index) return true;
            pos += r.text().length();
            if (pos > index) return false;
        }
        return false;
    }

    /** Der Text mit Farbcodes */
    String legacy() {
        return legacy(0, plain.length());
    }

    /**
     * Der Text mit Farbcodes fuer den Abschnitt [from, to) des Klartexts.
     *
     * Ein Code steht nur, wo sich der Stil aendert: eine Farbe loescht die Formate davor von selbst, ohne Farbe geht erst ein Zuruecksetzen voraus. So
     * gleichen sich zwei Zeilen mit demselben Aussehen auch im Text, ob Hypixel die Teile so oder anders geschnitten hat.
     */
    String legacy(int from, int to) {
        StringBuilder sb = new StringBuilder();
        String prev = "";
        int pos = 0;
        for (Run r : runs) {
            int len = r.text().length();
            int a = Math.max(from, pos);
            int b = Math.min(to, pos + len);
            if (a < b) {
                String codes = codes(r.style());
                if (!codes.equals(prev)) {
                    boolean colour = codes.length() >= 2 && isColourCode(codes.charAt(1));
                    if (!colour && !prev.isEmpty()) sb.append("§r");
                    sb.append(codes);
                    prev = codes;
                }
                sb.append(r.text(), a - pos, b - pos);
            }
            pos += len;
        }
        return sb.toString();
    }

    private static boolean isColourCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    /** Die Codes eines Stils: erst die Farbe, dann die Formate (fett, durchgestrichen ...) - ohne Klick und Text beim Zeigen */
    static String codes(Style s) {
        StringBuilder sb = new StringBuilder();
        TextColor color = s.getColor();
        if (color != null) {
            // 26.2: ChatFormatting.getColor()/getChar() sind weg; die Zuordnung Farbe -> Code kommt von TextColor.fromLegacyFormat(),
            // und toString() einer ChatFormatting ist "§c"
            for (ChatFormatting f : ChatFormatting.values()) {
                TextColor legacy = TextColor.fromLegacyFormat(f);
                if (legacy != null && legacy.getValue() == color.getValue()) {
                    sb.append(f);
                    break;
                }
            }
        }
        if (s.isObfuscated()) sb.append("§k");
        if (s.isBold()) sb.append("§l");
        if (s.isStrikethrough()) sb.append("§m");
        if (s.isUnderlined()) sb.append("§n");
        if (s.isItalic()) sb.append("§o");
        return sb.toString();
    }

    /** Der Buchstabe des Farbcodes dieser Farbe ({@code '6'} fuer Gold), oder 0, wenn es keine der sechzehn ist */
    static char colourCharOf(Style s) {
        TextColor color = s.getColor();
        if (color == null) return 0;
        for (ChatFormatting f : ChatFormatting.values()) {
            TextColor legacy = TextColor.fromLegacyFormat(f);
            if (legacy != null && legacy.getValue() == color.getValue()) return f.toString().charAt(1);
        }
        return 0;
    }

    /** Das erste Klick-Ereignis irgendeines Stuecks, oder null */
    ClickEvent firstClick() {
        for (Run r : runs) {
            if (r.style().getClickEvent() != null) return r.style().getClickEvent();
        }
        return null;
    }

    /** Das erste Hover-Ereignis irgendeines Stuecks, oder null */
    HoverEvent firstHover() {
        for (Run r : runs) {
            if (r.style().getHoverEvent() != null) return r.style().getHoverEvent();
        }
        return null;
    }

    // ---- Umschreiben (liefert immer eine neue Zeile) ----

    /** Die Stuecke des Abschnitts [from, to), an den Raendern zugeschnitten */
    private List<Run> cut(int from, int to) {
        List<Run> out = new ArrayList<>();
        int pos = 0;
        for (Run r : runs) {
            int len = r.text().length();
            int a = Math.max(from, pos);
            int b = Math.min(to, pos + len);
            if (a < b) out.add(new Run(r.text().substring(a - pos, b - pos), r.style()));
            pos += len;
        }
        return out;
    }

    /** Den Abschnitt [from, to) durch diese Stuecke ersetzen */
    StyledText replace(int from, int to, List<Run> insert) {
        List<Run> out = cut(0, from);
        for (Run r : insert) {
            if (!r.text().isEmpty()) out.add(r);
        }
        out.addAll(cut(to, plain.length()));
        return new StyledText(out);
    }

    /** Den Abschnitt [from, to) durch Text ersetzen; er bekommt den Stil, der an {@code from} galt */
    StyledText replace(int from, int to, String text) {
        return replace(from, to, List.of(new Run(text, styleAt(from))));
    }

    /** Den Abschnitt [from, to) durch eine andere Zeile ersetzen (mit deren Stilen) */
    StyledText replace(int from, int to, StyledText with) {
        return replace(from, to, with.runs);
    }

    /** Text mit eigenem Stil an dieser Stelle einfuegen */
    StyledText insert(int at, String text, Style style) {
        return replace(at, at, List.of(new Run(text, style)));
    }

    /** Eine andere Zeile hinten anhaengen */
    StyledText append(StyledText other) {
        return replace(plain.length(), plain.length(), other.runs);
    }

    /** Derselbe Text, aber auf jedem Stueck dieses Klick- und Hover-Ereignis (null laesst das vorhandene stehen) */
    StyledText withEvents(ClickEvent click, HoverEvent hover) {
        List<Run> out = new ArrayList<>(runs.size());
        for (Run r : runs) {
            Style s = r.style();
            if (click != null) s = s.withClickEvent(click);
            if (hover != null) s = s.withHoverEvent(hover);
            out.add(new Run(r.text(), s));
        }
        return new StyledText(out);
    }

    /** Der Abschnitt [from, to) als eigene Zeile (Stile, Klick und Text beim Zeigen bleiben, wie sie waren) */
    StyledText slice(int from, int to) {
        return new StyledText(cut(from, to));
    }

    /** Aus fertigen Stuecken (ein Stueck ohne Text faellt weg) */
    static StyledText ofRuns(List<Run> runs) {
        List<Run> out = new ArrayList<>();
        for (Run r : runs) {
            if (!r.text().isEmpty()) out.add(r);
        }
        return new StyledText(out);
    }

    /** Derselbe Text, jedes Stueck mit dem Stil, den die Funktion daraus macht (zum Beispiel eine andere Farbe) */
    StyledText restyled(java.util.function.UnaryOperator<Style> change) {
        List<Run> out = new ArrayList<>(runs.size());
        for (Run r : runs) out.add(new Run(r.text(), change.apply(r.style())));
        return new StyledText(out);
    }

    // ---- Bauen ----

    /** Die Zeile als Komponente: ein leerer Kopf, darunter ein Teil je Stueck mit seinem ganzen Stil */
    MutableComponent toComponent() {
        MutableComponent root = Component.literal("");
        for (Run r : runs) {
            Style st = r.style();
            root.append(Component.literal(r.text()).withStyle(s -> st));
        }
        return root;
    }

    /** Eine Komponente (Text mit Farbcodes) mit Klick und Hover auf dem Kopf: alle Teile erben es */
    static MutableComponent withEvents(MutableComponent root, ClickEvent click, HoverEvent hover) {
        return root.withStyle(s -> {
            Style x = s;
            if (click != null) x = x.withClickEvent(click);
            if (hover != null) x = x.withHoverEvent(hover);
            return x;
        });
    }

    /** Der Text beim Zeigen: Zeilen mit Farbcodes, mit {@code \n} getrennt */
    static HoverEvent hoverOf(String legacy) {
        return new HoverEvent.ShowText(ofLegacy(legacy).toComponent());
    }
}
