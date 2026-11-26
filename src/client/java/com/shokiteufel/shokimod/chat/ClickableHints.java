package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Chat > Hide Clickable Hints: Manche Zeilen der Mod haengen einen Hinweis zum Klicken an ("[Click to order again]", "[Click to invite Name]", "[Click here]").
 * Mit dem Schalter fehlt der Hinweis; der Klick und der Text beim Zeigen liegen dann auf der ganzen Zeile.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatConfig.hideClickableHint}, {@code ChatUtils.clickToActionOrDisable}). Von dort stammt die Regel: Der Schalter nimmt den
 * Hinweis "[CLICK to ...]" unter den eigenen Meldungen weg, die Meldung bleibt anklickbar und zeigt beim Zeigen, was der Klick tut. Die Umsetzung ist eigene Arbeit: Java.
 * SkyHannis Hinweis ist eine eigene Zeile unter der Meldung; bei ShokiMod ist es ein Knopf am Ende der Zeile, der den Klick selbst traegt - ohne ihn wanderten Klick und
 * Hover auf die Zeile.
 *
 * <p>Wo er wirkt: die Zeile zum erneuten Bestellen nach einem abgebrochenen Kaufauftrag im Basar ({@code BazaarHelpers}), die beiden Einladen-Zeilen ({@code PartyInvites},
 * {@code VanguardGuildCall}) und die Zeile zum Jahresgewinn des Profit-Trackers ({@code ProfitTracker}). Die benannten Knoepfe ([Get Cakes!], [Go], [Refuel ...], [Get Keys from
 * Sacks]) sind keine Hinweise und bleiben.
 */
public final class ClickableHints {

    private ClickableHints() {
    }

    /** Ist der Schalter an? */
    public static boolean hidden() {
        return ModConfig.INSTANCE.chatTab.hideClickableHints;
    }

    /** Haengt den Hinweis an die Zeile - oder, mit dem Schalter, nur seinen Klick und Hover auf die ganze Zeile */
    public static MutableComponent attach(MutableComponent line, Component hint) {
        return attach(line, hint, hidden());
    }

    /** Wie {@link #attach(MutableComponent, Component)}, mit der Wahl von aussen (fuer die Tests) */
    public static MutableComponent attach(MutableComponent line, Component hint, boolean hide) {
        if (!hide) return line.append(hint);
        Style s = hint.getStyle();
        ClickEvent click = s.getClickEvent();
        HoverEvent hover = s.getHoverEvent();
        if (click == null && hover == null) return line;
        return StyledText.withEvents(line, click, hover);
    }
}
