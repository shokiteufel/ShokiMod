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
| Vollbild-Einblendung | Aufbau des FullScreenAlert (Vorlage „Alert band“ in `BannerDesign`, früher `AlertBanner`) |
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
| Item aus dem Inventar in den Tracker nehmen | Der Weg „Add Item“, Betriebsart „Inventory“: ein Item im eigenen Inventar anklicken (`ProfitTrackerHudControls.wasInventoryItemAdded`, `TrackedItemSelectionPanel`). Gebucht wird anders: Skysoft nimmt das Item in eine feste Liste auf, hier kommt der Stapel als Fund in den Kasten (`InventoryPick`, `ProfitTracker.takeIn`) |

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
| Eigener Slayer-Boss | Woran man ihn erkennt: der Name auf dem Schild des Bosses; wem er gehört, sagt die Zeile „Spawned by: <Name>“ daneben, sonst die Seitenleiste (`SlayerBossTarget`, `EntityHighlightManager`) |
| Slayer-Boss erschienen | Woran man es sieht: die Zeile „Slay the boss!“ im Kasten „Slayer Quest“ der Seitenleiste (`SlayerBossSpawn`) |
| Slayer-Minibosse | Die Namen der Minibosse je Slayer (`SlayerMinibossTarget`) - in Skyblockers `SlayerType` und SkyHanni gleich |
| Garden Plots Widget | Die Karte der Plots (`PlotGrid`, `PlotsState`, `GardenPlotsHud`; Einstellungen `GardenPlotsCategory`): Skyblockers `GardenPlotsWidget`. Regeln: ein Raster aus fünf mal fünf Plots, in der Mitte die Scheune, die Nummern 1 bis 24 an festen Plätzen (die Tabelle stimmt mit SkyHannis `GardenPlotApi` überein); die befallenen Plots stehen in der Zeile „Plots: 4, 12, 13“ des Widgets „Pests“ der Tab-Liste; ein Klick auf einen Plot schickt `/plottp <Nummer>`, auf die Scheune `/plottp barn`; „Close Inventory on Teleport“. Eigene Zutaten: kein Widget neben dem Inventar, sondern ein Kasten des HUD-Editors mit fünf Textzeilen (zweistellige Nummern, befallen rot, der eigene Plot grün), den Klick nimmt der vorhandene Weg für Klicks auf Kästen bei offenem Inventar an. **Nicht übernommen:** die eigenen Symbole je Plot (Rechtsklick), die Knöpfe „Open Desk“, „TP to Garden spawn“ und „Set Garden spawn“, das Einlesen der Plot-Namen aus „Configure Plots“, das Ausblenden der Statuseffekte |
| Show Equipment in Inventory | Die vier Equipment-Plätze (Halskette, Umhang, Gürtel, Handschuhe) im Inventarfenster rechts neben der Spielfigur (`EquipmentInventory`, `InventoryEquipmentMixin`, `SlotXAccessor`, `EquipmentRules`, `EquipmentLayout`, `EquipmentStore`, `EquipmentFile`, `EquipmentCodec`, `EquipmentSet`; Einstellungen `UiVisualsCategory`, `SkyblockInventoryScreenCategory`): Skyblockers `SkyblockInventoryScreen` und `SkyBlockEquipmentUpdater`. Regeln: Gelesen wird in den Fenstern „Stats & Equipment“ und „Loadouts“ (zweite Spalte; schwarze Glasscheiben sind Füllung, die ersten vier Plätze danach zählen; ein Name, der mit „Empty“ oder „Slot “ beginnt, ist leer) und im Kleiderschrank „Equipment Sets“ (die Spalte des Satzes mit dem grünen Farbstoff in der untersten Reihe; ein Klick auf den Farbstoff nimmt den Satz ab); je Profil ein Satz für die Inseln, einer für die Rift und einer für die Safari, gemerkt über den Neustart; die Anzeige ist ein Zwischenspeicher und nicht in Echtzeit; ein Klick auf einen Platz schickt `/equipment` (in der Rift und der Safari `/stats`, dort gibt es den Kleiderschrank nicht). Eigene Zutaten: Das Inventarfenster wird nicht ersetzt (Skyblocker baut statt des `InventoryScreen` einen eigenen Bildschirm), sondern ein Einsprung in `InventoryScreen.extractLabels` zeichnet die Plätze rechts neben die Spielfigur; der Zweithand-Platz (Platz 45) rückt über einen Zugriff auf `Slot.x` und der Knopf des Rezeptbuchs über seine Lage um 21 Punkte nach rechts; beide Mixins stehen in der nicht erforderlichen Konfiguration (fehlen sie, fehlt nur die Anzeige); Rahmen und Hintergrund kommen aus dem Spiel (Fensterbild und `container/slot`), also aus dem gewählten Texturpaket, dunkel oder hell; auf leeren Plätzen steht ein schlichtes Sechseck; eine JSON-Datei `config/shokimod/equipment.json` für alle Profile statt einer NBT-Datei je Profil; die Gegenstände laufen durch den Codec des Spiels. **Nicht übernommen:** die Bilder für leere Plätze (hier ein Sechseck), das Wiki-Nachschlagen auf einem Platz, die Option „Open Equipment To '/stats'“ (gehört bei Skyblocker nicht zu diesem Schalter), das eigene Hintergrundbild des Inventars |
| Input Calculator | Rechnen auf den Zahlenschildern von SkyBlock (`InputCalculator`, `SignInputRules`, `SignCalculator`, `SignCalculatorMixin`; Einstellung `InputCalculatorCategory`): Skyblockers `SignCalculator` und `AbstractSignEditScreenMixin`. Regeln: Auf einem Zahlenschild (zweite Zeile fünfzehn Pfeile, oder sechs; kein Suchschild, dessen dritte Zeile auf „your“ oder „query“ endet, und nicht „Enter name“) steht unter dem Titel in Grün „Eingabe = Ergebnis“ mit Tausenderkommas, ein Fehler in Rot; beim Fertigmachen ersetzt das Ergebnis eine Rechnung (Menge auf ganze Zahlen gerundet, Preis auf zwei Nachkommastellen; ein Preisschild erkennt man am Wort „price“ in der dritten Zeile); k, m, b für Tausend, Million, Milliarde. Eigene Zutaten: ein eigener kleiner Parser (Potenz vor Punkt vor Strich, Klammern, Vorzeichen) ohne Funktionen; eine Zahl allein oder mit k/m/b bleibt, wie sie getippt wurde (das versteht Hypixel selbst); ein negatives oder zu langes Ergebnis ersetzt nichts; der Einsprung steht in der nicht erforderlichen Mixin-Konfiguration. **Nicht übernommen:** die Funktionen (sqrt, log, sin ...), die Optionen „Requires Equals“ und „Close Signs With Enter“, die Gebots-Eingabe der Auktion und die Bazaar-Schnellmengen |

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

Dazu der Gedanke, neben Zuwachs und Tempo auch die naechste Stufe der Collection zu
zeigen und aus dem Tempo zu sagen, wann sie erreicht ist. Wie die Stufe gefunden wird,
ist hier anders: SCT holt den Stand ueber seinen eigenen Server
(`api.skyblockcollections.com`, Anmeldung per Mojang-Sitzung) und stellt das Zaehlen ein,
wenn der nicht erreichbar ist; hier stehen die Stufen in Hypixels eigener, schluessellos
abrufbarer Liste, die die Mod fuer die Namen ohnehin schon holt (`CollectionData`).

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

