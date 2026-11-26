package com.shokiteufel.shokimod.chat;

import java.util.List;

/**
 * Die Namen, mit denen {@code /call} die Kontakte der Abiphone anruft ("/call elizabeth").
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code AbiphoneFeatures}, Tab-Vervollstaendigung von {@code /call}). SkyHanni liest die Namen aus einer Datei seines
 * Verzeichnisses im Netz ({@code constants/abiphone.json}, je Kontakt der Name oder eine Liste von Anrufnamen, kleingeschrieben); die gibt es hier nicht, und die
 * Mod laedt sie nicht. Stattdessen steht hier eine feste Liste: die Kontakte, die in SkyHannis Quelltext und in dieser Mod beim Namen vorkommen. Sie ist
 * <b>unvollstaendig</b> - die Abiphone hat weit mehr Kontakte. Wer einen vermisst, tippt ihn einfach aus; ein weiterer Name gehoert in {@link #NAMES}.
 *
 * <p>Belegt sind: Elizabeth (das Beispiel des Auftrags, "/call el" gibt "elizabeth"; sie ist auch ein Name der Liste in {@code WarpCommands}), Anita (Jacobs Wettbewerbs-Medaillen: SkyHanni sagt, man erreiche sie
 * von jeder Insel per Abiphone), Odger (Trophy Fishing), Kysha (Fusion Machine, Hunting), Phillip (Farming Fortune: SkyHanni schickt {@code /call Phillip}),
 * Jotraeline (der Drill-Auftanker dieser Mod: {@code /call jotraeline}) und Trevor (der Trapper: SkyHanni nennt seinen Abklingkasten "fuer Abiphone-Nutzer").
 */
public final class AbiphoneContacts {

    /** Die bekannten Anrufnamen, kleingeschrieben, alphabetisch */
    public static final List<String> NAMES = List.of("anita", "elizabeth", "jotraeline", "kysha", "odger", "phillip", "trevor");

    private AbiphoneContacts() {
    }
}
