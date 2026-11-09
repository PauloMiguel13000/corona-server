#!/usr/bin/env python3
"""
Confere o pacote de recursos do Corona Server antes de instalar (evita a textura roxa/preta e item invisivel).

Uso: python3 -I conferir_pacote.py <corona-pack.zip> <corona.mcpack> [<pasta das fontes dos plugins>]

Java (zip):
  - todo assets/<ns>/items/*.json: cada "model" de ns proprio existe em assets/<ns>/models/
  - todo modelo: "parent" de ns proprio existe; cada textura de ns proprio existe em textures/ (.png)
  - equipment/*.json (armaduras): cada camada aponta para textures/entity/equipment/<camada>/<tex>.png
  - fonts: cada "file" de bitmap existe
Bedrock (mcpack):
  - textures/item_texture.json: cada caminho existe (.png ou .tga)
  - attachables/*.json: texturas existem, geometrias usadas estao definidas em models/
  - entity/*.json (cliente): idem
  - geometrias duplicadas (mesmo identifier em dois arquivos) = erro
Plugins (opcional): todo "corona:xxx" usado como item_model nas fontes .java tem assets/corona/items/xxx.json.
Sai com codigo 1 se achar problema (e lista tudo).
"""
import json
import re
import sys
import zipfile


def ler_json(z, nome):
    txt = z.read(nome).decode("utf-8", "replace")
    # o Bedrock aceita comentarios // nos json
    txt = re.sub(r"^\s*//.*$", "", txt, flags=re.M)
    return json.loads(txt)


def id_para(ns_padrao, rid):
    if ":" in rid:
        ns, p = rid.split(":", 1)
    else:
        ns, p = ns_padrao, rid
    return ns, p


def conferir_java(caminho):
    erros = []
    z = zipfile.ZipFile(caminho)
    nomes = set(z.namelist())

    def modelos_do_item(no, achados):
        if isinstance(no, dict):
            if no.get("type") in ("minecraft:model", "model") and isinstance(no.get("model"), str):
                achados.append(no["model"])
            for v in no.values():
                modelos_do_item(v, achados)
        elif isinstance(no, list):
            for v in no:
                modelos_do_item(v, achados)

    itens = [n for n in nomes if re.match(r"assets/[^/]+/items/.+\.json$", n)]
    for n in itens:
        try:
            dados = ler_json(z, n)
        except Exception as e:
            erros.append(f"JAVA item com json quebrado: {n} ({e})")
            continue
        refs = []
        modelos_do_item(dados, refs)
        if not refs:
            erros.append(f"JAVA item sem modelo: {n}")
        for r in refs:
            ns, p = id_para("minecraft", r)
            if ns == "minecraft":
                continue
            if f"assets/{ns}/models/{p}.json" not in nomes:
                erros.append(f"JAVA modelo faltando: {r} (usado em {n})")

    modelos = [n for n in nomes if re.match(r"assets/[^/]+/models/.+\.json$", n)]
    for n in modelos:
        ns_m = n.split("/")[1]
        try:
            dados = ler_json(z, n)
        except Exception as e:
            erros.append(f"JAVA modelo com json quebrado: {n} ({e})")
            continue
        par = dados.get("parent")
        if isinstance(par, str):
            ns, p = id_para("minecraft", par)
            if ns != "minecraft" and not p.startswith("builtin/") and f"assets/{ns}/models/{p}.json" not in nomes:
                erros.append(f"JAVA parent faltando: {par} (em {n})")
        for chave, tex in (dados.get("textures") or {}).items():
            if not isinstance(tex, str) or tex.startswith("#"):
                continue
            ns, p = id_para("minecraft", tex)
            if ns == "minecraft":
                continue
            if f"assets/{ns}/textures/{p}.png" not in nomes:
                erros.append(f"JAVA textura faltando: {tex} (em {n}, '{chave}')")
        # elementos usando textura que nao foi declarada
        decl = set((dados.get("textures") or {}).keys())
        for el in dados.get("elements") or []:
            for face in (el.get("faces") or {}).values():
                t = face.get("texture", "")
                if t.startswith("#") and t[1:] not in decl and not isinstance(par, str):
                    erros.append(f"JAVA face usa #{t[1:]} que nao existe em textures (em {n})")

    for n in [n for n in nomes if re.match(r"assets/[^/]+/equipment/.+\.json$", n)]:
        try:
            dados = ler_json(z, n)
        except Exception as e:
            erros.append(f"JAVA equipamento com json quebrado: {n} ({e})")
            continue
        for camada, lista in (dados.get("layers") or {}).items():
            for item in lista:
                tex = item.get("texture")
                if not tex:
                    continue
                ns, p = id_para("minecraft", tex)
                if ns == "minecraft":
                    continue
                if f"assets/{ns}/textures/entity/equipment/{camada}/{p}.png" not in nomes:
                    erros.append(f"JAVA textura de armadura faltando: {camada}/{tex} (em {n})")

    for n in [n for n in nomes if re.match(r"assets/[^/]+/font/.+\.json$", n)]:
        try:
            dados = ler_json(z, n)
        except Exception as e:
            erros.append(f"JAVA fonte com json quebrado: {n} ({e})")
            continue
        for prov in dados.get("providers") or []:
            f = prov.get("file")
            if prov.get("type") == "bitmap" and f:
                ns, p = id_para("minecraft", f)
                if ns != "minecraft" and f"assets/{ns}/textures/{p}" not in nomes:
                    erros.append(f"JAVA imagem de fonte faltando: {f} (em {n})")
    itens_corona = {re.sub(r"^assets/corona/items/(.+)\.json$", r"\1", n) for n in itens if n.startswith("assets/corona/items/")}
    equipamentos = {re.sub(r"^assets/corona/equipment/(.+)\.json$", r"\1", n) for n in nomes if re.match(r"assets/corona/equipment/.+\.json$", n)}
    return erros, (itens_corona, equipamentos), len(itens), len(modelos)


