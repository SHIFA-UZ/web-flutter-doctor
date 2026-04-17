// lib/core/services/daily_flutter_stub.dart
// Stub file for web platform - daily_flutter doesn't support web
import 'package:flutter/widgets.dart';

// These are just type stubs to prevent compilation errors on web
class CallClient {
  Stream<dynamic> get events => const Stream.empty();
  Future<void> join({required String roomUrl, required String token}) async {}
  Future<void> leave() async {}
  Future<void> setLocalAudio(bool enabled) async {}
  Future<void> setLocalVideo(bool enabled) async {}
  Future<void> startScreenShare() async {}
  Future<void> stopScreenShare() async {}
  List<Participant> participants() => [];
}

class CallEvent {}
class CallStateUpdated extends CallEvent {
  final CallState state;
  CallStateUpdated(this.state);
}
class ParticipantJoined extends CallEvent {
  final Participant participant;
  ParticipantJoined(this.participant);
}
class ParticipantLeft extends CallEvent {
  final Participant participant;
  ParticipantLeft(this.participant);
}

class Participant {
  final String id;
  final bool isLocal;
  final dynamic videoTrack;
  Participant({required this.id, required this.isLocal, this.videoTrack});
}

class CallState {
  static const left = CallState._('left');
  static const error = CallState._('error');
  final String value;
  const CallState._(this.value);
}

class VideoView extends StatelessWidget {
  final dynamic track;
  final bool mirror;
  const VideoView({Key? key, required this.track, this.mirror = false}) : super(key: key);
  @override
  Widget build(BuildContext context) => Container();
}
