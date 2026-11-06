#!/usr/bin/env python3
"""
Recolhe da Carta Social (www.cartasocial.pt) todas as ERPI (Estruturas Residenciais
para Pessoas Idosas) de Portugal continental e grava um CSV que a app Avus importa
diretamente (separador ";", UTF-8, com coluna "Resposta Social").

O portal não tem exportação nem API pública; este script percorre a pesquisa
distrito -> concelho -> equipamento, de forma lenta (1 pedido/s por omissão) e com
cache em disco, para poder ser interrompido e retomado sem repetir pedidos.

Uso:
    python3 recolher_erpi.py                         # todo o continente
    python3 recolher_erpi.py --distrito Lisboa       # só um distrito (teste)
    python3 recolher_erpi.py --saida erpi.csv --cache ./cache --pausa 1.5

Requer: requests  (pip install requests)
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
AREA_IDOSOS = "21"          # "Pessoas Idosas"
RESPOSTA_ERPI = "2107"      # "Estrutura Residencial para Pessoas Idosas"
NS = "_SocialLetterPortlet_WAR_cartasocialportlet_"
SIDEBAR = NS + ":sidebarForm"
USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) Avus/1.0 (recolha ERPI, uso pessoal)"

DISTRITOS = {
    "01": "Aveiro", "02": "Beja", "03": "Braga", "04": "Bragança", "05": "Castelo Branco",
    "06": "Coimbra", "07": "Évora", "08": "Faro", "09": "Guarda", "10": "Leiria",
    "11": "Lisboa", "12": "Portalegre", "13": "Porto", "14": "Santarém", "15": "Setúbal",
    "16": "Viana do Castelo", "17": "Vila Real", "18": "Viseu",
}

COLUNAS = [
    "Distrito", "Concelho", "Freguesia", "Nome do Equipamento", "Entidade Proprietária",
    "Natureza Jurídica", "Resposta Social", "Morada", "Código Postal", "Localidade",
    "Telefone", "Email", "Capacidade", "Utentes", "Última atualização", "ID Carta Social",
]


class Cliente:
    def __init__(self, cache: Path, pausa: float):
        self.s = requests.Session()
        self.s.headers["User-Agent"] = USER_AGENT
        self.cache = cache
        self.pausa = pausa
        self._ultimo = 0.0

    def _esperar(self):
        falta = self.pausa - (time.monotonic() - self._ultimo)
        if falta > 0:
            time.sleep(falta)
        self._ultimo = time.monotonic()

    def pedido(self, metodo, url, **kw):
        for tentativa in range(5):
            self._esperar()
            try:
                r = self.s.request(metodo, url, timeout=60, **kw)
                if r.status_code == 200:
                    return r.text
                print(f"  HTTP {r.status_code} em {url[:90]}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"  erro de rede ({e.__class__.__name__}), a tentar de novo", file=sys.stderr)
            time.sleep(5 * (tentativa + 1))
        raise RuntimeError(f"Falhou repetidamente: {url}")

    def em_cache(self, nome, produzir):
        f = self.cache / nome
        if f.exists():
            return f.read_text(encoding="utf-8")
        texto = produzir()
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(texto, encoding="utf-8")
        return texto


def _campo(padrao, texto, obrig=True):
    m = re.search(padrao, texto)
    if not m:
        if obrig:
            raise RuntimeError(f"Estrutura da página inesperada (não encontrei {padrao!r})")
        return None
    return html.unescape(m.group(1))


def _limpar(t):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", t or ""))).strip()


def _capitalizar(t):
    """'ALTER DO CHÃO' -> 'Alter do Chão'."""
    pequenas = {"de", "da", "do", "das", "dos", "e", "a", "o"}
    palavras = t.strip().lower().split()
    return " ".join(w if (i and w in pequenas) else w[:1].upper() + w[1:] for i, w in enumerate(palavras))


class Pesquisa:
    """Uma página de resultados (distrito ou concelho), com o seu estado JSF."""

    def __init__(self, cli: Cliente, local: str):
        self.cli = cli
        url = f"{BASE}/resultados-da-pesquisa?vt={AREA_IDOSOS}&tp={RESPOSTA_ERPI}&l={local}"
        self.pagina = cli.pedido("GET", url)
        self.enc = _campo(r'name="javax.faces.encodedURL"[^>]*value="([^"]*)"', self.pagina)
        self.vs = _campo(r'name="javax.faces.ViewState"[^>]*value="([^"]*)"', self.pagina)
        self.lista = _campo(r'PrimeFaces\.cw\("DataList","[^"]*",\{id:"([^"]*)"', self.pagina, obrig=False)
        total = _campo(r"rowCount:(\d+)", self.pagina, obrig=False)
        if total is None:
            total = _campo(r"Equipamentos:\s*</[^>]+>\s*<[^>]+>\s*(\d+)", self.pagina, obrig=False)
        self.total = int(total or 0)

    def ajax(self, fonte, extra):
        dados = {
            "javax.faces.partial.ajax": "true",
            "javax.faces.source": fonte,
            "javax.faces.partial.execute": fonte,
            "javax.faces.partial.render": fonte,
            SIDEBAR: SIDEBAR,
            "javax.faces.ViewState": self.vs,
        }
        dados.update(extra)
        return self.cli.pedido("POST", self.enc, data=dados, headers={
            "Faces-Request": "partial/ajax", "X-Requested-With": "XMLHttpRequest"})

    def concelhos(self):
        cc = SIDEBAR + ":comboCity"
        r = self.ajax(cc, {cc + "_query": "", cc + "_input": ""})
        return [(v, html.unescape(n)) for v, n in
                re.findall(r'data-item-value="([^"]*)" data-item-label="([^"]*)"', r)]

    def equipamentos(self):
        """Devolve [(id, nome, freguesia)] de todos os resultados (todas as páginas)."""
        texto = self.pagina
        if self.lista and self.total > 10:
            texto = self.ajax(self.lista, {
                self.lista + "_pagination": "true",
                self.lista + "_first": "0",
                self.lista + "_rows": str(self.total + 50),
                self.lista + "_page": "0",
            })
        itens = re.findall(
            r'idEquipment=(\d+)"[^>]*>\s*<div class="equipment-title">(.*?)</div>\s*'
            r'<div class="equipment-location">(.*?)</div>', texto, re.S)
        vistos, saida = set(), []
        for i, nome, freg in itens:
            if i not in vistos:
                vistos.add(i)
                saida.append((i, _limpar(nome), _limpar(freg)))
        return saida


def url_detalhe(id_equip):
    return (f"{BASE}/resultados-da-pesquisa?p_p_id=SocialLetterPortlet_WAR_cartasocialportlet"
            "&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view&p_p_col_id=column-1&p_p_col_count=1"
            f"&{NS}_facesViewIdRender=%2Fviews%2FsocialLetter%2Flist%2Fview%2Fequipment%2Fequipment_detail.xhtml"
            f"&{NS}idEquipment={id_equip}")


def ler_detalhe(pagina):
    inicio = pagina.find('<div id="equipment">')
    if inicio < 0:
        raise RuntimeError("Página de detalhe sem o bloco do equipamento")
    bloco = pagina[inicio:]
    nome = _limpar(_campo(r"</script>([^<]*)</h2>", bloco, obrig=False) or "")
    campos = {_limpar(k): _limpar(v) for k, v in re.findall(r"<label>(.*?)</label>\s*<p>(.*?)</p>", bloco, re.S)}
    linhas = []
    for tr in re.findall(r'<tr data-ri="\d+"[^>]*>(.*?)</tr>', bloco, re.S):
        linhas.append([_limpar(td) for td in re.findall(r"<td[^>]*>(.*?)</td>", tr, re.S)])
    erpi = next((l for l in linhas if l and "estrutura residencial" in l[0].lower()), linhas[0] if linhas else [])
    cp_loc = campos.get("Código Postal", "")
    m = re.match(r"(\d{4}-\d{3})\s*(.*)", cp_loc)
    return {
        "Nome do Equipamento": nome,
        "Morada": campos.get("Morada", ""),
        "Código Postal": m.group(1) if m else cp_loc,
        "Localidade": _capitalizar(m.group(2) if m else ""),
        "Telefone": campos.get("Telefone / Fax", campos.get("Telefone", "")),
        "Email": campos.get("Email", ""),
        "Entidade Proprietária": campos.get("Entidade proprietária", ""),
        "Natureza Jurídica": campos.get("Natureza Jurídica", ""),
        "Resposta Social": re.sub(r"\s+", " ", erpi[0]) if erpi else "Estrutura Residencial para Pessoas Idosas",
        "Capacidade": erpi[1] if len(erpi) > 1 else "",
        "Utentes": erpi[2] if len(erpi) > 2 else "",
        "Última atualização": erpi[4] if len(erpi) > 4 else "",
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--saida", default="erpi_portugal.csv", help="ficheiro CSV de saída")
    ap.add_argument("--cache", default="cache", help="pasta da cache (permite retomar)")
    ap.add_argument("--pausa", type=float, default=1.0, help="segundos entre pedidos (mín. 0.5)")
    ap.add_argument("--distrito", action="append", help="limitar a distrito(s), pelo nome ou código")
    a = ap.parse_args()

    cli = Cliente(Path(a.cache), max(a.pausa, 0.5))
    escolhidos = DISTRITOS
    if a.distrito:
        pedidos = {d.lower() for d in a.distrito}
        escolhidos = {c: n for c, n in DISTRITOS.items() if c in pedidos or n.lower() in pedidos}
        if not escolhidos:
            sys.exit(f"Distrito desconhecido. Opções: {', '.join(DISTRITOS.values())}")

    linhas, falhas = [], []
    for cod_d, distrito in escolhidos.items():
        # Lista de concelhos e de equipamentos por concelho (guardada em cache como JSON).
        def listar():
            p = Pesquisa(cli, f"{cod_d}-00-00")
            resultado = {"total": p.total, "concelhos": []}
            for cod_c, concelho in p.concelhos():
                pc = Pesquisa(cli, cod_c)
                eq = pc.equipamentos()
                if len(eq) != pc.total:
                    print(f"  aviso: {concelho}: {len(eq)} de {pc.total} equipamentos", file=sys.stderr)
                resultado["concelhos"].append({"codigo": cod_c, "nome": concelho, "equipamentos": eq})
            return json.dumps(resultado, ensure_ascii=False)

        print(f"{distrito}: a listar concelhos e equipamentos…", flush=True)
        dados = json.loads(cli.em_cache(f"listas/{cod_d}.json", listar))
        soma = sum(len(c["equipamentos"]) for c in dados["concelhos"])
        print(f"{distrito}: {len(dados['concelhos'])} concelhos, {soma} ERPI (portal indica {dados['total']})", flush=True)

        for c in dados["concelhos"]:
            for n, (id_e, nome, freguesia) in enumerate(c["equipamentos"], 1):
                try:
                    pag = cli.em_cache(f"detalhes/{id_e}.html", lambda: cli.pedido("GET", url_detalhe(id_e)))
                    d = ler_detalhe(pag)
                except Exception as e:  # um equipamento com problemas não deve parar a recolha
                    falhas.append((id_e, nome, str(e)))
                    continue
                d.update({
                    "Distrito": distrito, "Concelho": c["nome"], "Freguesia": freguesia,
                    "ID Carta Social": id_e,
                })
                d["Nome do Equipamento"] = d["Nome do Equipamento"] or nome
                linhas.append(d)
            print(f"  {c['nome']}: {len(c['equipamentos'])}", flush=True)

    with open(a.saida, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUNAS, delimiter=";")
        w.writeheader()
        w.writerows(linhas)

    com_email = sum(1 for l in linhas if "@" in l["Email"])
    print(f"\nGravadas {len(linhas)} ERPI em {a.saida} ({com_email} com email).")
    if falhas:
        print(f"{len(falhas)} equipamentos falharam (volte a correr para tentar de novo):", file=sys.stderr)
        for f_ in falhas[:20]:
            print("  ", *f_, file=sys.stderr)


if __name__ == "__main__":
    main()
