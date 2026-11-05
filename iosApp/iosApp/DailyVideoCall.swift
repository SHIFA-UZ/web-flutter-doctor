import ComposeApp
import Daily
import UIKit

/// Daily's iOS SDK behind the shared `VideoCall` (composeApp VideoCall.ios.kt; copied from the patient app). Registered in AppDelegate.
/// Daily is main-actor only; the shared code calls from the main thread, hence the @preconcurrency conformances.
@MainActor
final class DailyVideoCall: NSObject, @preconcurrency NativeVideoCall, @preconcurrency CallClientDelegate {
    private lazy var client: CallClient = {
        let client = CallClient()
        client.delegate = self
        return client
    }()

    private lazy var local = DailyVideoCall.tile()
    private lazy var remote = DailyVideoCall.tile()
    private weak var listener: NativeVideoCallListener?
    private var joined = false
    private var left = false
    private var failed = false
    private var front = true
    // Left (or freed): a join still waiting for its inputs is dropped.
    private var leaving = false

    nonisolated override init() {
        super.init()
    }

    private static func tile() -> VideoView {
        let view = VideoView()
        view.videoScaleMode = .fill
        return view
    }

    func setListener(listener: NativeVideoCallListener) {
        self.listener = listener
    }

    func startPreview() {
        client.setInputsEnabled([.camera: true, .microphone: true], completion: nil)
    }

    func join(roomUrl: String, token: String, camera: Bool, mic: Bool) {
        guard let url = URL(string: roomUrl) else { return fail() }
        failed = false
        // The doctor's choices first: the join itself never turns the camera or microphone on.
        client.setInputsEnabled([.camera: camera, .microphone: mic]) { [weak self] _ in
            guard let self, !self.leaving else { return }
            self.joinRoom(url: url, token: token)
        }
    }

    private func joinRoom(url: URL, token: String) {
        client.join(url: url, token: MeetingToken(stringValue: token)) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success:
                // The loudspeaker, not the earpiece.
                self.client.set(preferredAudioDevice: .speakerphone, completion: nil)
            case let .failure(error):
                // Debug builds only, and never the SDK's text (it can carry the room or the token).
                #if DEBUG
                NSLog("FAILED Daily join")
                #endif
                self.fail()
            }
        }
    }

    func setCamera(on: Bool) {
        client.setInputsEnabled([.camera: on], completion: nil)
    }

    func setMic(on: Bool) {
        client.setInputsEnabled([.microphone: on], completion: nil)
    }

    func flipCamera() {
        front.toggle()
        let settings = VideoMediaTrackSettingsUpdate(facingMode: .set(front ? .user : .environment))
        client.updateInputs(.set(camera: .set(settings: .set(settings))), completion: nil)
    }

    func leave() {
        leaving = true
        client.leave(completion: nil)
    }

    func localView() -> UIView { local }

    func remoteView() -> UIView { remote }

    private func fail() {
        failed = true
        notify()
    }

    private func notify() {
        let participants = client.participants
        local.track = participants.local.media?.camera.track
        remote.track = participants.all.values.first { $0.id != participants.local.id }?.media?.camera.track
        listener?.onChange(
            joined: joined,
            left: left,
            cameraOn: client.inputs.camera.isEnabled,
            micOn: client.inputs.microphone.isEnabled,
            patientJoined: participants.all.count > 1,
            failed: failed
        )
    }

    func callClient(_ callClient: CallClient, callStateUpdated state: CallState) {
        joined = state == .joined
        left = state == .left
        notify()
    }

    func callClient(_ callClient: CallClient, inputsUpdated inputs: InputSettings) { notify() }

    func callClient(_ callClient: CallClient, participantJoined participant: Participant) { notify() }

    func callClient(_ callClient: CallClient, participantUpdated participant: Participant) { notify() }

    func callClient(_ callClient: CallClient, participantLeft participant: Participant, withReason reason: ParticipantLeftReason) { notify() }

    func callClient(_ callClient: CallClient, error: CallClientError) {
        #if DEBUG
        NSLog("FAILED Daily call")
        #endif
        fail()
    }
}
