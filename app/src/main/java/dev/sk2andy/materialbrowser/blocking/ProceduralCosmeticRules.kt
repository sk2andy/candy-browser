package dev.sk2andy.materialbrowser.blocking

import java.util.Base64

internal enum class ProceduralCosmeticAction { Hide, Remove }

internal data class ProceduralCosmeticRule(
    val action: ProceduralCosmeticAction,
    val hostPattern: String,
    val selector: String,
    val text: String?,
    val ignoreCase: Boolean,
)

internal class ProceduralCosmeticRules private constructor(
    val rules: List<ProceduralCosmeticRule>,
) {
    private val exactRules = rules.filterNot { '*' in it.hostPattern }
        .groupBy(ProceduralCosmeticRule::hostPattern)
    private val wildcardRules = rules.filter { '*' in it.hostPattern }

    fun matchingRules(pageUrl: String?): List<ProceduralCosmeticRule> {
        val host = CandyHostCanonicalizer.webHost(pageUrl) ?: return emptyList()
        return buildList {
            var candidate = host
            while (true) {
                exactRules[candidate]?.let(::addAll)
                val dot = candidate.indexOf('.')
                if (dot < 0) break
                candidate = candidate.substring(dot + 1)
            }
            wildcardRules.filterTo(this) { rule ->
                CosmeticHostPattern.matches(host, rule.hostPattern)
            }
        }.distinct().take(MAX_RESOLVED_RULES)
    }

    companion object {
        const val HEADER = "candy-procedural-cosmetic:1"
        private const val MAX_BYTES = 4 * 1_024 * 1_024
        private const val MAX_LINES = 50_000
        private const val MAX_TEXT_LENGTH = 128
        private const val MAX_RESOLVED_RULES = 64

        val Empty = ProceduralCosmeticRules(emptyList())

        fun parse(text: String): ProceduralCosmeticRules {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Procedural cosmetic asset too large"
            }
            val lines = text.lineSequence().toList()
            require(lines.size <= MAX_LINES) { "Too many procedural cosmetic rules" }
            require(lines.firstOrNull()?.trimStart('\uFEFF') == HEADER) {
                "Invalid procedural cosmetic asset header"
            }
            val declaredRules = lines.asSequence()
                .map(String::trim)
                .firstOrNull { it.startsWith(RULE_COUNT_PREFIX) }
                ?.removePrefix(RULE_COUNT_PREFIX)
                ?.trim()
                ?.toIntOrNull()
                ?: error("Missing procedural cosmetic rule count")
            val rules = lines.drop(1).mapIndexedNotNull { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith('#')) return@mapIndexedNotNull null
                val fields = line.split('\t')
                require(fields.size == 5) {
                    "Invalid procedural cosmetic rule at line ${index + 2}"
                }
                val action = when (fields[0]) {
                    "H" -> ProceduralCosmeticAction.Hide
                    "R" -> ProceduralCosmeticAction.Remove
                    else -> error("Invalid procedural cosmetic action at line ${index + 2}")
                }
                val host = CosmeticHostPattern.canonicalize(fields[1])
                    ?: error("Invalid procedural cosmetic host at line ${index + 2}")
                val selector = decode(fields[2], index + 2)
                require(CandyRuleValidator.isSafeSelector(selector)) {
                    "Unsafe procedural selector at line ${index + 2}"
                }
                val textValue = fields[3].takeUnless { it == "-" }
                    ?.let { value -> decode(value, index + 2) }
                require(textValue == null || (
                    textValue.length in 1..MAX_TEXT_LENGTH && textValue.none(Char::isISOControl)
                )) { "Invalid procedural text at line ${index + 2}" }
                val ignoreCase = when (fields[4]) {
                    "i" -> true
                    "s" -> false
                    else -> error("Invalid procedural text mode at line ${index + 2}")
                }
                ProceduralCosmeticRule(action, host, selector, textValue, ignoreCase)
            }
            require(rules.size == declaredRules) { "Procedural cosmetic rule count mismatch" }
            require(rules.distinct().size == rules.size) { "Duplicate procedural cosmetic rule" }
            return ProceduralCosmeticRules(rules)
        }

        private fun decode(value: String, lineNumber: Int): String = runCatching {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        }.getOrElse { error("Invalid procedural encoding at line $lineNumber") }

        private const val RULE_COUNT_PREFIX = "# Rules:"
    }
}

