// VireoLumaTV replacement for the Google IMA HTML5 SDK (ima3.js), written for this project from
// the public IMA SDK API reference. Video players load the SDK to show pre-rolls; when it is
// blocked they wait for it forever or show an error. This stand-in exposes the same API and
// answers every ad request with "no ads" (an AD_ERROR event), which players handle by starting
// the content. No network request is made.
(function () {
    'use strict';
    var w = window;
    if (w.google && w.google.ima && w.google.ima.__vireo) return;
    function noop() {}

    function Emitter() { this._listeners = {}; }
    Emitter.prototype.addEventListener = function (types, fn, capture, scope) {
        if (typeof fn !== 'function') return;
        [].concat(types).forEach(function (type) {
            (this._listeners[type] = this._listeners[type] || []).push({ fn: fn, scope: scope });
        }, this);
    };
    Emitter.prototype.removeEventListener = function (types, fn) {
        [].concat(types).forEach(function (type) {
            this._listeners[type] = (this._listeners[type] || []).filter(function (l) { return l.fn !== fn; });
        }, this);
    };
    Emitter.prototype._dispatch = function (type, event) {
        (this._listeners[type] || []).slice().forEach(function (l) {
            try { l.fn.call(l.scope || null, event); } catch (e) { setTimeout(function () { throw e; }, 0); }
        });
    };

    var AdErrorType = { AD_LOAD: 'adLoadError', AD_PLAY: 'adPlayError' };
    var ErrorCode = {
        VAST_EMPTY_RESPONSE: 1009, VAST_NO_ADS_AFTER_WRAPPER: 303, VAST_LOAD_TIMEOUT: 301,
        UNKNOWN_ERROR: 900, FAILED_TO_REQUEST_ADS: 1005, ADS_REQUEST_NETWORK_ERROR: 1012,
        VAST_MEDIA_LOAD_TIMEOUT: 402, VIDEO_PLAY_ERROR: 400, INVALID_ARGUMENTS: 1101
    };
    function AdError(message, code, type) {
        this.message = message; this.errorCode = code; this.type = type;
    }
    AdError.prototype.getErrorCode = function () { return this.errorCode; };
    AdError.prototype.getVastErrorCode = function () { return this.errorCode; };
    AdError.prototype.getMessage = function () { return this.message; };
    AdError.prototype.getType = function () { return this.type; };
    AdError.prototype.getInnerError = function () { return null; };
    AdError.prototype.toString = function () { return 'AdError ' + this.errorCode + ': ' + this.message; };
    AdError.ErrorCode = ErrorCode;
    AdError.Type = AdErrorType;

    function AdErrorEvent(error, context) { this.type = 'adError'; this._error = error; this._context = context; }
    AdErrorEvent.prototype.getError = function () { return this._error; };
    AdErrorEvent.prototype.getUserRequestContext = function () { return this._context || {}; };
    AdErrorEvent.Type = { AD_ERROR: 'adError' };

    var AdEventType = {
        AD_BREAK_READY: 'adBreakReady', AD_BUFFERING: 'adBuffering', AD_CAN_PLAY: 'adCanPlay',
        AD_METADATA: 'adMetadata', AD_PROGRESS: 'adProgress', ALL_ADS_COMPLETED: 'allAdsCompleted',
        CLICK: 'click', COMPLETE: 'complete', CONTENT_PAUSE_REQUESTED: 'contentPauseRequested',
        CONTENT_RESUME_REQUESTED: 'contentResumeRequested', DURATION_CHANGE: 'durationChange',
        FIRST_QUARTILE: 'firstQuartile', IMPRESSION: 'impression', INTERACTION: 'interaction',
        LINEAR_CHANGED: 'linearChanged', LOADED: 'loaded', LOG: 'log', MIDPOINT: 'midpoint',
        PAUSED: 'pause', RESUMED: 'resume', SKIPPABLE_STATE_CHANGED: 'skippableStateChanged',
        SKIPPED: 'skip', STARTED: 'start', THIRD_QUARTILE: 'thirdQuartile', USER_CLOSE: 'userClose',
        VIDEO_CLICKED: 'videoClicked', VIDEO_ICON_CLICKED: 'videoIconClicked', VOLUME_CHANGED: 'volumeChange',
        VOLUME_MUTED: 'mute'
    };
    function AdEvent(type) { this.type = type; }
    AdEvent.prototype.getAd = function () { return null; };
    AdEvent.prototype.getAdData = function () { return {}; };
    AdEvent.Type = AdEventType;

    function AdsManager() { Emitter.call(this); this._volume = 1; }
    AdsManager.prototype = Object.create(Emitter.prototype);
    ['collapse', 'configureAdsManager', 'destroy', 'discardAdBreak', 'expand', 'focus', 'pause',
        'resize', 'resume', 'setAdWillPlayMuted', 'skip', 'stop', 'updateAdsRenderingSettings', 'clicked']
        .forEach(function (name) { AdsManager.prototype[name] = noop; });
    AdsManager.prototype.init = noop;
    AdsManager.prototype.start = function () {
        var self = this;
        setTimeout(function () {
            self._dispatch(AdEventType.CONTENT_RESUME_REQUESTED, new AdEvent(AdEventType.CONTENT_RESUME_REQUESTED));
            self._dispatch(AdEventType.ALL_ADS_COMPLETED, new AdEvent(AdEventType.ALL_ADS_COMPLETED));
        }, 0);
    };
    AdsManager.prototype.getAdSkippableState = function () { return false; };
    AdsManager.prototype.getCuePoints = function () { return []; };
    AdsManager.prototype.getCurrentAd = function () { return null; };
    AdsManager.prototype.getRemainingTime = function () { return 0; };
    AdsManager.prototype.getVolume = function () { return this._volume; };
    AdsManager.prototype.setVolume = function (v) { this._volume = v; };
    AdsManager.prototype.isCustomClickTrackingUsed = function () { return false; };
    AdsManager.prototype.isCustomPlaybackUsed = function () { return false; };

    function AdsManagerLoadedEvent(context) { this.type = 'adsManagerLoaded'; this._context = context; }
    AdsManagerLoadedEvent.prototype.getAdsManager = function () { return new AdsManager(); };
    AdsManagerLoadedEvent.prototype.getUserRequestContext = function () { return this._context || {}; };
    AdsManagerLoadedEvent.Type = { ADS_MANAGER_LOADED: 'adsManagerLoaded' };

    function settingsObject() {
        var values = { locale: 'en', numRedirects: 4, playerType: '', playerVersion: '', vpaidMode: 0,
            autoPlayAdBreaks: true, cookiesEnabled: false, sessionId: '', ppid: '', disableCustomPlaybackForIOS10Plus: false };
        var settings = {};
        Object.keys(values).forEach(function (key) {
            var name = key.charAt(0).toUpperCase() + key.slice(1);
            settings['get' + name] = function () { return values[key]; };
            settings['set' + name] = function (v) { values[key] = v; };
        });
        settings.getVpaidAllowed = function () { return false; };
        settings.setVpaidAllowed = noop;
        settings.isCookiesEnabled = function () { return values.cookiesEnabled; };
        settings.getDisableFlashAds = function () { return true; };
        settings.setDisableFlashAds = noop;
        settings.getFeatureFlags = function () { return {}; };
        settings.setFeatureFlags = noop;
        settings.getCompanionBackfill = function () { return 'always'; };
        settings.setCompanionBackfill = noop;
        return settings;
    }
    var settings = settingsObject();

    function AdsLoader() { Emitter.call(this); }
    AdsLoader.prototype = Object.create(Emitter.prototype);
    AdsLoader.prototype.contentComplete = noop;
    AdsLoader.prototype.destroy = noop;
    AdsLoader.prototype.getSettings = function () { return settings; };
    AdsLoader.prototype.getVersion = function () { return ima.VERSION; };
    AdsLoader.prototype.requestAds = function (request, context) {
        var self = this;
        setTimeout(function () {
            var error = new AdError('No ads available', ErrorCode.VAST_EMPTY_RESPONSE, AdErrorType.AD_LOAD);
            self._dispatch(AdErrorEvent.Type.AD_ERROR, new AdErrorEvent(error, context));
        }, 0);
    };

    function AdDisplayContainer() {}
    AdDisplayContainer.prototype.initialize = noop;
    AdDisplayContainer.prototype.destroy = noop;

    function AdsRequest() {
        this.adTagUrl = ''; this.adsResponse = null; this.linearAdSlotWidth = 0; this.linearAdSlotHeight = 0;
        this.nonLinearAdSlotWidth = 0; this.nonLinearAdSlotHeight = 0; this.forceNonLinearFullSlot = false;
        this.vastLoadTimeout = 5000;
    }
    ['setAdWillAutoPlay', 'setAdWillPlayMuted', 'setContinuousPlayback'].forEach(function (name) {
        AdsRequest.prototype[name] = noop;
    });

    function AdsRenderingSettings() {
        this.autoAlign = true; this.bitrate = -1; this.enablePreloading = false; this.loadVideoTimeout = 8000;
        this.mimeTypes = null; this.playAdsAfterTime = -1; this.restoreCustomPlaybackStateOnAdBreakComplete = false;
        this.uiElements = null; this.useStyledLinearAds = false; this.useStyledNonLinearAds = false;
    }
    function CompanionAdSelectionSettings() {}
    CompanionAdSelectionSettings.CreativeType = { ALL: 'All', FLASH: 'Flash', IMAGE: 'Image' };
    CompanionAdSelectionSettings.ResourceType = { ALL: 'All', HTML: 'Html', IFRAME: 'IFrame', STATIC: 'Static' };
    CompanionAdSelectionSettings.SizeCriteria = { IGNORE: 'IgnoreSize', SELECT_EXACT_MATCH: 'SelectExactMatch', SELECT_NEAR_MATCH: 'SelectNearMatch' };

    var ima = {
        __vireo: true,
        VERSION: '3.600.0',
        AdDisplayContainer: AdDisplayContainer,
        AdError: AdError,
        AdErrorEvent: AdErrorEvent,
        AdEvent: AdEvent,
        AdsLoader: AdsLoader,
        AdsManager: AdsManager,
        AdsManagerLoadedEvent: AdsManagerLoadedEvent,
        AdsRenderingSettings: AdsRenderingSettings,
        AdsRequest: AdsRequest,
        CompanionAdSelectionSettings: CompanionAdSelectionSettings,
        ImaSdkSettings: { CompanionBackfillMode: { ALWAYS: 'always', ON_MASTER_AD: 'on_master_ad' },
            VpaidMode: { DISABLED: 0, ENABLED: 1, INSECURE: 2 } },
        OmidAccessMode: { DOMAIN: 'domain', FULL: 'full', LIMITED: 'limited' },
        OmidVerificationVendor: {},
        UiElements: { AD_ATTRIBUTION: 'adAttribution', COUNTDOWN: 'countdown' },
        ViewMode: { FULLSCREEN: 'fullscreen', NORMAL: 'normal' },
        settings: settings
    };
    w.google = w.google || {};
    w.google.ima = ima;
})();
