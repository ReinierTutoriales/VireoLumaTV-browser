(function () {
    'use strict';
    var KEY = 'vireo.home.shortcuts.v1';
    var MAX = 24;
    var DEFAULT_KEY = 'vireo.home.youtube.hidden.v1';
    var defaultLink = document.getElementById('default-youtube');
    var edit = document.getElementById('edit-bookmarks');
    var editing = false;
    var youtubeHidden = false;
    try { youtubeHidden = localStorage.getItem(DEFAULT_KEY) === '1'; } catch (_) { /* Keep default visible. */ }
    var list = document.getElementById('bookmarks');
    var add = document.getElementById('add-bookmark');
    var input = document.getElementById('search-input');
    var fallback = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 40"><circle cx="20" cy="20" r="16" fill="none" stroke="#718096" stroke-width="3"/><path d="M4 20h32M20 4c-10 10-10 22 0 32M20 4c10 10 10 22 0 32" fill="none" stroke="#718096" stroke-width="2"/></svg>');

    function address(value) {
        if (typeof value !== 'string' || value.length > 2048) return null;
        try {
            var text = value.trim();
            if (!text) return null;
            var url = new URL(/^[a-z][a-z0-9+.-]*:/i.test(text) ? text : 'https://' + text);
            if (url.href.length > 2048) return null;
            if ((url.protocol !== 'https:' && url.protocol !== 'http:') || !url.hostname || url.username || url.password) return null;
            return url;
        } catch (_) { return null; }
    }

    var saved = [];
    try {
        var raw = localStorage.getItem(KEY) || '[]';
        var data = raw.length <= 65536 ? JSON.parse(raw) : [];
        if (Array.isArray(data)) data.slice(0, MAX).forEach(function (value) {
            var url = address(value);
            if (url && saved.indexOf(url.href) === -1) saved.push(url.href);
        });
    } catch (_) { /* A blocked or damaged store must not disable search or YouTube. */ }

    function render() {
        defaultLink.hidden = youtubeHidden;
        defaultLink.style.display = youtubeHidden ? 'none' : '';
        edit.setAttribute('aria-pressed', String(editing));
        Array.prototype.forEach.call(list.querySelectorAll('[data-saved]'), function (node) { list.removeChild(node); });
        saved.forEach(function (value) {
            var url = address(value);
            var link = document.createElement('a');
            link.href = url.href;
            link.setAttribute('aria-label', url.hostname);
            link.setAttribute('data-saved', 'true');
            var icon = document.createElement('img');
            icon.width = icon.height = 40;
            icon.alt = '';
            // The browser serves only cached icons; adding a bookmark does not contact that site.
            icon.addEventListener('error', function fallbackIcon() {
                icon.removeEventListener('error', fallbackIcon);
                icon.src = fallback;
            });
            icon.src = 'favicon://' + url.hostname;
            link.appendChild(icon);
            if (editing) {
                link.addEventListener('click', function (event) {
                    event.preventDefault();
                    var replacement = prompt('Editar dirección (vacío para eliminar)', value);
                    if (replacement === null) return;
                    var next = saved.slice();
                    var position = next.indexOf(value);
                    if (position < 0) return;
                    if (!replacement.trim()) next.splice(position, 1);
                    else {
                        var valid = address(replacement);
                        if (!valid) { alert('Dirección no válida.'); return; }
                        if (next.some(function (entry, i) { return i !== position && entry === valid.href; })) return;
                        next[position] = valid.href;
                    }
                    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch (_) { alert('No se pudo guardar.'); return; }
                    saved = next;
                    render();
                });
            }
            list.insertBefore(link, add);
        });
    }

    edit.addEventListener('click', function () { editing = !editing; render(); });
    defaultLink.addEventListener('click', function (event) {
        if (!editing) return;
        event.preventDefault();
        if (!confirm('¿Eliminar YouTube del inicio?')) return;
        try { localStorage.setItem(DEFAULT_KEY, '1'); } catch (_) { alert('No se pudo guardar.'); return; }
        youtubeHidden = true;
        render();
    });

    add.addEventListener('click', function () {
        if (saved.length >= MAX) { alert('Máximo de 24 marcadores.'); return; }
        var value = prompt('Dirección del marcador (https://…)');
        if (value === null) return;
        var url = address(value);
        if (!url) { alert('Dirección no válida. Usa http:// o https:// sin usuario ni contraseña.'); return; }
        if (url.href === 'https://m.youtube.com/' || url.href === 'https://www.youtube.com/' || saved.indexOf(url.href) !== -1) return;
        var next = saved.concat(url.href);
        try { localStorage.setItem(KEY, JSON.stringify(next)); }
        catch (_) { alert('No se pudo guardar el marcador.'); return; }
        saved = next;
        render();
        var links = list.querySelectorAll('[data-saved]');
        if (links.length) links[links.length - 1].focus();
    });

    input.addEventListener('input', function () { input.setCustomValidity(''); });
    document.querySelector('form').addEventListener('submit', function (event) {
        input.value = input.value.trim();
        if (!input.value) {
            event.preventDefault();
            input.setCustomValidity('Escribe una búsqueda.');
            if (typeof input.reportValidity === 'function') input.reportValidity();
        }
    });
    render();
})();
