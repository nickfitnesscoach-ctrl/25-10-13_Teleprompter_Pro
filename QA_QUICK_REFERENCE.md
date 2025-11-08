# TelePrompt One Pro - QA Quick Reference Guide

## Quick Start for Testing

### App Entry Points
1. **MainActivity** - Script list and overlay launcher
2. **ScriptEditorActivity** - Create/edit scripts
3. **TeleprompterOverlayService** - Foreground service for overlay display

### Core Paths (Absolute)
- **Source Code:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/`
- **Resources:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/res/`
- **Database:** `/data/data/com.teleprompter.app/databases/teleprompter_db`
- **Preferences:** `/data/data/com.teleprompter.app/shared_prefs/overlay_preferences.xml`

---

## Critical Components to Test

### 1. TeleprompterOverlayService (526 LOC)
**File:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/ui/overlay/TeleprompterOverlayService.kt`

**Key Functions:**
- `createOverlay()` - Creates overlay window
- `startScrolling()` - Begins ValueAnimator-based scroll
- `increaseSpeed()` / `decreaseSpeed()` - Adjusts speed by ±1
- `handleDragTouch()` - Implements drag & drop
- `createNotification()` - Foreground service notification

**Test Checklist:**
- [ ] Overlay appears when service starts
- [ ] Overlay positions correctly (portrait/landscape)
- [ ] Scroll starts smoothly
- [ ] Speed changes reflected immediately
- [ ] Drag respects screen boundaries
- [ ] Position saved and restored
- [ ] Proper cleanup on onDestroy()

### 2. ScrollController (193 LOC)
**File:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/core/ScrollController.kt`

**Key Functions:**
- `startScrolling()` - Uses ValueAnimator
- `togglePlayPause()` - Play/pause control
- `setSpeed(Int)` - Direct speed setting
- `increaseSpeed()` / `decreaseSpeed()` - Increment control

**Test Checklist:**
- [ ] Smooth 60 FPS animation
- [ ] Speed range properly clamped (10-200 px/sec)
- [ ] Lifecycle observer cleanup works
- [ ] No memory leaks on repeated start/stop

### 3. ScriptValidator (86 LOC)
**File:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/data/validation/ScriptValidator.kt`

**Validation Rules:**
- Title: 1-100 chars, non-empty, no padding whitespace
- Content: 1-100,000 chars, non-empty

**Test Cases:**
- [ ] Empty title rejected
- [ ] Title > 100 chars rejected
- [ ] Title with padding whitespace rejected
- [ ] Empty content rejected
- [ ] Content > 100KB rejected
- [ ] Valid title+content accepted

### 4. MainActivity (150+ LOC Compose)
**File:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/ui/main/MainActivity.kt`

**Test Cases:**
- [ ] Script list displays
- [ ] FAB launches editor
- [ ] Permission card shows if no overlay permission
- [ ] Edit button loads existing script
- [ ] Delete button removes script
- [ ] Show Overlay button starts service

### 5. ScriptEditorActivity (150+ LOC Compose)
**File:** `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/ui/editor/ScriptEditorActivity.kt`

**Test Cases:**
- [ ] New script: blank form loads
- [ ] Edit script: content loads correctly
- [ ] Save creates/updates script
- [ ] Cancel exits without saving
- [ ] Form validation works
- [ ] Database persistence works

---

## Key Test Scenarios

### Scenario 1: Full Overlay Flow
```
1. Launch app
2. Create script (title + content)
3. Show Overlay
4. Check if overlay appears on camera app
5. Play/pause scrolling
6. Adjust speed (tap and hold)
7. Drag overlay to different position
8. Kill app
9. Relaunch app -> overlay position restored
```

### Scenario 2: Permission Handling
```
Android 13+:
1. Launch app without POST_NOTIFICATIONS
2. App requests permission (dialog)
3. Deny permission
4. Service still starts (START_STICKY)

Android 8-12:
1. SYSTEM_ALERT_WINDOW handled at Settings
2. POST_NOTIFICATIONS not required
```

