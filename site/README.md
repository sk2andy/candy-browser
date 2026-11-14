# Candy Browser website

Static, dependency-free GitHub Pages site for Candy Browser, the public English privacy policy, and
the project imprint.

## Local preview

From the repository root:

```sh
python3 -m http.server 4173 --directory site
```

Open `http://127.0.0.1:4173/`. The privacy policy is available at `/privacy/` and the imprint at
`/imprint/`.

## Mozilla sign-in return

`/oauth/mozilla/` returns Mozilla's OAuth response to Candy through an explicit **Open Candy** link.
Register `https://sk2andy.github.io/candy-browser/oauth/mozilla/` as the Android client's redirect URI.
The fixed app destination is `dev.sk2andy.materialbrowser://oauth/callback`; Candy validates the
OAuth state and completes the PKCE exchange. A direct visit without a response explains how to restart.

The page removes the response from its browser-history entry and uses no analytics or browser
storage. GitHub Pages receives the initial HTTPS request, including its query parameters.

Run the callback checks with `node --test scripts/mozilla_oauth_callback.test.mjs`.

## Deployment

`.github/workflows/pages.yml` deploys this directory after a push to `main`. Configure the repository's
Pages source as **GitHub Actions** before the first deployment.

## Feature visuals

The landing page explains product features with dependency-free HTML and CSS abstractions instead of
product screenshots. The main customization panels and tab-preview modes change only after direct
input. Tab switcher modes and profile-bound stacks also cycle automatically while visible, while
remaining directly selectable. A scroll-linked abstract phone viewport transitions from regular browsing into Cover flow.
The address-action palette demonstrates actions moving into and out of the address bar. Scroll-linked
motion and the looping palette both provide static states when `prefers-reduced-motion` is enabled.
