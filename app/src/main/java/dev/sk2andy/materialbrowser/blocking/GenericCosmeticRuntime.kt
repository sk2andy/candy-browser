package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import java.util.LinkedHashMap

internal class GenericCosmeticPolicyCache(
    private val maxEntries: Int,
    private val resolve: (String) -> String,
) {
    private val values = object : LinkedHashMap<String, String>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, String>?,
        ): Boolean = size > maxEntries
    }

    init {
        require(maxEntries > 0) { "Generic cosmetic policy cache must not be empty" }
    }

    fun get(host: String): String = synchronized(values) {
        values[host] ?: resolve(host).also { policy -> values[host] = policy }
    }

    internal val sizeForTesting: Int
        get() = synchronized(values) { values.size }
}

internal data class GenericCosmeticPayload(
    val encoded: String,
    val selectorCount: Int,
    val simpleSelectorCount: Int,
    val complexSelectorCount: Int,
) {
    companion object {
        const val MAX_SELECTORS = 15_000
        const val MAX_COMPLEX_SELECTORS = 750
        const val MAX_ENCODED_BYTES = 384 * 1_024
        private val simpleSelector = Regex("[.#][A-Za-z_][A-Za-z0-9_-]*")

        fun create(selectors: Collection<String>): GenericCosmeticPayload {
            val ordered = selectors.distinct().sorted()
            val classes = ArrayList<String>()
            val ids = ArrayList<String>()
            val complex = ArrayList<String>()
            ordered.forEach { selector ->
                when {
                    simpleSelector.matches(selector) && selector.startsWith('.') ->
                        classes += selector.drop(1)
                    simpleSelector.matches(selector) && selector.startsWith('#') ->
                        ids += selector.drop(1)
                    else -> complex += selector
                }
            }
            require(ordered.size <= MAX_SELECTORS) { "Too many generic cosmetic selectors" }
            require(complex.size <= MAX_COMPLEX_SELECTORS) {
                "Too many complex generic cosmetic selectors"
            }
            val encoded = listOf(
                encode(prefixCompress(classes)),
                encode(prefixCompress(ids)),
                encode(complex.joinToString("\n")),
            ).joinToString(".")
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_ENCODED_BYTES) {
                "Generic cosmetic payload too large"
            }
            return GenericCosmeticPayload(
                encoded = encoded,
                selectorCount = ordered.size,
                simpleSelectorCount = classes.size + ids.size,
                complexSelectorCount = complex.size,
            )
        }

        private fun prefixCompress(values: List<String>): String = buildString {
            var previous = ""
            values.forEachIndexed { index, value ->
                if (index > 0) append('\n')
                val commonLength = previous.commonPrefixWith(value).length
                append(commonLength)
                append(':')
                append(value, commonLength, value.length)
                previous = value
            }
        }

        private fun encode(value: String): String = Base64.getEncoder()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}

internal object GenericCosmeticPolicyEncoding {
    const val DISABLED = "!"
    private const val MAX_DENIED_SELECTORS = 128
    private const val MAX_ENCODED_BYTES = 16 * 1_024

    fun encode(policy: GenericCosmeticPolicy): String {
        if (policy.disabled) return DISABLED
        require(policy.deniedSelectors.size <= MAX_DENIED_SELECTORS) {
            "Too many generic cosmetic exceptions"
        }
        val encoded = Base64.getEncoder().encodeToString(
            policy.deniedSelectors.distinct().sorted().joinToString("\n")
                .toByteArray(Charsets.UTF_8),
        )
        require(encoded.length <= MAX_ENCODED_BYTES) { "Generic cosmetic policy too large" }
        return encoded
    }
}

internal object GenericCosmeticScript {
    const val BRIDGE_NAME = "CandyGenericCosmeticBridge"
    private val safeBridgeToken = Regex("[A-Za-z0-9-]{16,64}")
    const val cleanupScript =
        "(function c(w){try{var s=w.__candyGenericCosmeticV2;if(s&&s.cleanup)s.cleanup();" +
            "Array.from(w.frames).forEach(c)}catch(e){}})(window)"

