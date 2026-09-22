import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const context = vm.createContext({ Number });
const source = fs.readFileSync(
  new URL("../app/src/gecko/assets/candy_privacy/animation_policy.js", import.meta.url),
  "utf8",
);
vm.runInContext(source, context);
const policy = context.CandyAnimationPolicy;

test("disabled content policy injects motion suppression and settles current animations", () => {
  let installedStyle = null;
  let finished = 0;
  const document = {
    createElement: () => ({
      remove: () => { installedStyle = null; },
      setAttribute() {},
      textContent: "",
    }),
    documentElement: {
      appendChild: (style) => { installedStyle = style; },
    },
    getAnimations: () => [{
      effect: { getTiming: () => ({ iterations: 1 }) },
      finish: () => { finished += 1; },
      playState: "running",
    }],
    head: null,
    querySelector: () => installedStyle,
  };

  policy.installContentPolicy({ document }, false);

  assert.match(installedStyle.textContent, /animation-duration: 0\.001ms !important/);
  assert.match(installedStyle.textContent, /transition-duration: 0s !important/);
  assert.match(installedStyle.textContent, /scroll-behavior: auto !important/);
  assert.equal(finished, 1);

  policy.installContentPolicy({ document }, true);
  assert.equal(installedStyle, null);
});

test("document-start policy handles all current Web Animations and rejects stale revisions", () => {
  let finished = 0;
  const listeners = [];
  const pageContext = vm.createContext({
    Number,
    document: {
      addEventListener: (name) => listeners.push(name),
      getAnimations: () => [{
        effect: { getTiming: () => ({ iterations: 1 }) },
        finish: () => { finished += 1; },
        playState: "running",
      }],
    },
  });

  vm.runInContext(policy.registrationCode(4), pageContext);
  vm.runInContext(policy.registrationCode(3), pageContext);

  assert.equal(finished, 1);
  assert.deepEqual(listeners, ["animationstart", "transitionrun"]);
  assert.equal(pageContext.__candyAnimationPolicyRevision, 4);
});
