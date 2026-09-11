"""Sucht die Shard-Fusionen, bei denen am meisten haengen bleibt.

Laeuft nicht auf dem Spieler-PC, sondern als GitHub Action - wie bei petprofit.py.
Es sind rund 130.000 Kombinationen gegen 2.200 Bazaar-Produkte zu rechnen; das
gehoert nicht in den Spieltakt. Heraus faellt eine kleine Datei, die die Mod in
einem Zug holt.

Die Rezepte kommen von SkyShards (https://github.com/Campionnn/SkyShards, MIT),
nicht aus der Webseite: Dort liegen sie als JSON und tragen die Bazaar-Kennung
gleich mit, waehrend die Seite selbst eine JavaScript-Anwendung ohne abgreifbare
Daten ist. Aktualisiert wird das Verzeichnis dort automatisch.

Ausgegeben werden die Preise, nicht der fertige Gewinn. Fuer Zutaten und Ergebnis
gibt es je zwei Wege - sofort handeln oder einen Auftrag stellen -, und sie lassen
sich frei mischen: Zutaten sofort kaufen und das Ergebnis per Auftrag verkaufen ist
eine ebenso gueltige Wahl wie jede andere. Vier Kombinationen also, und welche gilt,
weiss nur der Spieler. Die Mod rechnet damit; hier wird gesammelt.

Was die beiden Bazaar-Zahlen bedeuten:

    buyPrice   was ein Stueck kostet, wenn man es sofort nimmt (Instabuy),
               und zugleich das, was eine Sell Order einbringt
    sellPrice  was ein Stueck bringt, wenn man es sofort abgibt (Instasell),
               und zugleich das, was eine Buy Order kostet

Dieselbe Zahl heisst je nach Richtung etwas anderes - deshalb stehen unten beide
bei jedem Shard, statt einer vorgerechneten Spanne.

    python shardprofit.py [ziel.json]
"""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SKYSHARDS = "https://raw.githubusercontent.com/Campionnn/SkyShards/master/public"
FUSIONEN = SKYSHARDS + "/fusion-data.json"
EIGENSCHAFTEN = SKYSHARDS + "/fusion-properties.json"
BAZAAR = "https://api.hypixel.net/v2/skyblock/bazaar"
KOPF = {"User-Agent": "ShokiMod-shardprofit (+https://github.com/shokiteufel/ShokiMod)"}

# So viele Zeilen wandern in die Datei. Mehr liest niemand, und die Mod soll sie
# in einem Zug holen koennen
GRENZE = 400
# Was seltener als das gehandelt wird, ist keine Gelegenheit, sondern eine Falle:
# Der Preis stammt dann von einem einzelnen Angebot und haelt keiner Menge stand
MINDESTUMSATZ = 1000


def holen(url: str) -> dict:
    anfrage = urllib.request.Request(url, headers=KOPF)
    with urllib.request.urlopen(anfrage, timeout=120) as antwort:
        return json.load(antwort)


class Preise:
    """Die Bazaar-Preise, nach Shard-Kennung nachschlagbar."""

    def __init__(self, produkte: dict) -> None:
        self.produkte = produkte

    def stand(self, kennung: str) -> dict | None:
        eintrag = self.produkte.get(kennung)
        return eintrag.get("quick_status") if eintrag else None

    def sofort_kaufen(self, kennung: str) -> float | None:
        """Was ein Stueck kostet, wenn man es jetzt aus dem Bazaar nimmt."""
        s = self.stand(kennung)
        return s["buyPrice"] if s and s.get("buyPrice") else None

    def sofort_verkaufen(self, kennung: str) -> float | None:
        """Was ein Stueck bringt, wenn man es jetzt hineingibt."""
        s = self.stand(kennung)
        return s["sellPrice"] if s and s.get("sellPrice") else None

    def umsatz(self, kennung: str) -> int:
        """Wie viel in der Woche bewegt wurde - der kleinere der beiden Wege."""
        s = self.stand(kennung)
        if not s:
            return 0
        return int(min(s.get("buyMovingWeek", 0), s.get("sellMovingWeek", 0)))


