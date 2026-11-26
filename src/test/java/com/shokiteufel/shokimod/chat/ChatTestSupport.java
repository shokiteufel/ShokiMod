package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gemeinsames fuer die Tests der Chat-Filter: feste Orte, frische Einstellungen, eine Zeile durch einen Filter ohne Gedaechtnis. */
final class ChatTestSupport {

    /** Eine lange Linie, wie Hypixel sie um Bloecke legt */
    static final String WRAP = "▬".repeat(64);

    static final ChatPlace HUB = ChatPlace.of(true, "Hub", false);
    static final ChatPlace DUNGEON = ChatPlace.of(true, "Catacombs", true);

    private ChatTestSupport() {
    }

    static ChatPlace at(String area) {
        return ChatPlace.of(true, area, false);
    }

    /** Einstellungen mit den Schaltern von Chat Filter Types, die der Test braucht */
    static ChatTabCategory types(Consumer<ChatFilterTypesCategory> on) {
        ChatTabCategory c = new ChatTabCategory();
        on.accept(c.filterTypes);
        return c;
    }

    /** Einstellungen mit den Schaltern von Chat, die der Test braucht */
    static ChatTabCategory chat(Consumer<ChatTabCategory> on) {
        ChatTabCategory c = new ChatTabCategory();
        on.accept(c);
        return c;
    }

    static ChatVerdict once(String line, ChatTabCategory c, ChatPlace p) {
        return new ChatFilterEngine().decide(line, c, p, 1000L);
    }

    static void assertHides(String line, ChatTabCategory c, ChatPlace p) {
        assertEquals(ChatVerdict.Kind.HIDE, once(line, c, p).kind(), "ausgeblendet: " + line);
    }

    static void assertShows(String line, ChatTabCategory c, ChatPlace p) {
        assertEquals(ChatVerdict.Kind.SHOW, once(line, c, p).kind(), "bleibt: " + line);
    }

    /** Jede Zeile wird mit dem Schalter ausgeblendet - und bleibt, wenn alles aus ist (von Haus aus) */
    static void assertTypeHides(Consumer<ChatFilterTypesCategory> on, ChatPlace p, String... lines) {
        for (String line : lines) {
            assertHides(line, types(on), p);
            assertShows(line, new ChatTabCategory(), p);
        }
    }

    /** Jede Zeile bleibt, auch mit dem Schalter */
    static void assertTypeShows(Consumer<ChatFilterTypesCategory> on, ChatPlace p, String... lines) {
        for (String line : lines) assertShows(line, types(on), p);
    }

    /** Jede Zeile wird mit dem Schalter von Chat ausgeblendet - und bleibt, wenn alles aus ist */
    static void assertChatHides(Consumer<ChatTabCategory> on, ChatPlace p, String... lines) {
        for (String line : lines) {
            assertHides(line, chat(on), p);
            assertShows(line, new ChatTabCategory(), p);
        }
    }

    static void assertChatShows(Consumer<ChatTabCategory> on, ChatPlace p, String... lines) {
        for (String line : lines) assertShows(line, chat(on), p);
    }
}
