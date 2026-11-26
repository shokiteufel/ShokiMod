package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;

import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gemeinsames fuer die Tests von Chat (Teil 2): Zeilen mit Farbcodes als Komponente, ein Ort mit allem, was die umschreibenden Funktionen lesen, und ein Ablauf mit
 * eigener Uhr. Die Zeilen der Tests sind Text mit {@code §}-Codes: Daraus werden Komponenten mit echten Stilen, der Klartext dazu ist deren Text.
 */
final class Chat2Support {

    private Chat2Support() {
    }

    /** Die Zeile mit Farbcodes als Komponente (mit Stilen) */
    static Component component(String legacy) {
        return StyledText.ofLegacy(legacy).toComponent();
    }

    /** Der Klartext einer Zeile mit Farbcodes */
    static String plain(String legacy) {
        return StyledText.ofLegacy(legacy).plain();
    }

    /** So schreibt der Chat-Weg einen Text mit Farbcodes in der Normalform auf (Code nur bei Aenderung) - fuer den Vergleich */
    static String normal(String legacy) {
        return StyledText.ofLegacy(legacy).legacy();
    }

    /** Einstellungen von Chat, mit den Schaltern, die der Test braucht */
    static ChatTabCategory config(Consumer<ChatTabCategory> on) {
        return ChatTestSupport.chat(on);
    }

    /** Ein Ort, den ein Test frei einstellt: alles, was Teil 2 vom Spiel liest */
    static final class Place implements ChatPlace {
        boolean skyblock = true;
        String area = "Hub";
        boolean dungeon = false;
        boolean experimentOpen = false;
        boolean menuOpen = false;
        Map<String, Integer> books = new HashMap<>();
        PowderContext powder = PowderContext.NONE;

        Place(String area) {
            this.area = area;
        }

        @Override
        public boolean skyblock() {
            return skyblock;
        }

        @Override
        public String area() {
            return area;
        }

        @Override
        public boolean dungeon() {
            return dungeon;
        }

        @Override
        public boolean sanctuary() {
            return false;
        }

        @Override
        public boolean alphaNetwork() {
            return false;
        }

        @Override
        public boolean experimentTableRecent(long now) {
            return experimentOpen;
        }

        @Override
        public Map<String, Integer> enchantedBooks() {
            return new HashMap<>(books);
        }

        @Override
        public boolean menuOpen() {
            return menuOpen;
        }

        @Override
        public PowderContext powderContext() {
            return powder;
        }
    }

    /** Eine Folge von Zeilen durch eine Maschine, mit einer Uhr, die der Test vorstellt */
    static final class Run {
        final ChatFilterEngine engine = new ChatFilterEngine();
        final ChatTabCategory config;
        final Place place;
        long now = 100_000L;

        Run(ChatTabCategory config, Place place) {
            this.config = config;
            this.place = place;
        }

        /** Eine Zeile (mit Farbcodes) 20 Millisekunden nach der vorigen */
        ChatVerdict line(String legacy) {
            now += 20;
            return engine.decide(plain(legacy), component(legacy), config, place, now);
        }

        /** Die Uhr vorstellen und fragen, was faellig ist */
        ChatVerdict poll(long advanceMillis) {
            now += advanceMillis;
            return engine.poll(config, place, now);
        }
    }

    /** Ein Urteil von der Art, die der Test erwartet */
    static void assertKind(ChatVerdict.Kind kind, ChatVerdict v, String what) {
        assertEquals(kind, v.kind(), what);
    }

    /** Die Zeilen eines Urteils als Text mit Farbcodes */
    static List<String> texts(ChatVerdict v) {
        return v.lines().stream().map(ChatVerdict.Line::legacyText).toList();
    }
}
