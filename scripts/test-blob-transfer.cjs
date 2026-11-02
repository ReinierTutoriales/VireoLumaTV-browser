const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/generic_injects.js', 'utf8');
const CHUNK = 512 * 1024;

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

// A blob whose slices read as data URLs; the reader is driven by the test.
class FakeBlob {
    constructor(size, start = 0) { this.size = size; this.start = start; this.type = 'text/plain'; }
    slice(from, to) { const part = new FakeBlob(to - from, this.start + from); part.range = [this.start + from, this.start + to]; return part; }
}

// WebMessage transport: no privileged bridge methods, asynchronous main-frame replies.
function modern({size = 3, sendThrows = false, readThrows = false} = {}) {
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
            constructor() { requests.push(this); this.status = 200; this.response = new FakeBlob(size); }
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
    // Completes the latest read with a data URL whose payload names the slice.
    const read = () => { const reader = readers.at(-1); reader.result = 'data:text/plain;base64,' + reader.blob.range.join('-'); reader.onload(); };
    return {sandbox, sent, requests, readers, click, reply, read};
}
const begun = x => { x.click(); x.reply('begin\nblob:https://site.test/id\npermit'); return x; };
const sized = x => { begun(x); x.requests[0].onload(); x.reply('size\npermit\n1'); return x; };
const kinds = (x, kind) => x.sent.filter(m => m.startsWith(kind + '\n'));

// One small chunk, then done; replies for other addresses or repeated replies change nothing.
const flow = modern();
flow.click();
assert.deepEqual(flow.sent, ['begin\nblob:https://site.test/id\na\nb.txt']);
flow.reply('begin\nblob:https://other.test/id\npermit');
assert.equal(flow.requests.length, 0);
flow.reply('begin\nblob:https://site.test/id\npermit');
flow.reply('begin\nblob:https://site.test/id\npermit');
assert.equal(flow.requests.length, 1);
flow.requests[0].onload();
assert.equal(flow.sent[1], 'size\npermit\n3\ntext/plain');
assert.equal(flow.readers.length, 0);
flow.reply('size\npermit\n1');
assert.deepEqual(flow.readers[0].blob.range, [0, 3]);
flow.read();
assert.deepEqual(kinds(flow, 'chunk'), ['chunk\npermit\n0\n0-3']);
assert.equal(kinds(flow, 'done').length, 0);
flow.reply('chunk\npermit\n3');
assert.deepEqual(kinds(flow, 'done'), ['done\npermit\nblob:https://site.test/id']);
flow.requests[0].onerror();
assert.equal(kinds(flow, 'cancel').length, 0);

// A large blob is read one chunk at a time, each only after the previous one was stored.
const large = sized(modern({size: 2 * CHUNK + 5}));
assert.equal(large.readers.length, 1);
large.read();
assert.equal(large.readers.length, 1, 'the next slice waits for the stored reply');
large.reply('chunk\npermit\n' + CHUNK);
assert.deepEqual(large.readers[1].blob.range, [CHUNK, 2 * CHUNK]);
large.read(); large.reply('chunk\npermit\n' + 2 * CHUNK);
assert.deepEqual(large.readers[2].blob.range, [2 * CHUNK, 2 * CHUNK + 5]);
large.read(); large.reply('chunk\npermit\n' + (2 * CHUNK + 5));
assert.deepEqual(kinds(large, 'chunk').map(m => m.split('\n')[2]), ['0', String(CHUNK), String(2 * CHUNK)]);
assert.equal(kinds(large, 'done').length, 1);

// The browser closing the transfer stops it without a second cancellation; no progress cancels.
const closed = sized(modern({size: 2 * CHUNK})); closed.read(); closed.reply('chunk\npermit\n-1');
assert.equal(closed.readers.length, 1); assert.equal(kinds(closed, 'cancel').length, 0); assert.equal(kinds(closed, 'done').length, 0);
const stalled = sized(modern({size: 2 * CHUNK})); stalled.read(); stalled.reply('chunk\npermit\n0');
assert.equal(kinds(stalled, 'cancel').length, 1); assert.equal(stalled.readers.length, 1);

// Refusals, errors and untrusted clicks.
const untrusted = modern(); untrusted.click(false); assert.equal(untrusted.sent.length, 0);
const refusedToken = modern(); refusedToken.click(); refusedToken.reply('begin\nblob:https://site.test/id\n');
assert.equal(refusedToken.requests.length, 0);
const refusedSize = begun(modern()); refusedSize.requests[0].onload(); refusedSize.reply('size\npermit\n0');
assert.equal(refusedSize.readers.length, 0); assert.deepEqual(kinds(refusedSize, 'cancel'), ['cancel\npermit']);
const abandoned = begun(modern()); abandoned.requests[0].onload(); abandoned.requests[0].onerror(); abandoned.reply('size\npermit\n1');
assert.equal(abandoned.readers.length, 0); assert.equal(kinds(abandoned, 'cancel').length, 1);
const unsent = begun(modern({sendThrows: true})); assert.equal(kinds(unsent, 'cancel').length, 1);
const unread = sized(modern({readThrows: true}));
assert.equal(kinds(unread, 'cancel').length, 1); assert.equal(kinds(unread, 'chunk').length, 0);
const badRead = sized(modern()); badRead.readers[0].result = 'data:text/plain;base64,'; badRead.readers[0].onload();
assert.equal(kinds(badRead, 'cancel').length, 1); assert.equal(kinds(badRead, 'chunk').length, 0);
const missing = begun(modern()); missing.requests[0].status = 404; missing.requests[0].onload();
assert.equal(kinds(missing, 'cancel').length, 1); assert.equal(kinds(missing, 'size').length, 0);

// Reinjection keeps one click listener and one reply listener.
const reinjected = modern(); vm.runInContext(source, reinjected.sandbox); begun(reinjected);
assert.equal(reinjected.requests.length, 1);
console.log('Blob transfer passed: WebMessage-only chunked transport with acknowledged chunks, legacy fail-closed, trusted clicks, refusals, errors, single completion, reinjection');
