# Unified Notes Menu - Combined Three-Dot Buttons

**Issue:** Two separate three-dot (⋮) menu buttons in notes section - confusing UX
**Date:** March 4, 2026
**Status:** READY TO IMPLEMENT

---

## 🎯 **Current State (2 Menus)**

### **Menu 1 (Icons.more_horiz - Horizontal dots)**
- Only shows if `_documentationType == 'general'`
- Items:
  - "From Shifa AI" → Expands AI notes section
  - "From last 025-2 form" → Expands form data section

### **Menu 2 (Icons.more_vert - Vertical dots)**
- Always shows
- Items:
  - "General" → Switch to general notes mode
  - "Form 025-2" → Switch to 025-2 form mode
- Has unsaved changes handling

### **Between Menus:**
- Fullscreen button (expand icon)

**Problem:**
- 2-3 buttons in title bar (confusing)
- Redundant menus
- Poor UX

---

## ✅ **Proposed Solution: Unified Menu**

### **One Menu (Icons.more_vert) With All Options**

**When in General Mode:**
```
┌─────────────────────────────┐
│ Notes                    ⋮ ☐ │ ← Only ONE menu now
├─────────────────────────────┤
Menu items:
  • From Shifa AI
  • From last 025-2 form
  ─────────────────
  • Switch to Form 025-2
  • Fullscreen
```

**When in Form 025-2 Mode:**
```
┌─────────────────────────────┐
│ Form 025-2               ⋮ ☐ │ ← Only ONE menu
├─────────────────────────────┤
Menu items:
  • Switch to General Notes
  • Fullscreen
```

---

## 🔧 **Implementation Plan**

### **Step 1: Remove First Menu**
Delete lines ~1212-1256 (first PopupMenuButton with more_horiz)

### **Step 2: Move Fullscreen to Menu**
Remove IconButton for fullscreen, add as menu item

### **Step 3: Enhance Second Menu**
Add to itemBuilder:
```dart
itemBuilder: (context) => [
  // If in general mode, show AI/025-2 helper options
  if (_documentationType == 'general') ...[
    PopupMenuItem(
      value: 'show_ai',
      child: Row(
        children: [
          Icon(Icons.auto_awesome, size: 18),
          SizedBox(width: 8),
          Text('From Shifa AI'),
        ],
      ),
    ),
    PopupMenuItem(
      value: 'show_0252',
      child: Row(
        children: [
          Icon(Icons.description, size: 18),
          SizedBox(width: 8),
          Text('From last 025-2 form'),
        ],
      ),
    ),
    PopupMenuDivider(),
  ],

  // Switch documentation type
  PopupMenuItem(
    value: 'switch_general',
    child: Row(
      children: [
        Icon(Icons.notes, size: 18),
        SizedBox(width: 8),
        Text('General Notes'),
      ],
    ),
  ),
  PopupMenuItem(
    value: 'switch_0252',
    child: Row(
      children: [
        Icon(Icons.assignment, size: 18),
        SizedBox(width: 8),
        Text('Form 025-2'),
      ],
    ),
  ),

  PopupMenuDivider(),

  // Fullscreen
  PopupMenuItem(
    value: 'fullscreen',
    child: Row(
      children: [
        Icon(Icons.fullscreen, size: 18),
        SizedBox(width: 8),
        Text('Fullscreen'),
      ],
    ),
  ),
],
```

### **Step 4: Update onSelected Handler**
```dart
onSelected: (value) async {
  final l10n = AppLocalizations.of(context)!;

  // Handle AI/025-2 helper sections
  if (value == 'show_ai') {
    setState(() => _notesSectionsExpanded = true);
    // ... load AI notes
  } else if (value == 'show_0252') {
    setState(() => _notesSectionsExpanded = true);
    // ... load 025-2 data
  }

  // Handle documentation type switch
  else if (value == 'switch_general' || value == 'switch_0252') {
    final newType = value == 'switch_general' ? 'general' : '025-2';
    if (_documentationType == newType) return;

    // Check for unsaved changes...
    // Switch type
  }

  // Handle fullscreen
  else if (value == 'fullscreen') {
    setState(() => _notesPanelFullScreen = true);
  }
}
```

---

## 🎨 **Visual Comparison**

### **Before (2 Menus):**
```
┌─────────────────────────────────┐
│ Notes          ⋯ ☐ ⋮            │ ← 3 buttons!
└─────────────────────────────────┘
```

### **After (1 Menu):**
```
┌─────────────────────────────────┐
│ Notes                    ⋮      │ ← 1 button!
└─────────────────────────────────┘
```

**Benefits:**
- ✅ Cleaner UI
- ✅ All options in one place
- ✅ Less visual clutter
- ✅ Better UX

---

## 📋 **Combined Menu Structure**

```
General Notes Mode:
├─ From Shifa AI (show AI helper)
├─ From last 025-2 form (show form helper)
├─ ─────────────
├─ Switch to Form 025-2
└─ Fullscreen

Form 025-2 Mode:
├─ Switch to General Notes
└─ Fullscreen
```

---

## ⚠️ **Complexity Note**

This requires careful editing because:
- Unsaved changes handling (dialog confirmation)
- State management (_documentationType, _notesSectionsExpanded)
- Multiple async operations
- Context requirements

**Estimated effort:** 20-30 minutes of careful editing

---

## ✅ **Status**

**Documentation:** Complete
**Implementation:** Ready to code
**Testing:** Will need thorough testing

**This is a good enhancement but can be done as a separate task if needed.**
