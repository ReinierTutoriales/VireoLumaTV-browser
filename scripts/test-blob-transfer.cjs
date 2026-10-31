const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/generic_injects.js', 'utf8');
// Legacy providers have no frame identity: no VireoDownload channel means no interception at all.
{
    const events = {}, requests = [];
    let prevented = false;
    const sandbox = {
        document: {addEventListener(name, listener) { events[name] = listener; }},
        addEventListener() {},
        VireoLumaTVApp: {pageFilters() { return ''; }, trustCertificateOnce() {}},
        XMLHttpRequest: class { constructor() { requests.push(this); } },
    };
    sandbox.window = sandbox;
    vm.createContext(sandbox);
    vm.runInContext(source, sandbox);
    const anchor = {download: 'file.txt', getAttribute: () => 'blob:https://site.test/id'};
    events.click({isTrusted: true, target: {closest: () => anchor},
        preventDefault() { prevented = true; }, stopPropagation() {}});
    assert.equal(prevented, false);
    assert.equal(requests.length, 0);
}

// WebMessage transport: no privileged bridge methods, asynchronous main-frame replies.
function modern({sendThrows = false, readThrows = false} = {}) {
    const events = {}, sent = [], requests = [], readers = [];
    let receive = null;
    const sandbox = {
        document: {addEventListener(name, listener) { events[name] = listener; }},
        addEventListener() {},
        VireoLumaTVApp: {pageFilters() { return ''; }},
        VireoDownload: {
            postMessage(message) { sent.push(message); },
            addEventListener(name, listener) { if (name === 'message') receive = listener; },
        },
        XMLHttpRequest: class {
            constructor() { requests.push(this); this.status = 200; this.response = {size: 3, type: 'text/plain'}; }
            open(...args) { this.opened = args; }
            send() { if (sendThrows) throw Error('network setup'); }
        },
        FileReader: class {
            constructor() { readers.push(this); }
            readAsDataURL(blob) { this.blob = blob; if (readThrows) throw Error('reader setup'); }
        },
    };
    sandbox.window = sandbox;
    vm.createContext(sandbox);
    vm.runInContext(source, sandbox);
    const anchor = {download: 'a\nb.txt', getAttribute: () => 'blob:https://site.test/id'};
    const click = (trusted = true) => events.click({isTrusted: trusted,
        target: {closest: () => anchor}, preventDefault() {}, stopPropagation() {}});
    const reply = data => receive({data});
    return {sandbox, sent, requests, readers, click, reply};
}
const flow = modern();
flow.click();
assert.deepEqual(flow.sent, ['begin\nblob:https://site.test/id\na\nb.txt']);
assert.equal(flow.requests.length, 0);
flow.reply('begin\nblob:https://other.test/id\npermit');
assert.equal(flow.requests.length, 0);
flow.reply('begin\nblob:https://site.test/id\npermit');
flow.reply('begin\nblob:https://site.test/id\npermit');
assert.equal(flow.requests.length, 1);
flow.requests[0].onload();
assert.equal(flow.sent[1], 'size\npermit\n3\ntext/plain');
assert.equal(flow.readers.length, 0);
flow.reply('size\npermit\n1');
flow.readers[0].result = 'data:text/plain;base64,YWJj';
flow.readers[0].onload();
flow.readers[0].onload();
flow.requests[0].onerror();
assert.deepEqual(flow.sent.slice(2), ['data\npermit\nblob:https://site.test/id\ndata:text/plain;base64,YWJj']);
const refusedToken = modern(); refusedToken.click(); refusedToken.reply('begin\nblob:https://site.test/id\n');
assert.equal(refusedToken.requests.length, 0);
const refusedSize = modern(); refusedSize.click(); refusedSize.reply('begin\nblob:https://site.test/id\npermit');
refusedSize.requests[0].onload(); refusedSize.reply('size\npermit\n0');
assert.equal(refusedSize.readers.length, 0);
assert.deepEqual(refusedSize.sent.slice(2), ['cancel\npermit']);
const abandoned = modern(); abandoned.click(); abandoned.reply('begin\nblob:https://site.test/id\npermit');
abandoned.requests[0].onload(); abandoned.requests[0].onerror(); abandoned.reply('size\npermit\n1');
assert.equal(abandoned.readers.length, 0);
assert.equal(abandoned.sent.filter(x => x.startsWith('cancel')).length, 1);
const reinjected = modern(); vm.runInContext(source, reinjected.sandbox); reinjected.click();
reinjected.reply('begin\nblob:https://site.test/id\npermit');
assert.equal(reinjected.requests.length, 1);
const untrusted = modern(); untrusted.click(false); assert.equal(untrusted.sent.length, 0);
const begun = x => { x.click(); x.reply('begin\nblob:https://site.test/id\npermit'); return x; };
const cancels = x => x.sent.filter(m => m.startsWith('cancel')).length;
const unsent = begun(modern({sendThrows: true})); assert.equal(cancels(unsent), 1);
const unread = begun(modern({readThrows: true})); unread.requests[0].onload(); unread.reply('size\npermit\n1');
assert.equal(cancels(unread), 1); assert.equal(unread.sent.filter(m => m.startsWith('data')).length, 0);
const missing = begun(modern()); missing.requests[0].status = 404; missing.requests[0].onload();
assert.equal(cancels(missing), 1); assert.equal(missing.sent.filter(m => m.startsWith('size')).length, 0);
console.log('Blob transfer passed: WebMessage-only transport, legacy fail-closed, trusted clicks, denied token, size gate, errors, single completion/cancellation, reinjection');