def conferir_bedrock(caminho):
    erros = []
    z = zipfile.ZipFile(caminho)
    nomes = set(z.namelist())
    pref = ""
    # mcpack pode ter uma pasta raiz
    if "manifest.json" not in nomes:
        raiz = [n for n in nomes if n.endswith("/manifest.json") and n.count("/") == 1]
        if raiz:
            pref = raiz[0].rsplit("/", 1)[0] + "/"

    def existe_tex(p):
        return any((pref + p + ext) in nomes for ext in (".png", ".tga", "")) or p.startswith("textures/entity/") and False

    geos = {}
    for n in nomes:
        if not n.startswith(pref + "models/") or not n.endswith(".json"):
            continue
        try:
            d = ler_json(z, n)
        except Exception as e:
            erros.append(f"BEDROCK geometria com json quebrado: {n} ({e})")
            continue
        for g in d.get("minecraft:geometry") or []:
            gid = (g.get("description") or {}).get("identifier")
            if gid:
                if gid in geos and geos[gid] != n:
                    erros.append(f"BEDROCK geometria duplicada: {gid} em {geos[gid]} e {n}")
                geos[gid] = n
        for k, v in d.items():
            if k.startswith("geometry."):
                geos.setdefault(k.split(":")[0], n)

    itp = pref + "textures/item_texture.json"
    if itp in nomes:
        d = ler_json(z, itp)
        for chave, val in (d.get("texture_data") or {}).items():
            tx = val.get("textures")
            lista = tx if isinstance(tx, list) else [tx]
            for t in lista:
                if isinstance(t, dict):
                    t = t.get("path")
                if isinstance(t, str) and not existe_tex(t):
                    erros.append(f"BEDROCK icone faltando: {t} (item_texture '{chave}')")

    for pasta in ("attachables/", "entity/"):
        for n in [n for n in nomes if n.startswith(pref + pasta) and n.endswith(".json")]:
            try:
                d = ler_json(z, n)
            except Exception as e:
                erros.append(f"BEDROCK json quebrado: {n} ({e})")
                continue
            desc = None
            for k in ("minecraft:attachable", "minecraft:client_entity"):
                if k in d:
                    desc = d[k].get("description") or {}
            if desc is None:
                continue
            for nome_t, t in (desc.get("textures") or {}).items():
                if isinstance(t, str) and not existe_tex(t) and not t.startswith("textures/misc/") and not t.startswith("textures/entity/") or (
                        isinstance(t, str) and t.startswith("textures/entity/") and any(x.startswith(pref + "textures/entity/") for x in nomes) and not existe_tex(t) and "corona" in t):
                    erros.append(f"BEDROCK textura faltando: {t} (em {n}, '{nome_t}')")
            for nome_g, g in (desc.get("geometry") or {}).items():
                if isinstance(g, str) and g.startswith("geometry.") and g not in geos and not g.startswith("geometry.humanoid") and "corona" in g:
                    erros.append(f"BEDROCK geometria nao definida: {g} (em {n})")
    return erros, len(geos)


