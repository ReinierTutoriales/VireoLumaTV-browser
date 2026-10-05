const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/pages/home/index.html', 'utf8');
const functionSource = name => {
  const match = html.match(new RegExp(`function ${name}\\([\\s\\S]*?(?=\\nfunction |\\n</script>)`));
  assert.ok(match, `Missing ${name}`);
  return match[0];
};
const element = () => ({style: {}, classList: {toggle() {}},
  set innerHTML(_) {throw Error('Untrusted content interpreted as HTML');}});
const title = element(), description = element(), icon = element(), more = element();
const tile = {getElementsByClassName: name => [{
  'sugg-title': title, 'sugg-desk': description, 'sugg-ico': icon, more
}[name]]};
const sandbox = {console: {log() {}}, URL,
  window: {location: {href: ''}},
  document: {getElementById: id => id === 'sugg0' ? tile : null},
  homePageLinksMode: '', recommendationsToDisplay: [], loadRecommendations: () => []};
vm.createContext(sandbox);
vm.runInContext(functionSource('renderLinks') + functionSource('onSuggestionClicked'), sandbox);
const malicious = '<img src=x onerror="VireoLumaTVApp.startVoiceSearch()">';
sandbox.renderLinks('LATEST_HISTORY', [{title: malicious, description: malicious, url: 'https://example.com', favicon: 'https://example.com/icon.png'}]);
assert.equal(title.textContent, malicious);
assert.equal(description.textContent, malicious);
sandbox.onSuggestionClicked(0);
assert.equal(sandbox.window.location.href, 'https://example.com/');
for (const url of ['javascript:alert(1)', 'data:text/html,<script>alert(1)</script>', 'file:///sdcard/test.html', 'content://example', 'intent://example', 'invalid']) {
  sandbox.window.location.href = '';
  sandbox.recommendationsToDisplay = [{url}];
  sandbox.onSuggestionClicked(0);
  assert.equal(sandbox.window.location.href, '', `Accepted unsafe navigation: ${url}`);
}
sandbox.renderLinks('LATEST_HISTORY', []);
assert.equal(title.textContent, '\u00a0');
console.log('Home-page security tests passed: plain text rendering and safe navigation');

// Native homepage tiles must share the bounded favicon pool even when a remote icon was supplied.
sandbox.VireoLumaTVApp = {};
sandbox.window.VireoLumaTVApp = sandbox.VireoLumaTVApp;
sandbox.renderLinks('LATEST_HISTORY', [{title: 'Site', url: 'https://example.com', favicon: 'https://cdn.example.com/huge.png'}]);
assert.equal(icon.src, 'favicon://example.com');

sandbox.document.getElementsByTagName = () => [icon];
vm.runInContext(functionSource('onFaviconLoaded'), sandbox);
icon.src = 'ic_not_available.svg';
icon.style.filter = 'invert(1)';
sandbox.onFaviconLoaded('example.com', 'data:image/png;base64,test');
assert.equal(icon.src, 'data:image/png;base64,test');
assert.equal(icon.style.filter, '');
sandbox.renderLinks('LATEST_HISTORY', [{title: 'Other', url: 'https://other.example', favicon: 'https://other.example/icon.png'}]);
sandbox.onFaviconLoaded('example.com', 'data:image/png;base64,stale');
assert.equal(icon.src, 'favicon://other.example');
