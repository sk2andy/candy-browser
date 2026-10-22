'use strict';

// Design-only module. Optional read-only API access; subscriptions never leave this tab.
const catalogPrototype = (() => {
  const api = ['127.0.0.1','localhost'].includes(location.hostname)
    ? 'http://127.0.0.1:8770/catalog-api' : 'https://rssamplifier.com';
  const model = {
    live: false, query: '', inputQuery: '', topic: '', kind: 'all', languages: [], draftLanguages: [],
    results: null, status: 'idle', offset: 0, more: true, selected: null,
    subscriptions: new Map(), controller: null, previewController: null,
  };
  const topics = [
    ['technology', 'Technik', '⌘', 'Neue Ideen. Offenes Web.'],
    ['science', 'Wissen', '✳', 'Die Welt besser verstehen.'],
    ['design', 'Design & Kultur', '◒', 'Für einen anderen Blick.'],
    ['food', 'Essen', '♧', 'Geschichten mit Geschmack.'],
    ['travel', 'Unterwegs', '↗', 'Raus aus dem Bekannten.'],
    ['environment', 'Natur', '❋', 'Mehr von draußen.'],
  ];
  // Illustrative source selection, not endorsements or a snapshot of provider rankings.
  const examples = [
    {slug:'example-colossal',title:'Colossal',description:'Art, design, and visual culture. A little wonder for your everyday.',language:'en',kind:'blog',siteUrl:'https://www.thisiscolossal.com/',feedUrl:'https://www.thisiscolossal.com/feed/',topics:['design'],items:[{title:'A new perspective on everyday things'},{title:'Where art and curiosity meet'}]},
    {slug:'example-lemonde',title:'Le Monde',description:'Actualités et regards sur le monde, la culture et les idées.',language:'fr',kind:'news',siteUrl:'https://www.lemonde.fr/',feedUrl:'https://www.lemonde.fr/rss/une.xml',topics:['design','environment'],items:[{title:'Les idées qui font bouger le monde'},{title:'La culture sous toutes ses formes'}]},
    {slug:'example-gigazine',title:'GIGAZINE',description:'日々のニュースから、テクノロジーや食の話題まで。',language:'ja',kind:'news',siteUrl:'https://gigazine.net/',feedUrl:'https://gigazine.net/news/rss_2.0/',topics:['technology','food'],items:[{title:'新しいテクノロジーをもっと身近に'},{title:'日常の発見を楽しむ'}]},
    {slug:'example-nasa',title:'NASA',description:'Explore discoveries, missions, and the science of our universe.',language:'en',kind:'news',siteUrl:'https://www.nasa.gov/',feedUrl:'https://www.nasa.gov/feed/',topics:['science'],items:[{title:'A closer look at our changing planet'},{title:'The next chapter of exploration'}]},
    {slug:'example-elpais',title:'EL PAÍS',description:'Noticias, ciencia y cultura para entender lo que pasa en el mundo.',language:'es',kind:'news',siteUrl:'https://elpais.com/',feedUrl:'https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada',topics:['science','environment'],items:[{title:'Miradas para entender un mundo que cambia'}]},
    {slug:'example-heise',title:'heise online',description:'Technik verstehen. Entwicklungen einordnen. Neues entdecken.',language:'de',kind:'news',siteUrl:'https://www.heise.de/',feedUrl:'https://www.heise.de/rss/heise-atom.xml',topics:['technology'],items:[{title:'Technik, die unseren Alltag verändert'}]},
    {slug:'example-g1',title:'g1',description:'As últimas notícias do Brasil e do mundo.',language:'pt-BR',kind:'news',siteUrl:'https://g1.globo.com/',feedUrl:'https://g1.globo.com/rss/g1/',topics:['travel','environment'],items:[{title:'Histórias de um mundo em movimento'}]},
    {slug:'example-aljazeera',title:'الجزيرة نت',description:'أخبار العالم وقصص الناس والثقافة والمعرفة.',language:'ar',kind:'news',siteUrl:'https://www.aljazeera.net/',feedUrl:'https://www.aljazeera.net/aljazeerarss',topics:['science','design'],items:[{title:'قصص وأفكار من حول العالم'}]},
    {slug:'example-sspai',title:'少数派',description:'让数字生活更美好。关于工具、效率与生活的新想法。',language:'zh',kind:'blog',siteUrl:'https://sspai.com/',feedUrl:'https://sspai.com/feed',topics:['technology','design'],items:[{title:'发现数字生活中的新可能'}]},
    {slug:'example-maangchi',title:'Maangchi',description:'Korean cooking, shared with the world. Recipes and stories from the kitchen.',language:'en',kind:'blog',siteUrl:'https://www.maangchi.com/',feedUrl:'https://www.maangchi.com/feed',topics:['food'],items:[{title:'Good food brings us together'}]},
  ];
  const languageCodes = 'aa ab ae af ak am an ar as av ay az ba be bg bh bi bm bn bo br bs ca ce ch co cr cs cu cv cy da de dv dz ee el en eo es et eu fa ff fi fj fo fr fy ga gd gl gn gu gv ha he hi ho hr ht hu hy hz ia id ie ig ii ik io is it iu ja jv ka kg ki kj kk kl km kn ko kr ks ku kv kw ky la lb lg li ln lo lt lu lv mg mh mi mk ml mn mr ms mt my na nb nd ne ng nl nn no nr nv ny oc oj om or os pa pi pl ps pt qu rm rn ro ru rw sa sc sd se sg si sk sl sm sn so sq sr ss st su sv sw ta te tg th ti tk tl tn to tr ts tt tw ty ug uk ur uz ve vi vo wa wo xh yi yo za zh zu'.split(' ');
  const displayNames = new Intl.DisplayNames(['de'], {type:'language'});
  const languageName = code => {
    try { return displayNames.of(code) || code; } catch { return code || 'Sprache offen'; }
  };
  const nativeLanguageName = code => {
    try { return new Intl.DisplayNames([code],{type:'language'}).of(code); } catch { return code; }
  };
  const englishNames = new Intl.DisplayNames(['en'],{type:'language'});
  const baseLanguage = code => String(code || '').toLowerCase().split(/[-_]/)[0];
  const folded = text => String(text).normalize('NFKD').replace(/\p{M}/gu,'').toLocaleLowerCase();
  const safeUrl = value => {
    try { const url = new URL(value); return ['http:','https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''; } catch { return ''; }
  };
  const identity = source => safeUrl(source.feedUrl) || source.slug;
  const textOnly = value => new DOMParser().parseFromString(String(value || ''), 'text/html').body.textContent || '';
  const domain = source => {
    try { return new URL(safeUrl(source.siteUrl) || safeUrl(source.feedUrl)).hostname.replace(/^www\./,''); } catch { return 'Website noch unbekannt'; }
  };
  const icon = name => ({
    search:'<circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5"/>',
    globe:'<circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18M5 6.5h14M5 17.5h14"/>',
    rss:'<circle cx="5" cy="19" r="1"/><path d="M4 11a9 9 0 0 1 9 9M4 4a16 16 0 0 1 16 16"/>',
    arrow:'<path d="M5 12h14m-6-6 6 6-6 6"/>',
    check:'<path d="m5 12 4 4L19 6"/>',
  }[name] || '');
  const svg = name => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icon(name)}</svg>`;
  const button = (act, label, cls='', extra='') => action('catalog-'+act, label, cls, `type="button" ${extra}`);
  const topicLabel = () => topics.find(([key])=>key===model.topic)?.[1] || '';
  const languageLabel = () => !model.languages.length ? 'Alle Sprachen' : model.languages.length === 1 ? languageName(model.languages[0]) : `${model.languages.length} Sprachen`;
  const unique = list => [...new Map(list.map(source=>[identity(source), source])).values()];
  function sourceCard(source) {
    const subscribed = model.subscriptions.has(identity(source));
    const index = [...String(source.title || '?')][0];
    return `<article class="catalog-source">
      <button class="catalog-source-main" data-action="catalog-preview" data-slug="${esc(source.slug)}" aria-label="Vorschau: ${esc(source.title)}">
        <span class="catalog-avatar" aria-hidden="true">${esc(index)}</span>
        <span class="catalog-source-copy"><strong dir="auto">${esc(source.title)}</strong><small>${esc(domain(source))}</small></span>
        <span class="catalog-forward" aria-hidden="true">›</span>
        <p dir="auto">${esc(textOnly(source.description) || 'Lerne diese Quelle in der Vorschau kennen.')}</p>
      </button>
      <div class="catalog-source-footer"><span>${esc(source.language ? languageName(source.language) : 'Sprache offen')}</span><span class="catalog-kind">${source.kind==='news'?'Nachrichten':'Blog'}</span>
        ${button('preview',subscribed?svg('check')+'Abonniert':'Vorschau '+svg('arrow'),'catalog-preview-btn',`data-slug="${esc(source.slug)}"`)}
      </div>
    </article>`;
  }
  function list() {
    const pool = model.results || examples;
    return pool.filter(source => (model.kind === 'all' || source.kind === model.kind) && (!model.languages.length || model.languages.includes(baseLanguage(source.language))));
  }
  function searchControls() {
    return `<div class="catalog-search-area">
      <form id="catalog-search-form" role="search" aria-label="Neue Feed-Quellen suchen"><div class="search-bar">
        ${svg('search')}<input id="catalog-query" type="search" placeholder="Thema, Name oder Website" aria-label="Thema, Name oder Website suchen" value="${esc(model.inputQuery)}" autocomplete="off" maxlength="200" enterkeyhint="search">
        ${button('clear','×','catalog-clear',`aria-label="Suchbegriff löschen" ${model.inputQuery?'':'hidden'}`)}
        <button type="submit" class="catalog-submit" aria-label="Quellen suchen">${svg('arrow')}</button>
      </div></form>
      <div class="catalog-filter-row">${button('languages',svg('globe')+esc(languageLabel())+' <span aria-hidden="true">⌄</span>','catalog-language',`aria-label="Sprachen auswählen: ${esc(languageLabel())}"`)}
        ${button('manual',svg('rss')+' Feed-Adresse','catalog-manual')}
      </div>
    </div>`;
  }
  function results() {
    const filtered = list();
    const title = model.query ? `„${esc(model.query)}“` : esc(topicLabel() || 'Entdecken');
    const tabs = [['all','Alle Quellen'],['blog','Blogs'],['news','Nachrichten']];
    const partial = model.live && model.languages.length ? '<p class="catalog-selection-note">Sprachfilter für diese Auswahl · weitere Quellen nachladen</p>' : '';
    let body;
    if (model.status==='loading') body = `<div class="catalog-loading" role="status">${svg('globe')}<h3>Neue Stimmen finden …</h3><p>Deine nächste gute Quelle ist vielleicht schon dabei.</p></div>`;
    else if (model.status==='error') body = `<div class="catalog-empty" role="status"><span aria-hidden="true">☁</span><h3>Die Welt wartet kurz.</h3><p>Der Katalog ist gerade nicht erreichbar. Deine Abos bleiben erhalten.</p>${button('retry','Erneut versuchen','btn')}${button('examples','Beispielkatalog ansehen','text-btn wide')}</div>`;
    else body = `<div class="catalog-result-heading"><h3>${filtered.length} ${filtered.length===1?'Quelle':'Quellen'}</h3><span>${model.live?'RSS Amplifier':'Beispielauswahl'}</span></div>${partial}
      <div class="catalog-results" aria-live="polite">${filtered.length ? filtered.map(sourceCard).join('') : `<div class="catalog-empty"><span aria-hidden="true">✳</span><h3>Noch nicht das Richtige?</h3><p>Versuche einen anderen Begriff${model.languages.length?' oder erweitere deine Sprachen':''}.</p>${model.languages.length?button('all-languages','Alle Sprachen zeigen','btn secondary'):button('reset','Themen entdecken','btn secondary')}</div>`}</div>
      ${model.live && model.more ? button('more',model.status==='more'?'Weitere Quellen suchen …':'Weitere Quellen laden','btn secondary wide',model.status==='more'?'disabled':'') : ''}`;
    return `<div class="catalog-results-title"><h3>${title}</h3>${button('reset','Zurücksetzen','text-btn')}</div>
      <div class="origin-tabs catalog-tabs" role="tablist" aria-label="Art der Quelle">${tabs.map(([key,label])=>button('kind',label,`origin-tab ${model.kind===key?'selected':''}`,`data-value="${key}" role="tab" aria-selected="${model.kind===key}" tabindex="${model.kind===key?'0':'-1'}"`)).join('')}</div>
      <div class="chips catalog-topic-chips" aria-label="Themen">${topics.map(([key,label,symbol])=>button('topic',`${symbol} ${label}`,`chip ${model.topic===key?'selected':''}`,`data-topic="${key}" aria-pressed="${model.topic===key}"`)).join('')}</div>${body}`;
  }
  function renderCatalog() {
    const browsing = model.results === null && model.status === 'idle';
    const hero = `<div class="catalog-hero"><span class="overline">NEUGIER. OHNE GRENZEN.</span><h3>Dein nächster<br><em>Lieblingsfeed.</em></h3><p>Große Geschichten.<br>Auch von kleinen Quellen.</p><div class="catalog-world" aria-hidden="true">${svg('globe')}<span class="world-glyph world-a">あ</span><span class="world-glyph world-b">Aa</span><span class="world-spark">✳</span></div></div>`;
    const landing = `<div class="section-heading catalog-section"><h3>Worauf bist du neugierig?</h3></div>
      <div class="catalog-topics">${topics.map(([key,label,symbol,description],i)=>button('topic',`<span class="catalog-topic-icon" aria-hidden="true">${symbol}</span><strong>${label}</strong><small>${description}</small>`,`catalog-topic topic-${i}`,`data-topic="${key}"`)).join('')}</div>
      <div class="section-heading catalog-section"><h3>Stimmen aus aller Welt</h3><span aria-hidden="true">↙</span></div>
      <p class="catalog-section-caption">Ein kleiner Blick über deinen Tellerrand.</p>
      ${list().slice(0,4).map(sourceCard).join('') || '<p class="hint">Suche nach einem Thema, um Quellen in deiner Sprachauswahl zu finden.</p>'}`;
    return `<div class="catalog-appbar"><button class="icon-btn" data-action="back" aria-label="Zurück">‹</button><h2>Entdecken</h2>${button('about',svg('rss'),'icon-btn tonal','aria-label="Über den Feed-Katalog"')}</div>
      ${browsing?hero:''}${searchControls()}${browsing?landing:results()}
      <div class="catalog-credit">Quellen entdecken mit <a href="https://rssamplifier.com/" target="_blank" rel="noopener noreferrer">RSS Amplifier ↗</a></div>`;
  }
  function catalogSheet(title, body) { sheet(title, body);document.querySelector('#sheet').classList.add('catalog-sheet'); }
  function repaint() {
    if (state.route === 'catalog') { const position = screen.scrollTop; render(); screen.scrollTop = position; }
  }
  async function request(path, signal) {
    const response = await fetch(api+path, {signal, credentials:'omit', referrerPolicy:'no-referrer'});
    if (!response.ok) throw Error('Catalog unavailable');
    return response.json();
  }
  function normalize(source) {
    return {...source, title:String(source.title || 'Unbenannte Quelle'), slug:String(source.slug || ''), description:textOnly(source.description).slice(0,600), siteUrl:safeUrl(source.siteUrl), feedUrl:safeUrl(source.feedUrl), language:String(source.language || ''), items:Array.isArray(source.items)?source.items:[]};
  }
  async function hydrate(sources, signal) {
    const output = new Array(sources.length);
    let cursor = 0;
    await Promise.all(Array.from({length:Math.min(4,sources.length)}, async()=>{
      while (cursor < sources.length) {
        const index = cursor++;
        const source = sources[index];
        try { output[index] = normalize(await request('/api/feeds/'+encodeURIComponent(source.slug),signal)); }
        catch (error) { if (signal.aborted) throw error; output[index] = normalize(source); }
      }
    }));
    return output.filter(source=>source.kind==='blog'||source.kind==='news');
  }
  async function search(more=false) {
    if (model.live && state.discoveryPrivate) { toast('Online-Suche ist im privaten Tab ausgeschaltet.'); return; }
    model.controller?.abort();
    const controller = new AbortController(); model.controller = controller;
    model.status = more ? 'more' : 'loading';
    if (!more) { model.results = []; model.offset = 0; model.more = true; }
    repaint();
    if (!more) screen.scrollTop = 0;
    if (!model.live) {
      const query = folded(model.query);
      model.results = examples.filter(source=>(!model.topic||source.topics.includes(model.topic))&&(!query||folded([source.title,source.description,source.siteUrl,...source.topics].join(' ')).includes(query)));
      model.status = 'ready'; model.more = false; repaint(); return;
    }
    const timeout = setTimeout(()=>controller.abort(),20000);
    try {
      let sources;
      if (model.topic) {
        const response = await request(`/api/topics/${encodeURIComponent(model.topic)}?limit=16&offset=${model.offset}`,controller.signal);
        sources = (response.feeds || []).filter(source=>source.kind==='blog'||source.kind==='news');
        model.more = (response.feeds || []).length===16;
      } else {
        const responses = await Promise.all(['blogs','news'].map(kind=>request(`/api/search?q=${encodeURIComponent(model.query)}&kind=${kind}&limit=6&offset=${model.offset}`,controller.signal)));
        sources = unique(responses.flatMap(response=>response.blogs || [])).filter(source=>['blog','news'].includes(source.kind || source.category)).map(source=>({...source,kind:source.kind || source.category}));
        model.more = responses.some(response=>(response.blogs || []).length>=6);
      }
      const hydrated = await hydrate(sources,controller.signal);
      if (controller.signal.aborted || model.controller!==controller) return;
      const old = more ? model.results : [];
      const merged = unique([...old,...hydrated]);
      if (more && merged.length===old.length) model.more = false;
      model.results = merged; model.offset += model.topic?16:6; model.status = 'ready'; repaint();
    } catch {
      if (model.controller!==controller) return;
      model.status = 'error'; repaint();
    } finally { clearTimeout(timeout); }
  }
  function currentSource(slug) { return [...(model.results || []),...examples,...model.subscriptions.values()].find(source=>source.slug===slug); }
  async function preview(slug) {
    if (model.live && state.discoveryPrivate) { toast('Online-Vorschau ist im privaten Tab ausgeschaltet.'); return; }
    const source = currentSource(slug); if (!source) return;
    model.selected = source;
    model.previewController?.abort();
    if (!model.live || source.manual || source.items?.length) { previewContent(source); return; }
    catalogSheet('Quelle kennenlernen','<div class="catalog-loading" role="status"><h3>Ein erster Einblick …</h3></div>'+button('dismiss','Zurück zur Suche','text-btn wide'));
    const controller = new AbortController(); model.previewController = controller;
    const timeout = setTimeout(()=>controller.abort(),15000);
    try {
      const detailed = normalize(await request('/api/feeds/'+encodeURIComponent(slug),controller.signal));
      if (!controller.signal.aborted && model.selected?.slug===slug && document.querySelector('#sheet').open) { model.selected=detailed; previewContent(detailed); }
    } catch {
      if (model.previewController===controller && model.selected?.slug===slug && document.querySelector('#sheet').open) catalogSheet('Vorschau gerade nicht verfügbar','<p>Die Quelle konnte nicht geladen werden. Versuche es noch einmal.</p>'+button('preview','Erneut versuchen','btn wide',`data-slug="${esc(slug)}"`)+button('dismiss','Zurück zur Suche','text-btn wide'));
    } finally { clearTimeout(timeout); }
  }
  function previewContent(source) {
    const subscribed = model.subscriptions.has(identity(source));
    const items = source.items || [];
    const original = safeUrl(source.siteUrl);
    catalogSheet(source.manual?'Eigene Quelle hinzufügen':'Quelle kennenlernen',`<div class="catalog-preview-head"><span class="catalog-avatar" aria-hidden="true">${esc([...source.title][0])}</span><div><h3 dir="auto">${esc(source.title)}</h3><p>${source.manual?'Deine Feed-Adresse':esc(source.language?languageName(source.language):'Sprache offen')+' · '+(source.kind==='news'?'Nachrichten':'Blog')}</p></div></div>
      <p dir="auto">${esc(textOnly(source.description))}</p>${original?`<a class="catalog-origin" href="${esc(original)}" target="_blank" rel="noopener noreferrer">${esc(domain(source))} ↗</a>`:''}
      ${source.manual?`<div class="panel"><p class="catalog-feed-address">${esc(source.feedUrl)}</p><p>Im Entwurf wird das Hinzufügen simuliert. In der App werden Feed und Artikel zuerst geprüft.</p></div>`:''}
      <div class="section-heading"><h3>${model.live&&!source.manual?'Letzte Beiträge':'Beispielhafte Artikelvorschau'}</h3></div>
      <div class="catalog-article-preview">${items.length?items.slice(0,3).map(item=>`<div><span aria-hidden="true">${svg('rss')}</span><strong dir="auto">${esc(textOnly(item.title))}</strong>${item.publishedAt?`<small>${esc(new Intl.DateTimeFormat('de',{dateStyle:'medium'}).format(new Date(Number.isFinite(Date.parse(item.publishedAt))?item.publishedAt:Date.now())))}</small>`:''}</div>`).join(''):'<p>Noch keine Artikelvorschau vorhanden. Du kannst die Originalseite ansehen.</p>'}</div>
      ${select('In Ordner ablegen','catalog-folder',['Ohne Ordner',...folders()],'Ohne Ordner')}
      ${button('subscribe',subscribed?svg('check')+' Bereits abonniert':svg('rss')+' Quelle abonnieren','btn wide catalog-subscribe',subscribed||!safeUrl(source.feedUrl)?'disabled':'')}
      ${!safeUrl(source.feedUrl)?'<p class="hint">Für diese Quelle fehlt noch eine gültige Feed-Adresse.</p>':''}${button('dismiss','Zurück zur Suche','text-btn wide')}`);
    document.querySelector('#sheet').classList.add('catalog-sheet');
  }
  function languages() {
    model.draftLanguages=[...model.languages];
    catalogSheet('Deine Lesesprachen',`<p>Eine Sprache oder mehrere. Du entscheidest, wie weit du schaust.</p>
      <div class="search-bar catalog-language-search">${svg('search')}<input type="search" id="catalog-language-query" aria-label="Sprache finden" placeholder="Sprache finden" autocomplete="off"></div>
      <div id="catalog-language-options"></div><div class="catalog-language-apply">${button('apply-languages','Auswahl übernehmen','btn wide')}<span id="catalog-language-summary"></span></div>`);
    document.querySelector('#sheet').classList.add('catalog-sheet');
    renderLanguages('');
  }
  function renderLanguages(query) {
    const codes = languageCodes.filter(code=>folded([languageName(code),nativeLanguageName(code),englishNames.of(code),code].join(' ')).includes(folded(query))).sort((a,b)=>languageName(a).localeCompare(languageName(b),'de'));
    const common=['en','de','es','fr','pt','zh','ja','ar','hi','ru'];
    const ordered=query?codes:uniqueLanguageCodes([...common,...codes]);
    document.querySelector('#catalog-language-options').innerHTML=`<label class="catalog-language-option"><input type="checkbox" data-catalog-language="all" ${!model.draftLanguages.length?'checked':''}><span><strong>Alle Sprachen</strong><small>Ohne Einschränkung entdecken</small></span>${svg('globe')}</label>${ordered.length?ordered.map(code=>{
      const native=nativeLanguageName(code);
      return `<label class="catalog-language-option"><input type="checkbox" data-catalog-language="${code}" ${model.draftLanguages.includes(code)?'checked':''}><span><strong>${esc(languageName(code))}</strong><small dir="auto">${esc(native)}</small></span><span class="catalog-language-code">${code.toUpperCase()}</span></label>`;
    }).join(''):'<p class="hint">Keine Sprache unter diesem Namen gefunden.</p>'}`;
    document.querySelector('#catalog-language-summary').textContent=model.draftLanguages.length?`${model.draftLanguages.length} ausgewählt`:'Die ganze Welt ist dabei';
  }
  function uniqueLanguageCodes(codes) { return [...new Set(codes)]; }
  function reset() { model.controller?.abort(); model.controller=null; model.query=''; model.inputQuery=''; model.topic=''; model.results=null; model.status='idle'; repaint(); screen.scrollTop=0; }
  document.addEventListener('submit',event=>{
    if (event.target.id!=='catalog-search-form') return;
    event.preventDefault(); model.query=document.querySelector('#catalog-query').value.trim(); model.inputQuery=model.query; model.topic='';
    if (!model.query) { reset(); return; }
    document.querySelector('#catalog-query').blur(); search();
  });
  document.addEventListener('input',event=>{
    if (event.target.id==='catalog-query') { model.inputQuery=event.target.value;document.querySelector('.catalog-clear').hidden=!event.target.value; }
    if (event.target.id==='catalog-language-query') renderLanguages(event.target.value);
  });
  document.addEventListener('change',event=>{
    if (event.target.id==='catalog-live') {
      model.live=event.target.checked; reset();
      toast(model.live?'Live-Suche bereit. Thema wählen oder Suche absenden.':'Beispielkatalog aktiviert.');
    }
    if (event.target.dataset.catalogLanguage) {
      const scroll=document.querySelector('#catalog-language-options').scrollTop;
      const code=event.target.dataset.catalogLanguage;
      model.draftLanguages=code==='all'?[]:event.target.checked?uniqueLanguageCodes([...model.draftLanguages,code]):model.draftLanguages.filter(value=>value!==code);
      renderLanguages(document.querySelector('#catalog-language-query').value);
      document.querySelector('#catalog-language-options').scrollTop=scroll;
      document.querySelector(`[data-catalog-language="${code}"]`)?.focus({preventScroll:true});
    }
  });
  document.addEventListener('keydown',event=>{
    const tab=event.target.closest('[data-action="catalog-kind"]');
    if (!tab || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
    event.preventDefault();const tabs=[...document.querySelectorAll('[data-action="catalog-kind"]')];
    const index=event.key==='Home'?0:event.key==='End'?tabs.length-1:(tabs.indexOf(tab)+(event.key==='ArrowRight'?1:tabs.length-1))%tabs.length;
    model.kind=tabs[index].dataset.value;repaint();document.querySelector(`[data-action="catalog-kind"][data-value="${model.kind}"]`).focus();
  });
  document.addEventListener('click',event=>{
    const target=event.target.closest('[data-action^="catalog-"]'); if (!target) return;
    switch(target.dataset.action.slice(8)) {
      case 'topic': model.query='';model.inputQuery='';model.topic=target.dataset.topic;search();break;
      case 'clear': reset();document.querySelector('#catalog-query').focus();break;
      case 'reset': reset();break;
      case 'kind': model.kind=target.dataset.value;repaint();break;
      case 'languages': languages();break;
      case 'apply-languages': model.languages=[...model.draftLanguages];closeSheet();repaint();break;
      case 'all-languages': model.languages=[];repaint();break;
      case 'preview': preview(target.dataset.slug);break;
      case 'dismiss': model.selected=null;model.previewController?.abort();closeSheet();break;
      case 'retry': search();break;
      case 'more': search(true);break;
      case 'examples': model.live=false;reset();break;
      case 'manual': catalogSheet('Eigene Quelle hinzufügen',field('Website oder Feed-Adresse','catalog-own-url','','https://…')+'<p>Du kannst auch Quellen hinzufügen, die nicht im Katalog stehen.</p>'+button('own-preview','Vorschau ansehen','btn wide')+button('dismiss','Zurück','text-btn wide'));break;
      case 'own-preview': {
        const url=safeUrl(document.querySelector('#catalog-own-url').value.trim());
        if (!url) { toast('Bitte eine vollständige HTTP(S)-Adresse eingeben.'); break; }
        const existing=[...model.subscriptions.values()].find(source=>identity(source)===url);
        model.selected=existing || {slug:'manual-'+encodeURIComponent(url),title:new URL(url).hostname.replace(/^www\./,''),description:'Eine Quelle, die du selbst mitgebracht hast.',feedUrl:url,siteUrl:new URL(url).origin,language:'',kind:'blog',manual:true,items:[]};
        previewContent(model.selected);break;
      }
      case 'subscribe': {
        if (state.discoveryPrivate) { toast('Im privaten Tab werden keine Abos gespeichert.'); return; }
        const source=model.selected;if(!source||!safeUrl(source.feedUrl))return;
        const folder=document.querySelector('#catalog-folder').value;
        model.subscriptions.set(identity(source),{...source,folder});
        catalogSheet('Eine gute Stimme mehr.',`<div class="catalog-success">${svg('check')}<h3 dir="auto">${esc(source.title)}</h3><p>In deinen Quellen${folder==='Ohne Ordner'?'':' · '+esc(folder)}</p></div><p>Neue Beiträge findest du künftig in deiner Inbox.</p>${button('dismiss','Weiter entdecken','btn wide')}<button class="text-btn wide" data-route="sources">Meine Quellen ansehen</button><p class="hint">Abo im Entwurf simuliert · bis zum Neuladen.</p>`);repaint();break;
      }
      case 'about': catalogSheet('Die Welt liest mit.',`<p>Entdecke Blogs und Nachrichten aus vielen Ländern und Sprachen. Die Quellen stammen aus dem öffentlichen Verzeichnis von RSS Amplifier.</p><p>Suche und Themenauswahl werden im Live-Modus an RSS Amplifier gesendet. Erst beim Absenden, nicht während du tippst.</p><p>Ein Abo verbindet dich später mit dem Originalfeed des Publishers.</p><a href="https://rssamplifier.com/terms" target="_blank" rel="noopener noreferrer">Über RSS Amplifier ↗</a>${button('dismiss','Weiter entdecken','btn wide')}`);break;
    }
  });
  return {
    render:renderCatalog,
    cancelPreview() { model.previewController?.abort();model.previewController=null; },
    afterRender() {
      const controls=document.querySelector('#catalog-demo-controls');controls.hidden=state.route!=='catalog';
      document.querySelector('#catalog-live').checked=model.live;
      document.querySelector('#catalog-live').disabled=state.discoveryPrivate;
      document.querySelector('#catalog-data-status').textContent=model.live?'Live-API · Abos nur simuliert':'Beispielkatalog · Abos nur simuliert';
      if(state.route!=='catalog') { model.controller?.abort();model.controller=null;if(['loading','more'].includes(model.status))model.status='idle'; }
    },
    subscriptionCount:()=>model.subscriptions.size,
    subscriptionRows:()=>[...model.subscriptions.values()].map(source=>`<button class="list-row" data-action="catalog-preview" data-slug="${esc(source.slug)}"><span class="list-icon">${svg('rss')}</span><span class="grow"><strong dir="auto">${esc(source.title)}</strong><small>${esc(source.folder)} · im Entwurf abonniert</small></span><span>›</span></button>`).join(''),
  };
})();