**Aura-Zeitgeber** (Fishing > Frozen Blaze > Aura timer, `FrozenBlaze.observe`,
`FrozenBlazeHud`) — Version 2.3.13. RFU zeigt seit 1.20.2 („Added frozen blaze aura
timer") einen Kasten mit den Sekunden, die die Frozen Blazing Aura noch wirkt. Gelesen
wurden `FrozenBlazeAura.kt` und `FrozenBlazeDisplay.kt` (Stand 1.20.3, 18.11.2026);
**übernommen ist die Regel**: dreißig Sekunden, Bewegung des Blicks UND des Körpers
nötig, die Restzeit ist die kürzere der beiden Uhren, beim Anlegen des Sets beginnen
beide von vorn, aus bei null. **Eigene Arbeit** ist alles Geschriebene: Java statt
Kotlin, eine Beobachtung je Tick mit nachrechenbaren Funktionen und Tests, ein Kasten
des HUD-Editors (Lage, Größe, Deckkraft), eigener Wortlaut („Blazing Aura", „OFF -
move!") und eigene Beschreibung. Ob Hypixel die Aura genau so prüft, ist hier nicht
nachgesehen; die Regel gilt, weil RFU sie so hat.

---

### NoammAddons (CC0-1.0, Noamm9, `Noamm9/NoammAddons`)

Aus NoammAddons kommt die **Funny Map** (Reiter *Funny Map*) und die Suche des Koerpers bei den **Stern-Mobs** (ikohS > Star mobs). Beides ist in Java
nachgebaut; die Regeln stammen aus Noamms Quelltext (Zweig `multiversion`, Ordner `features/impl/dungeon/map/` und `utils/dungeons/map/`; die
Stern-Mobs aus `StarMobESP` im Zweig `26.1.2-legacy`):

- Das Raster (11 x 11, Ursprung -185/-185, Raumabstand 32) und der **Welt-Scan** mit dem Kern-Hash der Blocksaeule (y 140 bis 12; Kisten, Kolben,
  Wasser, Lava, Feuer und Bretter zaehlen als Luft): `KernHash`, `WeltScanner`.
- Die **Raumtabelle** `rooms-modern.json` (Zweig `data`, unveraendert als Ressource `dungeonkarte/rooms-modern.json`): `RaumTabelle`.
- Das Lesen der Hypixel-Karte (Eingangsreihe, Farbnummern, Zustaende, Kalibrierung je Stockwerk): `KartenLeser`.
- **Position und Groesse** der Karte aus der Config von Noamm (Liste `hud`, Eintrag "Dungeon Map"): `NoammLage`.
- Stern-Mobs: der Koerper steht eine Entity-ID vor dem Namensschild (beim Withermancer drei): `StarMobTarget.bodyIdOffset`. Am Mitschnitt von Schiggy
  (09.11.) bei allen 46 Schildern bestaetigt.

Die Bilder der Funny Map sind die von NoammAddons (CC0): die vier Haekchen (gruen, weiss, Kreuz, Fragezeichen) und der Pfeil des eigenen Spielers
unter `assets/shokimod/textures/gui/dungeonmap/` (aus `assets/noammaddons/textures/gui/dungeonmap/`, unveraendert). Der Scan endet wie bei ihm, wenn
alle Chunks geladen waren (`hasScanned`), und die Karte von Hypixel wird nur bei einem Kartenpaket gelesen (`MapUpdater`).

Eigen sind die Darstellung (Flaechen und Text im HUD), die Einstellungen, das Erkennen des Dungeons (wie "Hide Players") und die
Tests an echten Daten.

**Lizenz:** CC0 1.0 Universal (Public Domain; Datei `LICENSE` im Repository). Es gibt keine Bedingungen; die Herkunft steht trotzdem hier.
**Eine Besonderheit:** Noamm baut seine Karte in zwei Fassungen (`LEGIT` und `CHEAT`). Unentdeckte Raeume zeigt nur die `CHEAT`-Fassung; das ist hier der
Zweck der Funktion, darum steht sie hinter einem eigenen Schalter (von Haus aus aus) mit einem Hinweis auf die Regeln des Servers.

### Odin (BSD-3-Clause, Odtheking, `OdinFabric`)

Aus Odin kommen die Funktionen im Reiter **Odin**. Jede ist in Java nachgebaut; die Regel stammt aus Odins Kotlin-Quelltext, und was
anders ist, steht dabei. Der Lizenztext folgt unten, wie die Lizenz es fuer die ausgelieferte Mod verlangt (Bedingung 2).

- **Hide Players** (`features/impl/render/HidePlayers.kt`, `EntityRendererMixin`) — Version 1.18.2. Ein Spieler wird nicht gezeichnet, wenn er
  ein echter Spieler ist (Kennung der Version 4; NPCs haben eine andere), nicht man selbst, und entweder alle ausgeblendet werden oder er
  naeher als die eingestellte Entfernung steht; auf Wunsch nur im Dungeon. Nachgebaut als `HidePlayers` und `HidePlayersMixin`. Anders: Der
  Dungeon wird aus der Zeile der Tab-Liste ("Area: ..." oder "Dungeon: ..." mit "Catacombs") **und** aus dem Kasten der Seitenleiste
  gelesen, zweimal je Sekunde zwischengespeichert; der Einsprung versucht beide Formen von `shouldRender` (mit und ohne Teilticks) und steht in
  einer eigenen, nicht zwingenden Mixin-Konfiguration, damit eine geaenderte Methode das Spiel nicht beim Start abbricht.
- **Camera** (`features/impl/render/Camera.kt`) — Version 1.18.3. Steht die Kamera am Ende eines Ticks auf der dritten Person von vorn, wird sie auf
  die erste Person zurueckgesetzt. Nachgebaut als `FrontCamera`; die Regel ist dieselbe, die Einstellung ist ein Schalter in der Klappe "Camera"
  im Reiter Odin.
- **No Cursor Reset** (`features/impl/skyblock/NoCursorReset.kt`, `MouseHandlerMixin`) — Version 1.18.4. Oeffnet sich ein Kisten-Fenster weniger als die
  eingestellte Zeit (Standard 150 ms, 0 bis 1000 in Zehnerschritten) nach dem Schliessen des letzten Fensters, wird der Mauszeiger an die Stelle gestellt, an
  der er beim Schliessen stand, statt in die Mitte. Nachgebaut als `NoCursorReset` und `MouseHandlerMixin`. Anders: Die Zeit nimmt der Mixin beim Schliessen
  selbst (Odin fuehrt sie jeden Tick nach); die Einspruenge sitzen an den Raendern von `grabMouse` und `releaseMouse` statt mitten in den Methoden; der
  Zeiger wird ueber GLFW gesetzt (`InputConstants.releaseMouse` gibt es nicht in jeder Version); und welches Fenster sich oeffnet, kommt aus dem Fabric-Ereignis
  `ScreenEvents.AFTER_INIT`, weil der Zugriff auf das aktuelle Fenster je Version anders heisst.
- **Player Size** und die Ping-Anzeige: siehe weiter unten unter "Nur nachgeschlagen". Seit 1.18.1 steht Player Size als Unterreiter im Reiter Odin.

Der Lizenztext von Odin (Quelle: `LICENSE` im Odin-Repository):

    BSD 3-Clause License

    Copyright (c) 2025, odtheking

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:

    1. Redistributions of source code must retain the above copyright notice, this
       list of conditions and the following disclaimer.

    2. Redistributions in binary form must reproduce the above copyright notice,
       this list of conditions and the following disclaimer in the documentation
       and/or other materials provided with the distribution.

    3. Neither the name of the copyright holder nor the names of its
       contributors may be used to endorse or promote products derived from
       this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
    FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
    DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
    SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
    CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
    OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
    OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

### Feesh (Apache-2.0, MoonTheSadFisher, `Sleepy-Panda/Feesh`)

Feesh ist die Fischerei-Mod, aus der die Funktionen unter **Fishing** (Rare catch, Sea creature HP, Trash books, Mute, Out of air, Frozen Blaze) und
Teile des Profit-Trackers stammen. Die Lizenz ist die **Apache License 2.0**: `LICENSE` im Repository und `"license": "Apache 2.0"` in `fabric.mod.json`
sagen dasselbe. Der Text ist derselbe wie beim Abschnitt IQ weiter unten und steht dort einmal vollstaendig. Ein eigener Urheberhinweis steht weder in
Feeshs `LICENSE` (die Vorlage am Ende ist nicht ausgefuellt) noch in den Quelldateien, und es gibt keine NOTICE-Datei; als Autor nennt `fabric.mod.json`
MoonTheSadFisher. Die Funktionen sind in Java nachgebaut; uebernommen sind nur Daten und Muster, und wo das so ist, steht es bei der Funktion
(zum Beispiel die Tabelle der Meeresbewohner bei "Rare catch").

Die aelteren Feesh-Eintraege (Pet-Stufen, Rare catch, HP-Anzeige, Banner, Buecher, Mute, Out of air, Hotspot teilen) stehen weiter unten unter
"Nur nachgeschlagen"; fuer sie gilt dieselbe Lizenz auf dieselbe Weise.

- **Frozen Blaze erkennen** (`utils/PlayerUtils.kt`, `isInFrozenBlaze`, `FROZEN_BLAZE_ARMOR_IDS`) — Version 1.18.7. Das Set gilt als getragen, wenn alle vier
  Ruestungsplaetze ein Stueck der Frozen Blaze tragen: erkannt an der Kennung `FROZEN_BLAZE_HELMET`, `_CHESTPLATE`, `_LEGGINGS` oder `_BOOTS`, ersatzweise am Namen,
  und das alle fuenf Ticks statt bei jeder Abfrage. Nachgebaut als `FrozenBlaze`. Anders: Die Erkennung laeuft nur, solange eine der Funktionen unter
  Fishing > Frozen Blaze an ist. Darauf setzt die erste eigene Funktion dieses Unterreiters auf, "Hide players": Wer das Set traegt, sieht keine anderen
  Spieler (Feesh blendet an dieser Stelle nur Chatzeilen aus; das Ausblenden der Spieler ist eigen und nutzt den Einsprung von "Hide Players" aus dem Reiter Odin).
- **Hide lootshare messages when in Frozen Blaze armor** (`features/chat/HideLootshareMessages.kt`, `settings/categories/Chat.kt`) — Version 1.18.8. Solange das ganze
  Set getragen wird, werden die Chatzeilen "LOOT SHARE You received ... for assisting <Spieler>!" ausgeblendet, die jeder Kill der Party bringt. Feesh hat dafuer ein
  Muster (`LOOTSHARE_ASSIST_PATTERN`) und blendet nur diese Zeilen aus - das README sagt zwar "all LOOT SHARE messages", der Quelltext tut es nicht; hier gilt der
  Quelltext. Nachgebaut als `FrozenBlaze.hidesLootshare`. Anders: Das Muster kennt auch die Schreibweise, die Hypixel heute verwendet (ein Ausrufezeichen hinter "LOOT SHARE",
  ein Zaehler in Klammern am Ende), und die Zeile wird erst ausgeblendet, nachdem der Jagd-Zaehler und der Rare-Loot-Alarm sie gelesen haben - bei Shards ist sie die einzige
  Meldung. Die Zeilen "from ... catching ..." bleiben.
- **Nearby rare** (`features/overlays/SeaCreatureHpTracker.kt`, `utils/EntityUtils.kt`: Namensschild "[Lv600] Name 69M/100M", Mob-Kennung = Kennung des Schildes minus eins) — Version 1.18.12.
  Die Erkennung der Bewohner aus den Namensschildern ist die der HP-Anzeige (siehe unten, "die HP-Anzeige der Meeresbewohner"), nachgebaut als `SeaCreatureHp.parse`.
  Neu ist, was darauf aufsetzt: eine Ansage je Mob, wenn ein seltener in den Umkreis kommt, und ein Kasten um seine Trefferbox (`SeaCreatureNear`,
  `SeaCreatureBoxRenderer`). Feesh hat beides nicht - sein Alarm liest nur den Chat. Die Tabelle der seltenen Bewohner ist dieselbe wie bei "Rare catch".

### SkyHanni (LGPL-2.1, `hannibal002/SkyHanni`)

Seit Version 2.4.10 (26.11.2026) werden Funktionen von SkyHanni nachgebaut, die der User dort sieht und hier haben will. Gelesen wurde der
Quelltext (Stand 25.11.2026, Version 7.48). **Übernommen ist jeweils die Regel** - was Hypixel schreibt, wo etwas liegt, wann etwas gilt -; **alles
Geschriebene ist eigene Arbeit**: Java statt Kotlin, eigener Aufbau über die Kästen, Banner und Einstellungen dieser Mod, eigener Wortlaut, eigene
Beschreibungen, eigene Tests. Es steht nichts Geschriebenes aus SkyHanni in dieser Mod.

- **Fishing > Hook display** (`FishingHookDisplay`, `FishingHookHud`): SkyHanni „Fishing Hook Display". Regel: Hypixels Ruestungsstaender am eigenen Haken
  traegt die Zeit; gelb und fett eine Zahl oder ein „!!!". Eigene Zutaten: Suche je Tick statt Namens-Ereignisse, einstellbarer Alarmtext mit „&"-Farbzeichen,
  Vorlaufzeit, Ausblenden des Originals ueber den vorhandenen Einsprung in `shouldRender`.
- **Fishing > Bait display** (`FishingBaitDisplay`, `FishingBaitHud`): SkyHanni „Fishing Bait Display". Regel: Der Koeder liegt im letzten Platz der Schnellzugriffsleiste, in seiner
  Beschreibung steht „Bait Remaining: N"; ohne Koeder „No Bait". Eigene Zutaten: Lesen alle fuenf Ticks, ein Kasten des HUD-Editors.
- **Fishing > Cocoon settings** (`SeaCreatureCocoon`, `CocoonScreen`): SkyHanni „Cocoon Settings". Regel: Die Chatzeile „CAUGHT! You cocooned a X!"; je Bewohner Titel
  („X Has Been Cocooned") und Gruppenchat („My X has been cocooned!"), ohne eigene Wahl die seltenen. Eigene Zutaten: Titel ueber die Banner dieser Mod mit
  waehlbarem Design und Ton, ein eigenes Fenster fuer die Liste (`/shoki cocoons`).
- **Slayer > Cocoon** (`SlayerCocoon`): SkyHanni „Slayer Cocoon Title" und „Slayer Cocoon Notification Sound". Regel: die Chatzeile „YOU COCOONED YOUR SLAYER BOSS"; Titel
  „Slayer Boss Cocooned!", zehn Töne im Abstand von hundert Millisekunden. Eigene Zutaten: Titel über die Banner dieser Mod (Design im Sandkasten), wählbare Tondatei, beides einzeln schaltbar, Testknopf.
- **Slayer > Spawn particles** (`SlayerSpawnParticles`, Einsprung in `ParticleHider.decide`): SkyHanni „Spawn Particles" (`HideSlayerSpawnParticles`). Regel: drei Arten (enchant = weiß,
  witch = lila, entity_effect = „Slayer specific"), nur bei laufender Slayer-Quest und nur nahe einem Mob, der in den letzten drei Sekunden gestorben ist (Abstand im Quadrat unter fünf).
  Eigene Zutaten: je Art drei Stellungen (wie es ist / ausblenden / immer zeigen), wobei „immer zeigen" über den vorhandenen Weg von „Show always" (`ParticleForce`) auch gegen
  Sodium Extra durchsetzt; die toten Mobs findet ein Tick alle zwei Ticks (`Mob.isDeadOrDying`) statt eines Gesundheits-Ereignisses; die Quest erkennt die Seitenleistenzeile „Slayer Quest".
- **Skill progress** (`SkillProgress`, `SkillLevels`, `SkillProgressHud`, `SkillBarHud`): SkyHanni „Skill Progress", „Progress Bar" und „Overflow" (`SkillApi`, `SkillUtil`, `SkillProgress`,
  `SkillTooltip`). Regel: Die Aktionsleiste schreibt „+3.5 Mining (12,345/40,000)" oder „+1.1 Mining (48.39%)"; aus dem Preis der nächsten Stufe folgt die Stufe, bei der Prozentform
  nennt sie das Widget der Tab-Liste; bei einer vollen Fähigkeit steht als Ziel 0 und als Stand die Erfahrung über der Obergrenze; das Menü „Your Skills" liefert den Stand vorab.
  Die Preise der Stufen bis 60 (zusammen 111.672.425) sind die der Hypixel-Wiki, ihre Summe belegt der Test; danach kostet jede Stufe 7.000.000 plus einen Zuschlag, der mit jeder Stufe um 600.000
  wächst und sich bei jeder zehnten verdoppelt (die Regel von SkyHannis `calculateSkillLevel`). Obergrenzen je Fähigkeit wie bei SkyHanni (Foraging 57, Fishing/Alchemy/Carpentry/Hunting 50, sonst 60).
  Eigene Zutaten: zwei Kästen des HUD-Editors (Zeile und Balken) statt fester Lage, der Balken mit Farbe, Regenbogen, Breite und Höhe, die gelesenen Stände in der Mod-Einstellung gespeichert,
  eine Rechnung der Prozentform, die über der Obergrenze nicht doppelt zählt, eigene Meldung bei einer neuen Stufe über der Obergrenze (ohne SkyHannis Belohnungszeilen), Tooltips und Stapelgröße im Menü
  der Fähigkeiten. **Nicht übernommen:** die Zeile mit allen Fähigkeiten, die Zeit bis zur nächsten Stufe, die Wunschstufe, die Erfahrung aus Geschenken und der Balken mit Texturen.
- **Inventory > Auction house** (`AuctionUnderbid`): SkyHanni „Auto Copy Underbid Price" und „Copy Underbid Keybind" (`AuctionHouseCopyUnderbidPrice`). Regel: Im Fenster „Create BIN Auction" liegt das Stück
  im Platz 13; sein Preis (niedrigster BIN mal Stapelgröße) minus ein Coin kommt in die Zwischenablage, einmal je Stück; ohne bekannten Preis wird sie geleert. In „Auctions Browser", „Manage Auctions" und
  „Auctions: ..." kopiert eine Taste den Preis der Auktion unter dem Zeiger (Zeile „Buy it now", „Starting bid" oder „Top bid") minus ein Coin. Eigene Zutaten: Die Preise sind die niedrigsten BIN des
  Profit-Trackers (`ItemValue.LOWEST_BIN`, auch für Pets), die Taste ist eine Taste dieser Mod (steigende Flanke), das Fenster kopiert beim erneuten Öffnen mit demselben Stück wieder, eigener Wortlaut.
