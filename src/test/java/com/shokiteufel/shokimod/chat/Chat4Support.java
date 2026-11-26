package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.PlayerMessagesCategory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Gemeinsames fuer die Tests von Chat (Teil 4): Zeilen mit Farbcodes als {@link StyledText}, ein Ort mit Spielername und Tab-Liste, Einstellungen von Player Messages.
 */
final class Chat4Support {

    private Chat4Support() {
    }

    /** Die Zeile mit Farbcodes, mit echten Stilen */
    static StyledText line(String legacy) {
        return StyledText.ofLegacy(legacy);
    }

    /** Ein Text mit Farbcodes in der Normalform (Code nur bei Aenderung) - fuer den Vergleich mit {@link StyledText#legacy()} */
    static String normal(String legacy) {
        return StyledText.ofLegacy(legacy).legacy();
    }

    /** Einstellungen von Player Messages, eingeschaltet, mit den Aenderungen des Tests */
    static PlayerMessagesCategory on(Consumer<PlayerMessagesCategory> change) {
        PlayerMessagesCategory c = new PlayerMessagesCategory();
        c.enable = true;
        change.accept(c);
        return c;
    }

    /** Ein Ort, den ein Test frei einstellt */
    static final class Place implements ChatPlace {
        boolean skyblock = true;
        String area = "Hub";
        String own = "";
        Map<String, TabPlayers.Extra> tab = Map.of();

        Place(String area) {
            this.area = area;
        }

        Place own(String name) {
            this.own = name;
            return this;
        }

        Place tab(Map<String, TabPlayers.Extra> tab) {
            this.tab = tab;
            return this;
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
            return false;
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
        public String ownName() {
            return own;
        }

        @Override
        public Map<String, TabPlayers.Extra> tabPlayers() {
            return tab;
        }
    }

    /** Zerlegen, mit dem Namen "Me" und ohne Privatinsel */
    static PlayerChatParser.Parsed parse(String legacy) {
        return PlayerChatParser.parse(line(legacy), "Me", false);
    }

    /**
     * Eine Zeile durch Zerlegen und Neuzusammensetzen: das Ergebnis, oder null, wenn die Zeile keine Spielerzeile ist oder sich nichts aendert.
     */
    static StyledText format(String legacy, PlayerMessagesCategory config, Place place) {
        StyledText in = line(legacy);
        PlayerChatParser.Parsed p = PlayerChatParser.parse(in, place.ownName(), place.privateIsland());
        if (p == null) return null;
        return PlayerChatFormatter.format(in, p, config, place);
    }
}
