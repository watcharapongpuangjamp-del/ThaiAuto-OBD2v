# Implementation Plan: Live Sensor Dashboard, Push Notifications, Advanced DTC Guides, & Vehicle Intelligence

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Choices from User Clarification**:
> 1. **Live Sensor Dashboard Style**: Modern cards featuring animated circular gauges for primary telemetry (RPM, Speed, Coolant Temp, Rail Pressure) and real-time sparkline trend graphs.
> 2. **Advanced DTC Analysis**: Interactive step-by-step troubleshooting guide with multimeter pinout checks, electrical specifications (voltage/resistance), and component testing procedures.
> 3. **Notification System**: 3 distinct Android Notification Channels (`channel_critical_alerts`, `channel_service_reminders`, `channel_diagnostic_reports`) covering critical thresholds, scheduled maintenance intervals, and report generation triggers.
> 4. **Cloud Repair History Backend**: Full Firebase Firestore integration specification with Google Sign-In via Jetpack `CredentialManager`, SHA-256 tamper-evident integrity hashes, and zero-trust security rules.
> 5. **VIN Scanner**: On-device CameraX scanner with live targeting reticle, flashlight toggle, and photo picker upload fallback using Google Play Services ML Kit Text Recognition.

---

## 1. Overview & Core Concept

This release transforms the Car Diagnostic Application into a comprehensive, professional automotive platform engineered for both vehicle owners and mechanics across Thailand:
- **Intuitive Mobile Dashboard**: A high-contrast, modern cockpit dashboard displaying live vehicle metrics with smooth circular needle/arc animations, real-time Canvas sparklines, urgent sensor alert banners, and upcoming service countdowns.
- **Push Notification Engine**: Android system notification dispatcher alerting drivers to dangerous engine conditions (overheating, low battery voltage, high common-rail fuel pressure), scheduled maintenance milestones (oil, timing belt, brake pads), and completed scan export reports.
- **Advanced DTC Diagnostic Guide**: Beyond standard error codes, provides interactive step-by-step troubleshooting workflows for OBD-II DTCs—including component wiring pinouts, normal multimeter voltage/resistance specs, physical inspection steps, and post-repair verification.
- **Pre-2000 Thai Vehicle Intelligence**: Rich database expansion for legendary 1990s–2000s Thai market vehicles (Hilux Mighty-X, Tiger D4D, Corolla AE101 สามห่วง, Soluna AL50, Isuzu TFR มังกรทอง, Civic EG เตารีด, Nissan Big-M, Mitsubishi Strada) including OBD-I adapter bridging pinouts and custom model registration.
- **On-Device VIN Scanner & Cloud Repair History**: ISO 3779 VIN decoder with camera and photo upload, alongside full technical specifications for secure cloud service history logging.

---

## 2. User Experience & Visual Design

### Aesthetic Direction & Theming
- **Vibe**: High-tech automotive telemetry cockpit, modern precision instrumentation, sleek dark theme with vivid M3 primary accents (Electric Cobalt `#2563EB`, Cyber Cyan `#06B6D4`, Emerald Safety `#10B981`, Amber Caution `#F59E0B`, Crimson Critical `#EF4444`).
- **Typography**: Clean, high-legibility tabular figures for speedometer, tachometer, temperature gauges, and multimeter ohm/volt measurements.
- **Layout & Density**:
  - **Dashboard**: Prominent primary gauge cluster (RPM & Speed) with smooth sweep animations, secondary telemetry cards with real-time Canvas sparkline charts, critical alert banners, and service reminder chips.
  - **DTC Diagnostic View**: Expandable multi-step checklist cards with test badge indicators (Multimeter, Smoke Test, Visual, Actuator Command), component pin diagrams, and "Mark Step Verified" toggles.
  - **VIN Scanner**: Dark transparent overlay with neon alignment reticle, real-time OCR text bounding boxes, torch toggle, and gallery photo picker button.

