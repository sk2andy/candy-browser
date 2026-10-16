"use strict";

// Only this APK's built-in privileged extension loads this parent-process API.
// No content script, runtime message handler or externally connectable endpoint exists.
this.candySiteData = class extends ExtensionAPI {
  getAPI() {
    return {
      candySiteData: {
        async clear(url, contextId, isPrivate) {
          const uri = Services.io.newURI(url);
          if (!["http", "https"].includes(uri.scheme) || !uri.host || uri.userPass ||
              !/^(?:|gvctxempty|gvctx-?[0-9a-f]+)$/.test(contextId)) {
            throw new Error("Invalid site data target");
          }
          const site = Services.eTLD.getSchemelessSiteFromHost(uri.host);
          const pattern = {
            geckoViewSessionContextId: contextId,
            privateBrowsingId: isPrivate ? 1 : 0,
            userContextId: 0,
          };
          // CookieCleaner's generic enumerator omits private cookies and also clears
          // unscoped compression dictionaries. Enumerate the exact storage context instead.
          const cookies = Services.cookies.getCookiesWithOriginAttributes(JSON.stringify(pattern));
          for (const cookie of cookies) {
            const ownsSite = Services.eTLD.getSchemelessSiteFromHost(cookie.rawHost) === site;
            const partitionedUnderSite = ChromeUtils.originAttributesMatchPattern(
              cookie.originAttributes,
              { ...pattern, partitionKeyPattern: { baseDomain: site } },
            );
            if (ownsSite || partitionedUnderSite) {
              Services.cookies.removeCookiesFromExactHost(
                cookie.rawHost, JSON.stringify(cookie.originAttributes),
              );
            }
          }
          const flags = Ci.nsIClearDataService.CLEAR_DOM_QUOTA |
            Ci.nsIClearDataService.CLEAR_IMAGE_CACHE |
            Ci.nsIClearDataService.CLEAR_CSS_CACHE |
            Ci.nsIClearDataService.CLEAR_JS_CACHE;
          // NetworkCacheCleaner's site method ignores the context pattern in Gecko 157.
          // Exclude it rather than evicting another profile's HTTP cache.
          await new Promise((resolve, reject) => {
            Services.clearData.deleteDataFromSite(site, pattern, false, flags, (failedFlags) => {
              if (failedFlags === 0) resolve();
              else reject(new Error("Site data deletion incomplete"));
            });
          });
        },
      },
    };
  }
};