internal object CandyProceduralCosmeticScript {
    const val cleanupScript =
        "(function clean(w){try{var s=w.__candyProceduralState;if(s){s.active=false;" +
            "if(s.start)w.removeEventListener('DOMContentLoaded',s.start);" +
            "if(s.observer)s.observer.disconnect();if(s.timer)w.clearTimeout(s.timer);" +
            "delete w.__candyProceduralState}w.document.querySelectorAll(" +
            "'[data-candy-procedural-hidden]').forEach(function(e){" +
            "var v=e.getAttribute('data-candy-procedural-display')||'';" +
            "var p=e.getAttribute('data-candy-procedural-priority')||'';" +
            "if(v)e.style.setProperty('display',v,p);else e.style.removeProperty('display');" +
            "e.removeAttribute('data-candy-procedural-hidden');" +
            "e.removeAttribute('data-candy-procedural-display');" +
            "e.removeAttribute('data-candy-procedural-priority')});" +
            "Array.from(w.frames).forEach(clean)}catch(ignored){}})(window)"

    fun create(rules: Collection<ProceduralCosmeticRule>): String {
        val encodedRules = rules.distinct().take(64).joinToString(",") { rule ->
            val selector = encode(rule.selector)
            val text = rule.text?.let(::encode).orEmpty()
            val action = if (rule.action == ProceduralCosmeticAction.Remove) "R" else "H"
            "{a:'$action',s:'$selector',t:'$text',i:${if (rule.ignoreCase) 1 else 0}}"
        }
        if (encodedRules.isEmpty()) return ""
        return """
            (function(){
              for(var frame=self;frame!==top;){
                frame=frame.parent;
                try{void frame.document}catch(ignored){return}
              }
              if(window.__candyProceduralState)return;
              var state={active:true,observer:null,start:null,timer:null};
              window.__candyProceduralState=state;
              var decode=function(value){
                var binary=atob(value);
                var bytes=Uint8Array.from(binary,function(c){return c.charCodeAt(0)});
                return new TextDecoder('utf-8').decode(bytes);
              };
              var rules=[$encodedRules].map(function(rule){
                return {a:rule.a,s:decode(rule.s),t:rule.t?decode(rule.t):'',i:rule.i};
              });
              var runs=0,scheduled=false;
              var apply=function(){
                scheduled=false;
                if(!state.active)return;
                if(++runs>20){if(state.observer)state.observer.disconnect();return}
                var deadline=performance.now()+8;
                rules.some(function(rule){
                  if(performance.now()>deadline)return true;
                  var nodes;
                  try{nodes=document.querySelectorAll(rule.s)}catch(ignored){return false}
                  Array.prototype.slice.call(nodes,0,128).some(function(node){
                    if(performance.now()>deadline)return true;
                    var content=node.textContent||'';
                    var expected=rule.t;
                    if(expected){
                      if(rule.i){content=content.toLowerCase();expected=expected.toLowerCase()}
                      if(content.indexOf(expected)<0)return false;
                    }
                    if(rule.a==='R'){node.remove();return false}
                    if(node.hasAttribute('data-candy-procedural-hidden'))return false;
                    node.setAttribute('data-candy-procedural-display',node.style.getPropertyValue('display'));
                    node.setAttribute('data-candy-procedural-priority',node.style.getPropertyPriority('display'));
                    node.setAttribute('data-candy-procedural-hidden','1');
                    node.style.setProperty('display','none','important');
                    return false;
                  });
                  return performance.now()>deadline;
                });
              };
              var schedule=function(){
                if(scheduled)return;
                scheduled=true;
                requestAnimationFrame(apply);
              };
              var start=function(){
                if(!state.active||!document.documentElement)return;
                apply();
                var observer=new MutationObserver(schedule);
                state.observer=observer;
                observer.observe(document.documentElement,{childList:true,subtree:true,characterData:true});
                state.timer=setTimeout(function(){observer.disconnect();state.timer=null},5000);
              };
              state.start=start;
              if(document.documentElement)start();else addEventListener('DOMContentLoaded',start,{once:true});
            })()
        """.trimIndent()
    }

    private fun encode(value: String): String = Base64.getEncoder()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
}
