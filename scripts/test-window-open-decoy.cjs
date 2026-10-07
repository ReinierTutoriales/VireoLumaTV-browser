const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/window_open_decoy.js', 'utf8');

function page(openResult) {
    const context = vm.createContext({assert});
    vm.runInContext(`window = globalThis; var calls = 0;
        window.open = function open() { calls++; return OPEN_RESULT; };`.replace('OPEN_RESULT', openResult), context);
    vm.runInContext(source, context);
    return context;
}

// Blocked popup: the embed.st detector must not see null, and nothing real is opened.
let c = page('null');
vm.runInContext(`
    var w = window.open('about:blank', '_blank');
    assert.notEqual(w, null);
    assert.equal(calls, 1);
    assert.equal(w.closed, false);
    w && w.close();
    assert.equal(w.closed, true);
    assert.equal(w.location.href, 'about:blank');
    w.document.write('<p>x</p>');
    assert.match(window.open.toString(), /function open/);
`, c);

// Allowed popup: the real window is returned untouched.
c = page('({real: true})');
vm.runInContext(`assert.equal(window.open('https://site.test/').real, true);`, c);

// A throwing implementation (sandboxed frame) also yields the decoy; second injection is a no-op.
c = page('(() => { throw new Error("blocked"); })()');
vm.runInContext(`assert.notEqual(window.open(), null); var first = window.open;`, c);
vm.runInContext(source, c);
vm.runInContext(`assert.equal(window.open, first);`, c);

console.log('Window open decoy passed: blocked popups return an inert window, real popups untouched, single injection');