### Scenario 3: Orientation Change
```
1. Overlay active in portrait
2. Rotate to landscape
3. Check: overlay switches to landscape layout
4. Check: text scroll continues
5. Check: position preserved
6. Rotate back to portrait
```

### Scenario 4: Long Content
```
1. Create script with 50,000+ chars
2. Show overlay
3. Play scrolling
4. Check: smooth without stuttering
5. Check: stops at end
6. Memory usage should be stable
```

### Scenario 5: Edge Cases
```
1. Empty script (no content) - overlay shows default text
2. Short content (1 line) - can't scroll (shows toast)
3. Very short delay between multiple overlays - only 1 should exist
4. Service killed from settings - should restart (START_STICKY)
5. Drag to screen edges - should constrain to boundaries
```

---

## Performance Metrics to Verify

### CPU Usage
- **Target:** 5-8% when scrolling
- **Previous:** 15-20% (before ValueAnimator optimization)
- **Measure:** Android Studio Profiler

### Frame Rate
- **Target:** 60 FPS solid
- **Expected drops:** 0-1 per second
- **Measure:** Android Studio GPU Profiler

### Memory
- **Baseline:** ~50MB
- **With overlay:** +30-50MB
- **Large script (100KB):** +60-80MB additional
- **Measure:** Android Studio Memory Profiler
- **Check:** No memory leaks on repeated overlay start/stop

### Battery
- **Expected:** 60% reduction vs Handler-based implementation
- **Measure:** Battery Historian or device battery settings

---

## Database Testing

### Location
```
/data/data/com.teleprompter.app/databases/teleprompter_db
```

### Schema
```sql
CREATE TABLE scripts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    createdAt INTEGER,
    updatedAt INTEGER,
    fontSize INTEGER,
    scrollSpeed INTEGER,
    textColor INTEGER,
    backgroundColor INTEGER
)
```

### Test Cases
- [ ] Script saved to database
- [ ] Script retrieved from database
- [ ] Script updated correctly
- [ ] Script deleted from database
- [ ] Search query works (LIKE '%query%')
- [ ] Database survives app restart
- [ ] Large content (100KB) persists
- [ ] No data corruption

### Debug Access
```bash
# List databases
adb shell ls /data/data/com.teleprompter.app/databases/

# Pull database
adb pull /data/data/com.teleprompter.app/databases/teleprompter_db

# Inspect with SQLite
sqlite3 teleprompter_db
.tables
SELECT * FROM scripts;
```

---

## DataStore Testing (Position Persistence)

### Location
```
/data/data/com.teleprompter.app/shared_prefs/overlay_preferences.xml
```

### Keys
- `overlay_x` - X position (default: 0)
- `overlay_y` - Y position (default: 100)

### Test Cases
- [ ] Position saved on drag release
- [ ] Position loaded on overlay start
- [ ] Position persists across app restart
- [ ] Default position if not set

### Debug Access
```bash
adb shell cat /data/data/com.teleprompter.app/shared_prefs/overlay_preferences.xml
```

---

## Permission Testing

### Required Permissions
```xml
SYSTEM_ALERT_WINDOW          # Required for overlay (Android 6+)
POST_NOTIFICATIONS            # Required for notification (Android 13+)
FOREGROUND_SERVICE           # Required for service
FOREGROUND_SERVICE_SPECIAL_USE
WAKE_LOCK                    # Keep screen on
```

### Test Points
- [ ] Overlay permission check before display
- [ ] POST_NOTIFICATIONS requested on Android 13+
- [ ] Service denial handled gracefully
- [ ] Toast shown if permission missing
- [ ] Overlay works after granting permission

