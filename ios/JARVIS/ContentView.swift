import SwiftUI

enum IOChoice: String, CaseIterable, Identifiable { case text="文字"; case voice="音声"; var id:String{rawValue} }

struct ContentView: View {
    @StateObject private var memory=MemoryStore(); @StateObject private var host=HostServer(); @StateObject private var voice=VoiceIO()
    @State private var input=""; @State private var inputMode:IOChoice = .text; @State private var outputMode:IOChoice = .text
    var body: some View { NavigationStack { VStack(spacing:0) {
        Text(host.status).font(.caption).padding(6).frame(maxWidth:.infinity).background(host.running ? Color.green.opacity(0.2):Color.gray.opacity(0.2))
        HStack { Picker("入力",selection:$inputMode){ForEach(IOChoice.allCases){Text("入力: \($0.rawValue)").tag($0)}}.pickerStyle(.menu); Picker("出力",selection:$outputMode){ForEach(IOChoice.allCases){Text("出力: \($0.rawValue)").tag($0)}}.pickerStyle(.menu) }.padding(.horizontal)
        ScrollView { LazyVStack(alignment:.leading,spacing:10) { ForEach(Array(memory.messages.enumerated()),id:\.offset) { _,m in VStack(alignment:.leading) { Text(m.0).font(.caption).foregroundStyle(.secondary); Text(m.1).padding(9).background(Color.blue.opacity(m.0=="JARVIS" ? 0.15:0.07)).clipShape(RoundedRectangle(cornerRadius:9)) } } }.padding() }
        HStack { if inputMode == .text { TextField("JARVISへの指示",text:$input,axis:.vertical).textFieldStyle(.roundedBorder) } else { Button(voice.isListening ? "聞き取り停止":"音声入力開始") { voice.toggleListening() }.buttonStyle(.borderedProminent); Text(voice.transcript.isEmpty ? "話しかけてください" : voice.transcript).lineLimit(2) }; Button("送信") { send() }.disabled(currentText.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty) }.padding()
    }.navigationTitle("JARVIS親機").toolbar { ToolbarItem(placement:.topBarTrailing) { Button(host.running ? "停止":"起動") { host.running ? host.stop():host.start(memory:memory) } } }.onAppear { host.start(memory:memory) } } }
    private var currentText:String { inputMode == .text ? input : voice.transcript }
    private func send() { let t=currentText.trimmingCharacters(in:.whitespacesAndNewlines); guard !t.isEmpty else{return}; input=""; voice.transcript=""; memory.add(role:"あなた",text:t); let answer=memory.answer(t); memory.add(role:"JARVIS",text:answer); if outputMode == .voice { voice.speak(answer) } }
}
