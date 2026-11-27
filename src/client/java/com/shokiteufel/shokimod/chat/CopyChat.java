package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.handler.MouseButtons;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Chat > Copy Chat: Ein Rechtsklick auf eine Chatzeile kopiert sie in die Zwischenablage; mit Strg nur die Zeile unter dem Zeiger statt der ganzen Nachricht.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code CopyChat}). Von dort stammen die Regel (Rechtsklick im Chatfenster; ohne Taste die ganze Nachricht, mit Strg nur
 * eine Zeile; der Text ohne Farbcodes) und der Weg zur Zeile unter dem Zeiger: Das Spiel zaehlt die Textstuecke des Chats auf, und die Zeile ist die, deren
 * Rechteck den Zeiger enthaelt. Die Umsetzung ist eigene Arbeit: Java; der Klick kommt aus dem Ereignis der Fabric-API ({@code ScreenMouseEvents.allowMouseClick}),
 * es gibt kein Mixin; das Rechteck der Zeile wird hier selbst gerechnet (Lage des Textes durch die Matrix des Chats zurueckgerechnet).
 *
 * <p>Abweichungen: SkyHanni kopiert mit Umschalt die Nachricht mit den Wort-Ersetzungen seiner "Visual Words" ("Shwords"); die gibt es hier nicht, Umschalt kopiert
 * wie ohne Taste. SkyHannis Zweig fuer die Menue-Taste (Nachricht mit Formatierung) fehlt auch. Die Meldung "Copied ... to clipboard!" kommt mit dem Namen der Mod.
 */
public final class CopyChat {

    /** Hoehe einer Chatzeile in Pixeln (die Schrift ist neun hoch) */
    static final int LINE_HEIGHT = 9;

    private static Field trimmedMessages;
    private static boolean warned = false;

    private CopyChat() {
    }

    public static void register() {
        // Die Ereignisse eines Fensters werden bei jedem Aufbau neu angelegt (auch nach einer Groessenaenderung): Jedes Mal wieder anmelden
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof ChatScreen) ScreenMouseEvents.allowMouseClick(screen).register(CopyChat::onClick);
        });
    }

    private static boolean onClick(Screen screen, MouseButtonEvent click) {
        // Der Klick geht immer weiter: kopiert wird nebenbei
        if (!isCopyClick(click) || !FeatureGate.copyChat()) return true;
        try {
            copy(click.x(), click.y(), click.hasControlDown());
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                ShokiMod.LOGGER.warn("[Chat] Copy Chat could not read the chat line under the pointer", e);
            }
        }
        return true;
    }

    /**
     * Kopiert nur der Rechtsklick. Auf 26.3 ist das nicht die Taste 1 (die zaehlt dort nach SDL: links = 1, rechts = 3) - bis 2.5.18 stand hier eine 1, und es kopierte
     * der Linksklick, der Rechtsklick nie. Die Taste kommt jetzt aus {@link MouseButtons}.
     */
    static boolean isCopyClick(MouseButtonEvent click) {
        return MouseButtons.isRight(click.button());
    }

    private static void copy(double mouseX, double mouseY, boolean oneLine) throws ReflectiveOperationException {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getWindow() == null) return;
        ChatComponent chat = client.gui.hud.getChat();

        HoveredLine finder = new HoveredLine(client.font, mouseX, mouseY);
        chat.captureClickableText(finder, client.getWindow().getGuiScaledHeight(), 0, ChatComponent.DisplayMode.FOREGROUND);
        FormattedCharSequence hovered = finder.hovered();
        if (hovered == null) return;

        for (GuiMessage.Line line : trimmedLines(chat)) {
            if (line.content() != hovered) continue;
            String text = chooseText(line.parent().content().getString(), lineText(line.content()), oneLine);
            client.keyboardHandler.setClipboard(text);
            client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal("Copied " + (oneLine ? "line" : "message") + " to clipboard!").withStyle(ChatFormatting.GRAY)));
            return;
        }
    }

    /** Was kopiert wird: mit Strg die Zeile unter dem Zeiger, sonst die ganze Nachricht */
    static String chooseText(String wholeMessage, String oneLine, boolean ctrl) {
        return ctrl ? oneLine : wholeMessage;
    }

    /** Der Text einer Zeile des Chats, ohne Stile und Farbcodes */
    static String lineText(FormattedCharSequence line) {
        StringBuilder out = new StringBuilder();
        line.accept((position, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }

    /**
     * Liegt der Punkt im Rechteck eines Textes? Der Text steht in den Koordinaten, die der Chat vergibt (nach seiner Matrix: Groesse des Chats, Einrueckung); die
     * Matrix rechnet den Punkt des Fensters dorthin zurueck.
     *
     * @param pose   die Matrix, mit der der Text auf den Bildschirm kommt
     * @param left   linker Rand des Textes
     * @param top    oberer Rand des Textes
     * @param mouseX Zeiger im Bild des Fensters
     */
    static boolean hit(Matrix3x2fc pose, float left, float top, float width, float height, double mouseX, double mouseY) {
        Matrix3x2f inverse = pose.invert(new Matrix3x2f());
        Vector2f local = inverse.transformPosition((float) mouseX, (float) mouseY, new Vector2f());
        return local.x >= left && local.x < left + width && local.y >= top && local.y < top + height;
    }

    /** Die Zeilen des Chats, nach Aufteilung auf die Breite (privates Feld {@code trimmedMessages}; die Namen sind die von Mojang) */
    @SuppressWarnings("unchecked")
    private static List<GuiMessage.Line> trimmedLines(ChatComponent chat) throws ReflectiveOperationException {
        if (trimmedMessages == null) {
            Field field = ChatComponent.class.getDeclaredField("trimmedMessages");
            field.setAccessible(true);
            trimmedMessages = field;
        }
        return (List<GuiMessage.Line>) trimmedMessages.get(chat);
    }

    /** Sammelt die Textstuecke, die der Chat meldet, und merkt sich das, dessen Rechteck den Zeiger enthaelt */
    private static final class HoveredLine implements ActiveTextCollector {
        private final Font font;
        private final double mouseX;
        private final double mouseY;
        private Parameters parameters = new Parameters(new Matrix3x2f());
        private FormattedCharSequence hovered;

        HoveredLine(Font font, double mouseX, double mouseY) {
            this.font = font;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        FormattedCharSequence hovered() {
            return hovered;
        }

        @Override
        public Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(Parameters newParameters) {
            parameters = newParameters;
        }

        @Override
        public void accept(TextAlignment alignment, int anchorX, int y, Parameters params, FormattedCharSequence text) {
            int left = alignment.calculateLeft(anchorX, font, text);
            if (hit(params.pose(), left, y, font.width(text), LINE_HEIGHT, mouseX, mouseY)) hovered = text;
        }

        @Override
        public void acceptScrolling(Component message, int centerX, int left, int right, int top, int bottom, Parameters params) {
            // Der Chat laesst keinen Text laufen
        }
    }
}
