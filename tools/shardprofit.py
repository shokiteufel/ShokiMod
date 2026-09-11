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

Wie viele Shards eine Fusion verschlingt, steht nicht im Rezept, sondern beim
Shard selbst: fuse_amount, meist 5, bei manchen 2. Eine Fusion nimmt von jeder
Zutat diese Menge - "Sun Fish + Sun Fish -> 2x Galaxy Fish" heisst also fuenf plus
fuenf, nicht eins plus eins. Wer das uebersieht, rechnet die Kosten um das
Fuenffache zu niedrig und haelt Verlustgeschaefte fuer Gewinne. So steht es auch
in der Rechnung der Vorlage (calculationService.ts):

    const totalCost = cost1 * fuse1 + cost2 * fuse2;

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
                sa = shards.get(a, {})
                sb = shards.get(b, {})
                ka = sa.get("internal_id")
                kb = sb.get("internal_id")
                if not ka or not kb:
                    continue
                # Wie viele Stueck die Fusion von jeder Zutat nimmt
                na = int(sa.get("fuse_amount") or 0)
                nb = int(sb.get("fuse_amount") or 0)
                if na <= 0 or nb <= 0:
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

                # Erst runden, dann rechnen: Die Mod bekommt gerundete Preise und
                # rechnet damit. Wer hier mit den ungerundeten Zahlen reiht, sortiert
                # nach etwas anderem als dem, was der Spieler spaeter liest - ein
                # Coin Unterschied, aber die Reihenfolge soll zur Anzeige passen
                r_ea, r_eb, r_ga, r_gb = round(ea), round(eb), round(ga), round(gb)
                r_sofort, r_auftrag = round(bringt_sofort), round(bringt_auftrag)

                # Alle vier Wege durchrechnen - der beste entscheidet, ob die Zeile
                # mitkommt und wo sie steht. Sonst haenge die Auswahl an einer
                # Annahme, die der Spieler gar nicht teilt
                bester = max(
                    stueck * bringt - (na * kauf_a + nb * kauf_b)
                    for bringt in (r_sofort, r_auftrag)
                    for kauf_a, kauf_b in ((r_ea, r_eb), (r_ga, r_gb)))
                if bester <= 0:
                    continue

                zeilen.append({
                    "ergebnis": ziel.get("name", ergebnis),
                    "ergebnisId": ergebnis,
                    "menge": stueck,
                    "zutat1": sa.get("name", a),
                    "zutat2": sb.get("name", b),
                    # Wie viele Stueck je Zutat noetig sind
                    "menge1": na,
                    "menge2": nb,
                    "zutat1Id": a,
                    "zutat2Id": b,
                    "seltenheit": (ziel.get("rarity") or "").upper(),
                    # Die Sparte, nach der die Mod Reiter baut - dieselbe Einteilung
                    # wie bei den Pets. Das Gebiet steht daneben, es hat nur drei Werte
                    # und taugt als Reiter nicht
                    "sparte": (ziel.get("type") or "").upper(),
                    "gebiet": eig.get("category") or "",
                    # Je Shard beide Zahlen, immer je Stueck. Was daraus Kosten
                    # oder Erloes wird, haengt an der Richtung und an der Stueckzahl -
                    # beides entscheidet die Mod
                    "z1Sofort": r_ea,
                    "z1Auftrag": r_ga,
                    "z2Sofort": r_eb,
                    "z2Auftrag": r_gb,
                    "ergSofort": r_sofort,
                    "ergAuftrag": r_auftrag,
                    "bester": bester,
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
