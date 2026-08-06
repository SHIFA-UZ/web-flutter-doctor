// Stub for web — daily_flutter is native-only. Keep API surface aligned with
// daily_flutter 0.37 usage in video_call_screen.dart so web still analyzes.
import 'package:flutter/widgets.dart';

class CallClient {
  CallClient._();
  static Future<CallClient> create() async => CallClient._();

  Stream<dynamic> get events => const Stream.empty();
  Participants get participants => Participants(local: Participant(id: 'local'));
  dynamic get inputs => null;
  dynamic get availableDevices => null;

  Future<void> join({
    required Uri url,
    String? token,
    dynamic clientSettings,
  }) async {}

  Future<void> leave() async {}

  Future<void> updateInputs({required dynamic inputs}) async {}

  Future<void> updatePublishing({required dynamic publishing}) async {}

  Future<void> setAudioDevice({required dynamic deviceId}) async {}

  Future<void> setInputsEnabled({bool? camera, bool? microphone}) async {}

  Future<void> dispose() async {}
}

class Participants {
  final Participant local;
  final Map<dynamic, Participant> remote;
  Participants({required this.local, Map<dynamic, Participant>? remote})
      : remote = remote ?? {};
}

class Participant {
  final dynamic id;
  final ParticipantInfo info;
  final ParticipantMedia? media;
  Participant({required this.id, ParticipantInfo? info, this.media})
      : info = info ?? ParticipantInfo(isLocal: true);
}

class ParticipantInfo {
  final bool isLocal;
  ParticipantInfo({this.isLocal = false});
}

class ParticipantMedia {
  final ParticipantMediaTrack camera;
  ParticipantMedia({ParticipantMediaTrack? camera})
      : camera = camera ?? ParticipantMediaTrack();
}

class ParticipantMediaTrack {
  final dynamic track;
  ParticipantMediaTrack({this.track});
}

class VideoViewController {
  void setTrack(dynamic track) {}
  void dispose() {}
}

class VideoView extends StatelessWidget {
  final VideoViewController controller;
  final dynamic fit;
  const VideoView({super.key, required this.controller, this.fit});
  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}

// Settings update stubs used at call sites (web never executes them).
class BoolUpdate {
  static dynamic set(bool value) => value;
}

class CameraInputSettingsUpdate {
  static dynamic set({dynamic isEnabled}) => null;
}

class MicrophoneInputSettingsUpdate {
  static dynamic set({dynamic isEnabled}) => null;
}

class InputSettingsUpdate {
  static dynamic set({dynamic camera, dynamic microphone}) => null;
}

class CameraPublishingSettingsUpdate {
  static dynamic set({dynamic isPublishing}) => null;
}

class MicrophonePublishingSettingsUpdate {
  static dynamic set({dynamic isPublishing}) => null;
}

class PublishingSettingsUpdate {
  static dynamic set({dynamic camera, dynamic microphone}) => null;
}

class ClientSettingsUpdate {
  static dynamic set({dynamic inputs, dynamic publishing}) => null;
}

typedef MediaStreamTrack = dynamic;