- **Inventory > Bazaar** (`BazaarHelpers`, `WindowHighlights`, `BazaarSellHud`): SkyHanni „Purchase Helper", „Order Helper", „Best Sell Method" und „Cancelled Buy Order Clipboard" (`BazaarApi`,
  `BazaarOrderApi`, `BazaarOrderHelper`, `BazaarBestSellMethod`, `BazaarCancelledBuyOrderClipboard`). Regeln: Das gesuchte Stück ist in den Listen des Basars (Plätze 9 bis 44) grün; jeder Auftrag in
  „Your Bazaar Orders" heißt „BUY/SELL Name" und trägt Menge, „Filled: x/y", „You have n items to claim!" und „Price per unit: p coins" - „100%!" ist fertig (grün), ein Kaufauftrag unter dem besten
  Kaufauftrag oder ein Verkaufsangebot über dem billigsten Angebot ist überboten (gold); im Fenster eines Produkts (Platz 10 „Buy Instantly", Platz 13 das Stück) ist der Unterschied zwischen Sofortverkauf
  und Angebot (billigstes Angebot minus bester Kaufauftrag) mal die Menge im Inventar zu sehen; im Fenster „Order options" nennt Platz 11 („Cancel Order") die fehlenden Stücke, und nach
  „[Bazaar] Cancelled! Refunded N coins from cancelling Buy Order!" steht eine Zeile mit Klick zum erneuten Bestellen. Eigene Zutaten: Die Markierungen sitzen über den vorhandenen Einsprung vor dem Stück
  (`SlotBackgroundMixin`), die Preise sind die Basarpreise des Profit-Trackers (Spitzen des Auftragsbuchs), welcher Auftrag gemeint ist, merkt sich der Zeiger statt eines Klick-Ereignisses, die Suche
  merkt sich auch /bz und /bazaar, der Klick läuft über `/shoki bzreorder`, der Kasten ist ein Kasten des HUD-Editors, eigener Wortlaut. Nicht übernommen: Hide Order Owner, Hide Trade Partners, Daily Limit
  Tracker, Price Website, Max Items With Purse und Craft Materials - sie standen nicht im Auftrag.
- **Misc** (`NpcDayLimit`, `NpcDayLimitHud`, `WarpCommands`, `FixDoubleClicks`, `DoppelklickMixin`): SkyHanni „NPC Daily Limit Tracker" (`NpcDayLimitTracker`), „Shorten /warp" (`ShortenWarpCommand`),
  „Replace /warp is" (`WarpIsCommand`) und „Fix item double clicks" (`FixDoubleClicks`). Regeln: NPCs nehmen am Tag nur bis 500 Millionen Coins ab (Mitternacht GMT), gezählt aus der Chatzeile
  „You sold … for N Coins!", angezeigt als 400k/500m oder 400,000/500,000,000; ein Befehl, der nur aus einem Warp-Namen besteht, wird zu „/warp Name" (auf der privaten Insel ausgenommen: /jerry), und
  „/warp is" wird zu „/is"; bei einem Rechtsklick auf einen Block mit einem der sechs Blaze-Dolche oder einer Angel wird nur das Block-Paket nicht gesendet. Die Liste der 96 Warp-Namen ist die von SkyHanni
  (Tatsachen über Hypixels Befehle). Eigene Zutaten: der Zähler liegt in der Einstellungsdatei und gehört zum Spieler statt zum Profil, gespeichert wird höchstens einmal je Minute, beim Kürzen wird abgeschnitten
  statt gerundet, der Befehl wird über die Fabric-API (`ALLOW_COMMAND`) ersetzt, das Auslassen des Pakets sitzt in einem nicht erforderlichen Einsprung an `Connection.sendPacket`, eigener Wortlaut. Nicht
  übernommen: das Vervollständigen mit Tab und SkyHannis Ausnahme für /barn im Garten.
- **Misc > Legion/Bobbin Overlay** (`LegionBobbin`, `LegionBobbinHud`): SkyHanni „Legion/Bobbin Overlay" (`LegionBobbinOverlay`, aus dem GUI-Bereich). Regeln: Legion zählt echte Spieler (UUID-Version 4) im Umkreis von
  30 Blöcken ohne einen selbst, höchstens 20, und gibt je Stufe 0,07 Prozent je Spieler; Bobbin' Time zählt Angelhaken im Umkreis von 30 Blöcken, höchstens 5, und gibt je Stufe 0,2 Prozent je Haken; die Stufen
  (`ultimate_legion`, `ultimate_bobbin_time` in den `enchantments` der vier Rüstungsteile) werden addiert, die Zeile steht mit „Hide without enchant" nur mit der Verzauberung an der Rüstung. Die Zahlen
  (30 / 20 / 0,07 und 30 / 5 / 0,2) stehen in SkyHanni-Repo (`Items.json`, `distance_enchant_data`) und sind Tatsachen über das Spiel; sie stehen hier fest im Code. Eigene Zutaten: Spieler und Haken werden alle
  fünf Ticks gezählt, die Rüstung einmal je Sekunde gelesen (SkyHanni: jeden Tick), der Kasten ist ein Kasten des HUD-Editors mit Beispielwerten, eigener Wortlaut. Nicht übernommen: das Nachladen der Zahlen aus
  dem Netz.
- **Inventory > Estimated Item Value** (`EstimatedItemValue`, `EivCalculator`, `EivData`, `EivLive`, `EivFormat`, `EstimatedItemValueHud`): SkyHanni „Estimated Item Value", „Estimated Item Value Tooltip" und „Estimated
  Wardrobe Price" (`EstimatedItemValue`, `EstimatedItemValueCalculator`, `EstimatedItemValueTooltip`, `EstimatedWardrobePrice`). Regeln: Der Wert ist der Grundpreis plus alles Verbaute in einer festen Reihenfolge
  (Umschmiedestein samt Kosten des Anbringens nach Seltenheit, Recombobulator, Art of War/Peace, Etherwarp, Kraftrolle, Singularität, Jalapeno- und Statistik-Buch, Anreicherung, Divan-Beschichtung, Mithril-Infusion,
  Free Will, Crimson-Prestige, Sterne mit Essenz, Coins und Gegenständen samt Kuudra-Stufen, Meistersterne, Hot-Potato- und Fuming-Bücher, Wet Book, Farming for Dummies, Overclocker, Silex, Transmission Tuner,
  Mana Disintegrator, Polarvoid, Bookworm, Pocket Sack-in-a-Sack, Helmskin, Rüstungsfarbe, Rune, Fähigkeits-Schriftrollen, Booster, Bohrer- und Angelteile, Edelstein-Plätze und Edelsteine, Verzauberungen mit den
  Sonderregeln für von sich aus aktive, Abschluss-Stücke, nur Stufe 1 oder 5, Buchpakete und stapelnde); es steht nichts da, wenn nur der Grundpreis bleibt; Preisquellen Sofortkauf, Sofortverkauf und NPC; die Zahlen
  werden abgeschnitten (56.1M, 12k); im Kleiderschrank steht unter dem Knopf eines Satzes jedes der vier Stücke mit Wert und die Summe. Die Tabellen (Umschmiedesteine, Essenz-Kosten, Edelsteinplätze von NEU-REPO;
  Items.json und Enchants.json von SkyHanni-Repo) sind Tatsachen über das Spiel und werden wie die Item-Namen aus dem Netz geholt. Eigene Zutaten: der Rechner läuft gegen eine Schnittstelle und ist ohne Spiel
  geprüft, der Gegenstand unter der Maus wird im Tick gelesen und alle drei Sekunden neu gerechnet, der Kasten ist ein Kasten des HUD-Editors, eigener Wortlaut. Nicht übernommen: der Herstellungspreis als Rückfall
  (der Grundpreis eines Stücks ohne Basar-, Auktions- und NPC-Preis bleibt null) und die Fehlerberichte von SkyHanni.
