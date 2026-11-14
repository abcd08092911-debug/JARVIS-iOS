import Foundation
import Speech
import AVFoundation

final class VoiceIO: NSObject, ObservableObject {
    @Published var transcript = ""
    @Published var isListening = false
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "ja-JP"))
    private let synthesizer = AVSpeechSynthesizer()
    func toggleListening() { isListening ? stopListening() : startListening() }
    private func startListening() {
        SFSpeechRecognizer.requestAuthorization { _ in }
        AVAudioSession.sharedInstance().requestRecordPermission { _ in }
        transcript = ""; task?.cancel(); request = SFSpeechAudioBufferRecognitionRequest(); guard let request=request else{return}
        let node=audioEngine.inputNode; let format=node.outputFormat(forBus:0); node.removeTap(onBus:0); node.installTap(onBus:0,bufferSize:1024,format:format){ [weak self] buffer,_ in self?.request?.append(buffer) }
        audioEngine.prepare(); try? audioEngine.start(); isListening=true
        task=recognizer?.recognitionTask(with:request){ [weak self] result,error in if let result=result { DispatchQueue.main.async { self?.transcript=result.bestTranscription.formattedString } }; if error != nil { self?.stopListening() } }
    }
    private func stopListening() { audioEngine.stop(); audioEngine.inputNode.removeTap(onBus:0); request?.endAudio(); task?.cancel(); task=nil; request=nil; isListening=false }
    func speak(_ text:String) { synthesizer.stopSpeaking(at:.immediate); let u=AVSpeechUtterance(string:text); u.voice=AVSpeechSynthesisVoice(language:"ja-JP"); u.rate=0.48; synthesizer.speak(u) }
}
