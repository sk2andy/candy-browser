import Foundation
import Network
import XCTest

final class CandyBrowserChromeUITests: XCTestCase {
    @MainActor
    func testScrollParkRepositionRestoreAndBlankEntry() throws {
        continueAfterFailure = false
        let ready = expectation(description: "Loopback fixture is listening")
        let fixture = try ChromePageFixture(ready: ready)
        defer { fixture.stop() }
        wait(for: [ready], timeout: 10)
        let fixtureURL = try XCTUnwrap(fixture.url)

        let app = XCUIApplication()
        // Standard UserDefaults argument-domain override: keep gestures, skip visual springs.
        app.launchArguments = ["-candy.appearance.animations", "NO"]
        app.launch()
        defer { app.terminate() }

        let handle = app.descendants(matching: .any)["address_bar_edge_tab"].firstMatch
        if handle.waitForExistence(timeout: 3) { handle.tap() }
        let address = app.textFields["Adresse oder Suche"].firstMatch
        XCTAssertTrue(address.waitForExistence(timeout: 10))
        address.tap()
        let previous = address.value as? String ?? ""
        address.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: previous.utf16.count))
        address.typeText(fixtureURL.absoluteString + "\n")
        // A remembered global dock can be hidden on the initial blank tab and reappear
        // as soon as navigation supplies a real page. Restore it before the scroll check.
        if handle.waitForExistence(timeout: 3) { handle.tap() }

        // Coordinates still exercise WebKit scrolling when its accessibility subtree is absent.
        let compact = app.descendants(matching: .any)["address_bar_compact_content"].firstMatch
        let scrollStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.68))
        let scrollEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.32))
        for _ in 0..<20 {
            scrollStart.press(forDuration: 0.01, thenDragTo: scrollEnd)
            if compact.waitForExistence(timeout: 2) { break }
        }
        XCTAssertTrue(compact.exists, "Scrolling down must compact the real address pill")
        scrollEnd.press(forDuration: 0.01, thenDragTo: scrollStart)
        XCTAssertTrue(address.waitForExistence(timeout: 5), "Scrolling up must expand the real address pill")
        XCTAssertFalse(compact.exists)
        scrollStart.press(forDuration: 0.01, thenDragTo: scrollEnd)
        // SwiftUI can inherit the parent identifier onto both compact buttons; use semantics.
        let park = app.buttons["Adressleiste am Rand parken"].firstMatch
        XCTAssertTrue(park.waitForExistence(timeout: 5))
        park.tap()
        XCTAssertTrue(handle.waitForExistence(timeout: 5))

        let before = handle.frame
        let wasOnLeft = before.midX < app.frame.midX
        let destination = app.coordinate(withNormalizedOffset: CGVector(
            dx: wasOnLeft ? 0.92 : 0.08,
            dy: before.midY > app.frame.midY ? 0.25 : 0.75
        ))
        handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(forDuration: 0.5, thenDragTo: destination)
        XCTAssertTrue(handle.exists, "Dropping must retain the parked handle")
        let moved = handle.frame
        if wasOnLeft {
            XCTAssertGreaterThan(moved.midX, app.frame.midX, "Drop must cross to the right edge")
        } else {
            XCTAssertLessThan(moved.midX, app.frame.midX, "Drop must cross to the left edge")
        }
        XCTAssertGreaterThan(abs(moved.midY - before.midY), 60, "Hold-and-drag must change parked height")

        app.terminate()
        app.launch()
        XCTAssertTrue(handle.waitForExistence(timeout: 15), "Parking must survive a process restart")
        XCTAssertEqual(handle.frame.midX, moved.midX, accuracy: 4)
        XCTAssertEqual(handle.frame.midY, moved.midY, accuracy: 4)
        handle.tap()
        XCTAssertTrue(address.waitForExistence(timeout: 5), "Tap must restore the full address bar")
        XCTAssertFalse(app.keyboards.firstMatch.exists, "Restoring must not start address editing")
        XCTAssertFalse(handle.exists)

        app.buttons["Neuer Tab"].firstMatch.tap()
        XCTAssertTrue(address.waitForExistence(timeout: 5), "Blank tabs must retain address entry")
        XCTAssertFalse(handle.exists, "Blank tabs must not show an orphaned parked handle")
        XCTAssertFalse(compact.exists, "Blank tabs must not show the compact pill")
        address.tap()
        address.typeText("blank entry works")
        XCTAssertEqual(address.value as? String, "blank entry works")
    }
}

/// Serves one static page on simulator loopback; no production launch hook or external service.
private final class ChromePageFixture {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "dev.sk2andy.candy.chrome-ui-fixture")

    var url: URL? {
        listener.port.flatMap { URL(string: "http://127.0.0.1:\($0.rawValue)/chrome") }
    }

    init(ready: XCTestExpectation) throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        listener = try NWListener(using: parameters)
        let body = Data("""
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Candy chrome gesture fixture</title><style>
        body{margin:0;min-height:12000px;background:linear-gradient(#e6f2ff,#a6d8ff);font:22px sans-serif}
        h1,p{padding:24px}section{height:800px;border-bottom:4px solid #589}
        </style></head><body><h1>Candy chrome gesture fixture</h1>
        <p>Scroll this document to exercise the native address pill.</p>
        <section>First section</section><section>Second section</section><section>Third section</section>
        </body></html>
        """.utf8)
        var response = Data("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: \(body.count)\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".utf8)
        response.append(body)
        let responseData = response
        let connectionQueue = queue
        listener.newConnectionHandler = { connection in
            connection.start(queue: connectionQueue)
            connection.receive(minimumIncompleteLength: 1, maximumLength: 8_192) { data, _, _, error in
                guard error == nil, data?.isEmpty == false else { connection.cancel(); return }
                connection.send(content: responseData, completion: .contentProcessed { _ in
                    connection.cancel()
                })
            }
        }
        listener.stateUpdateHandler = { state in
            if case .ready = state { ready.fulfill() }
        }
        listener.start(queue: queue)
    }

    func stop() {
        listener.cancel()
    }
}
