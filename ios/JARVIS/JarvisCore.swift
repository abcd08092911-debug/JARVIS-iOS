import Foundation
import Network
import Combine

struct ChatRequest: Codable { let text: String }
struct ChatReply: Codable { let status: String; let mode: String; let reply: String }

final class MemoryStore: ObservableObject {
    @Published var messages: [(String,String)] = []
    private let url: URL
    init() { let base=FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0]; try? FileManager.default.createDirectory(at:base,withIntermediateDirectories:true); url=base.appendingPathComponent("jarvis-memory.json"); load() }
    func add(role:String,text:String) { DispatchQueue.main.async { self.messages.append((role,text)); self.save() } }
    func save() { let data=messages.map { ["role":$0.0,"text":$0.1] }; if let d=try? JSONSerialization.data(withJSONObject:data),let s=String(data:d,encoding:.utf8){try? s.write(to:url,atomically:true,encoding:.utf8)} }
    private func load() { guard let d=try? Data(contentsOf:url),let a=try? JSONSerialization.jsonObject(with:d) as? [[String:String]] else{return}; messages=a.compactMap{guard let r=$0["role"],let t=$0["text"] else{return nil};return(r,t)} }
    func answer(_ text:String)->String { let t=text.lowercased(); if t.contains("こんにちは")||t.contains("こんばんは"){return "こんにちは。JARVIS iPhone親機です。"}; if t.contains("記憶")||t.contains("覚えて"){return "承知しました。iPhone本体の記憶へ保存します。"}; return "承知しました。現在、iPhone親機のローカルAIコアで処理しました。詳細な生成AIモデルはモデル追加後に有効になります。" }
}

final class HostServer: ObservableObject {
    @Published var running=false; @Published var port:UInt16=8765; @Published var status="停止中"
    private var listener:NWListener?; weak var memory:MemoryStore?
    func start(memory:MemoryStore) { guard listener == nil else{return}; self.memory=memory; do { let p=NWEndpoint.Port(rawValue:port)!; listener=try NWListener(using:.tcp,on:p); listener?.stateUpdateHandler={ [weak self] s in DispatchQueue.main.async { self?.running=(s == .ready); self?.status=(s == .ready ? "親機起動中 :8765":"停止中") } }; listener?.newConnectionHandler={ [weak self] c in self?.handle(c) }; listener?.start(queue:.global(qos:.userInitiated)) } catch { status="起動失敗" } }
    func stop(){listener?.cancel();listener=nil;DispatchQueue.main.async{self.running=false;self.status="停止中"}}
    private func handle(_ c:NWConnection){ c.start(queue:.global(qos:.userInitiated)); c.receive(minimumIncompleteLength:1,maximumLength:65536){ [weak self] data,_,_,_ in guard let self=self,let received=data else{c.cancel();return}; let raw=String(data:received,encoding:.utf8) ?? ""; let parts=raw.components(separatedBy:"\r\n\r\n"); let first=parts.first ?? ""; let tokens=first.split(separator:" "); let path=tokens.count>1 ? String(tokens[1]):"/"; let body=parts.count>1 ? parts[1]:""; let result=self.response(path:path,body:body); let payload=(try? JSONEncoder().encode(result)) ?? Data("{}".utf8); let head="HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: \(payload.count)\r\nConnection: close\r\n\r\n"; var output=Data(head.utf8); output.append(payload); c.send(content:output,completion:.contentProcessed({_ in c.cancel()})) } }
    private func response(path:String,body:String)->ChatReply { if path.hasPrefix("/health"){return ChatReply(status:"ok",mode:"iphone-host",reply:"JARVIS iPhone親機稼働中")}; if path.hasPrefix("/chat"),let d=body.data(using:.utf8),let q=try? JSONDecoder().decode(ChatRequest.self,from:d),let m=memory { let a=m.answer(q.text); m.add(role:"user",text:q.text); m.add(role:"JARVIS",text:a); return ChatReply(status:"ok",mode:"iphone-host-local",reply:a) }; return ChatReply(status:"ok",mode:"iphone-host",reply:"JARVIS iPhone親機です") }
}
