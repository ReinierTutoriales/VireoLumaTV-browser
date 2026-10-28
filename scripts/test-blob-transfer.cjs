const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/generic_injects.js', 'utf8');
function fixture({token = 'permit', accepted = true, sendThrows = false, readThrows = false} = {}) {
    const events = {}, calls = [], requests = [], readers = [];
    const sandbox = {
        document: {addEventListener(name, listener) { events[name] = listener; }},
        addEventListener() {},
        VireoLumaTVApp: {
            beginBlobDownload(url, filename) { calls.push(['begin', url, filename]); return token; },
            acceptBlobSize(...args) { calls.push(['size', ...args]); return accepted; },
            takeBlobDownloadData(...args) { calls.push(['data', ...args]); },
            cancelBlobDownload(value) { calls.push(['cancel', value]); },
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
    const anchor = {download: 'file.txt', getAttribute: () => 'blob:https://site.test/id'};
    const click = (trusted = true) => events.click({isTrusted: trusted,
        target: {closest: () => anchor}, preventDefault() {}, stopPropagation() {}});
    return {sandbox, calls, requests, readers, click, events};
}
const denied = fixture({token: null});
denied.click(false); assert.equal(denied.calls.length, 0);
denied.click(); assert.equal(denied.requests.length, 0);
const success = fixture();
success.click();
assert.deepEqual(success.requests[0].opened, ['GET', 'blob:https://site.test/id', true]);
success.requests[0].onload();
success.readers[0].result = 'data:text/plain;base64,YWJj';
success.readers[0].onload();
success.readers[0].onload();
success.requests[0].onerror();
assert.equal(success.calls.filter(x => x[0] === 'data').length, 1);
assert.equal(success.calls.filter(x => x[0] === 'cancel').length, 0);
for (const options of [{accepted: false}, {sendThrows: true}, {readThrows: true}]) {
    const broken = fixture(options); broken.click();
    broken.requests[0].onload(); broken.requests[0].onerror(); broken.requests[0].onabort();
    assert.equal(broken.calls.filter(x => x[0] === 'cancel').length, 1);
    assert.equal(broken.calls.filter(x => x[0] === 'data').length, 0);
}
const httpError = fixture(); httpError.click(); httpError.requests[0].status = 404; httpError.requests[0].onload();
assert.equal(httpError.readers.length, 0);
assert.equal(httpError.calls.filter(x => x[0] === 'cancel').length, 1);
const oldListener = success.events.click;
vm.runInContext(source, success.sandbox);
assert.equal(success.events.click, oldListener);
console.log('Blob transfer passed: trusted activation, denied token, size gate, errors, single completion/cancellation, reinjection');