- **Chat (Teil 1)** (`ChatFilters`, `ChatFilterEngine`, `ChatRule`, `ChatPlace`, `GeneralChatRules`, `DungeonChatRules`, `PowderMiningFilter`, `CrystalNucleusFilter`,
  `StashFilter`, `WatchdogFilter`, `FarDeathMessages`, `WrapperBlocks`): SkyHanni „Chat“ und „Chat Filter Types“ (`ChatFilter`, `PowderMiningChatFilter`, `CrystalNucleusChatFilter`,
  `StashCompact`, `WatchdogHider`, `ArachneChatMessageHider`, `PlayerDeathMessages`, `PlayerDeathManager`, `DungeonChatFilter`, `DungeonBossMessages`, `GiftProfitTracker`, `SackApi`).
  Regeln: welche Zeilen Hypixel schreibt und welcher Schalter sie trifft - Lobby, Empty, Warping, Welcome, Guild/Event EXP, Winter Gifts, Kill Combo, Profile Join, Fire Sale, Reward
  Bundles, Event Level Up, Factory Upgrade, Hoppity, Sacrifice, Garden Pest, Legacy Items, Parkour, Teleport Pads, Feast Chef Ted, Others; die elf Arten der Dungeon Filters und die Reden
  der Dungeon-Bosse; Arachne Hider, Anita's Accessories, Sky Mall, Lottery; Hide Far Deaths (die Zeile „☠ Name …“, wer in den letzten 30 Sekunden nicht im Umkreis von 25 Blöcken stand);
  Sack Change Hider; Powder Mining (die Zeilen vor der Truhe, im Block nur die Belohnungen über der Schwelle, die Liste der unwichtigen, Goblin-Eier nach Farbe, Edelsteine nach Güte, der
  Rest als „+ 537 Gemstone Powder“); Crystal Nucleus (ein gefundener Kristall als Zeile mit „(n/5)“, ein Lauf als „Crystal Nucleus Run complete!“, die Reden der Hüter, von Professor
  Robot, King Yolkar und der Goblin-Wächter); Stash (vier Zeilen zu einer mit Klick, derselbe Stand nur einmal je Welt, Schwelle, „Hide Added“, /viewstash); Watchdog. Die Beispielzeilen
  der Tests sind SkyHannis „REGEX-TEST“-Zeilen ohne Farbcodes. Eigene Zutaten: alle Muster neu für den Klartext der Zeile mit einem Vorfilter, die Entscheidung als reiner Wert
  (`ChatVerdict`) mit austauschbarem Ort (`ChatPlace`), ein gemeinsamer Zustand für die Blöcke zwischen zwei langen Linien (`WrapperBlocks`), in dem die erste Zeile die Art bestimmt (die
  Farbe der Linie fehlt im Klartext), eine Notbremse für Blöcke ohne Ende, die Farbe der kurzen Powder-Zeilen aus dem Namen der Belohnung, Friend Join/Left und Diana aus den Meldungen von
  Hypixel (bei SkyHanni gibt es dafür nur den Schalter, keine Regel), die Liste der Bauteile bei Professor Robot (bei SkyHanni wird sie nie erreicht), „Show all“ bei den Edelsteinen zeigt
  auch die rohen (SkyHanni blendet sie trotzdem aus), eigener Wortlaut. **Nicht übernommen:** das nachträgliche Löschen schon gezeigter Zeilen (die leeren Zeilen um Stash und Watchdog
  bleiben, dafür gibt es „Empty“), die Warnung „Disable Empty Warnings“, das Vervollständigen mit Tab, die Dungeon-Schalter Rare Drops, Solo Class, Solo Stats und Fairy (bei SkyHanni unter
  Dungeon) und alles aus Chat, was nicht ausblendet (Peek Chat, Player Messages, Sound Responses, Rare Drop Messages, Copy Chat, Current Chat Display, Compact Potion/Bestiary/Enchanting/
  Jacob, Translator, SkyBlock XP in Chat, Show Actual Powder Gained, Shorten Coin Amounts, Hide Clickable Hints, Dark Auction Item Display).
- **Chat (Teil 2)** (`ChatRewrites`, `StyledText`, `Incoming`, `CompactPotionMessages`, `CompactBestiary`, `CompactExperimentRewards`, `CompactJacobClaim`, `ShortenCoins`, `ActualPowder`,
  `DarkAuctionItems`, `RareDropMessages`, `SkyblockXpInChat`, `ClickableHints`; die Einstellungen `ChatTabCategory`, `RareDropMessagesCategory`, `CompactPotionCategory`): die Funktionen von
  SkyHanni „Chat“, die eine Zeile umschreiben statt sie auszublenden (`CompactSplashPotionMessage`, `CompactBestiaryChatMessage`, `CompactExperimentRewards`, `CompactJacobClaim`,
  `ShortenCoins`, `DarkAuctionItemDisplay`, `RareDropMessages`, `SkyblockXPInChat`, `ActualGemstonePowderDisplay`, `ActionBarStatsData`, `ChatConfig.hideClickableHint`).
  Regeln: die sechs Tranknachrichten und ihre kurze Zeile „Potion Effect! … by …“ mit Klick auf /effects; der Bestiary-Block (Zeile „BESTIARY“, Stufe, Beschreibung beim Zeigen, Meilenstein
  als eigene Zeile, höchstens 15 Zeilen); die Belohnungen des Experimentation Table (nur in den drei Sekunden nach dem Schließen, 100 Millisekunden nach der letzten Belohnung);
  der Jacob-Block (Tickets, Bücher, Medaillen, Bits, Overclocker; die Bits sind die letzte Zeile, sonst 300 Millisekunden Pause); Zahlen am Anfang eines goldenen Teils als „5.1M“; die
  Rechnung des tatsächlichen Powders (Zuschläge innerhalb einer Gruppe addiert, Gruppen multipliziert; Bohrer, Sky Mall 15 %, Scatha 0,2 % je Stufe); das Stück der Dark Auction hinter
  der Zeile mit dem höchsten Gebot; die Seltenheit des Pets vor dem Namen (fünf Meldungen, „a“ wird zu „an“), der Name des Buchs in „RARE DROP! Enchanted Book“, die Meldung für
  ein Slayer-Buch ohne Zeile und die Liste der Slayer-Bücher; SkyBlock XP aus der Aktionsleiste in den Chat; der Hinweis „Click to …“ fällt weg, Klick und Hover bleiben. Die Beispielzeilen
  der Tests sind SkyHannis „REGEX-TEST“-Zeilen ohne Farbcodes (mit den Farben der Teile, wo die Regel sie braucht). Eigene Zutaten: die Zeile als Folge von Stücken mit Stil (`StyledText`:
  ersetzen, einfügen, anhängen, dabei bleiben Farbe, Klick und Hover je Stück), der Text mit Farbcodes für genau den Abschnitt, den ein Muster getroffen hat, statt Muster mit Codes, die
  Uhr des Chats (`poll`) statt verzögerter Aufgaben, zwei Stufen wie bei SkyHannis „Allow“ und „Modify“ vor und nach den Ausblend-Schaltern, die Bestiary-Zeile zwischen zwei langen Linien
  (die öffnende bleibt stehen, die schließende wird noch einmal geschrieben), die Zeile „RARE DROP! Enchanted Book“ wird bis zu 1,5 Sekunden zurückgehalten, bis das Buch im Inventar ist,
  statt nachträglich geändert zu werden, ein Anspruch des Experimentation Table ohne Belohnung und ein Block ohne Ende gehen nicht verloren, „Hide Clickable Hints“ liegt auf den vier
  Zeilen der Mod mit angehängtem Knopf, eigener Wortlaut. **Nicht übernommen:** das nachträgliche Löschen und Ändern schon gezeigter Zeilen, der HOTM-Zuschlag „Powder Buff“, die
  Attribute (Atomized Crystals und seine Echos) und das Ereignis „2x Powder“ bei „Show Actual Powder Gained“ (ShokiMod liest sie nicht; der Text beim Zeigen sagt es), die „User Luck“ an
  der Meldung für ein fehlendes Buch, und von SkyHannis Hinweisen die Zeile „[CLICK to … or disable this feature]“ unter ihren eigenen Meldungen (bei ShokiMod gibt es nur Knöpfe am
  Ende einer Zeile).
- **Chat (Teil 3)** (`ChatPeek`, `CopyChat`, `CurrentChatDisplay`, `ChatChannelTracker`, `ChatChannel`, `SoundResponseType`, `ChatSoundResponse`, `TabCompleteRules`,
  `CommandSuggestionTree`, `TabCompleteCommands`, `TabCompleteRosters`, `PartyRoster`, `GuildRoster`, `FriendRoster`, `AbiphoneContacts`, `PlayerNames`, `ChatLineReaders`,
  `CurrentChatHud`, zwei Mixins in `paket/mixin`; die Einstellungen `ChatSoundResponseCategory` und `TabCompleteCategory`): die Funktionen von SkyHanni „Chat“, die den Chat
  weder ausblenden noch umschreiben (`ChatPeek`, `CopyChat`, `CurrentChatDisplay`, `ChatSoundResponse`, `SoundResponseTypes`), und die Tab-Vervollständigung von Befehlen aus
  SkyHannis „Commands“ (`TabComplete`, `PlayerTabComplete`, `WarpTabComplete`, `GetFromSacksTabComplete`, `ShowItemTabComplete`, `ViewRecipeCommand`, `AbiphoneFeatures`,
  `StashCompact`, `SuggestionProvider`, `PartyApi`, `GuildApi`, `FriendApi`). Regeln: Peek Chat zeichnet den Chat im Vordergrund-Modus und mit der Höhe des offenen Chats,
  solange die Taste gehalten wird; Copy Chat kopiert mit Rechtsklick die Nachricht, mit Strg nur die Zeile unter dem Zeiger; Current Chat Display liest „You are now in the X
  channel“, die Rückkehr zu ALL und das Gespräch unter vier Augen (fünf Minuten, mit jeder Nachricht neu) und zeigt „Chat: Party (3 members)“; die fünfzehn Tierlaute mit ihren
  Wörtern (als Muster, ganze Wörter, Groß und Klein egal); die Befehle und Unterbefehle von /p, /f, /g, /msg, /visit, /invite, /ah, /pv, /trade, die Quellen der Namen (Insel,
  Freunde, beste Freunde, Party, Gilde), /warp, /call, /gfs (enthält statt beginnt), /show (die Plätze), /viewrecipe (nur mit Rezept) und /viewstash (item, material); Party,
  Gilde und Freunde aus den Meldungen von Hypixel (Beitritt, Austritt, Übergabe, /party list, /guild list, /friend list). Die Beispielzeilen der Tests sind SkyHannis
  „REGEX-TEST“-Zeilen ohne Farbcodes. Eigene Zutaten: die Vorschläge kommen über Client-Befehle von Fabric statt über ein Mixin in Brigadier (ein Befehl, den die Mod nicht
  ausführen darf, lehnt mit „unbekannter Befehl“ ab und geht seinen gewohnten Weg), der Baum der Unterbefehle ist durchlässig für Gruppen mit Bedingung (bei SkyHanni findet
  „/p kick Na“ deshalb nie die Mitglieder), die Zeile unter dem Zeiger bei Copy Chat wird über die Matrix des Chats zurückgerechnet, die Mitglieder der Freundesliste werden
  nur auf einer Seite von „/friend list“ gelesen (nicht in jeder Zeile, die „Friends“ sagt), eigener Wortlaut. **Nicht übernommen:** „Carry Customers“ und „VIP Visits“ (die
  Daten fehlen), SkyHannis Wort-Ersetzungen („Shwords“) bei Umschalt in Copy Chat, die Menü-Taste in Copy Chat, das Nachladen der Abiphone-Kontakte aus dem Netz (SkyHanni lädt sie; hier
  steht eine feste Liste, siehe unten), die Beschränkung von /gfs auf Gegenstände, die in einen Sack passen,
  die Ersetzung von Unterstrichen in /gfs, die Vorschläge für die Menge hinter /gfs, und das Merken des Chats in der Datei des Spielers.
  **Die Liste der /call-Namen** (`AbiphoneContacts`, seit 2.5.10 alle 85 Kontakte mit 143 einwortigen Namen) stammt aus der Kontakt-Tabelle des Community-Wikis
  hypixelskyblock.minecraft.wiki (Seite „Abiphones“, Stand 27.11.2026; das offizielle Hypixel-Wiki ist seit Juli 2026 geschlossen). Übernommen sind nur die Anrufnamen
  (Tatsachen, kein Wikitext); neue Kontakte des Spiels stehen erst nach einer Änderung der Liste darin.
