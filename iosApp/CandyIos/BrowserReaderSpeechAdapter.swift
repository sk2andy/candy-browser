import AVFoundation
import CandyShared
import Foundation

@MainActor
final class BrowserReaderSpeechAdapter: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var activeUtterance: AVSpeechUtterance?
    private var content = ""
    private var isClosed = false

    private(set) lazy var bridge = BrowserReaderSpeech(
        onPlay: { [weak self] value in self?.play(value) },
        onPause: { [weak self] in self?.pause() },
        onStop: { [weak self] in self?.stop() },
        onClose: { [weak self] in self?.close() }
    )

    override init() {
        super.init()
        synthesizer.delegate = self
        reduce(ReaderSpeechEventInitialized.shared)
    }

    func close() {
        guard !isClosed else { return }
        isClosed = true
        activeUtterance = nil
        synthesizer.stopSpeaking(at: .immediate)
        synthesizer.delegate = nil
        bridge.close()
        content = ""
    }

    private func play(_ value: String) {
        guard !isClosed, !value.isEmpty else { return }
        if bridge.state.status == .paused, content == value,
           synthesizer.continueSpeaking() {
            reduce(ReaderSpeechEventPlay.shared)
            return
        }
        stop()
        content = value
        let utterance = AVSpeechUtterance(string: value)
        utterance.voice = AVSpeechSynthesisVoice(language: Locale.current.identifier)
        activeUtterance = utterance
        reduce(ReaderSpeechEventPlay.shared)
        synthesizer.speak(utterance)
    }

    private func pause() {
        guard !isClosed, synthesizer.pauseSpeaking(at: .word) else { return }
        reduce(ReaderSpeechEventPause.shared)
    }

    private func stop() {
        guard !isClosed else { return }
        activeUtterance = nil
        synthesizer.stopSpeaking(at: .immediate)
        reduce(ReaderSpeechEventStop.shared)
    }

    private func reduce(_ event: ReaderSpeechEvent) {
        bridge.updateState(value: ReaderSpeechRules.shared.reduce(
            state: bridge.state,
            event: event,
            textLength: Int32(clamping: content.utf16.count)
        ))
    }

    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString characterRange: NSRange,
        utterance: AVSpeechUtterance
    ) {
        let utteranceId = ObjectIdentifier(utterance)
        Task { @MainActor [weak self] in
            guard let self, !self.isClosed,
                  self.activeUtterance.map(ObjectIdentifier.init) == utteranceId else { return }
            self.reduce(ReaderSpeechEventRangeStarted(
                characterOffset: Int32(clamping: characterRange.location)
            ))
        }
    }

    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        let utteranceId = ObjectIdentifier(utterance)
        Task { @MainActor [weak self] in
            guard let self, !self.isClosed,
                  self.activeUtterance.map(ObjectIdentifier.init) == utteranceId else { return }
            self.activeUtterance = nil
            self.reduce(ReaderSpeechEventCompleted.shared)
        }
    }

    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didCancel utterance: AVSpeechUtterance
    ) {
        let utteranceId = ObjectIdentifier(utterance)
        Task { @MainActor [weak self] in
            guard let self, !self.isClosed,
                  self.activeUtterance.map(ObjectIdentifier.init) == utteranceId else { return }
            self.activeUtterance = nil
            self.reduce(ReaderSpeechEventStop.shared)
        }
    }
}
