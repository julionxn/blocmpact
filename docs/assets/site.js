// Blocmpact docs: theme, mobile menu, code blocks, "on this page" index and search across pages. No build step.
(function () {
  "use strict";

  var root = document.documentElement;
  var main = document.getElementById("main");

  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch (e) { return null; }
  }

  // ---- theme: system by default, the button pins the opposite of what is showing
  var themeBtn = document.getElementById("theme");
  if (themeBtn) themeBtn.addEventListener("click", function () {
    var dark = root.dataset.theme ? root.dataset.theme === "dark" : matchMedia("(prefers-color-scheme: dark)").matches;
    root.dataset.theme = dark ? "light" : "dark";
    store("blocmpact-theme", root.dataset.theme);
  });

  // ---- mobile menu
  var menuBtn = document.getElementById("menu");
  if (menuBtn) menuBtn.addEventListener("click", function () {
    var open = document.body.classList.toggle("nav-open");
    menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
  });
  document.addEventListener("click", function (e) {
    if (document.body.classList.contains("nav-open") && !e.target.closest(".sidebar") && !e.target.closest("#menu")) {
      document.body.classList.remove("nav-open");
      if (menuBtn) menuBtn.setAttribute("aria-expanded", "false");
    }
  });

  // ---- code blocks: language bar, copy button, highlighting
  var names = { java: "Java", json: "JSON", glsl: "GLSL", groovy: "Gradle", bash: "Shell", plaintext: "Text" };
  Array.prototype.forEach.call(main.querySelectorAll("pre > code"), function (code) {
    var pre = code.parentNode;
    var lang = (code.className.match(/language-(\w+)/) || [])[1] || "plaintext";
    var box = document.createElement("div");
    box.className = "code";
    var bar = document.createElement("div");
    bar.className = "code-bar";
    var label = document.createElement("span");
    label.textContent = names[lang] || lang;
    var copy = document.createElement("button");
    copy.type = "button";
    copy.className = "copy";
    copy.textContent = "Copy";
    copy.addEventListener("click", function () {
      var done = function () { copy.textContent = "Copied"; setTimeout(function () { copy.textContent = "Copy"; }, 1500); };
      if (navigator.clipboard) navigator.clipboard.writeText(code.textContent).then(done, function () {});
    });
    bar.appendChild(label);
    bar.appendChild(copy);
    pre.parentNode.insertBefore(box, pre);
    box.appendChild(bar);
    box.appendChild(pre);
    if (window.hljs && lang !== "plaintext") window.hljs.highlightElement(code);
  });

  // ---- heading anchors and "on this page"
  var heads = main.querySelectorAll("h2[id], h3[id]");
  Array.prototype.forEach.call(heads, function (h) {
    var a = document.createElement("a");
    a.className = "anchor";
    a.href = "#" + h.id;
    a.setAttribute("aria-label", "Link to this section");
    a.textContent = "#";
    h.dataset.label = h.textContent;
    h.appendChild(a);
  });

  var toc = document.getElementById("toc");
  if (toc && heads.length > 1) {
    var list = document.createElement("ol");
    var links = [];
    Array.prototype.forEach.call(heads, function (h) {
      var li = document.createElement("li");
      var a = document.createElement("a");
      a.href = "#" + h.id;
      a.textContent = h.dataset.label;
      if (h.tagName === "H3") a.className = "sub";
      li.appendChild(a);
      list.appendChild(li);
      links.push(a);
    });
    var title = document.createElement("p");
    title.textContent = "On this page";
    toc.appendChild(title);
    toc.appendChild(list);
    var spy = function () {
      var current = 0;
      for (var i = 0; i < heads.length; i++) if (heads[i].getBoundingClientRect().top < 120) current = i;
      links.forEach(function (l, i) { l.classList.toggle("active", i === current); });
    };
    addEventListener("scroll", spy, { passive: true });
    spy();
  }

  // ---- search: every page of the sidebar, indexed by section the first time the box is used
  var input = document.getElementById("q");
  var box = document.getElementById("results");
  if (!input || !box) return;
  var index = null, loading = null, selected = -1;

  var MARKS = new RegExp("[" + String.fromCharCode(0x300) + "-" + String.fromCharCode(0x36f) + "]", "g");   // accents, once split off by NFD
  function fold(s) { return s.normalize("NFD").replace(MARKS, "").toLowerCase(); }

  function load() {
    if (loading) return loading;
    var pages = Array.prototype.map.call(document.querySelectorAll(".sidebar ol a"), function (a) {
      return { url: a.getAttribute("href"), title: a.firstChild.textContent.trim() };
    });
    loading = Promise.all(pages.map(function (p) {
      return fetch(p.url).then(function (r) { return r.text(); }).then(function (html) {
        var doc = new DOMParser().parseFromString(html, "text/html");
        var m = doc.getElementById("main");
        var out = [], cur = { page: p.title, url: p.url, title: p.title, text: "" };
        Array.prototype.forEach.call(m.children, function (el) {
          if (/^H[1-3]$/.test(el.tagName) && el.id) {
            if (cur.text || cur.title !== p.title) out.push(cur);
            cur = { page: p.title, url: p.url + "#" + el.id, title: el.textContent.trim(), text: "" };
          } else {
            cur.text += " " + el.textContent;
          }
        });
        out.push(cur);
        out.forEach(function (s) { s.text = s.text.replace(/\s+/g, " ").trim(); s.ft = fold(s.title); s.fx = fold(s.text); });
        return out;
      }).catch(function () { return []; });
    })).then(function (all) { index = [].concat.apply([], all); return index; });
    return loading;
  }

  function esc(s) { return s.replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;" }[c]; }); }

  function snippet(s, words) {
    var at = -1;
    words.forEach(function (w) { var i = s.fx.indexOf(w); if (i >= 0 && (at < 0 || i < at)) at = i; });
    if (at < 0) return esc(s.text.slice(0, 140));
    var from = Math.max(0, at - 50), piece = s.text.slice(from, from + 160), folded = fold(piece), html = "", pos = 0;
    var hits = [];
    words.forEach(function (w) { var i = folded.indexOf(w); while (i >= 0) { hits.push([i, i + w.length]); i = folded.indexOf(w, i + w.length); } });
    hits.sort(function (a, b) { return a[0] - b[0]; });
    hits.forEach(function (h) { if (h[0] < pos) return; html += esc(piece.slice(pos, h[0])) + "<mark>" + esc(piece.slice(h[0], h[1])) + "</mark>"; pos = h[1]; });
    return (from > 0 ? "…" : "") + html + esc(piece.slice(pos)) + "…";
  }

  function render() {
    var q = fold(input.value.trim());
    selected = -1;
    if (!q) { box.hidden = true; box.innerHTML = ""; return; }
    var words = q.split(/\s+/).filter(Boolean);
    var hits = index.map(function (s) {
      var score = 0;
      for (var i = 0; i < words.length; i++) {
        var inTitle = s.ft.indexOf(words[i]) >= 0, inText = s.fx.indexOf(words[i]) >= 0;
        if (!inTitle && !inText) return null;
        score += inTitle ? 10 : 1;
      }
      return { s: s, score: score };
    }).filter(Boolean).sort(function (a, b) { return b.score - a.score; }).slice(0, 12);
    box.innerHTML = hits.length ? hits.map(function (h, i) {
      return "<li><a id=\"r" + i + "\" href=\"" + esc(h.s.url) + "\"><div class=\"r-page\">" + esc(h.s.page) + "</div><div class=\"r-title\">" +
        esc(h.s.title) + "</div><div class=\"r-snip\">" + snippet(h.s, words) + "</div></a></li>";
    }).join("") : "<li class=\"r-empty\">No results for &ldquo;" + esc(input.value.trim()) + "&rdquo;</li>";
    box.hidden = false;
  }

  function select(i) {
    var items = box.querySelectorAll("a");
    if (!items.length) return;
    selected = (i + items.length) % items.length;
    Array.prototype.forEach.call(items, function (a, k) { a.setAttribute("aria-selected", k === selected ? "true" : "false"); });
    items[selected].scrollIntoView({ block: "nearest" });
  }

  input.addEventListener("focus", load);
  input.addEventListener("input", function () { load().then(render); });
  input.addEventListener("keydown", function (e) {
    if (e.key === "ArrowDown") { e.preventDefault(); select(selected + 1); }
    else if (e.key === "ArrowUp") { e.preventDefault(); select(selected - 1); }
    else if (e.key === "Enter") {
      var items = box.querySelectorAll("a");
      if (items.length) { e.preventDefault(); location.href = items[Math.max(selected, 0)].getAttribute("href"); }
    } else if (e.key === "Escape") { input.value = ""; render(); input.blur(); }
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "/" && document.activeElement !== input && !/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) {
      e.preventDefault();
      input.focus();
    }
  });
  document.addEventListener("click", function (e) { if (!e.target.closest(".search")) box.hidden = true; });
  box.addEventListener("click", function (e) {
    var a = e.target.closest("a");
    if (a && a.pathname === location.pathname) { box.hidden = true; input.value = ""; document.body.classList.remove("nav-open"); }
  });
})();
