import SwiftUI

struct ContentView: View {
    @StateObject private var memory=MemoryStore(); @StateObject private var host=HostServer(); @State private var input=""
    var body: some View { NavigationStack { VStack(spacing:0) { Text(host.status).font(.caption).padding(6).frame(maxWidth:.infinity).background(host.running ? Color.green.opacity(0.2):Color.gray.opacity(0.2)); ScrollView { LazyVStack(alignment:.leading,spacing:10) { ForEach(Array(memory.messages.enumerated()),id:\.offset) { _,m in VStack(alignment:.leading) { Text(m.0).font(.caption).foregroundStyle(.secondary); Text(m.1).padding(9).background(Color.blue.opacity(m.0=="JARVIS" ? 0.15:0.07)).clipShape(RoundedRectangle(cornerRadius:9)) } } }.padding() }; HStack { TextField("JARVISへの指示",text:$input,axis:.vertical).textFieldStyle(.roundedBorder); Button("送信") { send() }.disabled(input.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty) }.padding() }.navigationTitle("JARVIS親機").toolbar { ToolbarItem(placement:.topBarTrailing) { Button(host.running ? "停止":"起動") { host.running ? host.stop():host.start(memory:memory) } } }.onAppear { host.start(memory:memory) } } }
    private func send() { let t=input; input=""; memory.add(role:"あなた",text:t); memory.add(role:"JARVIS",text:memory.answer(t)) }
}
