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
              const isViewportMeta = (meta) =>
                meta.tagName === 'META' &&
                (meta.getAttribute('name') || '').toLowerCase() === 'viewport';
              const containsMeta = (node) =>
                node instanceof Element &&
                (node.tagName === 'META' || Boolean(node.querySelector('meta')));
              const readCurrent = () => {
                let enabled = false;
                document.querySelectorAll('meta').forEach((meta) => {
                  if (isViewportMeta(meta) && meta.hasAttribute('content')) {
                    enabled = parse(meta.getAttribute('content'));
                  }
                });
                return enabled;
              };
              const reportCurrent = () =>
                window.$bridgeName.update(generation, readCurrent());

              window.__candyViewportFitObserver?.disconnect();
              window.__candyViewportFitObserver = new MutationObserver((records) => {
                const viewportMayHaveChanged = records.some((record) =>
                  (record.type === 'attributes' && record.target.tagName === 'META') ||
                  Array.from(record.addedNodes || []).some(containsMeta) ||
                  Array.from(record.removedNodes || []).some(containsMeta));
                if (viewportMayHaveChanged) reportCurrent();
              });
              window.__candyViewportFitObserver.observe(document.documentElement, {
                attributes: true,
                attributeFilter: ['name', 'content'],
                childList: true,
                subtree: true,
              });
              return readCurrent();
            })();
        """.trimIndent()

    fun isCoverResult(result: String?): Boolean = result == "true"
}