- **Chat (Teil 4)** (`PlayerChatParser`, `PlayerChatFormatter`, `PlayerChatBlacklist`, `TabPlayers`, `TranslatableLanguage`, `GoogleTranslate`, `Translator`,
  `TranslateOnClick`, `TranslateLines`; die Einstellungen `PlayerMessagesCategory` und `TranslatorCategory`): „Player Messages“ und „Translator“ aus SkyHannis „Chat“
  (`PlayerMessagesConfig`, `PlayerChatManager`, `PlayerNameFormatter`, `PlayerChatModifier`, `PlayerChatFilter`, `MultiFilter`, `TranslatorConfig`, `TranslatorCommand`,
  `GoogleTranslator`, `TranslatableLanguage`, `AdvancedPlayerList`). Regeln: die sechs Arten von Spielerzeilen (Chat an alle, Co-op, Party, Gilde, private Nachricht, „/show“)
  mit ihren Mustern und ihren „REGEX-TEST“-Beispielen (die Tests lesen sie ohne Farbcodes und mit Farbcodes), die Teile des Namens (Stufe, Emblem, Name samt Rang,
  Gildenrang, Rang und Gast-Hinweis der Privatinsel, Ironman und Fraktion aus der Tab-Liste) in der Reihenfolge der Einstellung, die Stufe mit grauen Klammern, der Name
  in der Farbe der Stufe oder ohne Rang (ADMIN bleibt, YOUTUBE auf Wunsch), die private Nachricht ohne Rang, der Rang-Verberger auch für Zeilen, die kein Spielerchat
  sind, der Abgleich der Sperrliste (gleich, beginnt, endet, enthält, enthält als Wort), die Liste der Sprachen mit ihren Codes und der Schreibweise in der Auswahl, die
  Adresse von Googles Übersetzer und die Form seiner Antwort. Eigene Zutaten: die Zeile wird Stück für Stück neu gebaut (Klick und Text beim Zeigen der Nachricht bleiben),
  die Farbcodes von SkyHannis Mustern werden als Farbe des Zeichens gelesen (gleich, ob Hypixel sie als Code oder als Stil schreibt), der Klick nennt eine Nummer statt des
  Textes (die Mod merkt die letzten hundert), das Textfeld „Language Code“ ist von Haus aus leer und gewinnt, wenn es gefüllt ist (so brauchen Feld und Auswahl keinen
  Abgleich), eine Nachricht von der Sperrliste bleibt auch mit „Same Chat Color“ grau, eigener Wortlaut. **Netzwerkanfrage:** Der Translator schickt bei einem Klick auf
  eine Spielerzeile (Schalter „Translate On Click“, von Haus aus aus) oder bei `/shoki translate <Sprachcode> <Text>` genau EINE Anfrage (HTTPS, GET) an
  `translate.googleapis.com`, ohne Schlüssel, in einem eigenen Thread mit zehn Sekunden Zeitlimit. Raus gehen der Text der einen angeklickten Zeile (nur die Nachricht,
  ohne den Namen davor) oder der Text des Befehls, die zwei Sprachcodes (Quelle „auto“, Ziel) und was jede Anfrage im Netz verrät (die Adresse des Rechners, der Kopf
  `User-Agent: ShokiMod`). Ohne Klick oder Befehl geht nichts raus, und die Tests machen keinen Netzzugriff. **Nicht übernommen:** der Teil „Bingo Level“ der Reihenfolge
  (SkyHanni liest die Zeichen und Ränge aus einer Datei seines Repos, die nicht im Quelltext liegt), SkyHannis Liste der Sperrwörter (sie liegt ebenfalls in seinem Repo;
  die Mod hat eine kleine eigene), die drei Muster des Rang-Verbergers für Namen ohne Rang in Zeilen, die kein Spielerchat sind, der Abgleich der Sprachauswahl mit dem
  Textfeld durch Rückrufe.
- **Fishing-Zeilen, Auction-house-Markierungen und Action Bar** (`SeaCreatureLines`, `AuctionHighlights`, die freien Farben in `WindowHighlights`,
  `SkillProgress.stripSkillGain`; die Einstellungen in `FishingCategory`, `AuctionHouseHelpersCategory` und `SkillProgressCategory`): drei kleine Funktionen aus SkyHanni
  (`SeaCreatureManager`, `AuctionsHighlighter`, `SkillProgress`, `SkillApi`). Regeln: „Compact Double Hook“ blendet die Zeile „It's a Double Hook! (Woot woot!)“ aus und
  merkt den Zeitpunkt; die nächste Fang-Zeile eines Meeresbewohners innerhalb von 100 Millisekunden bekommt „DOUBLE HOOK! “ in Fettgelb davor (Left) oder dahinter
  (Right), der Zeitpunkt wird nach einer Fang-Zeile vergessen, nicht bei einer anderen Zeile; „Shorten Fishing Message“ macht daraus „You caught a/an <Name>!“ in Blau
  mit dem Namen in der Farbe des Meeresbewohners; „Highlight Auctions“ färbt in „Manage Auctions“ die Einträge mit „Status: Sold!“ und „Status: Expired!“ ein, „Highlight
  Underbid Auctions“ die mit „Buy it now: N coins“, wenn N größer ist als der geschätzte Wert des Stücks (Summe von Estimated Item Value; bei gleichem Wert oder ohne
  Wert nicht); „Hide in action bar“ schneidet den Zugewinn „+881.8 Fishing (99.61%)“ samt den Leerzeichen davor aus der Aktionsleiste und trimmt den Rest. Eigene
  Zutaten: die Fang-Zeilen stehen schon in der Tabelle von Feesh (`SeaCreatures`), die Farbe des Namens ist deren Seltenheitsfarbe, die Aktionsleiste wird aus
  `MODIFY_GAME` gelesen und geändert (ALLOW_GAME lässt sie nur durch), damit jeder Leser das Original genau einmal sieht, der Zugewinn wird im Klartext erkannt und samt seinen
  Farbcodes entfernt, die Markierungen im Auktionshaus tragen freie Farben aus dem Farbwähler, eigener Wortlaut. **Abweichungen:** „Highlight Underbid Auctions“ gilt
  auch ohne „Highlight Auctions“ (bei SkyHanni liegt es darin), der geschätzte Wert wird mit der Stapelgröße malgenommen, ein Name mit „The“ davor bekommt keinen
  Artikel, „Hide in action bar“ wirkt nur, wenn der Zugewinn auch im Kasten gelandet ist, und alle Schalter außer „Hide in action bar“ sind von Haus aus aus (SkyHanni:
  Highlight Auctions und Compact Double Hook an, Hide In Action Bar aus).
- **Garden > Visitor Shopping List und Visitor Reward Warning** (`VisitorWindow`, `VisitorBook`, `VisitorOffer`, `VisitorDecision`, `VisitorReward`, `VisitorTab`,
  `ShoppingListText`, `VisitorPrices`, `VisitorShoppingHud`, `VisitorRewardNames`; die Einstellungen in `GardenShoppingListCategory` und `GardenRewardWarningCategory`): zwei
  Funktionen aus SkyHanni (`GardenVisitorShoppingList`, `VisitorRewardWarning`, `VisitorApi`, `GardenVisitorTooltip`, `VisitorPriceCalculator`, `VisitorReward`). Regeln: Die
  Namen der Besucher stehen im Widget „Visitors: (n)“ der Tab-Liste; das Fenster eines Besuchers erkennt man am Schild auf Platz 13 (vier Zeilen, die letzte „Offers Accepted: N“)
  und am Knopf „Accept Offer“ auf Platz 29, „Refuse Offer“ liegt auf Platz 33; die Beschreibung nennt unter „Items Required:“ die verlangten Gegenstände („Enchanted Carrot x3“, auch
  „8x Name“), nach einer Leerzeile unter „Rewards:“ „+20 Copper“, „+15 Garden Experience“ und Gegenstände, und „Click to give!“, sobald alles da ist. Die Liste summiert die
  verlangten Gegenstände aller Besucher, die weder angenommen noch abgelehnt sind, mit Menge und Preis; Besucher, deren Fenster noch nicht offen war, stehen als „New Visitors“
  mit Namen darunter, ein Klick auf einen Namen nimmt ihn (durchgestrichen) aus der Summe; sie steht im Garden (mit „Only when Close“ nur auf dem Plot der Scheune), in der Bazaar
  Alley und - mit „Farming Areas“ - auf den Farming Islands und auf dem Hof im Hub. Ein Copper ist so viel wert wie Green Thumb I durch 1500, der Preis eines Angebots ist der der
  verlangten Gegenstände abzüglich der Belohnungen (nie unter null). Die Sperre kennt sechs Gründe in fester Reihenfolge (gewählte Belohnung, nie bedienter Besucher, billiges
  Copper, teures Copper, kleiner Verlust, hoher Verlust), sperrt das Ablehnen oder das Annehmen und gilt nicht, solange die Umgehungstaste (Strg links) gehalten wird; die
  Zahlen (6.000 Coins je Copper, 150.000 Coins Verlust, Transparenz 180), die Liste der 32 Belohnungen samt der Vorauswahl von zehn, und die Chatzeile „Found Visitor Reward X!“
  beim ersten Öffnen. Eigene Zutaten: Java statt Kotlin; die Liste ist ein Kasten des HUD-Editors (Klick auf einen Gegenstand sucht ihn im Basar); jede Belohnung hat einen
  eigenen Schalter in einem Akkordeon statt einer ziehbaren Liste; die Klicks im Fenster kommen aus `ScreenMouseEvents.allowMouseClick` und die Tasten (Hotbar, Werfen, Tauschen,
  die ein Klick auf den Platz wären) aus `ScreenKeyboardEvents.allowKeyPress`, gezeichnet wird nach dem Fenster (`ScreenEvents.afterExtract`, mit zwei Zugriffen auf Lage und
  Platz unter dem Zeiger); das Fenster wird alle fünf Ticks gelesen; ein Besucher ist über seinen Namen geführt, nicht über die angeklickte Entität; die Preisregeln gelten nur,
  wenn alle verlangten Gegenstände und Green Thumb I einen Preis haben; der gesperrte Knopf nennt in seiner Beschreibung den Grund und die Umgehungstaste; „Show over Name“ ist ein
  Text auf dem Bildschirm über dem Ruestungsstaender des Besuchers. **Abweichungen:** alle Schalter außer den Unteroptionen sind von Haus aus aus (SkyHanni: Enable, Notify in Chat,
  Show over Name, Block Refusing Reward und Block Refusing New Visitors an); ein Bingo-Profil erkennt die Mod nicht, „Block Refusing New Visitors“ gilt dort auch; was aus der Summe
  genommen wurde, gilt nur bis zum Neustart. **Nicht übernommen:** Show Sack Count, Show Super Craft und Item Preview (die Mod kennt weder die Mengen im Sack noch Rezepte noch
  das Verzeichnis der Besucher mit ihren Wünschen), die Menge in ein Schild einsetzen, die Zeilen im Tooltip („Visitor Inventory“), Accept Hotkey, das Einfärben der Besucher.

