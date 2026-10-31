# AutoFlow - Android Task Automation & Scheduling Engine

AutoFlow is a modular Android task automation app designed to automate app launches, scheduled alarm/timer triggers, system setting toggles, and multi-step task flows. It features an interactive **Permission Center**, a flexible **Task Execution Engine**, a **Rule Builder**, a **Preset Library**, and device-local **Room Database** storage for complete privacy.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural decisions were confirmed in Phase 1:
> - **Preset Library**: Standard presets for **App Launch**, **Scheduled Alarms/Timers**, and **System Setting Toggles** (e.g., Brightness, Volume, DND, Bluetooth/Wi-Fi shortcuts).
> - **Permission Center**: Interactive guided checklist with single-tap status checks and direct system shortcuts for Accessibility, System Overlay (`SYSTEM_ALERT_WINDOW`), Exact Alarm (`SCHEDULE_EXACT_ALARM`), Usage Access (`PACKAGE_USAGE_STATS`), and Notification permissions.
> - **Data Persistence**: 100% device-local **Room Database** for high performance, zero latency, and absolute privacy.

---

## 1. Overview & Core Concept

- **What It Does**: Allows users to configure automation rules with flexible Triggers (Timer/Cron, App Launch/Foreground, System State) and Actions (Launch App, Set Alarm/Timer, Modify System Volume/Brightness/DND, Post Notifications).
- **Target Audience**: Power users, productivity enthusiasts, and anyone looking to automate repetitive smartphone routines.
- **Key Value**: Simple rule creation, modular architecture for adding future triggers/actions, robust permission onboarding, and local privacy.
- **APK Export Instructions**: To download the executable APK on Google AI Studio, click the **Export** or **Download APK** option in the top-right project settings/menu. AI Studio compiles the APK for direct installation on your Android device.

---

## 2. User Experience & Visual Design

### Key User Flow & Navigation Structure
1. **Interactive Permission Center Dashboard (Home / Onboarding)**:
   - Visual permission cards (Accessibility Service, Draw Over Apps, Exact Alarms, Usage Access, Notifications, Write Settings).
   - Real-time status indicators (Granted / Requires Setup) with "Grant Permission" action buttons directing straight to system settings pages.
2. **Task Rules Screen**:
   - List of active/inactive automation rules with quick enable/disable switches, trigger tags, action icons, and execution counters.
   - Filter rules by trigger type (App Launch, Scheduled Time, System Event).
3. **Rule Builder & Editor**:
   - Step 1: Select Trigger (e.g., App Opened -> pick target app; Time/Timer -> set schedule/countdown; System state).
   - Step 2: Configure Trigger Parameters.
   - Step 3: Select Action(s) (e.g., Launch another app, trigger alarm, adjust audio volume/brightness/DND).
   - Step 4: Rule Test & Save.
4. **Preset Library Screen**:
   - One-tap installable presets (e.g., "Morning Productivity Flow", "Bedtime Silent Mode", "Focus App Timer").
5. **Execution Log Screen**:
   - Real-time history log of triggered actions, timestamps, and execution status (Success/Skipped).

### Visual Identity & Theme (Material Design 3)
- **Primary Palette**: Vibrant Tech Cobalt (`#2563EB`), Emerald Green (`#059669`) for granted states, Amber (`#D97706`) for setup required, Dark Charcoal background (`#111827`) in dark mode and Clean Neutral (`#F9FAFB`) in light mode.
- **Typography**: Clean sans-serif hierarchy (Material 3 `Typography.titleMedium`, `labelLarge`, `bodyMedium`).
- **Interactive Feedback**: Tactile ripple effects, badge chips for rule status, animated permission progress cards.

---

## 3. Key Product Decisions & Trade-Offs

- **Modular Execution Engine Architecture**:
  - *Decision*: Decouple Triggers (`TriggerCondition`) and Actions (`ActionTask`) into extensible interfaces.
  - *Why*: Allows seamless expansion in future updates to add new triggers (location, battery, sensors) and actions without altering existing core logic.
- **Local Room Database**:
  - *Decision*: Store all rule entities, trigger conditions, action steps, and execution logs in Room SQLite.
  - *Why*: Privacy-first design, fast local reads/writes, zero reliance on external cloud servers.
- **Interactive Permission Center vs Force-Block Wizard**:
  - *Decision*: Interactive dashboard allowing partial rule creation while visually highlighting missing permissions required for specific actions.
  - *Why*: Superior user onboarding experience without frustrating hard blocks.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI Layer                          │
│  PermissionCenterScreen │ RulesListScreen │ RuleBuilderScreen │ Logs  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Observes UI State / Flows)
┌───────────────────────────────────▼────────────────────────────────────┐
│                             AutomationViewModel                        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Uses Repository)
┌───────────────────────────────────▼────────────────────────────────────┐
│                            AutomationRepository                        │
└─────────────────────────┬───────────────────────┬──────────────────────┘
                          │                       │
┌─────────────────────────▼─────────────┐   ┌─────▼──────────────────────┐
│        Room Database (Local Data)     │   │      Execution Engine      │
│  - AutomationRuleEntity               │   │ - Service / AlarmManager   │
│  - RuleLogEntity                      │   │ - Accessibility Engine     │
└───────────────────────────────────────┘   └────────────────────────────┘
```

### Key Entities
1. `AutomationRule`: Represents an automation rule (`id`, `title`, `isEnabled`, `triggerType`, `triggerConfigJson`, `actionType`, `actionConfigJson`, `lastExecutedMs`).
2. `ExecutionLog`: Record of rule execution (`id`, `ruleId`, `ruleTitle`, `timestampMs`, `status`, `detail`).
3. `PermissionStatus`: Represents permission item state (`permissionType`, `isGranted`, `systemIntentAction`).

---
