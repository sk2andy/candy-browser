import CandyShared
import Foundation
import UserNotifications

/// Apple scheduling only. Kotlin owns eligibility, due times and restoration.
@MainActor
final class BrowserSnoozeNotifications: NSObject, UNUserNotificationCenterDelegate {
    static let shared = BrowserSnoozeNotifications()
    private let center = UNUserNotificationCenter.current()
    private var records: [SnoozedTab] = []
    private var reminderRecords: [SnoozedTab] = []
    private var reconciliation: Task<Void, Never>?
    private var fingerprint: [String]?
    private var onOpen: ((String) -> Void)?
    private var onWake: (() -> Void)?
    private var pendingOpen: String?
    private var dueTimer: Timer?
    nonisolated private static let prefix = "candy.snooze."

    private override init() {
        super.init()
        center.delegate = self
    }

    func bind(onOpen: @escaping (String) -> Void, onWake: @escaping () -> Void) {
        self.onOpen = onOpen
        self.onWake = onWake
        if let pendingOpen {
            self.pendingOpen = nil
            Task { @MainActor [weak self] in self?.onOpen?(pendingOpen) }
        }
    }

    private func open(_ tabId: String) {
        if let onOpen { onOpen(tabId) } else { pendingOpen = tabId }
    }

    func requestReminders() {
        Task { [weak self] in
            guard let self else { return }
            _ = try? await center.requestAuthorization(options: [.alert, .sound])
            reconcile(records, reminderRecords: reminderRecords, force: true)
        }
    }

    func reconcile(_ records: [SnoozedTab], reminderRecords: [SnoozedTab], force: Bool = false) {
        self.records = records
        self.reminderRecords = reminderRecords
        let next = records.map { "\($0.tab.id):\($0.wakeAtMillis):\($0.tab.title)" } +
            reminderRecords.map { "reminder:\($0.tab.id)" }
        guard force || next != fingerprint else { return }
        fingerprint = next
        dueTimer?.invalidate()
        let now = Date().timeIntervalSince1970
        if let nextWake = records.map({ Double($0.wakeAtMillis) / 1_000 }).filter({ $0 > now }).min() {
            dueTimer = Timer.scheduledTimer(withTimeInterval: max(0.1, nextWake - now), repeats: false) { [weak self] _ in
                Task { @MainActor [weak self] in self?.onWake?() }
            }
        }
        let previous = reconciliation
        previous?.cancel()
        reconciliation = Task { [weak self] in
            await previous?.value
            guard let self, !Task.isCancelled else { return }
            let pending = await center.pendingNotificationRequests()
            guard !Task.isCancelled else { return }
            center.removePendingNotificationRequests(withIdentifiers: pending
                .map(\.identifier).filter { $0.hasPrefix(Self.prefix) })
            let delivered = await center.deliveredNotifications()
            guard !Task.isCancelled else { return }
            let retainedIDs = Set(reminderRecords.map { Self.prefix + $0.tab.id })
            center.removeDeliveredNotifications(withIdentifiers: delivered
                .map { $0.request.identifier }
                .filter { $0.hasPrefix(Self.prefix) && !retainedIDs.contains($0) })
            let settings = await center.notificationSettings()
            guard !Task.isCancelled,
                  settings.authorizationStatus == .authorized ||
                    settings.authorizationStatus == .provisional else { return }
            let now = Date().timeIntervalSince1970
            // Apple allows 64 pending notifications; reserve four for other app features.
            for record in reminderRecords.filter({ Double($0.wakeAtMillis) / 1_000 > now }).prefix(60) {
                guard !Task.isCancelled else { return }
                let content = UNMutableNotificationContent()
                content.title = "Candy"
                content.body = record.tab.title.isEmpty ? "Ein Tab wartet auf dich." : record.tab.title
                content.sound = .default
                content.userInfo = ["tabId": record.tab.id]
                let trigger = UNTimeIntervalNotificationTrigger(
                    timeInterval: max(1, Double(record.wakeAtMillis) / 1_000 - now),
                    repeats: false
                )
                try? await center.add(UNNotificationRequest(
                    identifier: Self.prefix + record.tab.id,
                    content: content,
                    trigger: trigger
                ))
            }
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard response.notification.request.identifier.hasPrefix(Self.prefix),
              let tabId = response.notification.request.content.userInfo["tabId"] as? String else { return }
        await MainActor.run { open(tabId) }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        []
    }
}