---

### IQ (Apache-2.0, PeHenrii und DarkJota, `iqaddons/IQ`)

Aus IQ, einer Kuudra-Mod fuer Hypixel SkyBlock, kommen die Funktionen im Reiter **IQ**. Sie sind in Java nachgebaut; die Regeln, Zahlen und der
Wortlaut stammen aus IQs Quelltext (Zweig `ver/26.1.x`), und was anders ist, steht dabei. Die Entwickler sind PeHenrii (Lead) und DarkJota.

**Zur Lizenz:** IQ ist nicht eindeutig ausgezeichnet. Die Datei `LICENSE` im Repository und die in IQs Jar mitgelieferte `LICENSE_IQ` sind der
Text der **Apache License 2.0**, und GitHub erkennt sie so; `fabric.mod.json` und das README nennen dagegen "MIT". Befolgt wird die strengere, die Apache
License 2.0 - was sie verlangt, erfuellt auch die MIT-Bedingung. Verlangt (Abschnitt 4): den Lizenztext mitzugeben (unten, und diese Datei liegt im
Jar), geaenderte Dateien als geaendert zu kennzeichnen (alle Dateien unten sind neu geschrieben und tragen den Hinweis auf IQ im Kopf), die Hinweise
des Originals zu behalten (die Quelltextdateien tragen keinen Urheberhinweis; die Entwickler stehen hier und im Kopf jeder Datei) und eine
NOTICE-Datei zu uebernehmen, falls es eine gibt (im IQ-Repository gibt es keine). Die Namen "IQ", PeHenrii und DarkJota stehen nur als
Herkunftsangabe da (Abschnitt 6).

- **Fire Veil Overlay** (`FireVeilOverlayFeature`, `FireVeilOverlayManager`, `FireVeilOverlayWidget`, `PhaseTwoConfig.FireVeilOverlayConfig`,
  `WorldRenderUtils.drawThickCircleOutline` und `drawCircleWireframeWall`) — Version 1.18.5. Nach einem Rechtsklick mit dem Fire Veil Wand (Item
  `FIRE_VEIL_WAND`, Rueckfall: der Name enthaelt "fire veil") zeichnet die Mod fuenf Sekunden lang den Wirkungskreis um die Spielerfigur: ein dicker
  Ring (Radius 3,5 Bloecke, Dicke 0,08, 60 Segmente) oder eine Wand (drei Ringe auf Boden, halber und voller Hoehe von einem Block, dazu senkrechte
  Striche), neun Sekunden lang werden die Flammen des Spiels ausgeblendet, ein Kasten zaehlt die Zeit herunter ("Fire Veil: 4.83s", Farbstufen
  gruen, gelb, orange, rot; danach zwei Sekunden "READY"), und beim Wiederwerfen kommt ein Ton (Notenblock-Pling, Lautstaerke 2, Tonhoehe 1,6).
  Einstellungen wie bei IQ: Render (Default / Wall / Circle, Standard Circle), Farbe (Standard orange, 255/170/0), Deckkraft (IQ: 200 von 255), durch
  Waende, Countdown, Ton. Nachgebaut als `FireVeil`, `FireVeilRenderer`, `FireVeilHud` und die Klappe "Fire Veil Overlay" im Reiter IQ.
  Anders: Der Hauptschalter steht standardmaessig **aus** (bei IQ an); die Deckkraft ist ein eigener Regler in Prozent (Standard 78), weil der
  Farbwaehler hier keine Deckkraft kennt; gezeichnet wird mit Minecrafts Gizmos statt mit IQs eigenen Zeichenschichten; der Wurf kommt aus dem
  Rechtsklick-Ereignis von Fabric; ausgeblendet werden die Flammen, die der Server schickt (das Partikel-Paket), nicht jede Flamme im Spiel - Fackeln und
  Feuer in der Welt bleiben; der Kasten ist ein Kasten des HUD-Editors (`/shoki hud`) mit Lage, Groesse und Deckkraft.

Der Lizenztext von IQ (Quelle: `LICENSE` im IQ-Repository, gleichlautend mit `LICENSE_IQ` in IQs Jar; die Vorlage am Ende ist von der Lizenz selbst
und nicht ausgefuellt, ein eigener Urheberhinweis steht dort nicht):

    Apache License
                               Version 2.0, January 2004
                            http://www.apache.org/licenses/

       TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

       1. Definitions.

          "License" shall mean the terms and conditions for use, reproduction,
          and distribution as defined by Sections 1 through 9 of this document.

          "Licensor" shall mean the copyright owner or entity authorized by
          the copyright owner that is granting the License.

          "Legal Entity" shall mean the union of the acting entity and all
          other entities that control, are controlled by, or are under common
          control with that entity. For the purposes of this definition,
          "control" means (i) the power, direct or indirect, to cause the
          direction or management of such entity, whether by contract or
          otherwise, or (ii) ownership of fifty percent (50%) or more of the
          outstanding shares, or (iii) beneficial ownership of such entity.

          "You" (or "Your") shall mean an individual or Legal Entity
          exercising permissions granted by this License.

          "Source" form shall mean the preferred form for making modifications,
          including but not limited to software source code, documentation
          source, and configuration files.

          "Object" form shall mean any form resulting from mechanical
          transformation or translation of a Source form, including but
          not limited to compiled object code, generated documentation,
          and conversions to other media types.

          "Work" shall mean the work of authorship, whether in Source or
          Object form, made available under the License, as indicated by a
          copyright notice that is included in or attached to the work
          (an example is provided in the Appendix below).

          "Derivative Works" shall mean any work, whether in Source or Object
          form, that is based on (or derived from) the Work and for which the
          editorial revisions, annotations, elaborations, or other modifications
          represent, as a whole, an original work of authorship. For the purposes
          of this License, Derivative Works shall not include works that remain
          separable from, or merely link (or bind by name) to the interfaces of,
          the Work and Derivative Works thereof.

          "Contribution" shall mean any work of authorship, including
          the original version of the Work and any modifications or additions
          to that Work or Derivative Works thereof, that is intentionally
          submitted to Licensor for inclusion in the Work by the copyright owner
          or by an individual or Legal Entity authorized to submit on behalf of
          the copyright owner. For the purposes of this definition, "submitted"
          means any form of electronic, verbal, or written communication sent
          to the Licensor or its representatives, including but not limited to
          communication on electronic mailing lists, source code control systems,
          and issue tracking systems that are managed by, or on behalf of, the
          Licensor for the purpose of discussing and improving the Work, but
          excluding communication that is conspicuously marked or otherwise
          designated in writing by the copyright owner as "Not a Contribution."

          "Contributor" shall mean Licensor and any individual or Legal Entity
          on behalf of whom a Contribution has been received by Licensor and
          subsequently incorporated within the Work.

       2. Grant of Copyright License. Subject to the terms and conditions of
          this License, each Contributor hereby grants to You a perpetual,
          worldwide, non-exclusive, no-charge, royalty-free, irrevocable
          copyright license to reproduce, prepare Derivative Works of,
          publicly display, publicly perform, sublicense, and distribute the
          Work and such Derivative Works in Source or Object form.

       3. Grant of Patent License. Subject to the terms and conditions of
          this License, each Contributor hereby grants to You a perpetual,
          worldwide, non-exclusive, no-charge, royalty-free, irrevocable
          (except as stated in this section) patent license to make, have made,
          use, offer to sell, sell, import, and otherwise transfer the Work,
          where such license applies only to those patent claims licensable
          by such Contributor that are necessarily infringed by their
          Contribution(s) alone or by combination of their Contribution(s)
          with the Work to which such Contribution(s) was submitted. If You
          institute patent litigation against any entity (including a
          cross-claim or counterclaim in a lawsuit) alleging that the Work
          or a Contribution incorporated within the Work constitutes direct
          or contributory patent infringement, then any patent licenses
          granted to You under this License for that Work shall terminate
          as of the date such litigation is filed.

       4. Redistribution. You may reproduce and distribute copies of the
          Work or Derivative Works thereof in any medium, with or without
          modifications, and in Source or Object form, provided that You
          meet the following conditions:

          (a) You must give any other recipients of the Work or
              Derivative Works a copy of this License; and

          (b) You must cause any modified files to carry prominent notices
              stating that You changed the files; and

          (c) You must retain, in the Source form of any Derivative Works
              that You distribute, all copyright, patent, trademark, and
              attribution notices from the Source form of the Work,
              excluding those notices that do not pertain to any part of
              the Derivative Works; and

          (d) If the Work includes a "NOTICE" text file as part of its
              distribution, then any Derivative Works that You distribute must
              include a readable copy of the attribution notices contained
              within such NOTICE file, excluding those notices that do not
              pertain to any part of the Derivative Works, in at least one
              of the following places: within a NOTICE text file distributed
              as part of the Derivative Works; within the Source form or
              documentation, if provided along with the Derivative Works; or,
              within a display generated by the Derivative Works, if and
              wherever such third-party notices normally appear. The contents
              of the NOTICE file are for informational purposes only and
              do not modify the License. You may add Your own attribution
              notices within Derivative Works that You distribute, alongside
              or as an addendum to the NOTICE text from the Work, provided
              that such additional attribution notices cannot be construed
              as modifying the License.

          You may add Your own copyright statement to Your modifications and
          may provide additional or different license terms and conditions
          for use, reproduction, or distribution of Your modifications, or
          for any such Derivative Works as a whole, provided Your use,
          reproduction, and distribution of the Work otherwise complies with
          the conditions stated in this License.

       5. Submission of Contributions. Unless You explicitly state otherwise,
          any Contribution intentionally submitted for inclusion in the Work
          by You to the Licensor shall be under the terms and conditions of
          this License, without any additional terms or conditions.
          Notwithstanding the above, nothing herein shall supersede or modify
          the terms of any separate license agreement you may have executed
          with Licensor regarding such Contributions.

       6. Trademarks. This License does not grant permission to use the trade
          names, trademarks, service marks, or product names of the Licensor,
          except as required for reasonable and customary use in describing the
          origin of the Work and reproducing the content of the NOTICE file.

       7. Disclaimer of Warranty. Unless required by applicable law or
          agreed to in writing, Licensor provides the Work (and each
          Contributor provides its Contributions) on an "AS IS" BASIS,
          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
          implied, including, without limitation, any warranties or conditions
          of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
          PARTICULAR PURPOSE. You are solely responsible for determining the
          appropriateness of using or redistributing the Work and assume any
          risks associated with Your exercise of permissions under this License.

       8. Limitation of Liability. In no event and under no legal theory,
          whether in tort (including negligence), contract, or otherwise,
          unless required by applicable law (such as deliberate and grossly
          negligent acts) or agreed to in writing, shall any Contributor be
          liable to You for damages, including any direct, indirect, special,
          incidental, or consequential damages of any character arising as a
          result of this License or out of the use or inability to use the
          Work (including but not limited to damages for loss of goodwill,
          work stoppage, computer failure or malfunction, or any and all
          other commercial damages or losses), even if such Contributor
          has been advised of the possibility of such damages.

       9. Accepting Warranty or Additional Liability. While redistributing
          the Work or Derivative Works thereof, You may choose to offer,
          and charge a fee for, acceptance of support, warranty, indemnity,
          or other liability obligations and/or rights consistent with this
          License. However, in accepting such obligations, You may act only
          on Your own behalf and on Your sole responsibility, not on behalf
          of any other Contributor, and only if You agree to indemnify,
          defend, and hold each Contributor harmless for any liability
          incurred by, or claims asserted against, such Contributor by reason
          of your accepting any such warranty or additional liability.

       END OF TERMS AND CONDITIONS

       APPENDIX: How to apply the Apache License to your work.

          To apply the Apache License to your work, attach the following
          boilerplate notice, with the fields enclosed by brackets "[]"
          replaced with your own identifying information. (Don't include
          the brackets!)  The text should be enclosed in the appropriate
          comment syntax for the file format. We also recommend that a
          file or class name and description of purpose be included on the
          same "printed page" as the copyright notice for easier
          identification within third-party archives.

       Copyright [yyyy] [name of copyright owner]

       Licensed under the Apache License, Version 2.0 (the "License");
       you may not use this file except in compliance with the License.
       You may obtain a copy of the License at

           http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing, software
       distributed under the License is distributed on an "AS IS" BASIS,
       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
       See the License for the specific language governing permissions and
       limitations under the License.

