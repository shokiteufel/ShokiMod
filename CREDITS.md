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
  eigener Code. Dagegen gehalten wurde außerdem das **Aussehen** seiner Wegweiser: Die
  Führungslinie beginnt zwei Blöcke vor der Kamera — am Fadenkreuz, nicht an den Füßen —
  und das Ziel wird gefüllt statt nur umrissen, mit einer Deckkraft, die mit der
  Entfernung wächst (`WorldRenderUtils.drawLineToCrosshair`, `drawWaypointFilled`).
  Abgelesen, nicht übernommen: Die Umsetzung hier nutzt die Gizmos der Spielversion,
  und die Steigung der Deckkraft ist flacher, weil eine Ader mehr als einen Block füllt.
  Die AGPL wäre mit der LGPL **nicht** vereinbar, deshalb ist hier bewusst nie Code
  übernommen worden, nur Tatsachen abgelesen.
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
  übernommen — und mit drei Unterschieden: Die Symbole links vom Namen und das Herz rechts fehlen,
  die Reihenfolge lässt sich auch nach Seltenheit wählen, und die Auswahl der Bewohner steht im
  selben Fenster wie die des Alarms (`/shoki seacreatures`, Spalte HP) statt in einer eigenen Liste.
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
