(function () {
    'use strict';
    // Stored formats are kept as earlier versions wrote them: a JSON list of addresses, and '1'
    // when the user removed the built-in YouTube shortcut.
    var KEY = 'vireo.home.shortcuts.v1';
    var DEFAULT_KEY = 'vireo.home.youtube.hidden.v1';
    var MAX = 24;
    var YOUTUBE = ['https://m.youtube.com/', 'https://www.youtube.com/', 'https://youtube.com/'];
    var list = document.getElementById('bookmarks');
    var add = document.getElementById('add-bookmark');
    var input = document.getElementById('search-input');
    // Either control may be missing from an older page; search and shortcuts must keep working.
    var defaultLink = document.getElementById('default-youtube');
    var edit = document.getElementById('edit-bookmarks');
    var editing = false;
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

    var youtubeHidden = false;
    try { youtubeHidden = localStorage.getItem(DEFAULT_KEY) === '1'; } catch (_) { /* YouTube stays shown. */ }

    /** Writes first; the page changes only once the store accepted the change. */
    function setYoutubeHidden(hidden) {
        try {
            if (hidden) localStorage.setItem(DEFAULT_KEY, '1');
            else localStorage.removeItem(DEFAULT_KEY);
        } catch (_) { alert('No se pudo guardar.'); return false; }
        youtubeHidden = hidden;
        return true;
    }

    function saveList(next) {
        try { localStorage.setItem(KEY, JSON.stringify(next)); } catch (_) { alert('No se pudo guardar.'); return false; }
        saved = next;
        return true;
    }

    /** Edits one saved shortcut: a new address replaces it, an empty one removes it after a question. */
    function editSaved(value) {
        var replacement = prompt('Editar dirección. Déjala vacía para eliminar el acceso.', value);
        if (replacement === null) return;
        var next = saved.slice();
        var position = next.indexOf(value);
        if (position < 0) return;
        if (!replacement.trim()) {
            if (!confirm('¿Eliminar este acceso del inicio?')) return;
            next.splice(position, 1);
        } else {
            var valid = address(replacement);
            if (!valid) { alert('Dirección no válida. Usa http:// o https:// sin usuario ni contraseña.'); return; }
            if (next.some(function (entry, i) { return i !== position && entry === valid.href; })) {
                alert('Ese acceso ya está en el inicio.');
                return;
            }
            next[position] = valid.href;
        }
        if (saveList(next)) render(position);
    }

    function render(focusIndex) {
        if (list.classList) list.classList.toggle('editing', editing);
        if (edit) {
            edit.setAttribute('aria-pressed', String(editing));
            edit.textContent = editing ? 'Listo' : 'Editar';
        }
        if (defaultLink) {
            // While editing, a removed YouTube stays visible, dimmed, so it can be brought back.
            var shown = !youtubeHidden || editing;
            defaultLink.hidden = !shown;
            defaultLink.style.display = shown ? '' : 'none';
            if (defaultLink.classList) defaultLink.classList.toggle('removed', youtubeHidden);
            defaultLink.setAttribute('aria-label', youtubeHidden ? 'YouTube (eliminado, pulsa para restaurarlo)' : 'YouTube');
        }
        Array.prototype.forEach.call(list.querySelectorAll('[data-saved]'), function (node) { list.removeChild(node); });
        var links = [];
        saved.forEach(function (value) {
            var url = address(value);
            var link = document.createElement('a');
            link.href = url.href;
            link.setAttribute('aria-label', editing ? 'Editar ' + url.hostname : url.hostname);
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
            link.addEventListener('click', function (event) {
                if (!editing) return;
                event.preventDefault();
                editSaved(value);
            });
            list.insertBefore(link, add);
            links.push(link);
        });
        // After a change the remote stays where it was, or on the nearest remaining shortcut.
        if (typeof focusIndex === 'number') {
            var target = links[Math.min(focusIndex, links.length - 1)] || edit || add;
            if (target && target.focus) target.focus();
        }
    }

    if (edit) edit.addEventListener('click', function () { editing = !editing; render(); });
    if (defaultLink) defaultLink.addEventListener('click', function (event) {
        if (!editing) return;
        event.preventDefault();
        if (youtubeHidden) {
            if (confirm('¿Volver a mostrar YouTube en el inicio?') && setYoutubeHidden(false)) render();
        } else if (confirm('¿Eliminar YouTube del inicio?') && setYoutubeHidden(true)) render();
    });

    add.addEventListener('click', function () {
        if (saved.length >= MAX) { alert('Máximo de 24 marcadores.'); return; }
        var value = prompt('Dirección del marcador (https://…)');
        if (value === null) return;
        var url = address(value);
        if (!url) { alert('Dirección no válida. Usa http:// o https:// sin usuario ni contraseña.'); return; }
        if (YOUTUBE.indexOf(url.href) !== -1) {
            // YouTube has its own shortcut: adding it brings that one back instead of a copy.
            if (youtubeHidden && defaultLink && setYoutubeHidden(false)) render();
            return;
        }
        if (saved.indexOf(url.href) !== -1) return;
        if (saveList(saved.concat(url.href))) render(saved.length - 1);
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