## Nur nachgeschlagen — kein Code

Diese Mods stehen hier, weil gegen sie geprüft wurde, nicht weil etwas von ihnen
drinsteckt.

- **SkyHanni** (LGPL-2.1, `SkyHanniStudios/SkyHanni`) — die Schadenszahl über einem getroffenen Mob (`HideDamageSplash`,
  `DamageIndicatorManager.isDamageSplash`, `damagePattern`), Version 1.18.9: ein Rüstungsständer mit Namen, höchstens 300 Ticks alt, dessen Name ohne
  Kommas nur aus einer Zahl mit ein paar Zeichen davor und dahinter besteht (✧ ✯ ⚔ + ❤ ♞ ☄ ✷ ﬗ). Das ist eine Beobachtung darüber, wie Hypixel diese Namen
  schickt, und kein Code: Das Muster ist hier selbst hingeschrieben (`FrozenBlaze.hidesDamage`), nur die Zeichen sind dieselben. Anders: SkyHanni blendet die
  Zahlen immer aus, wenn man es einschaltet; hier nur, solange man das ganze Frozen-Blaze-Set trägt (Fishing > Frozen Blaze > Hide damage numbers).
  *Korrektur:* An dieser Stelle stand bisher "AGPL-3.0". Die `LICENSE` im Repository ist die GNU Lesser General Public License v2.1 (am 24.10.2026 gelesen).
- **SkyHanni** (LGPL-2.1) — das Vorgehen, ein Pet zu erkennen (Version 1.19.5, `PetStorageApi`, `CurrentPetApi`): Das
  Pet-Item im Menü trägt `petInfo` mit Kennung, Erfahrung, Item und Skin; die Autopet-Zeile nennt das Item im
  Mauszeiger-Text ("Held Item: ..."); gleich nach einem Wechsel gilt die Tab-Liste fünf Sekunden lang weniger als Chat und Menü,
  weil sie hinterherhinkt; eine Zuordnung zählt nur, wenn genau ein gemerktes Pet passt (Name, Seltenheit, Skin, Item).
  Hier ist es eigener Code (`PetIdentity`, `PetState`): der Schlüssel besteht aus Name, Skin-Zeichen und Item, weil Tab-Liste,
  Chatzeile und Menü diese drei alle nennen, die Kennung aber nur das Menü. Die Kennung wird erst ins Protokoll geschrieben (`shokimod-pet.log`).
