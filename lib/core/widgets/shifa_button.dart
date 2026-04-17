import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

// ─────────────────────────────────────────────────────────
//  Shifa Design System — Button Library
// ─────────────────────────────────────────────────────────

/// Width behaviour for all Shifa buttons.
enum ButtonWidth { hug, fill }

/// Visual variant that applies destructive (red) colouring.
enum ButtonVariant { normal, destructive }

// ═════════════════════════════════════════════════════════
//  1. PRIMARY BUTTON
// ═════════════════════════════════════════════════════════

class ShifaPrimaryButton extends StatelessWidget {
  const ShifaPrimaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.width = ButtonWidth.hug,
    this.variant = ButtonVariant.normal,
    this.isLoading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final ButtonWidth width;
  final ButtonVariant variant;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !isLoading;

    Color bg;
    Color hoverBg;
    if (!enabled) {
      bg = AppColors.disabledGrey;
      hoverBg = AppColors.disabledGrey;
    } else if (variant == ButtonVariant.destructive) {
      bg = AppColors.destructiveRed;
      hoverBg = AppColors.destructiveLight;
    } else {
      bg = AppColors.primaryTeal;
      hoverBg = AppColors.primaryLight;
    }

    final style = ElevatedButton.styleFrom(
      backgroundColor: bg,
      foregroundColor: AppColors.white,
      disabledBackgroundColor: AppColors.disabledGrey,
      disabledForegroundColor: AppColors.white,
      padding: const EdgeInsets.all(10),
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      textStyle: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.bold,
      ),
    ).copyWith(
      backgroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) return AppColors.disabledGrey;
        if (states.contains(WidgetState.hovered)) return hoverBg;
        return bg;
      }),
      elevation: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.hovered) && enabled) return 2;
        return 0;
      }),
    );

    Widget child;
    if (isLoading) {
      child = const SizedBox(
        height: 16,
        width: 16,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          valueColor: AlwaysStoppedAnimation<Color>(AppColors.white),
        ),
      );
    } else if (icon != null) {
      child = Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 16),
          const SizedBox(width: 8),
          Text(label),
        ],
      );
    } else {
      child = Text(label);
    }

    final button = ElevatedButton(
      onPressed: enabled ? onPressed : null,
      style: style,
      child: child,
    );

    if (width == ButtonWidth.fill) {
      return SizedBox(width: double.infinity, child: button);
    }
    return button;
  }
}

// ═════════════════════════════════════════════════════════
//  2. SECONDARY BUTTON
// ═════════════════════════════════════════════════════════

class ShifaSecondaryButton extends StatelessWidget {
  const ShifaSecondaryButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.width = ButtonWidth.hug,
    this.variant = ButtonVariant.normal,
    this.isLoading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final ButtonWidth width;
  final ButtonVariant variant;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !isLoading;

    Color accent;
    Color hoverBg;
    if (!enabled) {
      accent = AppColors.disabledGrey;
      hoverBg = AppColors.white;
    } else if (variant == ButtonVariant.destructive) {
      accent = AppColors.destructiveRed;
      hoverBg = AppColors.destructiveSecondaryLight;
    } else {
      accent = AppColors.primaryTeal;
      hoverBg = AppColors.secondaryLight;
    }

    final style = OutlinedButton.styleFrom(
      foregroundColor: accent,
      backgroundColor: AppColors.white,
      disabledForegroundColor: AppColors.disabledGrey,
      padding: const EdgeInsets.all(10),
      side: BorderSide(color: accent, width: 2),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      textStyle: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.bold,
      ),
    ).copyWith(
      backgroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.hovered) && enabled) return hoverBg;
        return AppColors.white;
      }),
      side: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) {
          return const BorderSide(color: AppColors.disabledGrey, width: 2);
        }
        return BorderSide(color: accent, width: 2);
      }),
    );

    Widget child;
    if (isLoading) {
      child = SizedBox(
        height: 16,
        width: 16,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          valueColor: AlwaysStoppedAnimation<Color>(accent),
        ),
      );
    } else if (icon != null) {
      child = Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 16),
          const SizedBox(width: 8),
          Text(label),
        ],
      );
    } else {
      child = Text(label);
    }

    final button = OutlinedButton(
      onPressed: enabled ? onPressed : null,
      style: style,
      child: child,
    );

    if (width == ButtonWidth.fill) {
      return SizedBox(width: double.infinity, child: button);
    }
    return button;
  }
}

// ═════════════════════════════════════════════════════════
//  3. ACTION BUTTON (compact — cards, calendars, etc.)
// ═════════════════════════════════════════════════════════

/// Whether the action button uses filled (primary) or outlined (secondary) style.
enum ActionButtonStyle { primary, secondary }