### Key User Flows

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            App Main Navigation                              │
├───────────────────┬───────────────────┬───────────────────┬─────────────────┤
│    [Dashboard]    │    [Diagnostics]  │   [Maintenance]   │   [VIN Scan]    │
└─────────┬─────────┴─────────┬─────────┴─────────┬─────────┴────────┬────────┘
          │                   │                   │                  │
          ▼                   ▼                   ▼                  ▼
  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   ┌──────────────┐
  │ Live Gauges  │    │ Active DTCs  │    │ Service Log  │   │ Camera View  │
  │  & Trends    │    │ & Live Scan  │    │ & Reminders  │   │ or Gallery   │
  └───────┬──────┘    └───────┬──────┘    └───────┬──────┘   └───────┬──────┘
          │                   │                   │                  │
          ▼                   ▼                   ▼                  ▼
  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐   ┌──────────────┐
  │ Sensor Alert │    │ Step-by-Step │    │ Cloud Sync   │   │ ISO 3779     │
  │ Notification │    │ Multimeter   │    │ (Firestore   │   │ Decoder &    │
  │ Dispatcher   │    │ Guide Modal  │    │  Specs Spec) │   │ Auto-Match   │
  └──────────────┘    └──────────────┘    └──────────────┘   └──────────────┘
```

---

## 3. Detailed Component Architecture

### A. Live Sensor Dashboard (`LiveDashboardScreen.kt`)
1. **Circular Animated Gauges (`GaugeCluster.kt` enhanced)**:
   - **Tachometer (RPM)**: 0 to 8000 RPM dial with green, amber, and redline zone arcs. Animated needle and center numerical readout.
   - **Speedometer (km/h)**: 0 to 240 km/h with fluid sweeping acceleration physics.
   - **Coolant Temperature (°C)**: Dial with warning threshold at 100°C and critical threshold at 108°C.
   - **Common-Rail / Fuel Pressure (bar / kPa)**: Real-time reading with baseline tolerance bands.
2. **Real-Time Sparkline History Cards**:
   - 30-second rolling window Canvas sparkline graphing values for Intake Air Temperature, Battery Voltage, Mass Air Flow (MAF), and Throttle Position (TPS).
   - High/Low peak badges and normal range indicators.
3. **Critical Alert Banner**:
   - Conditionally displayed when any sensor crosses dangerous thresholds (e.g., Coolant > 105°C, Voltage < 11.8V, Fuel Rail Pressure spike).
   - One-tap "Diagnose Now" action navigating directly to diagnostic procedures.
4. **Upcoming Service Countdown Card**:
   - Days and mileage remaining until next engine oil change, fuel filter replacement, and brake inspection with quick action to add new service record.

### B. Push Notification System (`DiagnosticNotificationHelper.kt`)
1. **Channel Setup**:
   - `channel_critical_alerts` (Importance HIGH, sound & vibration): Fires when live sensor telemetry breaches dangerous thresholds or critical trouble codes are detected.
   - `channel_service_reminders` (Importance DEFAULT): Reminds user of upcoming scheduled maintenance (oil, belts, filters) based on accumulated mileage.
   - `channel_diagnostic_reports` (Importance DEFAULT): Dispatches when comprehensive PDF/CSV diagnostic scan reports are generated and ready to share.
2. **Notification Dispatch Logic & Testing**:
   - Permissions check (`POST_NOTIFICATIONS` on API 33+).
   - In-app notification trigger buttons allowing immediate simulation/verification of alert delivery and deep links back to the relevant screen.

### C. Advanced DTC Analysis & Step-by-Step Multimeter Guides (`DtcDiagnosticGuide.kt`)
1. **Interactive Step-by-Step Diagnostic Procedures**:
   - For major OBD-II codes (e.g. `P0300` Random Misfire, `P0171` System Too Lean, `P0420` Catalyst Efficiency, `P0087` Low Fuel Rail Pressure, `P0101` MAF Sensor Range, `P0505` Idle Air Control, `P0340` Camshaft Position Sensor, `P0700` Transmission Control):
     - **Symptom Overview**: Misfiring, black smoke, rough idle, stalling under load.
     - **Probable Root Causes**: Ranked by frequency in Thai driving conditions (heat, humidity, B20 diesel sediment, aftermarket modifications).
     - **Required Tools**: Digital Multimeter (DMM), backprobe pins, vacuum gauge, infrared thermometer.
     - **Step 1: Visual & Mechanical Inspection**: Check intake boots, vacuum hoses, wiring harness chaffing, grounded grounds.
     - **Step 2: Live PID Telemetry Check**: Normal expected sensor range vs actual vehicle reading.
     - **Step 3: Electrical Multimeter Testing**:
       - Exact probe points (Pin 1 12V B+, Pin 2 Ground, Pin 3 Signal).
       - Resistance test (Ω at 20°C and 80°C).
       - Voltage test (Key ON Engine OFF vs Idling).
     - **Step 4: Actuator / Active Test Procedure**: Testing injector clicks, solenoid activation.
     - **Step 5: Clearing & Drive Cycle Verification**: Specific drive cycle criteria to set OBD readiness monitors to "Ready".
2. **Interactive Checklist UI**:
   - Mechanics can tap checkboxes to track completed test steps with progress indicator (`3 / 5 Steps Verified`).

### D. Expanded Thai Vehicle Database (Pre-2000 & Custom Models)
1. **Pre-2000 Thai Automotive Icons in `ThaiCarDatabase.kt`**:
   - **Toyota Hilux Mighty-X / Super LUX (1989–1997)**: `2L-II` 2.4L Diesel (89 hp), mechanical pump, Toyota 17-pin / 22-pin DLC OBD-I under hood.
   - **Toyota Hilux Tiger / Tiger D4D (1998–2004)**: `5L` 3.0L NA, `1KZ-TE` 3.0L Turbo Diesel, `2KD-FTV` 2.5L D4D Common-Rail.
   - **Toyota Corolla AE100 / AE101 "สามห่วง" (1992–1996)**: `4A-FE` 1.6L 16V DOHC (115 hp), TE1-E1 jumper diagnostic.
   - **Toyota Soluna AL50 "หยดน้ำ / มุมส้ม" (1997–2002)**: `5A-FE` 1.5L Twin Cam EFI.
   - **Isuzu TFR "มังกรทอง" / Dragon Eyes (1990–2002)**: `4JA1` 2.5L Direct Injection, `4JH1-T` 3.0L Turbo Direct Injection, Isuzu 3-pin DLC.
   - **Honda Civic EG "เตารีด" (1992–1995)**: `D15B` 1.5L SOHC / VTEC, Honda 3-pin / 5-pin diagnostic connector.
   - **Nissan Big-M / King Cab (1986–1997)**: `BD25` 2.5L Diesel, Nissan 14-pin Consult diagnostic port.
   - **Mitsubishi L200 Strada (1996–2005)**: `4D56` 2.5L Turbo Diesel, dual 12-pin / OBD-II port.
2. **OBD-I Connector Adapter Reference**:
   - Detailed pinout conversion table for bridging Toyota 17/22-pin, Honda 3/5-pin, Nissan 14-pin, and Isuzu 3-pin to modern 16-pin ELM327 pins (Pin 4/5 Ground, Pin 16 12V B+, Pin 7 K-Line).
3. **Custom Model Registration UI**:
   - Modal allowing mechanics to add bespoke or swapped vehicles (e.g. 1JZ-GTE / 2JZ, SR20DET, K20A) with custom alert thresholds.

### E. Secure Cloud Repair History Database Specification
1. **Cryptographic Standards**:
   - AES-256 Cloud KMS at rest; TLS 1.3 in transit.
   - SHA-256 tamper-proof chain hash on each maintenance log (`hash = SHA256(vehicleId + prevHash + timestamp + mileage + partsCost)`).
2. **Authentication & Authorization**:
   - Google Sign-In via Jetpack Credential Manager.
   - Zero-trust Firestore security rules segregating owner and workshop permissions.
3. **In-App Integration & Interactive Specs Drawer**:
   - Service record management with cryptographic verification badges and one-touch export.

### F. On-Device VIN Scanner Integration
1. **CameraX & ML Kit Integration**:
   - Live camera preview with OCR bounding boxes filtered for 17-character ISO 3779 VIN pattern (`[A-HJ-NPR-Z0-9]{17}`).
   - Photo picker support for selecting photos from device gallery.
   - Flashlight (torch) toggle for dark engine bay inspections.
2. **ISO 3779 VIN Decoder**:
   - Decodes World Manufacturer Identifier (WMI - e.g. `MR0` for Toyota Thailand, `MP1` for Isuzu Thailand, `MHR` for Honda Thailand), Vehicle Descriptor Section (VDS), and Model Year (10th character mapping from 1980 to 2030).
   - Auto-fills vehicle registration forms and matches with `ThaiCarDatabase`.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Presentation Layer                               │
│  [LiveDashboardScreen]   [DiagnosticScannerScreen]   [MaintenanceScreen]    │
│  - Animated Gauge Arc    - DtcDiagnosticGuide Modal  - Service Reminders    │
│  - Canvas Sparklines     - Multimeter Test Steps     - History Log & Crypto │
│  - Alert Banners         - Live Sensor Checkpoints   - Cloud Spec Drawer    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        MainViewModel & State Flow                           │
│  - uiState: DashboardUiState, DiagnosticUiState, MaintenanceUiState         │
│  - Telemetry Engine (interpolated live PIDs, threshold monitors)            │
│  - Notification Dispatcher (checks thresholds -> triggers system channels)  │
│  - VIN Scanner Controller (CameraX Analyzer + ML Kit Text Recognizer)       │
└───────────────────┬───────────────────────────────────┬─────────────────────┘
                    │                                   │
                    ▼                                   ▼
┌──────────────────────────────────────┐  ┌───────────────────────────────────┐
│           Repository Layer           │  │          Device Services          │
│  - ThaiCarDatabase (Pre-2000 & OBD1) │  │  - DiagnosticNotificationHelper   │
│  - DtcDatabase & DiagnosticGuideRepo │  │    (Channels & System Alerts)     │
│  - Room Database (Offline Cache)     │  │  - CameraX & ML Kit OCR Provider  │
│  - Firestore Cloud Sync (Specs/Model)│  │  - Haptic Feedback & Vibrator     │
└──────────────────────────────────────┘  └───────────────────────────────────┘
```

---

## 5. Verification & Testing Plan

1. **Compilation Verification**:
   - Execute `compile_applet` to verify clean build across all new components, CameraX, and ML Kit dependencies.
2. **Live Sensor Dashboard Verification**:
   - Test smooth gauge sweep animations and dynamic sparkline chart point rendering under varying telemetry values.
   - Verify threshold triggering of critical alert banners when coolant or voltage thresholds are breached.
3. **Notification System Verification**:
   - Verify creation of all 3 notification channels on Android system.
   - Test in-app dispatch of critical sensor warning, service interval reminder, and report completion alerts.
4. **DTC Diagnostic Guide Verification**:
   - Test interactive step-by-step troubleshooting workflow for multiple DTCs (e.g. `P0300`, `P0171`, `P0087`).
   - Verify multimeter pinout values, normal range displays, and interactive checklist state transitions.
5. **Thai Vehicle Database & VIN Scanner**:
   - Test ISO 3779 decoding with Thai WMI prefixes (`MR0`, `MP1`, `MHR`) and pre-2000 model year letters (`N`=1992, `S`=1995, `W`=1998, `Y`=2000).
   - Test camera preview and image picker text recognition flow.