    fun create(
        pausedHosts: Collection<String> = emptyList(),
        bridgeToken: String,
    ): String {
        require(safeBridgeToken.matches(bridgeToken)) { "Invalid generic cosmetic bridge token" }
        val pauses = pausedHosts.asSequence()
            .mapNotNull(CandyHostCanonicalizer::canonicalHost)
            .distinct()
            .take(64)
            .joinToString(",") { "'$it'" }
        return """
            (function(){
              for(var frame=self;frame!==top;){
                frame=frame.parent;
                try{void frame.document}catch(ignored){return}
              }
              if(self.__candyGenericCosmeticV2)return;
              var host=location.hostname.toLowerCase().replace(/\.$/,'');
              if([$pauses].some(function(value){
                return host===value||host.endsWith('.'+value);
              }))return;
              var bridge=self.$BRIDGE_NAME;
              if(!bridge)return;
              var decode=function(value){
                if(!value)return '';
                var binary=atob(value);
                var bytes=Uint8Array.from(binary,function(character){
                  return character.charCodeAt(0);
                });
                return new TextDecoder('utf-8').decode(bytes);
              };
              var policy;
              try{policy=bridge.policy('$bridgeToken',host)}catch(ignored){return}
              if(policy==='${GenericCosmeticPolicyEncoding.DISABLED}')return;
              var denied=new Set(decode(policy).split('\n').filter(Boolean));
              var cacheKey='__candyGenericCosmeticIndexV2';
              var cache=top[cacheKey];
              if(!cache){
                var payload;
                try{payload=bridge.payload('$bridgeToken').split('.')}catch(ignored){return}
                if(payload.length!==3)return;
                var unpack=function(value){
                  var previous='';
                  var result=new Set();
                  decode(value).split('\n').filter(Boolean).forEach(function(row){
                    var separator=row.indexOf(':');
                    if(separator<1)return;
                    var common=Number(row.slice(0,separator));
                    if(!Number.isInteger(common)||common<0||common>previous.length)return;
                    previous=previous.slice(0,common)+row.slice(separator+1);
                    result.add(previous);
                  });
                  return result;
                };
                cache={
                  classes:unpack(payload[0]),
                  ids:unpack(payload[1]),
                  complex:decode(payload[2]).split('\n').filter(Boolean)
                };
                try{
                  Object.defineProperty(top,cacheKey,{value:cache,configurable:true});
                }catch(ignored){top[cacheKey]=cache}
              }
              var styles=[];
              var appendStyle=function(style){
                var parent=document.head||document.documentElement;
                if(!parent)return false;
                if(!style.isConnected){parent.appendChild(style);styles.push(style)}
                return true;
              };
              var complex=cache.complex.filter(function(selector){return !denied.has(selector)});
              var complexStyle=null;
              if(complex.length){
                complexStyle=document.createElement('style');
                complexStyle.dataset.candyGenericFilter='complex';
                complexStyle.textContent=complex.map(function(selector){
                  return selector+'{display:none!important}';
                }).join('');
                appendStyle(complexStyle);
              }
              var simpleStyle=null;
              var pendingSelectors=[];
              var matched=new Set();
              var matchedBytes=0;
              var nodes=[];
              var nodeIndex=0;
              var scheduled=false;
              var seen=new WeakSet();
              var observer=null;
              var maxSelectors=1024;
              var maxSelectorBytes=96*1024;
              var maxPendingNodes=8192;
              var flush=function(){
                if(!pendingSelectors.length)return;
                if(!simpleStyle){
                  simpleStyle=document.createElement('style');
                  simpleStyle.dataset.candyGenericFilter='simple';
                }
                if(!appendStyle(simpleStyle)){
                  return;
                }
                try{
                  simpleStyle.sheet.insertRule(
                    pendingSelectors.join(',')+'{display:none!important}',
                    simpleStyle.sheet.cssRules.length
                  );
                }catch(ignored){}
                pendingSelectors=[];
              };
              var addSelector=function(selector){
                if(denied.has(selector)||matched.has(selector))return;
                if(matched.size>=maxSelectors||matchedBytes+selector.length>maxSelectorBytes)return;
                matched.add(selector);
                matchedBytes+=selector.length;
                pendingSelectors.push(selector);
                if(pendingSelectors.length>=256)flush();
              };
              var inspect=function(element){
                var id=element.id;
                if(id&&cache.ids.has(id))addSelector('#'+id);
                try{
                  element.classList.forEach(function(name){
                    if(cache.classes.has(name))addSelector('.'+name);
                  });
                }catch(ignored){}
              };
              var schedule=function(){
                if(scheduled)return;
                scheduled=true;
                (self.requestAnimationFrame||function(callback){setTimeout(callback,0)})(drain);
              };
              var enqueue=function(node){
                if(!node||node.nodeType!==1||seen.has(node))return;
                if(nodes.length-nodeIndex>=maxPendingNodes)return;
                if(complexStyle)appendStyle(complexStyle);
                seen.add(node);
                inspect(node);
                nodes.push(node);
                schedule();
              };
              var drain=function(){
                scheduled=false;
                var started=performance.now();
                var processed=0;
                while(nodeIndex<nodes.length&&processed<512&&performance.now()-started<4){
                  var node=nodes[nodeIndex++];
                  for(var child=node.firstElementChild;child;child=child.nextElementSibling){
                    enqueue(child);
                  }
                  processed++;
                }
                flush();
                if(nodeIndex>=nodes.length){nodes=[];nodeIndex=0}
                else{
                  if(nodeIndex>4096){nodes=nodes.slice(nodeIndex);nodeIndex=0}
                  schedule();
                }
              };
              observer=new MutationObserver(function(records){
                records.forEach(function(record){
                  if(record.type==='attributes')inspect(record.target);
                  else record.addedNodes.forEach(enqueue);
                });
                flush();
              });
              observer.observe(document,{subtree:true,childList:true,attributes:true,
                attributeFilter:['id','class']});
              enqueue(document.documentElement);
              self.__candyGenericCosmeticV2={
                cleanup:function(){
                  observer.disconnect();
                  styles.forEach(function(style){style.remove()});
                  try{delete self.__candyGenericCosmeticV2}catch(ignored){}
                  if(self===top){try{delete top[cacheKey]}catch(ignored){}}
                },
                matched:matched
              };
            })()
        """.trimIndent()
    }
}
