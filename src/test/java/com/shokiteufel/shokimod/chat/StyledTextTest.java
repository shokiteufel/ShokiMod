package com.shokiteufel.shokimod.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Zeile als Stuecke mit Stil: einlesen, den Text mit Farbcodes zurueckgeben, umschreiben - und dabei Klick und Hover behalten. */
class StyledTextTest {

    @Test
    void legacyTextIsReadAndWrittenBackInNormalForm() {
        StyledText t = StyledText.ofLegacy("§6§lPET DROP! §r§5Baby Yeti §r§b(+§r§b168%)");
        assertEquals("PET DROP! Baby Yeti (+168%)", t.plain());
        // Ein Code steht nur, wo sich der Stil aendert; eine Farbe loescht die Formate davor von selbst
        assertEquals("§6§lPET DROP! §5Baby Yeti §b(+168%)", t.legacy());
    }

    @Test
    void aResetBetweenTwoStylesIsKeptWhereTheNextStyleHasNoColour() {
        assertEquals("§lbold§r plain", StyledText.ofLegacy("§lbold§r plain").legacy());
        // Ein Format kommt dazu: ohne Farbe geht ein Zuruecksetzen voraus, danach stehen beide Formate
        assertEquals("§lbold§r§l§oitalic", StyledText.ofLegacy("§lbold§oitalic").legacy());
        assertEquals("§aone §rtwo", StyledText.ofLegacy("§aone §rtwo").legacy());
    }

    @Test
    void aSliceStartsFromNothingAndKeepsItsOwnCodes() {
        StyledText t = StyledText.ofLegacy("§bHello §a§lWorld§r!");
        assertEquals("Hello World!", t.plain());
        assertEquals("§a§lWorld", t.legacy(6, 11));
        assertEquals("§bHello ", t.legacy(0, 6));
        assertEquals("", t.legacy(3, 3));
    }

    @Test
    void aCodeInsideThePartOfAComponentBecomesAStyle() {
        // Hypixel schreibt manchmal das Zeichen selbst in den Text
        StyledText t = StyledText.of(Component.literal("§aGreen §cRed"));
        assertEquals("Green Red", t.plain());
        assertEquals("§aGreen §cRed", t.legacy());
    }

    @Test
    void theStyleOfAComponentIsInheritedByItsChildren() {
        MutableComponent root = Component.literal("a ").withStyle(ChatFormatting.RED)
                .append(Component.literal("b ").withStyle(ChatFormatting.BOLD))
                .append(Component.literal("c").withStyle(ChatFormatting.GREEN));
        StyledText t = StyledText.of(root);
        assertEquals("a b c", t.plain());
        // Die Teile erben vom Kopf (rot), nicht voneinander: der zweite ist rot und fett, der dritte gruen ohne Fett
        assertEquals("§ca §c§lb §ac", t.legacy());
    }

    @Test
    void clickAndHoverSurviveReplacingText() {
        MutableComponent line = Component.literal("Pay ").append(Component.literal("5,000 coins").withStyle(ChatFormatting.GOLD)
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/pay"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("tip")))));
        StyledText t = StyledText.of(line);
        StyledText changed = t.replace(4, 9, "5k");
        assertEquals("Pay 5k coins", changed.plain());
        Style style = changed.styleAt(4);
        assertEquals("/pay", ((ClickEvent.RunCommand) style.getClickEvent()).command());
        assertNotNull(style.getHoverEvent());
        // Und in der fertigen Komponente
        MutableComponent back = changed.toComponent();
        assertEquals("Pay 5k coins", back.getString());
        assertEquals("/pay", ((ClickEvent.RunCommand) changed.firstClick()).command());
        assertNull(StyledText.ofLegacy("§aplain").firstClick());
        assertNull(StyledText.ofLegacy("§aplain").firstHover());
        assertTrue(back.getSiblings().size() >= 2);
    }

    @Test
    void insertAndAppendTakeTheirOwnStyle() {
        StyledText t = StyledText.ofLegacy("§6You got §fa Pet");
        StyledText inserted = t.insert(8, "EPIC ", StyledText.ofLegacy("§5§lx").styleAt(0));
        assertEquals("You got EPIC a Pet", inserted.plain());
        assertEquals("§6You got §5§lEPIC §fa Pet", inserted.legacy());
        assertEquals("§6You got §fa Pet§7 (X)", t.append(StyledText.ofLegacy("§7 (X)")).legacy());
    }

    @Test
    void startsRunSaysWhereAPartBegins() {
        StyledText t = StyledText.ofLegacy("one §6two§r three");
        assertTrue(t.startsRun(0));
        assertTrue(t.startsRun(4));
        assertTrue(t.startsRun(7));
        assertFalse(t.startsRun(5));
        assertFalse(t.startsRun(100));
    }

    @Test
    void colourCharNamesTheCodeOfAColourAndNothingForOtherStyles() {
        assertEquals('6', StyledText.colourCharOf(StyledText.ofLegacy("§6x").styleAt(0)));
        assertEquals('5', StyledText.colourCharOf(StyledText.ofLegacy("§5§lx").styleAt(0)));
        assertEquals(0, StyledText.colourCharOf(StyledText.ofLegacy("x").styleAt(0)));
        assertEquals(0, StyledText.colourCharOf(Style.EMPTY));
    }

    @Test
    void withEventsPutsClickAndHoverOnEveryPart() {
        StyledText t = StyledText.ofLegacy("§aone §bTwo").withEvents(new ClickEvent.RunCommand("/x"), StyledText.hoverOf("§7tip"));
        for (StyledText.Run r : t.runs()) {
            assertEquals("/x", ((ClickEvent.RunCommand) r.style().getClickEvent()).command());
            assertNotNull(r.style().getHoverEvent());
        }
        assertEquals("§aone §bTwo", t.legacy());
    }

    @Test
    void anEmptyOrNullLineIsEmpty() {
        assertTrue(StyledText.ofLegacy("").isEmpty());
        assertTrue(StyledText.ofLegacy(null).isEmpty());
        assertTrue(StyledText.ofPlain("").isEmpty());
        assertEquals("", StyledText.ofLegacy("").legacy());
        assertEquals(0, StyledText.ofLegacy("").runs().size());
    }

    @Test
    void theIncomingLineFallsBackToPlainTextWhenTheComponentDiffers() {
        Component c = Chat2Support.component("§aHello");
        assertEquals("§aHello", new Incoming("Hello", c).styled().legacy());
        // Weicht der Klartext ab (oder fehlt die Komponente), gibt es keinen Stil
        assertEquals("Other", new Incoming("Other", c).styled().legacy());
        assertEquals("Hello", new Incoming("Hello", null).styled().legacy());
    }
}
