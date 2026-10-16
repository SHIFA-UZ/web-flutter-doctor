import 'dart:async';

import 'package:flutter/material.dart';

/// Runs [action] when Enter is pressed in a single-line field.
///
/// Use this when a screen, dialog, or sheet has one primary button
/// (Continue, Verify, Save, Send, Search). Pass null while that action
/// is already running.
///
/// With several fields, set [TextInputAction.next] on every field except
/// the last, and [TextInputAction.done] plus this callback on the last.
/// Chat and Ask composers use [TextInputAction.send] with this callback.
///
/// Leave multiline clinical notes alone so Enter inserts a new line.
ValueChanged<String>? submitOnEnter(FutureOr<void> Function()? action) {
  if (action == null) return null;
  return (_) => action();
}