def bauen(fusionen: dict, eigenschaften: dict, preise: Preise) -> list[dict]:
    """Jede Kombination durchrechnen und die lohnenden behalten."""
    shards = fusionen["shards"]
    zeilen: list[dict] = []

    for ergebnis, nach_menge in fusionen["recipes"].items():
        ziel = shards.get(ergebnis)
        if not ziel:
            continue
        kennung = ziel.get("internal_id")
        if not kennung:
            continue
        # Was das Ergebnis bringt - einmal sofort, einmal ueber einen Auftrag
        bringt_sofort = preise.sofort_verkaufen(kennung)
        bringt_auftrag = preise.sofort_kaufen(kennung)
        if bringt_sofort is None or bringt_auftrag is None:
            continue
        if preise.umsatz(kennung) < MINDESTUMSATZ:
            continue

        eig = eigenschaften.get(ergebnis, {})
        for menge, paare in nach_menge.items():
            try:
                stueck = int(menge)
            except (TypeError, ValueError):
                continue
            if stueck <= 0:
                continue

            for paar in paare:
                if not isinstance(paar, list) or len(paar) != 2:
                    continue
                a, b = paar
                ka = shards.get(a, {}).get("internal_id")
                kb = shards.get(b, {}).get("internal_id")
                if not ka or not kb:
                    continue
                # Zutaten sofort nehmen, Ergebnis sofort abgeben: der schnelle Weg
                ea = preise.sofort_kaufen(ka)
                eb = preise.sofort_kaufen(kb)
                # Zutaten per Auftrag holen, Ergebnis per Auftrag abgeben: der geduldige
                ga = preise.sofort_verkaufen(ka)
                gb = preise.sofort_verkaufen(kb)
                if None in (ea, eb, ga, gb):
                    continue
                if min(preise.umsatz(ka), preise.umsatz(kb)) < MINDESTUMSATZ:
                    continue

                # Alle vier Wege durchrechnen - der beste entscheidet, ob die Zeile
                # mitkommt und wo sie steht. Sonst haenge die Auswahl an einer
                # Annahme, die der Spieler gar nicht teilt
                bester = max(
                    stueck * bringt - (kauf_a + kauf_b)
                    for bringt in (bringt_sofort, bringt_auftrag)
                    for kauf_a, kauf_b in ((ea, eb), (ga, gb)))
                if bester <= 0:
                    continue

                zeilen.append({
                    "ergebnis": ziel.get("name", ergebnis),
                    "ergebnisId": ergebnis,
                    "menge": stueck,
                    "zutat1": shards.get(a, {}).get("name", a),
                    "zutat2": shards.get(b, {}).get("name", b),
                    "zutat1Id": a,
                    "zutat2Id": b,
                    "seltenheit": (ziel.get("rarity") or "").upper(),
                    # Die Sparte, nach der die Mod Reiter baut - dieselbe Einteilung
                    # wie bei den Pets. Das Gebiet steht daneben, es hat nur drei Werte
                    # und taugt als Reiter nicht
                    "sparte": (ziel.get("type") or "").upper(),
                    "gebiet": eig.get("category") or "",
                    # Je Shard beide Zahlen. Was daraus Kosten oder Erloes wird,
                    # haengt an der Richtung und entscheidet die Mod
                    "z1Sofort": round(ea),
                    "z1Auftrag": round(ga),
                    "z2Sofort": round(eb),
                    "z2Auftrag": round(gb),
                    "ergSofort": round(bringt_sofort),
                    "ergAuftrag": round(bringt_auftrag),
                    "bester": round(bester),
                    "umsatz": preise.umsatz(kennung),
                })

    # Nach dem besten erreichbaren Gewinn reihen
    zeilen.sort(key=lambda z: z["bester"], reverse=True)
    return zeilen


def main() -> int:
    ziel = Path(sys.argv[1] if len(sys.argv) > 1 else "shardprofit.json")
    try:
        fusionen = holen(FUSIONEN)
        eigenschaften = holen(EIGENSCHAFTEN)
        bazaar = holen(BAZAAR)
    except (urllib.error.URLError, json.JSONDecodeError, OSError) as e:
        print("Daten holen fehlgeschlagen: %s" % e, file=sys.stderr)
        return 1

    produkte = bazaar.get("products") or {}
    if not produkte:
        print("Der Bazaar hat nichts geliefert", file=sys.stderr)
        return 1

    zeilen = bauen(fusionen, eigenschaften, Preise(produkte))
    if not zeilen:
        print("Keine lohnende Fusion gefunden - das waere ungewoehnlich", file=sys.stderr)
        return 1

    # Die Sparten, damit die Mod Reiter bauen kann, ohne selbst zu suchen
    sparten = sorted({z["sparte"] for z in zeilen if z["sparte"]})

    ziel.write_text(json.dumps({
        "aktualisiert": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "kombinationen": sum(len(p) for m in fusionen["recipes"].values() for p in m.values()),
        "lohnend": len(zeilen),
        "sparten": sparten,
        "quelle": "https://github.com/Campionnn/SkyShards (MIT)",
        "fusionen": zeilen[:GRENZE],
    }, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    print("%d von %d Kombinationen lohnen sich, %d geschrieben nach %s"
          % (len(zeilen),
             sum(len(p) for m in fusionen["recipes"].values() for p in m.values()),
             min(len(zeilen), GRENZE), ziel))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
