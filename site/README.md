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

## Deployment

`.github/workflows/pages.yml` deploys this directory after a push to `main`. Configure the repository's
Pages source as **GitHub Actions** before the first deployment.

## Feature visuals

The landing page explains product features with dependency-free HTML and CSS abstractions instead of
product screenshots. The main customization panels and tab-preview modes change only after direct
input. The address-action palette is the only looping visual and stops when `prefers-reduced-motion`
is enabled.