def conferir_fontes(pasta, itens_corona, equipamentos):
    import os
    erros = []
    exatos, prefixos, equip = {}, {}, {}
    # Key.key("corona", "x/y") , Key.key(NS, "x/" + var) , new NamespacedKey("corona", "x")
    pad = re.compile(r'(?:Key\.key|NamespacedKey)\(\s*(?:"corona"|NS)\s*,\s*"([a-z0-9_/.\-]+)"\s*(\+)?')
    for raiz, _, arqs in os.walk(pasta):
        for a in arqs:
            if not a.endswith(".java"):
                continue
            cam = os.path.join(raiz, a)
            txt = open(cam, encoding="utf-8", errors="replace").read()
            if "ITEM_MODEL" not in txt and "setItemModel" not in txt:
                continue
            for m in pad.finditer(txt):
                antes = txt[max(0, m.start() - 40):m.start()]
                if "assetId(" in antes:
                    equip.setdefault(m.group(1), cam)
                    continue
                if m.group(2):
                    prefixos.setdefault(m.group(1), cam)
                else:
                    exatos.setdefault(m.group(1), cam)
    for mod, cam in sorted(exatos.items()):
        if mod not in itens_corona and not any(i.startswith(mod) for i in itens_corona if mod.endswith("_f0")):
            erros.append(f"PLUGIN usa item_model corona:{mod} que NAO existe no pacote ({cam})")
    for pre, cam in sorted(prefixos.items()):
        if not any(i.startswith(pre) for i in itens_corona):
            erros.append(f"PLUGIN monta item_model corona:{pre}... e o pacote nao tem NENHUM item com esse comeco ({cam})")
    for eq, cam in sorted(equip.items()):
        if eq not in equipamentos:
            erros.append(f"PLUGIN usa armadura (equipment) corona:{eq} que NAO existe no pacote ({cam})")
    return erros, len(exatos) + len(prefixos) + len(equip)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    ej, itens_corona, n_itens, n_mod = conferir_java(sys.argv[1])
    eb, n_geo = conferir_bedrock(sys.argv[2])
    ef, n_usados = ([], 0)
    if len(sys.argv) > 3:
        ef, n_usados = conferir_fontes(sys.argv[3], itens_corona[0], itens_corona[1])
    todos = ej + eb + ef
    print(f"Java: {n_itens} itens, {n_mod} modelos | Bedrock: {n_geo} geometrias | plugins: {n_usados} item_model citados")
    for e in todos[:400]:
        print(" -", e)
    if len(todos) > 400:
        print(f" ... e mais {len(todos) - 400}")
    print("OK: nenhum problema" if not todos else f"PROBLEMAS: {len(todos)}")
    sys.exit(1 if todos else 0)


if __name__ == "__main__":
    main()
