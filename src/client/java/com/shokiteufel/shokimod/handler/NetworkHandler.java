package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.render.ShinyAlert;
import com.shokiteufel.shokimod.scanner.EssenceState;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import com.shokiteufel.shokimod.scanner.MiningState;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.session.SessionManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Serverwechsel und eingehende Chatnachrichten. */
public class NetworkHandler {

    /**
     * "[Sacks] +269 items. (Last 5s.)" - die Sammelzeile, die Hypixel alle paar
     * Sekunden schickt.
     *
     * Menge und Zeitraum stehen nie fest, und manchmal sind es zwei Haelften:
     * "+10,144 items, -812,640 items. (Last 10s.)". Das Muster laesst beides zu, bleibt
     * aber streng genug, dass andere Zeilen mit demselben Kopf stehen bleiben - "Moved
     * 9 Enchanted Bone from your Sacks to your inventory." etwa soll man weiter sehen.
     */
    private static final java.util.regex.Pattern SACK_SUMMARY = java.util.regex.Pattern.compile(
            "^\\[Sacks\\]\\s*[+-][\\d,.]+ items?(?:,\\s*[+-][\\d,.]+ items?)*\\.?(?:\\s*\\(Last [^)]*\\))?\\.?$");

    /** Farbcodes einer Chatzeile. Vorbereitet, weil jede Nachricht hier durchlaeuft */
    private static final java.util.regex.Pattern COLOUR_CODE = java.util.regex.Pattern.compile("§[0-9a-fk-or]");

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            GameState.resetAll();
            // Gemeldete Funde gelten nur für die Welt, in der sie gesehen wurden
            ShinyAlert.reset();
            FloorDropHandler.reset();
            NestTracker.reset();
            SessionManager.onWorldChange();
            RareLootHandler.reset();
            com.shokiteufel.shokimod.scanner.ItemChanges.reset();
            // Der erste Essence-Stand im neuen Profil ist der Anfang, kein Fund
            com.shokiteufel.shokimod.scanner.EssenceState.reset();
            PestReminder.reset();
            HotspotTracker.reset();
            com.shokiteufel.shokimod.scanner.PetState.reset();
            com.shokiteufel.shokimod.scanner.PerformanceState.reset();
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String msg = message.getString();

            // Die Action Bar ist keine Chatzeile, dort greifen keine Regeln - bis auf
            // eine: Beim Abbauen steht der Essence-Zugang dort ("+3 Fossil Essence")
            // und sonst nirgends. Wer das Widget der Tab-Liste nicht anhat, wird nur
            // hier gezaehlt. Die Zeile kommt in jedem Tick, deshalb erst der billige
            // Blick auf das Wort und nur dann die Arbeit
            if (overlay) {
                if (FeatureGate.itemChanges() && GameState.Server.isSkyblock()) {
                    // Auch die Zeile ohne Essence wird gemeldet: An ihr merkt der
                    // Zaehler, dass das Segment weg ist, und laesst denselben Betrag
                    // spaeter wieder zaehlen
                    ItemChanges.report(EssenceState.processActionBar(msg.contains("Essence")
                            ? COLOUR_CODE.matcher(msg).replaceAll("")
                            : ""), "essence bar");
                }
                return true;
            }

            String unformattedMsg = COLOUR_CODE.matcher(msg).replaceAll("");

            // Die Lauf-Mitschrift liest nur mit und aendert an der Zeile nichts
            SessionManager.onChatMessage(msg);
            // Seltene Funde ebenso: bewerten und melden, die Zeile bleibt
            RareLootHandler.onChatMessage(unformattedMsg);
            // Ein Pet auf Hoechststufe gehoert in den Kasten - es faellt nie ins Inventar
            com.shokiteufel.shokimod.handler.ProfitTracker.onChatMessage(msg, unformattedMsg);
            // Der Shiny-Ruf eines anderen: nur hoeren, nichts aendern
            ShinyAlert.onChatMessage(unformattedMsg);
            // Der Hunting Tracker zaehlt dieselben Shard-Zeilen mit
            HuntingTracker.onChatMessage(unformattedMsg);
            // Der Kuchen-Alarm merkt sich, wann welcher Kuchen gegessen wurde
            CakeReminder.onChatMessage(unformattedMsg);
            RareCatchAlert.onChatMessage(unformattedMsg);
            // Der Wechsel per Autopet ist die einzige Meldung ueber das aktive Pet,
            // die ohne offenes Menue kommt
            com.shokiteufel.shokimod.scanner.PetState.onChatMessage(unformattedMsg);

            // Der Sky-Mall-Buff wird nur einmal am Tag angekuendigt - diese Zeile ist
            // die einzige Gelegenheit, ihn mitzubekommen
            MiningState.onChatMessage(unformattedMsg);
            // Eine Leichen-Stelle aus der Party ist ein Marker wert - sie kommt von
            // jemandem, der davorsteht
            com.shokiteufel.shokimod.scanner.CorpseFinder.onChatMessage(unformattedMsg);
            // Der Collection-Tracker braucht die Nachricht selbst: die Aufstellung haengt am Mauszeiger
            CollectionTracker.onChatMessage(message, unformattedMsg);
            // Und der Profit-Tracker dieselbe Zeile: was in einen Sack faellt, sieht das Inventar nie
            com.shokiteufel.shokimod.scanner.ItemChanges.onChatMessage(message, msg, unformattedMsg);

            // Ein "false" blendet die Originalzeile aus
            boolean zeigen = ChatRuleHandler.handleMessage(message, msg, unformattedMsg);

            // Die Sammelzeile der Saecke zuletzt - erst muss jeder sie gelesen haben.
            //
            // Sie steht hier ganz unten und nicht oben: Was in einen Sack faellt, sieht
            // das Inventar nie, und diese Zeile ist die einzige Meldung darueber. Wer
            // sie frueher abfangen wuerde, haette einen Kasten, der nichts mehr zaehlt
            if (zeigen && com.shokiteufel.shokimod.data.ModConfig.INSTANCE.profit.hideSackLine
                    && SACK_SUMMARY.matcher(unformattedMsg.trim()).matches()) {
                return false;
            }
            return zeigen;
        });
    }
}
