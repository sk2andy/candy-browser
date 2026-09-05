import CandyShared
import WebKit

enum ToppingInstaller {
    static func install(
        _ plan: ToppingInjectionPlan,
        into controller: WKUserContentController
    ) {
        let injectionTime: WKUserScriptInjectionTime = plan.runAt == .documentstart
            ? .atDocumentStart
            : .atDocumentEnd
        let world = WKContentWorld.world(name: plan.contentWorldName)
        let script = WKUserScript(
            source: plan.source,
            injectionTime: injectionTime,
            forMainFrameOnly: plan.forMainFrameOnly,
            in: world
        )
        controller.addUserScript(script)
    }
}
