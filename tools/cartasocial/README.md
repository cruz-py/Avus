# ERPI list from the Carta Social

The [Carta Social](https://www.cartasocial.pt) is the official register of social facilities
in Portugal (run by GEP/MTSSS), but it has no export or API. `scrape_erpi.py` walks the
portal's search (district → municipality → facility) and writes a CSV with every ERPI in
mainland Portugal, ready to import in the app (**Lares** tab → **Importar lista (CSV)**).

```bash
pip install requests
python3 scrape_erpi.py                         # all of mainland Portugal (~45–60 min)
python3 scrape_erpi.py --district Lisboa       # a single district
```

- Makes 1 request per second (`--delay` to slow down) and caches everything in `cache/`:
  if interrupted, run it again and it carries on where it stopped.
- For fresh data, delete `cache/` (or just `cache/lists/` to pick up new homes).
- The CSV header stays in Portuguese: the app's importer matches those column names.
- The Azores and Madeira are not in the Carta Social (they have their own regional systems).
- If the portal changes, the script stops with "Unexpected page structure".

The data is public but belongs to GEP/MTSSS: use it to contact care homes, and don't
redistribute it inside the app without permission.
