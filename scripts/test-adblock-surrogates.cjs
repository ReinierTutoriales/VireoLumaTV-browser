const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const read = name => fs.readFileSync('app/src/main/assets/surrogates/' + name, 'utf8');
function page(setup) {
    const timers = [];
    const context = vm.createContext({assert, Proxy, Symbol, setTimeout: fn => timers.push(fn)});
    vm.runInContext('window = globalThis;' + (setup || ''), context);
    context.flush = () => { while (timers.length) timers.shift()(); };
    return context;
}
(async () => {
    // adsbygoogle: queued array keeps its identity, pushes are inert, detection sees "loaded".
    let c = page('window.adsbygoogle = [{}];');
    vm.runInContext(read('adsbygoogle.js'), c);
    vm.runInContext(`assert.equal(adsbygoogle.loaded, true); adsbygoogle.push({}); assert.equal(adsbygoogle.length, 1);`, c);
    c = page();
    vm.runInContext(read('adsbygoogle.js') + `; (adsbygoogle = window.adsbygoogle || []).push({}); assert.equal(adsbygoogle.loaded, true);`, c);

    // GPT: queued and later commands run; chained API calls never throw.
    c = page('var ran = 0; window.googletag = {cmd: [function () { ran++; }]};');
    vm.runInContext(read('gpt.js'), c);
    vm.runInContext(`
        assert.equal(ran, 1);
        googletag.cmd.push(function () {
            googletag.defineSlot('/1/x', [300, 250], 'div').addService(googletag.pubads());
            googletag.pubads().enableSingleRequest().addEventListener('slotRenderEnded', function () {});
            googletag.enableServices(); googletag.display('div'); ran++;
        });
        assert.equal(ran, 2);
        assert.equal(googletag.apiReady, true);
        assert.equal(String(googletag.pubads()), '');
    `, c);

    // analytics.js: hitCallback from the queue and from later calls runs; no network.
    c = page('var hits = 0; window.ga = function () {}; ga.q = [["send", "pageview", {hitCallback: function () { hits++; }}]];');
    vm.runInContext(read('analytics.js'), c);
    vm.runInContext(`ga('send', 'event', 'link', 'click', {hitCallback: function () { hits++; }});
        assert.equal(ga.create().get('clientId'), undefined);`, c);
    c.flush();
    assert.equal(vm.runInContext('hits', c), 2);

    // GTM / gtag: eventCallback and event_callback run for queued and new entries.
    c = page('var done = 0; window.dataLayer = [{event: "a", eventCallback: function () { done++; }}];');
    vm.runInContext(read('gtm.js'), c);
    vm.runInContext(`
        function gtag() { dataLayer.push(arguments); }
        gtag('event', 'login', {event_callback: function () { done++; }});
        dataLayer.push({event: 'b', eventCallback: function () { done++; }});
        assert.equal(dataLayer.length, 3);
    `, c);
    c.flush();
    assert.equal(vm.runInContext('done', c), 3);
    // IMA: the usual player flow (video.js-ima, JW, custom) gets "no ads" and resumes content.
    c = page('var events = [];');
    vm.runInContext(read('google-ima.js'), c);
    vm.runInContext(`
        var ima = google.ima;
        var container = new ima.AdDisplayContainer({}, {});
        container.initialize();
        var loader = new ima.AdsLoader(container);
        loader.getSettings().setVpaidMode(ima.ImaSdkSettings.VpaidMode.ENABLED);
        loader.addEventListener(ima.AdsManagerLoadedEvent.Type.ADS_MANAGER_LOADED, function () { events.push('loaded'); }, false);
        loader.addEventListener(ima.AdErrorEvent.Type.AD_ERROR, function (e) {
            events.push('error:' + e.getError().getErrorCode());
        }, false);
        var request = new ima.AdsRequest();
        request.adTagUrl = 'https://ads.example/vast';
        request.setAdWillAutoPlay(true);
        loader.requestAds(request);
        var manager = new ima.AdsManager();
        manager.addEventListener(ima.AdEvent.Type.CONTENT_RESUME_REQUESTED, function () { events.push('resume'); });
        manager.init(640, 360, ima.ViewMode.NORMAL);
        manager.start();
    `, c);
    c.flush();
    assert.equal(vm.runInContext('events.join()', c), 'error:1009,resume');
    console.log('Adblock surrogates passed: adsbygoogle, GPT command queue, analytics hitCallback, GTM/gtag callbacks, IMA no-ads');
})().catch(error => { console.error(error); process.exit(1); });
