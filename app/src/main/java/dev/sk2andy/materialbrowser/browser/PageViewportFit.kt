package dev.sk2andy.materialbrowser.browser

internal object PageViewportFit {
    const val bridgeName = "CandyViewportFit"

    fun observerScript(navigationGeneration: Int): String =
        """
            (() => {
              const generation = $navigationGeneration;
              const isSeparator = (character) =>
                character === ' ' || character === '\t' || character === '\n' ||
                character === '\r' || character === '=' || character === ',' ||
                character === '\0';
              const parse = (content) => {
                const buffer = content.toLowerCase();
                let viewportFit = 'auto';
                for (let index = 0; index < buffer.length;) {
                  while (index < buffer.length && isSeparator(buffer[index])) index++;
                  const keyStart = index;
                  while (index < buffer.length && !isSeparator(buffer[index])) index++;
                  const key = buffer.substring(keyStart, index);
                  while (index < buffer.length && buffer[index] !== '=' && buffer[index] !== ',') {
                    index++;
                  }
                  while (index < buffer.length && isSeparator(buffer[index])) {
                    if (buffer[index] === ',') break;
                    index++;
                  }
                  const valueStart = index;
                  while (index < buffer.length && !isSeparator(buffer[index])) index++;
                  const value = buffer.substring(valueStart, index);
                  if (key === 'viewport-fit') viewportFit = value;
                }
                return viewportFit === 'cover';
              };
              const report = (enabled) => window.$bridgeName.update(generation, enabled);
              const processMeta = (meta) => {
                if ((meta.getAttribute('name') || '').toLowerCase() !== 'viewport') return false;
                if (!meta.hasAttribute('content')) return false;
                report(parse(meta.getAttribute('content')));
                return true;
              };
              const processAddedNode = (node) => {
                if (!(node instanceof Element)) return;
                if (node.tagName === 'META') processMeta(node);
                node.querySelectorAll('meta').forEach(processMeta);
              };

              let enabled = false;
              document.querySelectorAll('meta').forEach((meta) => {
                if ((meta.getAttribute('name') || '').toLowerCase() === 'viewport' &&
                    meta.hasAttribute('content')) {
                  enabled = parse(meta.getAttribute('content'));
                }
              });

              window.__candyViewportFitObserver?.disconnect();
              window.__candyViewportFitObserver = new MutationObserver((records) => {
                records.forEach((record) => {
                  if (record.type === 'attributes') processMeta(record.target);
                  record.addedNodes?.forEach(processAddedNode);
                });
              });
              window.__candyViewportFitObserver.observe(document.documentElement, {
                attributes: true,
                attributeFilter: ['name', 'content'],
                childList: true,
                subtree: true,
              });
              return enabled;
            })();
        """.trimIndent()

    fun isCoverResult(result: String?): Boolean = result == "true"
}
