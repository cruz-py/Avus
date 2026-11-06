#!/usr/bin/env python3
"""
Collects every ERPI (Estrutura Residencial para Pessoas Idosas, i.e. residential care
home for the elderly) in mainland Portugal from the Carta Social (www.cartasocial.pt)
and writes a CSV the Avus app imports directly (";" separator, UTF-8, with a
"Resposta Social" column).

The portal has no export or public API, so this walks its search
district -> municipality -> facility, slowly (1 request/s by default) and with an
on-disk cache, so it can be interrupted and resumed without repeating requests.

Usage:
    python3 scrape_erpi.py                          # all of mainland Portugal
    python3 scrape_erpi.py --district Lisboa        # one district (for testing)
    python3 scrape_erpi.py --output erpi.csv --cache ./cache --delay 1.5

Requires: requests  (pip install requests)
"""
import argparse
import csv
import html
import json
import re
import sys
import time
from pathlib import Path

import requests

BASE = "https://www.cartasocial.pt"
AREA_ELDERLY = "21"        # area of intervention "Pessoas Idosas"
RESPONSE_ERPI = "2107"     # social response "Estrutura Residencial para Pessoas Idosas"
NS = "_SocialLetterPortlet_WAR_cartasocialportlet_"
SIDEBAR = NS + ":sidebarForm"
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) Avus/1.0 (ERPI list, personal use)"

# Official district codes (DD in the portal's DD-MM-FF location codes) and names.
DISTRICTS = {
    "01": "Aveiro", "02": "Beja", "03": "Braga", "04": "Bragança", "05": "Castelo Branco",
    "06": "Coimbra", "07": "Évora", "08": "Faro", "09": "Guarda", "10": "Leiria",
    "11": "Lisboa", "12": "Portalegre", "13": "Porto", "14": "Santarém", "15": "Setúbal",
    "16": "Viana do Castelo", "17": "Vila Real", "18": "Viseu",
}

# CSV header: kept in Portuguese because the app's importer matches these names
# and people open the file in a spreadsheet.
COLUMNS = [
    "Distrito", "Concelho", "Freguesia", "Nome do Equipamento", "Entidade Proprietária",
    "Natureza Jurídica", "Resposta Social", "Morada", "Código Postal", "Localidade",
    "Telefone", "Email", "Capacidade", "Utentes", "Última atualização", "ID Carta Social",
]


