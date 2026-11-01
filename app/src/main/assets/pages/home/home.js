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
    // Messages in the app's language, filled in by the app when it serves the page.
    var TEXT = {};
    try {
        var meta = document.querySelector && document.querySelector('meta[name="vireo-strings"]');
        if (meta) TEXT = JSON.parse(meta.getAttribute('content')) || {};
    } catch (_) { /* Without them the keys show; search and shortcuts keep working. */ }
    function t(key) { return typeof TEXT[key] === 'string' ? TEXT[key] : key; }
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

    // The browser's favorites, read from this page's own favorites.json; empty until they arrive.
    var favorites = [];
    var FAVORITES_MAX = 48;

    function readFavorites(data) {
        var result = [];
        if (!Array.isArray(data)) return result;
        data.forEach(function (entry) {
            if (result.length >= FAVORITES_MAX || !entry || typeof entry !== 'object') return;
            var url = address(entry.url);
            if (!url || result.some(function (known) { return known.url.href === url.href; })) return;
            var title = typeof entry.title === 'string' ? entry.title.trim().slice(0, 200) : '';
            result.push({url: url, title: title});
        });
        return result;
    }

    var youtubeHidden = false;
    try { youtubeHidden = localStorage.getItem(DEFAULT_KEY) === '1'; } catch (_) { /* YouTube stays shown. */ }

    /** Writes first; the page changes only once the store accepted the change. */
    function setYoutubeHidden(hidden) {
        try {
            if (hidden) localStorage.setItem(DEFAULT_KEY, '1');
            else localStorage.removeItem(DEFAULT_KEY);
        } catch (_) { alert(t('saveFailed')); return false; }
        youtubeHidden = hidden;
        return true;
    }

    function saveList(next) {
        try { localStorage.setItem(KEY, JSON.stringify(next)); } catch (_) { alert(t('saveFailed')); return false; }
        saved = next;
        return true;
    }

    /** Edits one saved shortcut: a new address replaces it, an empty one removes it after a question. */
    function editSaved(value) {
        var replacement = prompt(t('editPrompt'), value);
        if (replacement === null) return;
        var next = saved.slice();
        var position = next.indexOf(value);
        if (position < 0) return;
        if (!replacement.trim()) {
            if (!confirm(t('removeConfirm'))) return;
            next.splice(position, 1);
        } else {
            var valid = address(replacement);
            if (!valid) { alert(t('invalid')); return; }
            if (next.some(function (entry, i) { return i !== position && entry === valid.href; })) {
                alert(t('duplicate'));
                return;
            }
            next[position] = valid.href;
        }
        if (saveList(next)) render(position);
    }

    /**
     * One shortcut before the add control: the site's icon when the browser has it cached, or a
     * globe, and a short name, so shortcuts stay distinguishable without their icons.
     */
    function tile(url, label, name) {
        var link = document.createElement('a');
        link.href = url.href;
        link.setAttribute('aria-label', label);
        link.setAttribute('data-saved', 'true');
        var icon = document.createElement('img');
        icon.width = icon.height = 40;
        icon.alt = '';
        // The browser serves only cached icons; showing a shortcut does not contact that site.
        icon.addEventListener('error', function fallbackIcon() {
            icon.removeEventListener('error', fallbackIcon);
            icon.src = fallback;
        });
        icon.src = 'favicon://' + url.hostname;
        link.appendChild(icon);
        var caption = document.createElement('span');
        caption.textContent = name || url.hostname.replace(/^www\./, '');
        link.appendChild(caption);
        list.insertBefore(link, add);
        return link;
    }

    function render(focusIndex) {
        if (list.classList) list.classList.toggle('editing', editing);
        if (edit) {
            edit.setAttribute('aria-pressed', String(editing));
            edit.textContent = editing ? t('done') : t('edit');
        }
        if (defaultLink) {
            // While editing, a removed YouTube stays visible, dimmed, so it can be brought back.
            var shown = !youtubeHidden || editing;
            defaultLink.hidden = !shown;
            defaultLink.style.display = shown ? '' : 'none';
            if (defaultLink.classList) defaultLink.classList.toggle('removed', youtubeHidden);
            defaultLink.setAttribute('aria-label', youtubeHidden ? t('youtubeRemoved') : 'YouTube');
        }
        Array.prototype.forEach.call(list.querySelectorAll('[data-saved]'), function (node) { list.removeChild(node); });
        var shown = [];
        // Favorites first, as the browser's Favorites list has them; they are managed there.
        favorites.forEach(function (favorite) {
            var link = tile(favorite.url, favorite.title || favorite.url.hostname, favorite.title);
            link.setAttribute('data-favorite', 'true');
            link.addEventListener('click', function (event) {
                if (!editing) return;
                event.preventDefault();
                alert(t('favoriteManaged'));
            });
            shown.push(favorite.url.href);
        });
        var links = [];
        saved.forEach(function (value) {
            if (shown.indexOf(value) !== -1) return;
            var url = address(value);
            var link = tile(url, editing ? t('editItem').replace('%s', url.hostname) : url.hostname, '');
            link.addEventListener('click', function (event) {
                if (!editing) return;
                event.preventDefault();
                editSaved(value);
            });
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
            if (confirm(t('youtubeRestore')) && setYoutubeHidden(false)) render();
        } else if (confirm(t('youtubeRemove')) && setYoutubeHidden(true)) render();
    });

    add.addEventListener('click', function () {
        if (saved.length >= MAX) { alert(t('limit')); return; }
        var value = prompt(t('addPrompt'));
        if (value === null) return;
        var url = address(value);
        if (!url) { alert(t('invalid')); return; }
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
            input.setCustomValidity(t('emptySearch'));
            if (typeof input.reportValidity === 'function') input.reportValidity();
        }
    });
    render();
    if (typeof fetch === 'function') {
        fetch('favorites.json', {cache: 'no-store'})
            .then(function (response) { return response.ok ? response.json() : []; })
            .then(function (data) {
                favorites = readFavorites(data);
                if (favorites.length) render();
            })
            .catch(function () { /* Without favorites the page still has search and its shortcuts. */ });
    }
})();
