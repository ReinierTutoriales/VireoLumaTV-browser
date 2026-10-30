(function () {
    'use strict';
    var KEY = 'vireo.home.shortcuts.v1';
    var MAX = 24;
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
            list.insertBefore(link, add);
        });
    }

    add.addEventListener('click', function () {
        if (saved.length >= MAX) { alert('Máximo de 24 marcadores.'); return; }
        var value = prompt('Dirección del marcador (https://…)');
        if (value === null) return;
        var url = address(value);
        if (!url) { alert('Dirección no válida. Usa http:// o https:// sin usuario ni contraseña.'); return; }
        if (url.href === 'https://www.youtube.com/' || saved.indexOf(url.href) !== -1) return;
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