class Client:
    def __init__(self, cache: Path, delay: float):
        self.session = requests.Session()
        self.session.headers["User-Agent"] = USER_AGENT
        self.cache = cache
        self.delay = delay
        self._last = 0.0

    def _throttle(self):
        remaining = self.delay - (time.monotonic() - self._last)
        if remaining > 0:
            time.sleep(remaining)
        self._last = time.monotonic()

    def request(self, method, url, **kw):
        for attempt in range(5):
            self._throttle()
            try:
                r = self.session.request(method, url, timeout=60, **kw)
                if r.status_code == 200:
                    return r.text
                print(f"  HTTP {r.status_code} for {url[:90]}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"  network error ({e.__class__.__name__}), retrying", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
        raise RuntimeError(f"Repeatedly failed: {url}")

    def cached(self, name, produce):
        f = self.cache / name
        if f.exists():
            return f.read_text(encoding="utf-8")
        text = produce()
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(text, encoding="utf-8")
        return text


def _find(pattern, text, required=True):
    m = re.search(pattern, text)
    if not m:
        if required:
            raise RuntimeError(f"Unexpected page structure (could not find {pattern!r})")
        return None
    return html.unescape(m.group(1))


def _clean(t):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", t or ""))).strip()


def _title_case(t):
    """'ALTER DO CHÃO' -> 'Alter do Chão' (Portuguese particles stay lower-case)."""
    particles = {"de", "da", "do", "das", "dos", "e", "a", "o"}
    words = t.strip().lower().split()
    return " ".join(w if (i and w in particles) else w[:1].upper() + w[1:] for i, w in enumerate(words))


class Search:
    """One results page (district or municipality) together with its JSF view state."""

    def __init__(self, client: Client, location: str):
        self.client = client
        url = f"{BASE}/resultados-da-pesquisa?vt={AREA_ELDERLY}&tp={RESPONSE_ERPI}&l={location}"
        self.page = client.request("GET", url)
        self.ajax_url = _find(r'name="javax.faces.encodedURL"[^>]*value="([^"]*)"', self.page)
        self.view_state = _find(r'name="javax.faces.ViewState"[^>]*value="([^"]*)"', self.page)
        self.list_id = _find(r'PrimeFaces\.cw\("DataList","[^"]*",\{id:"([^"]*)"', self.page, required=False)
        total = _find(r"rowCount:(\d+)", self.page, required=False)
        if total is None:
            total = _find(r"Equipamentos:\s*</[^>]+>\s*<[^>]+>\s*(\d+)", self.page, required=False)
        self.total = int(total or 0)

    def ajax(self, source, extra):
        data = {
            "javax.faces.partial.ajax": "true",
            "javax.faces.source": source,
            "javax.faces.partial.execute": source,
            "javax.faces.partial.render": source,
            SIDEBAR: SIDEBAR,
            "javax.faces.ViewState": self.view_state,
        }
        data.update(extra)
        return self.client.request("POST", self.ajax_url, data=data, headers={
            "Faces-Request": "partial/ajax", "X-Requested-With": "XMLHttpRequest"})

    def municipalities(self):
        combo = SIDEBAR + ":comboCity"
        r = self.ajax(combo, {combo + "_query": "", combo + "_input": ""})
        return [(code, html.unescape(name)) for code, name in
                re.findall(r'data-item-value="([^"]*)" data-item-label="([^"]*)"', r)]

    def facilities(self):
        """Returns [(id, name, parish)] for every result (all pages)."""
        text = self.page
        if self.list_id and self.total > 10:
            text = self.ajax(self.list_id, {
                self.list_id + "_pagination": "true",
                self.list_id + "_first": "0",
                self.list_id + "_rows": str(self.total + 50),
                self.list_id + "_page": "0",
            })
        items = re.findall(
            r'idEquipment=(\d+)"[^>]*>\s*<div class="equipment-title">(.*?)</div>\s*'
            r'<div class="equipment-location">(.*?)</div>', text, re.S)
        seen, out = set(), []
        for fid, name, parish in items:
            if fid not in seen:
                seen.add(fid)
                out.append((fid, _clean(name), _clean(parish)))
        return out


def detail_url(facility_id):
    return (f"{BASE}/resultados-da-pesquisa?p_p_id=SocialLetterPortlet_WAR_cartasocialportlet"
            "&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view&p_p_col_id=column-1&p_p_col_count=1"
            f"&{NS}_facesViewIdRender=%2Fviews%2FsocialLetter%2Flist%2Fview%2Fequipment%2Fequipment_detail.xhtml"
            f"&{NS}idEquipment={facility_id}")


def parse_detail(page):
    start = page.find('<div id="equipment">')
    if start < 0:
        raise RuntimeError("Detail page without the facility block")
    block = page[start:]
    name = _clean(_find(r"</script>([^<]*)</h2>", block, required=False) or "")
    fields = {_clean(k): _clean(v) for k, v in re.findall(r"<label>(.*?)</label>\s*<p>(.*?)</p>", block, re.S)}
    rows = []
    for tr in re.findall(r'<tr data-ri="\d+"[^>]*>(.*?)</tr>', block, re.S):
        rows.append([_clean(td) for td in re.findall(r"<td[^>]*>(.*?)</td>", tr, re.S)])
    # A facility can offer several social responses; take the ERPI row.
    erpi = next((r for r in rows if r and "estrutura residencial" in r[0].lower()), rows[0] if rows else [])
    postcode_town = fields.get("Código Postal", "")
    m = re.match(r"(\d{4}-\d{3})\s*(.*)", postcode_town)
    return {
        "Nome do Equipamento": name,
        "Morada": fields.get("Morada", ""),
        "Código Postal": m.group(1) if m else postcode_town,
        "Localidade": _title_case(m.group(2) if m else ""),
        "Telefone": fields.get("Telefone / Fax", fields.get("Telefone", "")),
        "Email": fields.get("Email", ""),
        "Entidade Proprietária": fields.get("Entidade proprietária", ""),
        "Natureza Jurídica": fields.get("Natureza Jurídica", ""),
        "Resposta Social": re.sub(r"\s+", " ", erpi[0]) if erpi else "Estrutura Residencial para Pessoas Idosas",
        "Capacidade": erpi[1] if len(erpi) > 1 else "",
        "Utentes": erpi[2] if len(erpi) > 2 else "",
        "Última atualização": erpi[4] if len(erpi) > 4 else "",
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--output", default="erpi_portugal.csv", help="output CSV file")
    ap.add_argument("--cache", default="cache", help="cache folder (allows resuming)")
    ap.add_argument("--delay", type=float, default=1.0, help="seconds between requests (min. 0.5)")
    ap.add_argument("--district", action="append", help="limit to district(s), by name or code")
    a = ap.parse_args()

    client = Client(Path(a.cache), max(a.delay, 0.5))
    chosen = DISTRICTS
    if a.district:
        wanted = {d.lower() for d in a.district}
        chosen = {c: n for c, n in DISTRICTS.items() if c in wanted or n.lower() in wanted}
        if not chosen:
            sys.exit(f"Unknown district. Options: {', '.join(DISTRICTS.values())}")

    rows, failures = [], []
    for district_code, district in chosen.items():
        # Municipalities and their facility ids (cached as JSON per district).
        def list_district():
            search = Search(client, f"{district_code}-00-00")
            result = {"total": search.total, "municipalities": []}
            for code, municipality in search.municipalities():
                ms = Search(client, code)
                found = ms.facilities()
                if len(found) != ms.total:
                    print(f"  warning: {municipality}: {len(found)} of {ms.total} facilities", file=sys.stderr)
                result["municipalities"].append({"code": code, "name": municipality, "facilities": found})
            return json.dumps(result, ensure_ascii=False)

        print(f"{district}: listing municipalities and facilities…", flush=True)
        data = json.loads(client.cached(f"lists/{district_code}.json", list_district))
        count = sum(len(m["facilities"]) for m in data["municipalities"])
        print(f"{district}: {len(data['municipalities'])} municipalities, {count} ERPI "
              f"(portal reports {data['total']})", flush=True)

        for m in data["municipalities"]:
            for facility_id, name, parish in m["facilities"]:
                try:
                    page = client.cached(f"details/{facility_id}.html",
                                         lambda: client.request("GET", detail_url(facility_id)))
                    row = parse_detail(page)
                except Exception as e:  # one broken facility should not stop the run
                    failures.append((facility_id, name, str(e)))
                    continue
                row.update({
                    "Distrito": district, "Concelho": m["name"], "Freguesia": parish,
                    "ID Carta Social": facility_id,
                })
                row["Nome do Equipamento"] = row["Nome do Equipamento"] or name
                rows.append(row)
            print(f"  {m['name']}: {len(m['facilities'])}", flush=True)

    with open(a.output, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, delimiter=";")
        w.writeheader()
        w.writerows(rows)

    with_email = sum(1 for r in rows if "@" in r["Email"])
    print(f"\nWrote {len(rows)} ERPI to {a.output} ({with_email} with email).")
    if failures:
        print(f"{len(failures)} facilities failed (run again to retry):", file=sys.stderr)
        for failure in failures[:20]:
            print("  ", *failure, file=sys.stderr)


if __name__ == "__main__":
    main()
