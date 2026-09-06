"use strict";

document.getElementById("open-options").addEventListener("click", () => {
  browser.runtime.openOptionsPage();
});
window.setTimeout(() => browser.runtime.openOptionsPage(), 100);
