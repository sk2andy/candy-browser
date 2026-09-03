# Release-note format

Use the subset rendered by Candy Browser and GitHub:

- level 1–3 headings;
- paragraphs;
- unordered and ordered lists;
- blockquotes;
- `**bold**`, `*italic*`, inline code, and Markdown links;
- fenced code blocks only when users must copy a command;
- Markdown images from the repository screenshot catalog.

Suggested shape:

```markdown
# <Short user-facing release theme>

<One paragraph explaining the release's main benefit.>

![<Specific accessible description>](https://raw.githubusercontent.com/sk2andy/candy-browser/v<version>/docs/screenshots/<image>.png)

## <Outcome-oriented feature heading>

- <What users can now do>
- <How it behaves across important states>

Learn more in [<feature name>](https://github.com/sk2andy/candy-browser/blob/v<version>/<README-path>#<section-anchor>).

## Private by design

<Relevant privacy/security boundary in plain language.>

> <Optional setup, migration, or compatibility note.>
```

Rules:

- First line must be one `# ` heading.
- Image URL must pin `v<version>` and map to an existing `docs/screenshots/` file.
- README links must pin `v<version>` and include a useful section anchor when possible.
- Avoid HTML: the app intentionally renders a safe Markdown subset.
- Avoid tables in release notes; narrow phone pages make them hard to scan.
- Keep the file below 64 KiB and screenshots below the validator's per-release limits.