- **SkyHanni** (LGPL-2.1) — dagegen gegengeprüft wurden: die Sky-Mall-Platzierung, die
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
  eigener Code. Dagegen gehalten wurde außerdem das **Aussehen** seiner Wegweiser: Die
  Führungslinie beginnt zwei Blöcke vor der Kamera — am Fadenkreuz, nicht an den Füßen —
  und das Ziel wird gefüllt statt nur umrissen, mit einer Deckkraft, die mit der
  Entfernung wächst (`WorldRenderUtils.drawLineToCrosshair`, `drawWaypointFilled`).
  Abgelesen, nicht übernommen: Die Umsetzung hier nutzt die Gizmos der Spielversion,
  und die Steigung der Deckkraft ist flacher, weil eine Ader mehr als einen Block füllt.
  Die AGPL wäre mit der LGPL **nicht** vereinbar, deshalb ist hier bewusst nie Code
  übernommen worden, nur Tatsachen abgelesen. Auch der eigene Slayer-Boss ist so entstanden:
  Aus `Mob`, `MobFilter` und `MobUtils` stammt, dass Hypixel den Mob und seine Namensschilder
  nacheinander anlegt und der Besitzer in der Zeile „Spawned by: <Name>“ steht, zwei Kennungen
  hinter dem ersten Namensschild. Hier wird bis sechs Kennungen zurück gesucht, weil nicht geprüft
  ist, ob es immer genau zwei sind, und dazu in der Nähe; gewählt wird nach Art, Kennung, Sichtbarkeit
  und Nähe. Kein Code übernommen. Dazu stammt aus
  `SlayerApi` die Zeile „Slay the boss!“ in der Seitenleiste als Zeichen, dass der eigene Boss da
  ist; sie entscheidet hier auch, wem ein Boss gehört, der keine Zeile „Spawned by“ trägt (so auf
  Bildern von Hypixel gesehen). Dass aus NPC-Spielern gebaute Mobs (keine Kennung der Version 4)
  zählen, stammt aus `MobFilter.isSkyBlockMob`. Partikel nutzt SkyHanni dafür nicht, nur um die Spawn-Effekte auszublenden
  (`HideSlayerSpawnParticles`); hier gilt allein die Seitenleiste. Die Namen der Slayer-Minibosse
  (`SlayerMiniBossType`) stammen ebenfalls von dort und stimmen mit denen in Skyblockers
  `SlayerType` überein; Erkennung und Umriss sind eigener Code. SkyHanni zeichnet außerdem eine Linie
  zum Miniboss (`SlayerMiniBossFeatures`); die Idee ist dieselbe, die Umsetzung (nur die nächste Linie
  je Art, über die Linien der Mob-Markierung) ist eigen. Dass ein als unsichtbar gekennzeichneter Mob
  trotzdem der Boss sein kann, stammt aus `Mob.isInvisible` (Zombies gelten dort immer als sichtbar,
  und angelegt wird der Mob ohnehin ohne diese Prüfung); Skyblocker (`BossFight`) sucht den Boss nach
  Art (Zombie, Spinne, Wolf, Enderman, Blaze) und ohne Sichtbarkeitsprüfung. Beides nur abgelesen;
  die Zuordnung der Schilder zu einer Art (`SlayerMobKind`) und die Bewertung sind eigener Code.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — dort wird ein Pet, das die Hoechststufe
  erreicht, in den Tracker gelegt: Die Zeile "Your … leveled up to level 100!" wird
  gelesen, und nur 100 und 200 zaehlen. Auch die Schreibweise der Kennung ist von dort
  uebernommen (`FLYING_FISH;4+100`) — zwei Mods, die dieselbe Ware gleich benennen,
  ersparen dem naechsten Leser eine Uebersetzung. Der Preis kommt hier aus der eigenen
  Auktions-Auswertung, nicht aus einer fremden Schnittstelle.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Einblendung, wenn ein Pet seine
  Höchststufe erreicht (`features/alerts/PetLevelUpAlert.kt`): ein Titel „X is maxed"
  mit der Stufe darunter, ein Ton, und wahlweise eine Zeile dazu, was das Hochziehen
  wert war. Nachgebaut, nicht übernommen — und mit einem Unterschied: Feesh meldet bei
  Stufe 100 und 200 gleichermaßen, hier zählt die Höchststufe des jeweiligen Pets. Ein
  Golden Dragon auf 100 ist nicht fertig, sondern halb fertig, und „is maxed" wäre dort
  falsch. Die beiden Preise kommen aus der eigenen Auktions-Auswertung statt aus zwei
  Einzelabfragen.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Form der Pet-Beutezeile
  (`events/publishers/RareDropsPublisher.kt`): "PET DROP!", davor optional die Seltenheit als
  Wort. Abgelesen als Zeilenformat, die Umsetzung (Kennung `NAME;Stufe`, Rettung aus dem Chat)
  ist eigen.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — der Alarm bei seltenen Meeresbewohnern
  (`features/alerts/RareCatchAlert.kt` und `constants/SeaCreatures.kt`). Hier ist zum
  ersten Mal nicht nur die Idee übernommen, sondern **Daten**: die Tabelle der neunzig
  Meeresbewohner mit dem Satz, mit dem Hypixel jeden Fang meldet, dazu Seltenheit und
  die Angabe, welche als selten gelten (26 von 90). Diese Sammlung *ist* die Arbeit —
  ohne sie erkennt eine Mod gar nichts, und sie Zeile für Zeile neu abzuschreiben
  brächte niemandem etwas. Übernommen sind außerdem die Muster, mit denen Feesh und
  SkyHanni ihre Funde in den Gruppenchat schreiben, damit der Alarm auch dann greift,
  wenn die anderen in der Gruppe eine andere Mod benutzen. Die Einblendung selbst ist
  die dieser Mod: dasselbe Banner, derselbe Ton und dieselbe Lautstärkeregel wie bei
  einem seltenen Fund.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — dort steht auch, dass George für einen
  Baby Yeti eine Million zahlt (`npcPrice = 1_000_000.0`). Nachgebaut ist das anders:
  Feesh trägt die Zahl je Fund von Hand ein und nimmt sie nur, wenn Basar und Auktionshaus
  nichts hergeben; hier kommt die ganze Liste aus dem NEU-Repo und gilt als Untergrenze,
  nicht als Rückfall — steht im Auktionshaus weniger, zählt trotzdem George.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — dort wurde nachgesehen, woher ein
  Essence-Zugang überhaupt zu erfahren ist. Feesh liest ihn für Ice Essence aus der
  Action Bar (`+100 Ice Essence`) und behilft sich gegen das Segment, das mehrere
  Sekunden stehen bleibt, mit der zuletzt gesehenen Zahl. Dass Hypixel den Zugang dort
  ankündigt, ist eine Tatsache über Hypixel; der Umgang mit dem Dauer-Segment ist
  derselbe Gedanke, hier je Sorte statt für eine. Der Stand aus dem Essence-Widget der
  Tab-Liste hat hier Vorrang, weil er nichts verlieren kann — Feesh liest ihn nicht.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Zeilen zu Deployables
  (`features/overlays/DeployablesTimer.kt`): „Your flare disappeared because you were too far
  away!" und „Your previous … was removed!". Das sind Texte von Hypixel, kein Code. Die Meldung,
  wenn ein Deployable abläuft (`DeployableReminder`), liest daneben „Your … despawned." Die
  Warnung davor (`DeployableCountdown`, seit 1.15.19) geht den Weg, den SkyHanni (`Deployable.kt`),
  Riccio's Fishing Utils und SkyCubed gehen: die Restzeit aus dem Namen des Rüstungsständers über
  dem Deployable, „Umberella 298s". Sie gilt deshalb für jedes Deployable in Reichweite, auch ein
  fremdes — der Ständer sagt nicht, wem es gehört.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die HP-Anzeige der Meeresbewohner
  (`features/overlays/SeaCreatureHpTracker.kt`, Fassung 1.13.0, und das Lesen der Namensschilder in
  `utils/EntityUtils.kt`): die Namensschilder der Rüstungsständer im Umkreis von 30 Blöcken, der
  Reindrake auch weiter weg, die Zeilen nach niedrigsten Lebenspunkten, die Liste der Bewohner mit
  der Voreinstellung (achtundzwanzig), die Immune-Anzeige für die ersten fünf Sekunden und Nessies
  Flucht, der Ton beim Neuzugang (nicht beim Wiederkehren des Reindrake). Nachgebaut, nicht
  übernommen — und mit vier Unterschieden: Die Symbole links vom Namen und das Herz rechts fehlen,
  die Reihenfolge lässt sich auch nach Seltenheit wählen, die Namen stehen in der Farbe ihrer
  Seltenheit (abschaltbar; die Farben der Lebenspunkte bleiben die des Namensschilds), und die Auswahl
  der Bewohner steht im selben Fenster wie die des Alarms (`/shoki seacreatures`, Spalte HP) statt in
  einer eigenen Liste.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — der Banner bei seltenen Fängen
  (`utils/CommonUtils.kt` `showTitle`, `constants/SeaCreatures.kt` `getTitle`): Feesh zeigt den Fang als
  Titel des Spiels — der Name fett in der Farbe der Seltenheit, bei einem Double Hook ein rotes „X2",
  bei einem Mythic ein flackerndes goldenes „x" davor und dahinter, darunter der Name des Fängers; zwei
  Sekunden stehen, eine halbe Sekunde ausblenden. Die Voreinstellung „Feesh" (Hintergrund „Like the game's
  title") zeichnet es mit demselben Aufruf wie das Spiel selbst und stimmt Pixel für Pixel mit dessen
  Titel überein. Übernommen ist die Gestalt und der Aufbau der Zeilen, kein Code.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Markierung der Bücher, die man loswerden will
  (`features/items/background/TrashBooksHighlighter.kt`, `BackgroundHighlighterManager.kt`,
  `ItemUtils.getEnchantedBookName`): Ein Slot mit einem „Enchanted Book" bekommt einen roten Untergrund
  (`0x90FF0000`), den Namen des Buches liest Feesh aus der ersten Beschreibungszeile ohne „Combinable in
  Anvil", gezeichnet wird in `extractSlot` vor dem Aufruf, der das Stück zeichnet (`SlotBackgroundMixin`).
  Nachgebaut — mit vier Unterschieden: Die Bücher stehen in einem Fenster (die bisher im Inventar gesehenen
  und ein Feld für einen neuen Namen) statt in einem Textfeld mit Komma, und jedes hat drei Zustände —
  neutral, rot, grün —, wobei ein neu gefundenes immer neutral beginnt (Feesh hat nur Rot und „Corruption I"
  von Haus aus); gemerkt wird nur, was im eigenen Inventar liegt, und gefärbt wird nur dort, nicht in jedem
  Fenster.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die beiden Stummschaltungen
  (`features/sounds/MuteJadeDragonSound.kt`, `MuteReindrakeGifts.kt`, `mixin/SoundEngineMixin.java`):
  Der Mixin in `SoundEngine.play` verschluckt alle Töne `entity.ender_dragon.*` im Moonglade Marsh in den
  Zonen „Dragon's Lair" und „Murkwater Depths" und den Ton `item.totem.use` mit der Lautstärke 0,5 in Jerry's
  Workshop. Nachgebaut — mit drei Unterschieden: Der Mixin meldet „nicht gestartet" statt nichts
  zurückzugeben, die Zone wird einmal in der Sekunde aus der Seitenleiste gelesen statt bei jedem Ton, und
  beide Schalter stehen unter Fishing > Mute.
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — die Warnung vor dem Ertrinken
  (`features/alerts/OutOfAirAlert.kt`): Einmal in der Sekunde die Luft des Spielers im Verhältnis zum vollen
  Vorrat, bei 15 Prozent oder weniger eine Meldung — Titel „Out of air soon!", Chatzeile „You are almost out of
  air!" und der Ertrink-Ton —, nur im Moonglade Marsh und im Torrhus Canyon, einmal, bis die Luft wieder über
  der Schwelle war. Nachgebaut — mit einem Unterschied: Banner, Chatzeile und Ton lassen sich einzeln
  abschalten, der Ton lässt sich durch eine eigene Datei ersetzen, und der Banner nimmt ein Design (Voreinstellung
  „Feesh", das den Titel des Spiels nachzeichnet).
- **Feesh** (Apache-2.0, `Sleepy-Panda/Feesh`) — der Vorschlag, einen gefundenen Hotspot zu teilen
  (`features/chat/HotspotFoundMessage.kt`, `utils/HotspotUtils.kt`): Alle zehn Ticks der nächste Rüstungsständer
  namens „HOTSPOT" in zehn Blöcken (mit einer Angel in der Hotbar, in einer der acht Welten mit Hotspots), der
  Bonus vom Ständer knapp darunter (gleiches X und Z, bis ein Block tiefer, gleicher Neigungswinkel); jeder
  Hotspot einmal, gemerkt werden die letzten zwei. Im Chat „You found <Bonus> Hotspot." und darunter
  „[Share to PARTY chat] or [Share to ALL chat]"; ein Klick schickt „x: …, y: …, z: … | <Bonus> Hotspot at <Zone>"
  an `/pchat`, an `/achat` mit einer zufälligen Kennung dahinter. Nachgebaut — mit drei Unterschieden: Der
  Ton lässt sich einzeln abschalten, die Einstellungen haben einen Testknopf, dessen Zeile nicht anklickbar
  ist, und Feeshs Vergessen eines Hotspots, wenn sein Ständer in der Nähe verschwindet, entfällt (ein neuer
  Hotspot ist ein neuer Ständer mit neuer Kennung, gemerkt werden ohnehin nur die letzten zwei). Dazu Feeshs
  Taste „Share hotspot", die den nächsten Hotspot in zehn Blöcken ohne Chat an die Gruppe schickt (`sendMessageWithNearestHotspot`):
  übernommen nur für die Gruppe, ohne Angel-Pflicht, mit einer Hinweiszeile, wenn keiner in Reichweite ist. Nicht
  übernommen: Feeshs Taste für alle und das selbsttätige Teilen.
- **Odin** — die Ping-Anzeige wurde gegen seine gehalten, um den eigenen Messfehler zu
  finden.
- **Odin** (BSD-3-Clause, Odtheking, `OdinFabric`) — die eigene Figur in anderer Größe
  (`features/impl/render/PlayerSize.kt`, `AvatarRendererMixin`): kurz vor dem Körper das
  Matrix-Blatt skalieren, bei negativer Höhe um die doppelte Höhe nach oben schieben, damit
  die umgekippte Figur auf dem Boden bleibt, und der Wertebereich von −1 bis 3. Das ist
  der Handgriff, und er ist hier nachgebaut (`PlayerSize`, `PlayerSizeMixin`). **Nicht**
  übernommen ist, was Odin daneben vom Entwickler-Server holt (feste Größen und Namen für
  einzelne Spieler). Anders ist auch das Erkennen der eigenen Figur: Odin merkt sich das
  Spielerprofil im Render-Zustand über einen Fabric-Schlüssel, hier genügt die
  Entitätsnummer. Der Mixin zielt auf `AvatarRenderer.scale(AvatarRenderState, PoseStack)`,
  das in 26.1, 26.2 und 26.3 dieselbe Signatur hat.

- **Skyblocker** (LGPL-3.0-or-later, `SkyblockerMod/Skyblocker`) — die Zeilen des Forge-Widgets in der Tab-Liste
  (`skyblock/tabhud/widget/ForgeWidget.java`, am 06.11.2026 gelesen): "Forges:" als Überschrift, darunter je Platz
  `<Nr>) LOCKED`, `<Nr>) EMPTY` oder `<Nr>) <Gegenstand>: <Zeit>`, mit "Ready!" statt der Zeit; sieben Plätze, Platz 3 bis 7
  gesperrt bis Heart of the Mountain 3 bis 7. Das ist eine Beobachtung darüber, wie Hypixel die Zeilen schickt, und kein
  Code: Das Lesen (`ForgeTab`), das Merken des Endes (`ForgeTracker`) und der Kasten (`ForgeHud`) sind eigen. Anders als
  Skyblockers Widget, das nur zeigt, was gerade in der Liste steht, merkt sich die Mod das Ende und zeigt es auf jeder Map.
  Auf Hypixel selbst ist das Format hier noch nicht gesehen (`ForgeTracker` schreibt unverstandene Zeilen ins Log).

## Daten von außen

- **Spawn-Runde in den Glacite Tunnels** — die 25 Punkte in
  `assets/shokimod/routes/glacite-spawn.json` stammen nicht aus dieser Mod: Sie wurden im
  Spiel abgelaufen und aufgeschrieben und für ShokiMod beigesteuert. Eigene Runden legt man
  als `.json` in `config/shokimod/routes`; die Mod nimmt die, in deren Nähe man steht.


Keine Mod-Funktionen, sondern Zahlen, die die Mod holt:

- **[NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)**
  (MIT) — Pet-Stufen, Item-Namen und -Bilder, Handwerks- und Forge-Rezepte. Dazu
  `constants/george.json` — was George für ein Pet zahlt, je Pet und Seltenheit. Diese
  Datei stammt laut ihrem eigenen Kopf aus dem [Hypixel-SkyBlock-Wiki](https://hypixelskyblock.minecraft.wiki/w/George/Prices)
  und steht unter CC BY-NC-SA 3.0; sie wird zur Laufzeit geholt und liegt im
  Config-Ordner, statt mitgeliefert zu werden.
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
