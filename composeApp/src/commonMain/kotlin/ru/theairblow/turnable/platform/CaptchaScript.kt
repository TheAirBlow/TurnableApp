package ru.theairblow.turnable.platform

/**
 * Port of the service's captcha_manual.user.js: joins the call anonymously and reports the tokens the page
 * receives from calls.getAnonymousToken, leaving only the captcha to the user.
 * The host defines `window.__tc` with `done(messagesToken, callsToken)` and `loggedIn()` before this runs.
 */
const val CAPTCHA_CAPTURE_SCRIPT = """
(function () {
    if (window.top !== window || window.__turnableCaptcha) return;
    window.__turnableCaptcha = true;
    var finished = false;

    function formParam(body, key) {
        if (!body || typeof body !== 'string') return '';
        var pairs = body.split('&');
        for (var i = 0; i < pairs.length; i++) {
            var idx = pairs[i].indexOf('=');
            if (idx < 0) continue;
            try {
                if (decodeURIComponent(pairs[i].slice(0, idx)) === key) return decodeURIComponent(pairs[i].slice(idx + 1));
            } catch (e) { }
        }
        return '';
    }

    function report(messagesToken, callsToken) {
        if (finished || !callsToken) return;
        finished = true;
        __tc.done(messagesToken || '', callsToken);
    }

    var origFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
        var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
        if (url.indexOf('calls.getAnonymousToken') < 0) return origFetch(input, init);
        var body = init && init.body;
        var text = body instanceof URLSearchParams ? body.toString() : (typeof body === 'string' ? body : '');
        return origFetch(input, init).then(function (response) {
            return response.clone().json().then(function (data) {
                if (data && data.response && data.response.token) report(formParam(text, 'access_token'), data.response.token);
                return response;
            }).catch(function () { return response; });
        });
    };

    var origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url) {
        if (typeof url === 'string') this._captureUrl = url;
        return origOpen.apply(this, arguments);
    };
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function (body) {
        if (this._captureUrl && this._captureUrl.indexOf('calls.getAnonymousToken') >= 0) {
            var sent = typeof body === 'string' ? body : (body instanceof URLSearchParams ? body.toString() : '');
            this.addEventListener('load', function () {
                try {
                    var data = JSON.parse(this.responseText);
                    if (data && data.response && data.response.token) report(formParam(sent, 'access_token'), data.response.token);
                } catch (e) { }
            });
        }
        return origSend.apply(this, arguments);
    };

    var joined = false;
    function tryAutoJoin() {
        if (joined) return;
        if (document.querySelector('[data-testid="calls_preview_join_button"]')) {
            joined = true;
            __tc.loggedIn();
            return;
        }
        var anonBtn = document.querySelector('[data-testid="calls_preview_join_button_anonym"]');
        var input = document.querySelector('input[type="text"][maxlength="25"]');
        if (!anonBtn || !input) return;
        joined = true;
        try {
            var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
            setter.call(input, '123');
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
        } catch (e) { input.value = '123'; }
        var start = Date.now();
        (function waitAndClick() {
            var btn = document.querySelector('[data-testid="calls_preview_join_button_anonym"]');
            if (btn && !btn.disabled) { btn.click(); return; }
            if (Date.now() - start < 3000) setTimeout(waitAndClick, 100);
        })();
    }

    new MutationObserver(tryAutoJoin).observe(document.documentElement, { subtree: true, childList: true });
    document.addEventListener('DOMContentLoaded', tryAutoJoin);
    tryAutoJoin();
})();
"""
