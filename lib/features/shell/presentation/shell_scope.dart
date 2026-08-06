// lib/features/shell/presentation/shell_scope.dart
import 'package:flutter/material.dart';

/// Top-level key for the shell's nested navigator. Use this to push routes
/// into the shell's content area from places that do not have a BuildContext
/// inside the shell (e.g. notification tap handlers in [app.dart]).
///
/// The shell's [Navigator] in `main_shell.dart` is keyed with this so its
/// `currentState` is reachable globally once the shell is mounted. Using the
/// outer [navigatorKey] for the same routes would render the target screen
/// outside the sidebar/scaffold, since the outer router's matching cases
/// don't wrap them in [MainShell].
final GlobalKey<NavigatorState> shellNavigatorKey =
    GlobalKey<NavigatorState>(debugLabel: 'shellNavigator');

/// Provides access to the shell's nested navigator so that pushes happen
/// inside the content area and the sidebar stays visible.
class ShellScope extends InheritedWidget {
  const ShellScope({
    super.key,
    required this.navigatorKey,
    required super.child,
  });

  final GlobalKey<NavigatorState> navigatorKey;

  static ShellScope? of(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<ShellScope>();
  }

  /// Pushes a named route using the shell navigator when available,
  /// so the sidebar remains visible. Falls back to root Navigator otherwise.
  static Future<T?> pushNamed<T extends Object?>(
    BuildContext context,
    String routeName, {
    Object? arguments,
  }) {
    final scope = ShellScope.of(context);
    if (scope != null) {
      return scope.navigatorKey.currentState!.pushNamed<T>(
        routeName,
        arguments: arguments,
      );
    }
    return Navigator.pushNamed<T>(context, routeName, arguments: arguments);
  }

  /// Replaces the current route using the shell navigator when available.
  static Future<T?> pushReplacementNamed<T extends Object?, TO extends Object?>(
    BuildContext context,
    String routeName, {
    Object? arguments,
    TO? result,
  }) {
    final scope = ShellScope.of(context);
    if (scope != null) {
      return scope.navigatorKey.currentState!.pushReplacementNamed<T, TO>(
        routeName,
        arguments: arguments,
        result: result,
      );
    }
    return Navigator.pushReplacementNamed<T, TO>(
      context,
      routeName,
      arguments: arguments,
      result: result,
    );
  }

  /// Pushes a route using the shell navigator when available.
  static Future<T?> push<T extends Object?>(
    BuildContext context,
    Route<T> route,
  ) {
    final scope = ShellScope.of(context);
    if (scope != null) {
      return scope.navigatorKey.currentState!.push<T>(route);
    }
    return Navigator.push<T>(context, route);
  }

  /// Whether the shell navigator or the nearest [Navigator] can pop one route.
  static bool canPop(BuildContext context) {
    if (shellNavigatorKey.currentState?.canPop() == true) {
      return true;
    }
    final scope = ShellScope.of(context);
    if (scope != null && scope.navigatorKey.currentState?.canPop() == true) {
      return true;
    }
    return Navigator.of(context).canPop();
  }

  /// Pushes a route into the shell nested navigator (sidebar stays visible).
  /// Use when [BuildContext] is outside [ShellScope] (e.g. clinic doctor schedule
  /// on the root navigator).
  static void pushIntoShell(
    Object arguments, {
    int retriesLeft = 5,
  }) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final navState = shellNavigatorKey.currentState;
      if (navState != null) {
        navState.pushNamed(
          '/app/patients/selection',
          arguments: arguments,
        );
        return;
      }
      if (retriesLeft > 0) {
        pushIntoShell(arguments, retriesLeft: retriesLeft - 1);
      }
    });
  }

  /// Pops the shell navigator when available.
  static void pop<T extends Object?>(BuildContext context, [T? result]) {
    final scope = ShellScope.of(context);
    if (scope != null && scope.navigatorKey.currentState?.canPop() == true) {
      scope.navigatorKey.currentState!.pop(result);
    } else {
      Navigator.of(context).pop(result);
    }
  }

  @override
  bool updateShouldNotify(ShellScope oldWidget) =>
      navigatorKey != oldWidget.navigatorKey;
}
