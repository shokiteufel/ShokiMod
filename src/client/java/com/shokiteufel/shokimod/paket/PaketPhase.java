package com.shokiteufel.shokimod.paket;

/**
 * Wann ein Abonnent ein Paket sieht.
 *
 * <p>{@link #VOR}: Das Spiel hat das Paket noch nicht angewendet. Inventar, Seitenleiste, Spielerliste und Co. zeigen
 * den alten Stand; so lassen sich "vorher" und "nachher" direkt vergleichen.
 * <p>{@link #NACH}: Das Spiel hat das Paket angewendet (und damit auch alles, was Fabric und andere Mods dabei
 * ausgeloest haben, zum Beispiel das Chatereignis einer Systemnachricht).
 *
 * <p>Bei einem Buendel gilt beides fuer das ganze Buendel: {@code VOR} sieht den Stand vor dem ersten Teilpaket,
 * {@code NACH} den Stand nach dem letzten.
 */
public enum PaketPhase {
    VOR,
    NACH
}
