# Recolha das ERPI da Carta Social

A [Carta Social](https://www.cartasocial.pt) é o registo oficial dos equipamentos sociais
(GEP/MTSSS), mas não tem exportação nem API. `recolher_erpi.py` percorre a pesquisa do
portal (distrito → concelho → equipamento) e grava um CSV com todas as ERPI de Portugal
continental, pronto a importar na app (separador **Lares** → **Importar lista (CSV)**).

```bash
pip install requests
python3 recolher_erpi.py                       # continente inteiro (~45–60 min)
python3 recolher_erpi.py --distrito Lisboa     # só um distrito
```

- Faz 1 pedido por segundo (`--pausa` para abrandar) e guarda tudo em `cache/`:
  se for interrompido, volte a correr e continua onde parou.
- Para dados atualizados, apague `cache/` (ou só `cache/listas/` para apanhar ERPI novas).
- Açores e Madeira não estão na Carta Social (têm sistemas regionais próprios).
- Se o portal mudar de estrutura, o script falha com "Estrutura da página inesperada".

Os dados são públicos, mas pertencem ao GEP/MTSSS: use-os para contactar lares, não os
redistribua dentro da app sem autorização.