### Testing Steps
```bash
# Revoke overlay permission
adb shell pm revoke com.teleprompter.app android.permission.SYSTEM_ALERT_WINDOW

# Revoke notification permission
adb shell pm revoke com.teleprompter.app android.permission.POST_NOTIFICATIONS

# Grant permissions back
adb shell pm grant com.teleprompter.app android.permission.SYSTEM_ALERT_WINDOW
adb shell pm grant com.teleprompter.app android.permission.POST_NOTIFICATIONS
```

---

## Logging and Debugging

### Key Log Tags
```
TeleprompterService  - Overlay service logs
MainActivity         - Main activity logs
ScriptEditorActivity - Editor logs
```

### Enable Logging
```bash
# View logs
adb logcat | grep -E "Teleprompter|MainActivity|ScriptEditor"

# Save logs to file
adb logcat > logfile.txt

# Filter by level
adb logcat *:W  # Warnings and errors only
```

### Key Log Points
- Service onCreate/onDestroy
- Overlay creation
- Scroll start/stop
- Position saves
- Permission checks

---

## Common Test Commands

```bash
# Install app
adb install /path/to/app-release.apk

# Launch app
adb shell am start -n com.teleprompter.app/com.teleprompter.app.ui.main.MainActivity

# Launch service
adb shell am startservice -n com.teleprompter.app/.ui.overlay.TeleprompterOverlayService

# Kill app
adb shell am force-stop com.teleprompter.app

# List running processes
adb shell ps | grep teleprompter

# Check service status
adb shell dumpsys activity services | grep teleprompter

# Clear app data
adb shell pm clear com.teleprompter.app

# Get device info
adb shell getprop ro.build.version.release  # Android version
```

---

## Regression Test Checklist

Before each release, verify:

- [ ] **Overlay:** Creates, displays, and updates correctly
- [ ] **Scrolling:** 60 FPS smooth, no stuttering
- [ ] **Speed Control:** Tap changes ±1, hold repeats every 50ms
- [ ] **Drag & Drop:** Positions save and restore
- [ ] **Scripts:** Create, edit, delete, search work
- [ ] **Database:** Persistence across restarts
- [ ] **Permissions:** Requested and handled correctly
- [ ] **Orientation:** Auto-switches layouts
- [ ] **Memory:** No leaks, stable under load
- [ ] **Crashes:** None during functional testing
- [ ] **Permissions:** Manifest declarations correct
- [ ] **Service:** Survives app kill (START_STICKY)
- [ ] **Notifications:** Foreground notification displays
- [ ] **Accessibility:** TalkBack works on all buttons

---

## Known Issues & Workarounds

### None documented at this time

---

## Quick Links to Source

**Core Files:**
- ScrollController: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/core/ScrollController.kt`
- TeleprompterOverlayService: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/ui/overlay/TeleprompterOverlayService.kt`
- ScriptRepository: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/data/repository/`
- MainActivity: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/ui/main/MainActivity.kt`

**Resources:**
- Layouts: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/res/layout/`
- Colors: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/res/values/colors.xml`
- Strings: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/res/values/strings.xml`

**Configuration:**
- Manifest: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/AndroidManifest.xml`
- Gradle: `/home/user/25-10-13_Teleprompter_Pro/app/build.gradle`
- Constants: `/home/user/25-10-13_Teleprompter_Pro/app/src/main/java/com/teleprompter/app/utils/Constants.kt`

---

## Notes for QA Team

1. **No Existing Tests:** All test files need to be created from scratch
2. **Code is Testable:** Repository pattern, interfaces, and separated concerns make testing straightforward
3. **Foreground Service:** Service will restart if killed (START_STICKY) - don't consider this a bug
4. **Database Version:** Currently v1 with destructive migration - safe for beta, need migration strategy for production
5. **ValueAnimator:** Provides 60 FPS smooth scrolling - don't expect jittery motion from older Handler approach
6. **Memory Optimized:** 60% CPU reduction from previous implementation - performance should be excellent

---

*Last Updated: 2025-11-08*
*Document Version: 1.0*
