# ERPI list from the Carta Social

The [Carta Social](https://www.cartasocial.pt) is the official register of social facilities
in Portugal (run by DGSSS, under the Ministry of Labour, Solidarity and Social Security), but it has no export or API. `scrape_erpi.py` walks the
portal's search (district → municipality → facility) and writes a CSV with every ERPI in
mainland Portugal. The app ships that CSV in `app/src/main/assets/` and loads it on first launch.

```bash
pip install requests
python3 scrape_erpi.py                         # all of mainland Portugal (~45–60 min)
python3 scrape_erpi.py --district Lisboa       # a single district
```

**The list shipped with the app** lives in `app/src/main/assets/erpi_portugal.csv` (+ `.properties`).
It is **not kept in git** (see `.gitignore`): generate it before building a release. A build without
it still works; it simply has no official list and hides the "Carregar/Restaurar lista oficial" options.

**Refreshing it** (before a release):

```bash
rm -rf cache/lists                              # re-list homes (new/closed ones); keep cache/details to reuse pages
python3 scrape_erpi.py --output ../../app/src/main/assets/erpi_portugal.csv
```

This also writes `erpi_portugal.properties` (collection date and count). Installed apps pick up
the new list on update, unless the user deleted it.

- Adds approximate coordinates to each home from its postal code via [geoapi.pt](https://geoapi.pt)
  (free, no key): full code, else first 4 digits, else the municipality's centre. One request per
  postal code (~2,400, cached in `cache/geo/`); `--no-geocode` skips it.
- Makes 1 request per second (`--delay` to slow down) and caches everything in `cache/`:
  if interrupted, run it again and it carries on where it stopped.
- For fresh data, delete `cache/` (or just `cache/lists/` to pick up new homes).
- The CSV header stays in Portuguese: the app's importer matches those column names.
- The Azores and Madeira are not in the Carta Social (they have their own regional systems).
- If the portal changes, the script stops with "Unexpected page structure".

The data is public information published by DGSSS for citizens. The app credits the source
("Fonte: Carta Social (DGSSS)") with the collection date; let cartasocial@seg-social.pt know
the data is being reused. If a home or person asks to be removed, delete their row and refresh.
