(function () {
  "use strict";
  var $ = function (id) { return document.getElementById(id); };
  var estado = { loja: null, produto: null, pedido: null, timer: null, relogio: null, cat: "vip" };

  function reais(v) { return "R$ " + Number(v).toFixed(2).replace(".", ","); }
  function el(tag, cls, txt) { var e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; }
  function aviso(t) { var a = $("aviso"); a.textContent = t; a.classList.remove("escondido"); clearTimeout(aviso.t); aviso.t = setTimeout(function () { a.classList.add("escondido"); }, 2200); }
  function copiar(t) {
    if (navigator.clipboard && window.isSecureContext) { navigator.clipboard.writeText(t).then(function () { aviso("Copiado!"); }, function () { copiarVelho(t); }); }
    else copiarVelho(t);
  }
  function copiarVelho(t) { var i = el("textarea"); i.value = t; document.body.appendChild(i); i.select(); try { document.execCommand("copy"); aviso("Copiado!"); } catch (e) { } document.body.removeChild(i); }

  function api(url, corpo) {
    var op = corpo ? { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(corpo).toString() } : {};
    return fetch(url, op).then(function (r) { return r.json(); });
  }

  function carregar() {
    api("/api/loja").then(function (d) {
      if (!d.ok) return;
      estado.loja = d;
      var s = d.servidor;
      $("nomeServidor").textContent = s.nome;
      document.title = s.nome + ": loja oficial e como entrar";
      if (s.discord) Array.prototype.forEach.call(document.querySelectorAll("[data-discord]"), function (a) { a.href = s.discord; });
      online(s);
      $("ipJava").textContent = s.ipJava;
      $("ipBedrock").textContent = s.ipBedrock + (s.portaBedrock ? ", porta " + s.portaBedrock : "");
      $("campoEmail").classList.toggle("escondido", !d.pedeEmail);
      $("email").required = !!d.pedeEmail;
      desenhar();
    }).catch(function () {
      $("status").classList.add("off");
      $("online").textContent = "loja fora do ar agora";
    });
  }

  function online(s) {
    $("status").classList.remove("off");
    $("online").textContent = s.online > 0 ? s.online + (s.online === 1 ? " jogando agora" : " jogando agora") : "Servidor online";
  }

  var EXPLICA = {
    vip: "Cada VIP vale 30 dias e não renova sozinho. Os benefícios chegam na hora.",
    coronas: "Coronas são o dinheiro do servidor. Use nos mercadores das masmorras e no Mercador Lendário.",
    extras: "Eliminado num minigame de terror? Gaste uma Moeda de Reviver e volte para a partida.",
    historico: ""
  };

  function desenhar() {
    var g = $("produtos");
    g.innerHTML = "";
    $("catExplica").textContent = EXPLICA[estado.cat] || "";
    if (!estado.loja) return;
    var lista = estado.loja.produtos.filter(function (p) { return p.categoria === estado.cat; });
    if (!lista.length) g.appendChild(el("p", "vazio", "Nada aqui por enquanto. Volte em breve."));
    lista.forEach(function (p) {
      var c = el("article", "cartao" + (p.destaque ? " destaque" : ""));
      c.style.setProperty("--cor", p.cor || "#C8102E");
      if (p.destaque) c.appendChild(el("span", "tag-destaque", p.destaque));
      c.appendChild(el("h3", null, p.nome));
      if (p.descricao) c.appendChild(el("p", "desc", p.descricao));
      var pr = el("div", "preco", reais(p.preco));
      if (p.categoria === "vip") pr.appendChild(el("small", null, "por 30 dias"));
      c.appendChild(pr);
      var ul = el("ul");
      (p.beneficios || []).forEach(function (b) { ul.appendChild(el("li", null, b)); });
      c.appendChild(ul);
      var aberta = estado.loja.aberta;
      var bt = el("button", "botao", aberta ? "Comprar " + p.nome : "Em breve");
      bt.disabled = !aberta;
      if (aberta) bt.addEventListener("click", function () { abrir(p); });
      c.appendChild(bt);
      g.appendChild(c);
    });
  }

  function trocarAba(cat) {
    estado.cat = cat;
    Array.prototype.forEach.call(document.querySelectorAll(".aba"), function (a) { a.classList.toggle("ativa", a.dataset.cat === cat); a.setAttribute("aria-selected", a.dataset.cat === cat ? "true" : "false"); });
    $("catExplica").classList.toggle("escondido", cat === "historico");
    $("produtos").classList.toggle("escondido", cat === "historico");
    $("historico").classList.toggle("escondido", cat !== "historico");
    if (cat !== "historico") desenhar();
  }

  function passo(n) { [1, 2, 3].forEach(function (i) { $("passo" + i).classList.toggle("escondido", i !== n); }); }

  function abrir(p) {
    estado.produto = p;
    $("mTitulo").textContent = p.nome;
    $("mPreco").textContent = reais(p.preco);
    $("erro").textContent = "";
    var salvo = "";
    try { salvo = localStorage.getItem("nick") || ""; } catch (e) { }
    if (salvo && !$("nick").value) $("nick").value = salvo;
    passo(1);
    $("modal").classList.remove("escondido");
    setTimeout(function () { $("nick").focus(); }, 50);
  }

  function fechar() {
    $("modal").classList.add("escondido");
    clearInterval(estado.timer); clearInterval(estado.relogio);
    estado.timer = estado.relogio = null;
  }

  function mostrarPedido(d) {
    estado.pedido = d;
    $("pPreco").textContent = d.produto + ": " + reais(d.valor);
    $("codigo").textContent = d.codigo;
    var auto = d.modo === "auto";
    $("pixAuto").classList.toggle("escondido", !auto);
    $("pixManual").classList.toggle("escondido", auto);
    if (auto) {
      if (d.qrImg) { $("qrImg").src = "data:image/png;base64," + d.qrImg; $("qrImg").classList.remove("escondido"); }
      else $("qrImg").classList.add("escondido");
      $("qrTexto").value = d.qr || "";
    } else {
      $("mvValor").textContent = reais(d.valor);
      $("chave").value = d.chave || "(chave Pix ainda não configurada — fale com a equipe)";
      $("recebedor").textContent = d.recebedor ? "Recebedor: " + d.recebedor : "";
      $("mvCodigo").textContent = d.codigo;
    }
    passo(2);
    clearInterval(estado.timer);
    estado.timer = setInterval(function () { conferir(d.codigo); }, 4000);
    clearInterval(estado.relogio);
    var fim = d.expiraEm;
    var tick = function () {
      var r = Math.max(0, Math.floor((fim - Date.now()) / 1000));
      var h = Math.floor(r / 3600), m = Math.floor((r % 3600) / 60), s = r % 60;
      $("contador").textContent = "(" + (h ? h + "h " : "") + m + ":" + (s < 10 ? "0" : "") + s + ")";
    };
    tick();
    estado.relogio = setInterval(tick, 1000);
  }

  function conferir(codigo) {
    api("/api/pedido?codigo=" + encodeURIComponent(codigo)).then(function (d) {
      if (!d.ok) return;
      if (d.status === "pago" || d.status === "entregue") {
        clearInterval(estado.timer); clearInterval(estado.relogio);
        $("okTitulo").textContent = d.status === "entregue" ? "Compra entregue!" : "Pagamento confirmado!";
        $("okTexto").textContent = d.status === "entregue"
          ? d.produto + " já está no personagem " + d.jogador + ". Bom jogo!"
          : d.produto + " será entregue para " + d.jogador + " assim que você estiver no servidor (em segundos se já estiver online).";
        passo(3);
      } else if (d.status === "expirado" || d.status === "cancelado") {
        clearInterval(estado.timer); clearInterval(estado.relogio);
        passo(1);
        $("erro").textContent = d.status === "expirado" ? "O Pix expirou. Gere um novo." : "O pagamento foi cancelado. Gere um novo Pix.";
      }
    }).catch(function () { });
  }

  $("formCompra").addEventListener("submit", function (e) {
    e.preventDefault();
    var nick = $("nick").value.trim();
    var bt = $("btnPagar");
    $("erro").textContent = "";
    bt.disabled = true; bt.textContent = "Gerando Pix…";
    try { localStorage.setItem("nick", nick); } catch (x) { }
    api("/api/comprar", { produto: estado.produto.id, jogador: nick, email: $("email").value.trim() }).then(function (d) {
      if (!d.ok) { $("erro").textContent = d.erro || "Não deu certo. Tente de novo."; return; }
      mostrarPedido(d);
    }).catch(function () {
      $("erro").textContent = "Sem conexão com a loja. Tente de novo.";
    }).then(function () { bt.disabled = false; bt.textContent = "Gerar Pix"; });
  });

  $("formHistorico").addEventListener("submit", function (e) {
    e.preventDefault();
    var nick = $("nickHistorico").value.trim();
    var l = $("listaHistorico");
    l.innerHTML = "";
    api("/api/historico?jogador=" + encodeURIComponent(nick)).then(function (d) {
      if (!d.ok) { l.appendChild(el("p", "erro", d.erro)); return; }
      if (!d.pedidos.length) { l.appendChild(el("p", "miudo", "Nenhuma compra encontrada para esse nick.")); return; }
      var nomes = { aguardando: "aguardando pagamento", pago: "pago · entrega ao entrar", entregue: "entregue", cancelado: "cancelado", expirado: "expirado" };
      d.pedidos.forEach(function (p) {
        var it = el("div", "item");
        var info = el("span");
        info.appendChild(el("b", null, p.produto + " "));
        info.appendChild(document.createTextNode(reais(p.valor) + ", em " + p.data));
        it.appendChild(info);
        var t = el("span", "tag " + p.status, nomes[p.status] || p.status);
        it.appendChild(t);
        l.appendChild(it);
      });
    });
  });

  Array.prototype.forEach.call(document.querySelectorAll(".aba"), function (a) { a.addEventListener("click", function () { trocarAba(a.dataset.cat); }); });
  Array.prototype.forEach.call(document.querySelectorAll(".ip"), function (b) {
    b.addEventListener("click", function () {
      var s = estado.loja && estado.loja.servidor;
      if (!s) return;
      copiar(b.dataset.copiar === "java" ? s.ipJava : s.ipBedrock);
    });
  });
  $("fechar").addEventListener("click", fechar);
  $("okFechar").addEventListener("click", fechar);
  $("modal").addEventListener("click", function (e) { if (e.target === $("modal") && !$("passo2").offsetParent) fechar(); });
  $("copiarQr").addEventListener("click", function () { copiar($("qrTexto").value); });
  $("copiarChave").addEventListener("click", function () { copiar($("chave").value); });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape" && $("passo2").classList.contains("escondido")) fechar(); });

  // pedido feito no jogo (totem do spawn): abre o Pix pelo codigo
  function abrirCodigo(codigo, silencioso) {
    codigo = (codigo || "").trim().toUpperCase();
    if (!codigo) return;
    api("/api/pedido?codigo=" + encodeURIComponent(codigo)).then(function (d) {
      if (!d.ok) { if (!silencioso) aviso("Pedido não encontrado. Confira o código."); return; }
      $("erro").textContent = "";
      $("modal").classList.remove("escondido");
      if (d.status === "aguardando") mostrarPedido(d);
      else if (d.status === "pago" || d.status === "entregue") { passo(3); conferir(codigo); }
      else { aviso("Esse Pix expirou. Clique no totem de novo para gerar outro."); $("modal").classList.add("escondido"); }
    }).catch(function () { aviso("Sem conexão com a loja."); });
  }
  $("formCodigo").addEventListener("submit", function (e) { e.preventDefault(); abrirCodigo($("codigoPedido").value); });
  try { var q = new URLSearchParams(location.search).get("pedido"); if (q) abrirCodigo(q, true); } catch (e) { }

  Array.prototype.forEach.call(document.querySelectorAll("[data-ir]"), function (b) {
    b.addEventListener("click", function () { trocarAba(b.dataset.ir); $("produtos-titulo").scrollIntoView(); });
  });

  // coroa grande em pixels (a mesma do icone do servidor)
  (function () {
    var desenho = [
      "#......#......#",
      "##....###....##",
      "###..#####..###",
      "####.#####.####",
      "###############",
      "###J###J###J###",
      "...............",
      "###############",
      "###############"
    ];
    var c = $("coroaGrande");
    if (!c) return;
    desenho.forEach(function (linha, y) {
      linha.split("").forEach(function (ch, x) {
        var i = document.createElement("i");
        if (ch === "#") i.className = "o";
        else if (ch === "J") i.className = "j";
        i.style.setProperty("--d", (desenho.length - 1 - y) * 15 + x);
        c.appendChild(i);
      });
    });
  })();

  desenhar();
  carregar();
  setInterval(function () { api("/api/loja").then(function (d) { if (d.ok) online(d.servidor); }).catch(function () { }); }, 30000);
})();
