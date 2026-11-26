package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import com.shokiteufel.shokimod.data.ModConfig.PlayerMessagesCategory;
import com.shokiteufel.shokimod.data.ModConfig.RareDropMessagesCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat (Teil 2): die Funktionen, die eine Chatzeile umschreiben statt sie nur auszublenden - gekuerzt (Potion, Bestiary, Enchanting, Jacob), ergaenzt (Dark Auction,
 * Pet-Seltenheit, Buchname) oder in der Zahl geaendert (Shorten Coin Amounts, Show Actual Powder Gained). Alles, was dafuer ueber mehrere Zeilen gemerkt wird, liegt in
 * diesem Objekt; {@link ChatFilterEngine} haelt eines davon.
 *
 * Zwei Stufen, wie bei SkyHanni (Ereignis "Allow", dann "Modify"):
 * <ol>
 * <li>{@link #compact}: schluckt Zeilen und schreibt eine kurze (Potion, Bestiary, Enchanting, Jacob). Kommt vor den einfachen Ausblend-Schaltern von Teil 1.</li>
 * <li>{@link #modify}: schreibt eine Zeile um, die niemand ausgeblendet hat (Pet-Seltenheit, Dark Auction, Powder, Muenzen) - eine Zeile durchlaeuft die Schritte
 * nacheinander, jeder bekommt das Ergebnis des vorigen.</li>
 * </ol>
 * Ein Schritt, der erst spaeter etwas zu schreiben hat (Enchanting wartet auf die letzte Belohnung, Jacob auf das Ende des Blocks, das Buch auf das Inventar),
 * liefert es ueber {@link #poll}; der Tick des Chats ruft es.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code features/chat}). Von dort stammt die Reihenfolge der Stufen und je Schritt die Regel (siehe die Klasse des Schritts).
 * Die Umsetzung ist eigene Arbeit: Java, eine Entscheidung als reiner Wert statt eines Ereignisses, alles gegen den Klartext der Zeile.
 */
final class ChatRewrites {

    private final CompactBestiary bestiary = new CompactBestiary();
    private final CompactExperimentRewards experiments = new CompactExperimentRewards();
    private final CompactJacobClaim jacob = new CompactJacobClaim();
    private final DarkAuctionItems darkAuction = new DarkAuctionItems();
    private final RareDropMessages rareDrop = new RareDropMessages();

    /** Neue Welt: was ueber mehrere Zeilen gemerkt wurde, gilt nicht mehr */
    void reset() {
        bestiary.reset();
        experiments.reset();
        jacob.reset();
        darkAuction.reset();
        rareDrop.reset();
    }

    /** Wartet noch etwas, das erst der Tick schreibt? */
    boolean pending() {
        return experiments.pending() || jacob.pending() || rareDrop.pending();
    }

    /** Erste Stufe: Zeilen schlucken und kurze schreiben. null heisst, keiner dieser Schritte ist zustaendig */
    ChatVerdict compact(Incoming in, ChatTabCategory t, ChatPlace p, long now) {
        if (!t.anyCompact() || !p.skyblock()) return null;
        ChatVerdict v;
        if (t.compactPotion != null && t.compactPotion.enabled) {
            v = CompactPotionMessages.line(in, t);
            if (v != null) return v;
        }
        if (t.compactBestiary) {
            v = bestiary.line(in, now);
            if (v != null) return v;
        }
        if (t.compactExperiments) {
            v = experiments.line(in, p.experimentTableRecent(now), now);
            if (v != null) return v;
        }
        if (t.compactJacobClaim && ChatPlace.isGarden(p.area())) {
            v = jacob.line(in, now);
            if (v != null) return v;
        }
        return null;
    }

    /** Zweite Stufe: eine Zeile umschreiben, die bis hierher stehen blieb. Liefert SHOW, REPLACE (die neue Zeile) oder HIDE (die Zeile wird zurueckgehalten) */
    ChatVerdict modify(Incoming in, ChatTabCategory t, ChatPlace p, long now) {
        if (!t.anyRewrite() || !p.skyblock()) return ChatVerdict.SHOW;
        String plain = in.plain;
        RareDropMessagesCategory rd = t.rareDrop;
        boolean rare = rd != null && plain.startsWith("RARE DROP!");
        if (rare && rd.enchantedBookMissingMessage) rareDrop.noteRareDrop(plain, now);
        if (rare && rd.enchantedBook) {
            ChatVerdict held = rareDrop.book(in, p, now);
            if (held != null) return held;
        }

        StyledText cur = null;

        // Chat (Teil 4): die Zeile eines Spielers wird neu zusammengesetzt (Player Messages); jede andere Zeile hoechstens vom Rang-Verberger angefasst
        PlayerMessagesCategory pm = t.playerMessages;
        boolean formatting = pm != null && pm.enable;
        boolean translating = t.translator != null && t.translator.translateOnClick;
        boolean hidingRanks = pm != null && pm.playerRankHider;
        PlayerChatParser.Parsed player = null;
        // Auch der Rang-Verberger allein muss wissen, ob es eine Spielerzeile ist: Deren Rang aendert er nur mit "Enable Chat Formatting"
        if ((formatting || translating || hidingRanks) && PlayerChatParser.mayBePlayerChat(plain)) {
            player = PlayerChatParser.parse(in.styled(), p.ownName(), p.privateIsland());
        }
        if (player != null) {
            if (formatting) cur = or(cur, PlayerChatFormatter.format(in.styled(), player, pm, p));
        } else if (hidingRanks) {
            cur = or(cur, PlayerChatFormatter.hideRanks(in.styled()));
        }

        if (rd != null && rd.petRarity && RareDropMessages.mayBePet(plain)) {
            cur = or(cur, RareDropMessages.petRarity(cur != null ? cur : in.styled()));
        }
        if (t.darkAuctionItemDisplay && plain.startsWith("[NPC] Sirius:") && ChatPlace.isDarkAuction(p.area())) {
            cur = or(cur, darkAuction.line(cur != null ? cur : in.styled()));
        }
        if (t.showEffectivePowder && plain.startsWith("    Gemstone Powder") && ChatPlace.isCrystalHollows(p.area())) {
            cur = or(cur, ActualPowder.rewrite(cur != null ? cur : in.styled(), p.powderContext()));
        }
        if (t.shortenCoinAmounts && ShortenCoins.mayApply(cur != null ? cur.plain() : plain)) {
            cur = or(cur, ShortenCoins.rewrite(cur != null ? cur : in.styled()));
        }
        // Chat (Teil 4): zuletzt der Klick der Uebersetzung, auf der Zeile, wie sie jetzt aussieht (eine Zeile mit eigenem Klick bleibt, wie sie ist)
        if (translating && player != null && player.translatable() && !player.message().plain().isBlank()) {
            StyledText base = cur != null ? cur : in.styled();
            if (base.firstClick() == null) {
                StyledText wrapped = TranslateOnClick.wrap(base, TranslateLines.remember(player.message().plain()));
                if (wrapped != null) cur = wrapped;
            }
        }
        return cur == null ? ChatVerdict.SHOW : ChatVerdict.replace(ChatVerdict.Line.ofComponent(cur.toComponent()));
    }

    private static StyledText or(StyledText before, StyledText result) {
        return result != null ? result : before;
    }

    /** Vom Tick: was ist inzwischen faellig? SHOW heisst: nichts */
    ChatVerdict poll(ChatTabCategory t, ChatPlace p, long now) {
        List<ChatVerdict.Line> out = null;
        if (t.compactExperiments) out = add(out, experiments.poll(now));
        if (t.compactJacobClaim) out = add(out, jacob.poll(now));
        if (t.rareDrop != null && (t.rareDrop.enchantedBook || t.rareDrop.enchantedBookMissingMessage)) out = add(out, rareDrop.poll(t, p, now));
        return out == null ? ChatVerdict.SHOW : new ChatVerdict(ChatVerdict.Kind.REPLACE, out);
    }

    private static List<ChatVerdict.Line> add(List<ChatVerdict.Line> out, ChatVerdict v) {
        if (v == null || v.kind() != ChatVerdict.Kind.REPLACE) return out;
        List<ChatVerdict.Line> list = out == null ? new ArrayList<>() : out;
        list.addAll(v.lines());
        return list;
    }
}