class ShifaActionButton extends StatelessWidget {
  const ShifaActionButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.width = ButtonWidth.hug,
    this.variant = ButtonVariant.normal,
    this.actionStyle = ActionButtonStyle.primary,
    this.isLoading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final ButtonWidth width;
  final ButtonVariant variant;
  final ActionButtonStyle actionStyle;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    if (actionStyle == ActionButtonStyle.secondary) {
      return _buildSecondary(context);
    }
    return _buildPrimary(context);
  }

  Widget _buildPrimary(BuildContext context) {
    final enabled = onPressed != null && !isLoading;

    Color bg;
    Color hoverBg;
    if (!enabled) {
      bg = AppColors.disabledGrey;
      hoverBg = AppColors.disabledGrey;
    } else if (variant == ButtonVariant.destructive) {
      bg = AppColors.destructiveRed;
      hoverBg = AppColors.destructiveLight;
    } else {
      bg = AppColors.primaryTeal;
      hoverBg = AppColors.primaryLight;
    }

    final style = ElevatedButton.styleFrom(
      backgroundColor: bg,
      foregroundColor: AppColors.white,
      disabledBackgroundColor: AppColors.disabledGrey,
      disabledForegroundColor: AppColors.white,
      padding: const EdgeInsets.all(8),
      minimumSize: Size.zero,
      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      textStyle: const TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.bold,
      ),
    ).copyWith(
      backgroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) return AppColors.disabledGrey;
        if (states.contains(WidgetState.hovered)) return hoverBg;
        return bg;
      }),
      elevation: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.hovered) && enabled) return 2;
        return 0;
      }),
    );

    return _wrapWidth(
      ElevatedButton(
        onPressed: enabled ? onPressed : null,
        style: style,
        child: _buildChild(isLoading: isLoading, iconSize: 12, gap: 4),
      ),
    );
  }

  Widget _buildSecondary(BuildContext context) {
    final enabled = onPressed != null && !isLoading;

    Color accent;
    Color hoverBg;
    if (!enabled) {
      accent = AppColors.disabledGrey;
      hoverBg = AppColors.white;
    } else if (variant == ButtonVariant.destructive) {
      accent = AppColors.destructiveRed;
      hoverBg = AppColors.destructiveSecondaryLight;
    } else {
      accent = AppColors.primaryTeal;
      hoverBg = AppColors.secondaryLight;
    }

    final style = OutlinedButton.styleFrom(
      foregroundColor: accent,
      backgroundColor: AppColors.white,
      disabledForegroundColor: AppColors.disabledGrey,
      padding: const EdgeInsets.all(8),
      minimumSize: Size.zero,
      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      side: BorderSide(color: accent, width: 2),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      textStyle: const TextStyle(
        fontSize: 12,
        fontWeight: FontWeight.bold,
      ),
    ).copyWith(
      backgroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.hovered) && enabled) return hoverBg;
        return AppColors.white;
      }),
      side: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) {
          return const BorderSide(color: AppColors.disabledGrey, width: 2);
        }
        return BorderSide(color: accent, width: 2);
      }),
    );

    return _wrapWidth(
      OutlinedButton(
        onPressed: enabled ? onPressed : null,
        style: style,
        child: _buildChild(isLoading: isLoading, iconSize: 12, gap: 4),
      ),
    );
  }

  Widget _buildChild({
    required bool isLoading,
    required double iconSize,
    required double gap,
  }) {
    if (isLoading) {
      return SizedBox(
        height: iconSize,
        width: iconSize,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          valueColor: AlwaysStoppedAnimation<Color>(
            actionStyle == ActionButtonStyle.primary
                ? AppColors.white
                : AppColors.primaryTeal,
          ),
        ),
      );
    }
    if (icon != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: iconSize),
          SizedBox(width: gap),
          Text(label),
        ],
      );
    }
    return Text(label);
  }

  Widget _wrapWidth(Widget button) {
    if (width == ButtonWidth.fill) {
      return SizedBox(width: double.infinity, child: button);
    }
    return button;
  }
}

// ═════════════════════════════════════════════════════════
//  4. ICON-ONLY BUTTON
// ═════════════════════════════════════════════════════════

/// The parent button type whose colours to inherit.
enum IconButtonType { primary, secondary, action }

class ShifaIconButton extends StatelessWidget {
  const ShifaIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.type = IconButtonType.primary,
    this.variant = ButtonVariant.normal,
    this.size = 40,
    this.iconSize = 16,
    this.tooltip,
  });

  final IconData icon;
  final VoidCallback? onPressed;
  final IconButtonType type;
  final ButtonVariant variant;
  final double size;
  final double iconSize;
  final String? tooltip;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;

    // Resolve colours based on parent type + variant
    final bool isFilled =
        type == IconButtonType.primary || type == IconButtonType.action;

    Color bg, fg, borderColor, hoverBg;

    if (!enabled) {
      if (isFilled) {
        bg = AppColors.disabledGrey;
        fg = AppColors.white;
        borderColor = Colors.transparent;
        hoverBg = AppColors.disabledGrey;
      } else {
        bg = AppColors.white;
        fg = AppColors.disabledGrey;
        borderColor = AppColors.disabledGrey;
        hoverBg = AppColors.white;
      }
    } else if (variant == ButtonVariant.destructive) {
      if (isFilled) {
        bg = AppColors.destructiveRed;
        fg = AppColors.white;
        borderColor = Colors.transparent;
        hoverBg = AppColors.destructiveLight;
      } else {
        bg = AppColors.white;
        fg = AppColors.destructiveRed;
        borderColor = AppColors.destructiveRed;
        hoverBg = AppColors.destructiveSecondaryLight;
      }
    } else {
      if (isFilled) {
        bg = AppColors.primaryTeal;
        fg = AppColors.white;
        borderColor = Colors.transparent;
        hoverBg = AppColors.primaryLight;
      } else {
        bg = AppColors.white;
        fg = AppColors.primaryTeal;
        borderColor = AppColors.primaryTeal;
        hoverBg = AppColors.secondaryLight;
      }
    }

    Widget button = SizedBox(
      width: size,
      height: size,
      child: Material(
        color: bg,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: borderColor == Colors.transparent
              ? BorderSide.none
              : BorderSide(color: borderColor, width: 2),
        ),
        child: InkWell(
          onTap: onPressed,
          borderRadius: BorderRadius.circular(8),
          hoverColor: hoverBg,
          child: Center(
            child: Icon(icon, size: iconSize, color: fg),
          ),
        ),
      ),
    );

    if (tooltip != null) {
      button = Tooltip(message: tooltip!, child: button);
    }

    return button;
  }
}
