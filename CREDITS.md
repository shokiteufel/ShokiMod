# Herkunft der Funktionen

Diese Datei beantwortet eine einzige Frage: **Was in ShokiMod kommt woher?**

Sie ist keine Höflichkeit, sondern Pflicht. ShokiMod steht unter LGPL-3.0, und wer
fremde Arbeit einbaut, nennt sie beim Namen — auch dann, wenn nur die Idee übernommen
wurde und keine einzige Zeile.

Gemessen am Stand vom 13.09.2026 (Version 1.6.3).

---

## Kurzfassung

ShokiMod ist ein **Fork von [GanKura](https://github.com/GanKuraDee/GanKura)**. Das
bleibt es auch: Die Vorgeschichte steckt im Repo, die Lizenz ist von dort geerbt, und
ein paar Dateien stehen noch fast unverändert da.

Ein *Abklatsch* von GanKura ist es nicht mehr. Der Vergleich gegen den Abzweigpunkt
(`39d3901`, GanKura 6.4.0 vom 22.08.2026) über 143 eigene Commits:

| | Zeilen | Anteil |
|---|---:|---:|
| praktisch unverändert aus GanKura | 532 | 2,6 % |
| aus GanKura, überarbeitet | 475 | 2,3 % |
| von ShokiTeufel | 19 337 | **95,1 %** |

Von GanKuras 111 Java-Dateien sind 83 gelöscht, 89 neue sind dazugekommen. Sechs
Dateien sind noch zu über 95 % Original.

Die treffendere Beschreibung ist deshalb: **ein Fork von GanKura, in dem von GanKura
kaum noch etwas steckt — und ein Zusammenschnitt von Funktionen, die anderen Mods
nachgebaut sind, plus einem größeren eigenen Teil.**

---

## Was von GanKura übrig ist

Fast unverändert (über 95 % gleich):

| Datei | Was sie tut |
|---|---|
| `ColorPickerScreen`, `ColorPalette`, `ColorSwatchButton` | Der Farbwähler, den alle Marker benutzen |
| `ScoreboardUtils` | Sidebar auslesen |
| `FloorDropHandler` | Boden-Fundstücke markieren (GanKura hatte es seinerseits von Skyblocker) |
| `HudRenderMixin`, `GizmoTextFogMixin` | Zeichen-Einhängepunkte |

Überarbeitet, aber erkennbar GanKura:

`BossNameplateRenderer`, `EntityTracerRenderer`, `PlayerGlowingMixin`,
`ArmorStandHeadOnlyGlowMixin`, `FloorDropParticleMixin`, `LocationScanner`,
`TabListScanner`, `ModMenuIntegration`, `WorldTextRenderMixin`.

Umgeschrieben bis zur Unkenntlichkeit, Idee von GanKura:

`EntityHighlightManager` (2007 → 388 Zeilen), `MobVisual` (432 → 46),
`WorldTextRenderer` (259 → 73), `GameState` (307 → 29), `ModConstants` (233 → 22),
`HudRenderer`, `NetworkHandler`, `EntityGlowingMixin`, `DayHud`.

**Was es aus GanKura nicht mehr gibt:** die kompletten Area-Minibosse (Broodmother,
Ashfang, Golem, Dragon, Magma Boss …) mit Stage Announce, HP-HUD, DPS-Rechner und
Loot-Quality; die Custom Waypoints; Armor-, Equipment-, Quiver-, Ferocity-, Yaw/Pitch-
und Armor-Stack-HUD; Warp-Cooldown-Queue; Server-Reboot-Alarm; die Menü-Tastenkürzel.
Nachgeprüft: Keines dieser Wörter kommt im heutigen Quelltext noch vor.

---

## Aus anderen Mods nachgebaut

### Critter Safari Tracker (MIT, Rok)

Die gesamte Safari-Seite geht auf crittermod zurück.

| Funktion | Was übernommen wurde |
|---|---|
| Critter-Artenliste | Arten und Stückzahlen je Biom (`Critters`) |
| „Fehlt noch"-Anzeige | Aufbau und Zeilen (`MissingHud`) |
| Fortschritts-Anzeige | Nachbau des CritterHud (`ProgressHud`) |
| Nest-Zähler | Nachbau des NestTracker |
| Wände und Rockmite Mounds | Koordinaten und Erkennungsregel (`SafariExtras`) |
| Vollbild-Einblendung | Aufbau des FullScreenAlert (`AlertBanner`) |
| Kasten-Layout | Der Panel-Aufbau beider Anzeigen (`HudPanel`) |
| Biom-Farben | Reihenfolge und Farben (`SafariBiome`) |
| Shiny-Erkennung | Die Prüfung hinter dem Präfix (`SparklingTarget`) |
| Welttext-Umriss | Nur Rahmen, keine Füllung |

### Skysoft (LGPL-3.0-only / LGPL-2.1-only, Akinsoft)

| Funktion | Was übernommen wurde |
|---|---|
| Rare Drop Titles | Der Grundaufbau des Fund-Alarms (`RareLootHandler`) |
| Rare Loot Sharing | Das Teilen in Party und Gilde, samt Vorlagen-Wortlaut |
| Fund-Erkennung | Portiert aus RareLootChatParser, RareLootItemIds, RareLootDisplayNames |
| Preis-Auflösung | Reihenfolge und Quellen aus RareLootValueResolver (`ItemValue`) |
| Profit/h | Die Idee, Pausen nicht mitzuzählen (`HuntingTracker`) |
| Fund-Erkennung am Inventar | Der Weg selbst: jeden Tick nachzählen statt den Chat zu lesen, mit Beruhigungszeit nach einem Wechsel und Blindheit bei offenem Behälter — aus SkyBlockInventoryChanges / ProfitTrackerItemTracking (`ItemChanges`) |
| Profit-Tracker | Aufbau und Bedienidee: Auswahl je Item, eigene Verkaufsart je Item, Preisquellen Instant Sell / Sell Order / NPC (`ProfitTracker`, `ProfitItemScreen`) |

### Skyblocker (LGPL-3.0-or-later, SkyblockerMod)

| Funktion | Was übernommen wurde |
|---|---|
| Chat-Regeln | Funktionsumfang: ausblenden, ersetzen, Action Bar, Einblendung, Toast, Ton |
| Gebiets-Liste | Location-Enum als Grundstock (`KnownAreas`) |
| Toast | Aufbau des BasicToast (`ShokiModToast`) |
| Special Effects | Die Erkenntnis, welche zwei Spielfunktionen das können (`SpecialEffects`) |
| Dye-Drop-Zeile | Das Muster, an dem Hypixels Meldung abzulesen ist |
| Preis-Rückfall | hysky.de als Tiefstpreis-Quelle |
| Floor Drops | Mittelbar — GanKura hatte es von dort |

### SkyOcean (SkyOcean License v1 — Quelltext MIT, meowdding)

Der Hotspot-Kreis beim Angeln: Erkennung über den unsichtbaren Armorstand, Kreis in
der Farbe des Bonus, Warnung beim Verschwinden (`HotspotTracker`, `HotspotRenderer`).

Die Leichen-Stellen im Glacite Mineshaft: die Funktionsidee aus `CorpseWaypoint` —
Bauplan aus der Seitenleiste lesen und die möglichen Stellen dieses Schachts
einblenden. Eigene Umsetzung in Java (`MineshaftState`, `MineshaftCorpses`); von
SkyOcean stammt der Gedanke, nicht der Code.

### SkyblockCollectionTracker (LGPL-2.1-only, ChindeaOne)

Der Gedanke, die Schacht-Marker an eine Bedingung zu haengen: Dort erscheinen die
Routen nur in Schaechten mit genug Lapis-Leichen. Übernommen ist die Idee, nicht der
Code — und hier steht die Bedingung nicht fest, sondern je Bauplan mit eigener Zahl
je Leichen-Sorte (`MineshaftRule`, `MineshaftRuleScreen`).

### Meowdding-Repo (MIT, meowdding)

Die Koordinaten der Leichen-Stellen selbst: `repo/mining/mineshaft_corpses` — sechzehn
Baupläne mit ein bis drei Ausführungen, von Spielern zusammengetragen. Die Datei wird
zur Laufzeit geholt und liegt im Config-Ordner, statt mitgeliefert zu werden: So kommen
neu gefundene Stellen von selbst dazu.

### SkyBlock API (MIT, thatgravyboat)

Wo Hypixel den Schacht hinschreibt: in der untersten Zeile der Seitenleiste, hinter
Datum und Server, als `TUNG_1` (Bauplan und Ausführung). Das ist eine Tatsache über
Hypixels Anzeige — übernommen wurde das Muster, kein Code.

### RiccioFishingUtils (GPL-3.0-or-later, Riccio)

Die Kuchen-Erinnerung (`CakeReminder`): „Yum! You gain … for 48 hours!" mitlesen, je
Kuchen den Zeitpunkt merken, nach 48 Stunden melden, und die Liste leeren, sobald die
Tab-Liste wieder die volle Zahl Century Cakes zeigt.

**Abgeglichen am 13.09.2026** gegen RFUs
[`CakeExpiredAlert.kt`](https://github.com/Ricciow/RiccioFishingUtils-Modern/blob/main/src/main/kotlin/cloud/glitchdev/rfu/feature/other/CakeExpiredAlert.kt),
Zeile für Zeile:

| | RFU | ShokiMod |
|---|---|---|
| Ablauf-Logik | 48 h, `lastOutdated`-Differenz, Tab-Abgleich bei `current == total` | **gleich** |
| Muster (Chat, Tab) | zwei Regexe | **zeichengleich** (nur `^`/`$` ergänzt) |
| Sprache und Aufbau | Kotlin, drei getrennte Tick-Schleifen (20/300/3000) | Java, eine Schleife alle 20 Ticks mit Verzweigung |
| Datenhaltung | eigene Datenklassen `CakesEntry`/`Cake` mit `Instant` | `Map<String, Long>` in der Mod-Einstellung |
| Wiederholung | fest alle 3000 Ticks | einstellbar in Minuten |
| Zurücksetzen | eigener Befehl `/rfuclearcakes` | Knopf in den Einstellungen |
| Zusätzlich | — | `[Get Cakes!]` mit Klick-Befehl, Ton, Testknopf |

**Einordnung: inspiriert, nicht übernommen.** Der Ablauf ist RFUs, die Umsetzung ist
eigene Arbeit — andere Sprache, anderer Aufbau, andere Datenhaltung, andere
Bedienung. Wörtlich gleich sind allein die beiden Muster, und die beschreiben
Hypixels Chat- und Tab-Zeilen, nicht RFUs Erfindung.

Beim Abgleich fiel auf, dass die **Meldungstexte** wörtlich RFUs waren („… of your
cakes just expired!", „You have … expired cakes!", „Expired Cakes:"). Das sind
Riccios Sätze, keine Tatsachen über Hypixel. Sie sind durch eigene ersetzt — damit
steht nichts Geschriebenes aus einer GPL-Mod in dieser LGPL-Mod, und die Frage
erledigt sich, statt offen zu bleiben.

---

## Nur nachgeschlagen — kein Code

Diese Mods stehen hier, weil gegen sie geprüft wurde, nicht weil etwas von ihnen
drinsteckt.

- **SkyHanni** (AGPL-3.0) — dagegen gegengeprüft wurden: die Sky-Mall-Platzierung, die
  Mengen in der Hunting Box, das Whale-Bait-Rezept und die vier Helme, an denen eine
  gefrorene Leiche zu erkennen ist (`LAPIS_ARMOR_HELMET`, `ARMOR_OF_YOG_HELMET`,
  `MINERAL_HELMET`, `VANGUARD_HELMET` — Lapis, Umber, Tungsten, Vanguard) sowie die
  Farben, an denen eine Edelstein-Ader zu erkennen ist (gefärbtes Glas bzw. Glasscheibe:
  rot = Ruby, lila = Amethyst, hellblau = Sapphire, blau = Aquamarine, braun = Citrine,
  grün = Peridot, lime = Jade, magenta = Jasper, weiß = Opal, schwarz = Onyx, gelb =
  Topaz, orange = Amber — nachgesehen in `OreBlock`). Aus `CorpseType` stammt zusätzlich,
  welcher Schlüssel zu welcher Leiche gehört: Tungsten und Umber je ihren, Vanguard den
  `SKELETON_KEY`, Lapis keinen — und die Farbe je Sorte (Lapis blau, Tungsten grau, Umber
  gold, Vanguard weiß). Das sind Tatsachen über Hypixel; Scanner und Marker hier sind
  eigener Code.
  Die AGPL wäre mit der LGPL **nicht** vereinbar, deshalb ist hier bewusst nie Code
  übernommen worden, nur Tatsachen abgelesen.
- **Odin** — die Ping-Anzeige wurde gegen seine gehalten, um den eigenen Messfehler zu
  finden.

## Daten von außen

Keine Mod-Funktionen, sondern Zahlen, die die Mod holt:

- **[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)**
  (MIT) — Pet-Stufen, Item-Namen und -Bilder, Handwerks- und Forge-Rezepte.
- **[SkyShards](https://github.com/Campionnn/SkyShards)** (MIT, Campionnn) — die
  Fusionsdaten für `/shoki shardprofit`.
- **Hypixel API** — Bazaar, Auktionen, Item-Liste.
- **hysky.de** — Auktions-Tiefstpreise.

---

## Ohne Vorbild in einer anderen Mod

Das hier hat keinen Ursprung in einer fremden Mod — Idee, Aufbau und Umsetzung sind
in diesem Projekt entstanden.

**Marktdaten und Gewinn**
- Fusions-Gewinn (`/shoki shardprofit`) samt Bestand aus der geöffneten Hunting Box
- Handwerks- und Forge-Gewinn (`/shoki craftprofit`) mit Forge-Zeit und Coins/h
- Pet-Gewinn (`/shoki petprofit`)
- Der Live-Bazaar mit gzip und 15-Sekunden-Takt
- Die GitHub-Action, die alle zehn Minuten die Listen vorrechnet

**Banner**
- Das ganze Banner-Modell: ein Design statt zwanzig fester Stile
- Die Sandbox mit vier Karteikarten, sechs Umrissen und den vier Schriftschnitten
- Die Kisten-Animation
- Banner teilen und einlesen als Code über die Zwischenablage

**Anzeigen**
- Mining-HUD: Aufträge, Spitzhacken-Fähigkeit mit Abklingzeit, Sky-Mall-Buff
- Performance-HUD mit selbst gemessenem Ping über ein eigenes Ping-Paket
- Pet-HUD mit Overflow-Stufen und der Chat-Meldung beim Aufstieg
- Collection-Tracker
- Contest-HUD mit eigener Uhr
- Nearby-Übersicht der Mobs in der Nähe

**Gilde**
- Die Gilden-Events im Spiel und der ShokiTeufelBot dahinter — eigenes Projekt

**Sonstiges**
- Eigene Audiodateien für jeden Alarm (`CustomSoundPlayer`)
- HUD-Editor und die MoulConfig-Oberfläche
- Shiny-Alarm mit Party-Ruf
- Hideyho-Finder

### Eigene Erweiterungen an übernommenen Funktionen

Der Kern kam von woanders, das hier nicht:

- **Rare Loot:** vier Stufen statt Skysofts einer, jede mit eigener Reaktion und
  eigenem Banner; die Beutebündel aus dem Crystal Nucleus und aus den Leichen der
  Gletscherschächte; der eigene Dye-Alarm.
- **Chat-Regeln:** eigene Audiodateien, die Skyblocker nicht abspielen kann; umgesetzt
  mit Vanilla-APIs statt Skyblockers Innereien.
- **Hunting-Tracker:** der Bestand aus der Hunting Box und die Verbindung zum
  Fusions-Gewinn.
