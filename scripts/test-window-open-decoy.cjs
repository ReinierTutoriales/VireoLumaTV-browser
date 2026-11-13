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
    w.location = 'https://popunder.example/ad';
    assert.equal(w.location.href, 'https://popunder.example/ad');
    w.document.open().write('<p>x</p>');
    w.document.body.appendChild(w.document.createElement('script'));
    assert.equal(w.document.readyState, 'complete');
    assert.match(window.open.toString(), /function open/);
    // Detectors read the source through Function.prototype.toString and list window keys.
    assert.equal(Function.prototype.toString.call(window.open).indexOf('function open() { calls++;'), 0);
    assert.equal(window.open.name, 'open');
    assert.equal(Function.prototype.toString.call(Function.prototype.toString), 'function toString() { [native code] }');
    assert.equal(Object.keys(window).indexOf('__vireoOpenDecoy'), -1);
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
