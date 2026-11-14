import Foundation
import Combine

struct RelayMessage: Codable { let id: String; let text: String }
struct RelayMessages: Codable { let messages: [RelayMessage] }

final class RelayClient: ObservableObject {
    @Published var running = false
    @Published var status = "中継停止中"
    private var timer: Timer?
    private weak var memory: MemoryStore?
    private var base = ""
    private var room = ""
    private var token = ""
    func start(url: String, room: String, token: String, memory: MemoryStore) {
        self.base=url.trimmingCharacters(in:.whitespacesAndNewlines).trimmingCharacters(in:CharacterSet(charactersIn:"/")); self.room=room; self.token=token; self.memory=memory; running=true; status="中継接続中"
        post(path:"/register", body:["room":room,"token":token]) { [weak self] _ in DispatchQueue.main.async { self?.status="中継稼働中"; self?.poll() } }
        timer=Timer.scheduledTimer(withTimeInterval:2,repeats:true){ [weak self] _ in self?.poll() }
    }
    func stop() { timer?.invalidate(); timer=nil; running=false; status="中継停止中" }
    private func poll() { guard running, let u=URL(string:"\(base)/host/poll?room=\(room.addingPercentEncoding(withAllowedCharacters:.urlQueryAllowed) ?? room)&token=\(token.addingPercentEncoding(withAllowedCharacters:.urlQueryAllowed) ?? token)") else{return}; URLSession.shared.dataTask(with:u){ [weak self] data,_,_ in guard let self,let data,let result=try? JSONDecoder().decode(RelayMessages.self,from:data) else{return}; for m in result.messages { guard let memory=self.memory else{continue}; let answer=memory.answer(m.text); memory.add(role:"子機",text:m.text); memory.add(role:"JARVIS",text:answer); self.post(path:"/host/reply",body:["room":self.room,"token":self.token,"message_id":m.id,"reply":answer],completion:nil) } }.resume() }
    private func post(path:String,body:[String:String],completion:((Data?)->Void)?) { guard let u=URL(string:base+path),let d=try? JSONSerialization.data(withJSONObject:body) else{return}; var r=URLRequest(url:u); r.httpMethod="POST"; r.setValue("application/json",forHTTPHeaderField:"Content-Type"); r.httpBody=d; URLSession.shared.dataTask(with:r){ data,_,_ in completion?(data) }.resume() }
    deinit { timer?.invalidate() }
}
