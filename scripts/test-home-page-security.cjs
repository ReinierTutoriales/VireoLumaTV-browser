const fs = require('node:fs');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/pages/home/index.html', 'utf8');
// Native Home keeps only a history/storage document in WebView: no DOM UI or startup request.
assert.match(html, /<body><\/body>/);
assert.doesNotMatch(html, /<(script|iframe|form|input|button|img|svg|link)\b/i);
assert.doesNotMatch(html, /\bon\w+\s*=|fetch\(|XMLHttpRequest|@import|url\s*\(/i);
const csp = html.match(/http-equiv="Content-Security-Policy" content="([^"]*)"/)[1];
assert.match(csp, /default-src 'none'/);
assert.match(csp, /form-action 'none'/);
assert.match(csp, /base-uri 'none'/);
// Both app-owned documents retain the black, system-font presentation.
for (const page of [html, fs.readFileSync('app/src/main/assets/pages/warning-certificate.html', 'utf8')]) {
    assert.match(page, /name="color-scheme" content="dark"/);
    assert.doesNotMatch(page, /prefers-color-scheme|color-scheme[^;<>]*light/);
    const body = Array.from(page.match(/<style[^>]*>([\s\S]*?)<\/style>/i)[1].matchAll(/([^{}]+)\{([^{}]*)\}/g)).find(match => match[1].trim() === 'body')[2];
    assert.match(body, /background:\s*#000(?:000)?\s*;/);
    assert.match(body, /sans-serif/);
}
assert.equal(fs.existsSync('app/src/main/assets/pages/home/home.js'), false);
console.log('Home document passed: no DOM UI, script, remote asset or network connection; native behavior covered by Android tests');
