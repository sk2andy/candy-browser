// Adapted from BareFilter 2026.11.01.6, BSD-3-Clause; see third_party_notices.txt.
// Source: https://github.com/BareBrowser/bare-browser/blob/9a20cee0014f49479764aaff05597f0fe3dd1e96/filters/BareFilter.txt
(function () {
  if (document.querySelector('style[data-candy-bare-filter]')) return;
  const host = location.hostname.toLowerCase().replace(/\.$/, '');
  const matches = domain => host === domain || host.endsWith('.' + domain);
  let css;
  if (matches('reddit.com')) {
    css = `
      div[id^="configured-xpromo"], #xpromo-small-header { display: none !important; }
      body:has(div[id^="configured-xpromo"]) { overflow: auto !important; }
    `;
  } else if (matches('m.facebook.com')) {
    css = `
      div[role="button"][aria-label="Open app"], [data-candy-bare-filter-hidden] { display: none !important; }
      div[aria-label="Navigate to your Reels profile"] { margin-left: calc(100vw - 44px) !important; }
      div[aria-label="Search"][role="button"]:has(+ div[aria-label="Navigate to your Reels profile"]) { margin-left: calc(100vw - 92px) !important; }
    `;
  } else if (matches('m.youtube.com')) {
    css = `.mobile-topbar-header-sign-in-button:has(a[aria-label="Open App"]) { display: none !important; }`;
  } else {
    return;
  }
  const style = document.createElement('style');
  style.setAttribute('data-candy-bare-filter', '');
  style.textContent = css;
  (document.head || document.documentElement).appendChild(style);
  if (!matches('m.facebook.com')) return;

  const banner = 'div[style*="pointer-events: none"][style*="background"]';
  const appBar = 'div[data-mcomponent="MContainer"]:has(> div[data-mcomponent="MContainer"] > div[role="button"])';
  let runs = 0;
  let observer;
  const apply = () => {
    scheduled = false;
    if (++runs > 20) {
      observer.disconnect();
      return;
    }
    document.querySelectorAll(banner + ',' + appBar + ',[data-candy-bare-filter-hidden]').forEach(node => {
      const hide = (node.matches(banner) && node.textContent.includes('Get Facebook for Android')) ||
        (node.matches(appBar) && /^Open app$/.test(node.textContent));
      node.toggleAttribute('data-candy-bare-filter-hidden', hide);
    });
  };
  let scheduled = false;
  apply();
  observer = new MutationObserver(() => {
    if (!scheduled) {
      scheduled = true;
      requestAnimationFrame(apply);
    }
  });
  observer.observe(document.documentElement, {
    childList: true, subtree: true, characterData: true, attributes: true,
    attributeFilter: ['style', 'data-mcomponent', 'role']
  });
  setTimeout(() => observer.disconnect(), 5000);
})();
