# ShokiMod

Fabric-Mod fuer **Hypixel Skyblock** auf **Minecraft 26.2**, gepflegt von
[shokiteufel](https://github.com/shokiteufel).

ShokiMod ist ein Zusammenschnitt: Funktionen, die es in anderen Mods einzeln gibt, in
einer Mod — dazu ein groesserer eigener Teil, vor allem rund um Marktpreise, Gewinn und
die Einblendungen bei seltenen Funden. Entstanden ist sie als Fork von
[GanKura](https://github.com/GanKuraDee/GanKura); davon sind heute noch rund fuenf
Prozent des Codes uebrig.

**Woher welche Funktion stammt, steht vollstaendig in [CREDITS.md](CREDITS.md)** — samt
Lizenz und dem, was genau uebernommen wurde.

## Download

Die fertige `.jar` liegt bei den **[Releases](https://github.com/shokiteufel/ShokiMod/releases)** —
immer die oberste Version nehmen, `shokimod-<version>+26.2.jar` herunterladen und in den
`mods/`-Ordner legen. Die `-sources.jar` wird **nicht** gebraucht, die ist nur der Quelltext.

Voraussetzungen: Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API.

Wer noch auf **26.1.2** spielt, nimmt eine Datei mit `+26.1.x` im Namen; gepflegt wird dieser
Stand im Zweig [`26.1.x`](https://github.com/shokiteufel/ShokiMod/tree/26.1.x). Die Mod prueft
die Spielversion beim Start — die falsche Datei laedt der Loader gar nicht erst.

## Was die Mod kann

<details>
<summary><b>Seltene Funde</b></summary>

- Liest Hypixels `RARE DROP!`-Zeilen, die Beutebuendel aus dem Crystal Nucleus und aus
  den Leichen der Gletscherschaechte, gefangene Shards und die Farbmeldungen
  (`WOW! ... found a Necron Dye!`).
- Bewertet jeden Fund im Bazaar, sonst im Auktionshaus.
- Vier Stufen mit eigener Schwelle, jede mit eigenem Banner, Toast, Chatzeile und Ton.
- Eigener Dye-Alarm, der auch dann kommt, wenn die Farbe gerade keinen Preis hat.
- Teilen in Party und Gilde ab einer eigenen Schwelle.
</details>

<details>
<summary><b>Banner-Sandbox</b></summary>

- Ein Design statt fester Stile: Umriss (Rechteck, gerundet, Pille, Oval, Raute,
  geschnitten), Hintergrund, Rahmen, Akzent, Ausrichtung, Innenabstand.
- Schriftschnitte fett, kursiv, unterstrichen, durchgestrichen — getrennt fuer
  Ueberschrift und Wert.
- Animationen bis hin zur Kiste, die aufspringt und den Fund herauswirft.
- Grosser Auftritt: das Stueck fliegt ueber den Bildschirm, Funken steigen auf.
- Designs lassen sich als Code ueber die Zwischenablage teilen.
</details>

<details>
<summary><b>Gewinn-Fenster</b></summary>

- `/shoki shardprofit` — welche Shard-Fusion sich gerade lohnt, mit dem Bestand aus der
  zuletzt geoeffneten Hunting Box.
- `/shoki craftprofit` — Handwerks- und Forge-Rezepte mit Kosten, Erloes, Forge-Zeit und
  Coins pro Stunde.
- `/shoki petprofit` — welche Pets sich zu leveln lohnen.
- Preise aus dem Bazaar (auf Wunsch live alle 15 Sekunden) und dem Auktionshaus.
</details>

<details>
<summary><b>Anzeigen</b></summary>

- Mining: laufende Auftraege, Spitzhacken-Faehigkeit mit Abklingzeit, Sky-Mall-Buff.
- Mineshaft: die moeglichen Leichen-Stellen des Schachts, in dem man steht — der
  Bauplan steht in der Seitenleiste, die Stellen kommen aus dem Repo von Meowdding.
- Pet: Name, Stufe, Fortschritt — samt Overflow-Stufen und Meldung beim Aufstieg.
- Leistung: Bilder je Sekunde, Server-Takt, selbst gemessener Ping und auf Wunsch der
  Tages-Zaehler - untereinander oder nebeneinander in einer Zeile.
- Sammlungen: was die Saecke einsammeln, in Sammlungs-Einheiten und in Coins.
- Hunting-Tracker: jeder gefangene Shard, bewertet und auf die Stunde gerechnet.
- Profit-Tracker: alles, was waehrend eines Laufs ins Inventar oder in einen Sack
  faellt — auch ohne Chatzeile. Je Item einstellbar, ob es im Kasten steht und ob es
  sofort verkauft, in eine Order gelegt oder dem NPC gegeben wird (`/shoki profit`).
- Contest, Mobs in der Naehe.
- Alle Kaesten frei verschiebbar im HUD-Editor (`/shoki hud`).
</details>

<details>
<summary><b>Mob-Marker</b></summary>

- Eigene Mobs markieren: Hervorhebung, Tracer-Linie, Namensschild, Welttext.
- Farbe und Stil je Marker frei waehlbar.
</details>

<details>
<summary><b>Critter Safari</b></summary>

- Was im Biom noch fehlt, Fortschritt, Nester, zerstoerbare Waende, Rockmite Mounds.
- Shiny-Alarm mit Ruf an die Party.
</details>

<details>
<summary><b>Chat und Erinnerungen</b></summary>

- Chat-Regeln: ausblenden, ersetzen, Action Bar, Einblendung, Toast — mit eigenen
  Audiodateien.
- Kuchen-Erinnerung mit einem Klick zum Nachholen.
- Pest-Fallen: Warnung, sobald genug Fallen voll sind, und beim Verlassen des
  Gartens mit vollen Fallen. Der Stand kommt aus Hypixels Tab-Widget.
- Gilden-Events aus dem ShokiTeufelBot: Banner zum Start, laufende Rangliste im Kasten.
</details>

## Befehle

| Befehl | Was er tut |
|---|---|
| `/shoki` | Einstellungen |
| `/shoki hud` | HUD-Editor: Kaesten verschieben und skalieren |
| `/shoki shardprofit` | Gewinn je Shard-Fusion |
| `/shoki craftprofit` | Gewinn je Handwerks- und Forge-Rezept |
| `/shoki petprofit` | Gewinn je Pet |
| `/shoki profit` | Liste des Profit-Trackers: welche Funde zaehlen und wie sie verkauft werden |
| `/shoki pet` | Pet-HUD bauen |
| `/shoki hub` | Zurueck in den Hub |

## Ist das sicher?

Der komplette Quelltext liegt hier offen — jede Zeile ist einsehbar und die Jar wird
genau daraus gebaut. Wer nachsehen will, was die Mod tut, findet den Code unter
[`src/client/java/com/shokiteufel/shokimod/`](src/client/java/com/shokiteufel/shokimod).
Nach draussen redet die Mod nur mit oeffentlichen Skyblock-APIs (`api.hypixel.net`,
`hysky.de`, das NotEnoughUpdates-Verzeichnis) und mit dem eigenen Daten-Zweig auf
GitHub. Selber bauen geht mit `./gradlew build`, die Jar landet dann in `build/libs/`.

## Lizenz

LGPL-3.0-or-later, geerbt vom Upstream-Projekt GanKura. Siehe [LICENSE](LICENSE).

Alle uebernommenen Funktionen, ihre Quell-Mods und deren Lizenzen stehen in
**[CREDITS.md](CREDITS.md)**.
