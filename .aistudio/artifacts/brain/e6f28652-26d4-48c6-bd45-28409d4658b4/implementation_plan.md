# Implementation Plan: Expanded Thai Vehicle Database, Secure Cloud Repair History, & On-Device VIN Scanner

## User Review Required

> [!IMPORTANT]
> **Firebase Cloud Backend Setup**: The user chose **Full Firebase Firestore sync with Google Sign-In**. In the subsequent step, Firebase integration will require running `set_up_firebase` with `userConfirmedTermsAcceptedInUI: false` to allow project provisioning.
> 
> **Camera Permission**: The on-device camera VIN scanner requires declaring `android.permission.CAMERA` in `AndroidManifest.xml` and requesting runtime permission via Compose `rememberLauncherForActivityResult`. A Photo Picker fallback (`PickVisualMedia`) will be available without runtime permissions.
>
> **ML Kit Dependency**: For OCR extraction of 17-character VINs from camera frames and images, we will include Google Play Services ML Kit Text Recognition (`com.google.android.gms:play-services-mlkit-text-recognition`), ensuring zero network latency and privacy-friendly on-device processing.

---

## 1. Expanded Thai Vehicle Database (Pre-2000 & Custom Models)

### Pre-2000 & 1990s Iconic Thai Market Vehicles
We will expand `ThaiCarDatabase.kt` to include classic, popular trucks, sedans, and vans that dominated Thai roads from the late 1980s through early 2000s:

1. **Toyota**:
   - **Hilux Mighty-X / Super LUX (1989–1997)**: Engine `2L-II` (2,446 cc SOHC Diesel, 89 hp / 167 Nm), Pre-OBD Toyota 17-pin / 22-pin DLC under hood, mechanical injection pump parameters.
   - **Hilux Tiger / Tiger D4D (1998–2004)**: Engines `5L` (3.0L NA Diesel), `1KZ-TE` (3.0L Turbo Diesel), and the first Thai common-rail `2KD-FTV` (2.5L D4D).
   - **Corolla AE100 / AE101 "สามห่วง" (1992–1996)**: Engine `4A-FE` (1.6L 16V DOHC, 115 hp) & `2E` (1.3L 12V), Toyota 17-pin OBD1 protocol diagnostics.
   - **Soluna AL50 "หยดน้ำ / มุมส้ม" (1997–2002)**: Engine `5A-FE` (1.5L Twin Cam EFI, 95 hp).
2. **Isuzu**:
   - **TFR "มังกรทอง" / Dragon Eyes / Dragon Power (1990–2002)**: Legendary `4JA1` (2.5L Direct Injection, 90 hp), `4JB1` (2.8L), and `4JH1-T` (3.0L Turbo Direct Injection). Isuzu 3-pin DLC diagnostic adapter requirements.
   - **D-Max 1st Gen (2002–2006)**: `4JA1-T`, `4JH1-T`, transition to OBD-II 16-pin connector.
3. **Honda**:
   - **Civic EG "เตารีด / 3-Door & 4-Door" (1992–1995)**: Engines `D15B` (1.5L SOHC / VTEC), `B16A` (1.6L DOHC VTEC). Honda 3-pin / 5-pin diagnostic connector under passenger kick panel.
   - **Civic EK "ตาโต" (1996–2000)**: `D16Y` (1.6L VTEC), early OBD-II compliance.
   - **City Type Z (1999–2002)**: `D15B` & `D13B` VTEC / Hyper 16V.
4. **Nissan**:
   - **Big-M / King Cab (1986–1997)**: Engines `BD25` (2.5L Diesel) & `TD27` (2.7L Diesel), Nissan 14-pin Consult diagnostic port.
   - **Frontier D22 (1998–2007)**: `YD25DDTi` & `ZD30DDT` Turbo Diesel.
   - **Cefiro A31 / A32 (1990–2001)**: `RB20DET` / `VQ20DE` / `VQ30DE`.
5. **Mitsubishi**:
   - **L200 Strada (1996–2005)**: `4D56` (2.5L Turbo Diesel) & `4M40` (2.8L Diesel). Mitsubishi 12-pin / OBD-II dual DLC.
   - **Lancer Champ II / E-Car (1989–1996)**: `4G15` (1.5L) & `4G63` (2.0L DOHC).

### How to Add Specific, Rare, or Custom Models
- **Custom Vehicle Registry Dialog & Repository**: An interactive UI for mechanics and enthusiasts to register custom vehicles, engine swaps (e.g. 1JZ/2JZ-GTE, SR20DET, K20A, UZ-FE), custom boost tolerances, and specialized OBD-I to OBD-II adapter pinouts.
- **Diagnostic Adapter Pinout Reference Guide**: Embedded technical wiring diagrams and guidance on how OBD-I 3-pin/12-pin/14-pin/17-pin connectors interface with standard 16-pin OBD-II ELM327 scanners via bridge converters.

---

## 2. Technical Specifications: Secure Cloud-Based Repair History Database

### Architecture Overview
A multi-tier cloud persistence architecture connecting vehicle owners, fleet managers, and independent repair shops with zero-trust security and end-to-end auditability.

```
+-------------------------------------------------------------------------------+
|                       Android App (Jetpack Compose)                           |
|  [Credential Manager]   [Maintenance ViewModel]   [Local Room / Cache]        |
+------------------------------------+------------------------------------------+
                                     | (TLS 1.3 + Firebase Auth JWT)
                                     v
+-------------------------------------------------------------------------------+
|                           Firebase Cloud Infrastructure                       |
|                                                                               |
|  +--------------------+   +-----------------------+   +--------------------+  |
|  |   Firebase Auth    |   |  Cloud Firestore      |   |  Firebase Storage  |  |
|  | (Google Identity)  |   | (Encrypted Database)  |   | (Invoices & Scans) |  |
|  +--------------------+   +-----------------------+   +--------------------+  |
+-------------------------------------------------------------------------------+
```

### Detailed Specifications

#### 1. User Authentication & Authorization
- **Authentication Provider**: Firebase Authentication backed by Jetpack `CredentialManager` with `GetSignInWithGoogleOption` (`com.google.android.libraries.identity.googleid:googleid`).
- **Identity Lifecycle**:
  - OAuth 2.0 PKCE exchange returning standard Firebase ID tokens (JWT).
  - Strict token expiration with automatic refresh rotation.
  - Role-based attributes (`role: "owner" | "mechanic" | "fleet_admin"`).

#### 2. Data Encryption Standards
- **In-Transit**: TLS 1.3 enforced across all API endpoints with HTTP/2 and gRPC channel encryption.
- **At-Rest**: Default AES-256 cloud encryption backed by Google Cloud KMS.
- **Field-Level Sensitive Hashing**: VINs and vehicle registration plates indexed with SHA-256 hashes for fast lookup while protecting privacy against unauthorized scraping.
- **Data Integrity**: Each repair entry includes a cryptographic SHA-256 checksum composed of `(vehicleId + timestamp + mileage + partsList + totalCost)` to prevent tampering with historical service records.

#### 3. Firestore Schema Design
- `/users/{userId}`: User profile, workshop affiliation, registered vehicle IDs.
- `/vehicles/{vehicleId}`: Vehicle metadata (VIN, make, model, year, engineCode, currentMileage, licensePlate).
- `/vehicles/{vehicleId}/repair_logs/{logId}`:
  ```json
  {
    "logId": "uuid-v4",
    "vehicleId": "vehicle_xyz",
    "vin": "MR0FZ...",
    "recordedByUserId": "auth_uid_123",
    "workshopName": "อู่ช่างวิทย์ เซอร์วิส สุขุมวิท",
    "serviceDate": "2026-10-26T14:30:00Z",
    "mileageKm": 185420,
    "serviceCategory": "ROUTINE_MAINTENANCE",
    "title": "เปลี่ยนถ่ายน้ำมันเครื่องสังเคราะห์แท้และกรองโซล่า",
    "details": "เปลี่ยนถ่ายน้ำมันเครื่อง 5W-30 API CK-4 ปริมาณ 7.5L, เปลี่ยนกรองโซล่าลูกแท้, ล้างวาล์ว SCV",
    "replacedParts": [
      {"name": "Toyota Genuine Oil 5W-30 (8L)", "partNumber": "08880-83389", "costThb": 1850.0},
      {"name": "Fuel Filter Element", "partNumber": "23390-0L070", "costThb": 420.0}
    ],
    "laborCostThb": 500.0,
    "totalCostThb": 2770.0,
    "dtcSnapshot": ["P0087"],
    "attachedPhotoUrls": ["https://firebasestorage.googleapis.com/..."],
    "nextServiceMileageKm": 195000,
    "nextServiceDate": "2027-04-26",
    "hashChecksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  }
  ```

#### 4. Scalable Storage & Offline-First Sync
- **Local Persistence**: Room SQLite database (`maintenance_logs` table) caches records offline and syncs bidirectionally upon network reconnection.
- **Firebase Storage**: Stores high-resolution photos of invoices, receipts, and pre/post OBD-II diagnostic freeze frame reports.

#### 5. Firestore Zero-Trust Security Rules
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    match /vehicles/{vehicleId} {
      allow read: if request.auth != null && (
        resource.data.ownerId == request.auth.uid ||
        request.auth.uid in resource.data.sharedWith
      );
      allow create: if request.auth != null && request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if request.auth != null && resource.data.ownerId == request.auth.uid;
      
      match /repair_logs/{logId} {
        allow read: if request.auth != null && (
          get(/databases/$(database)/documents/vehicles/$(vehicleId)).data.ownerId == request.auth.uid ||
          request.auth.uid in get(/databases/$(database)/documents/vehicles/$(vehicleId)).data.sharedWith
        );
        allow create, update: if request.auth != null && (
          get(/databases/$(database)/documents/vehicles/$(vehicleId)).data.ownerId == request.auth.uid ||
          request.auth.uid in get(/databases/$(database)/documents/vehicles/$(vehicleId)).data.sharedWith
        );
        allow delete: if request.auth != null && get(/databases/$(database)/documents/vehicles/$(vehicleId)).data.ownerId == request.auth.uid;
      }
    }
  }
}
```

---

## 3. VIN Scanner Integration (CameraX + Photo Picker + Decoder)

### Architecture
1. **CameraX Live Scanner (`VinCameraScannerView.kt`)**:
   - Modern `PreviewView` with a high-contrast rectangular scanning target overlay.
   - Real-time frame analysis using ML Kit Text Recognition to detect 17-character VIN formats (`[A-HJ-NPR-Z0-9]{17}`).
   - Visual scan feedback with haptic confirmation upon successful lock-on.
2. **Photo Gallery Picker**:
   - Zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) for scanning photos of engine plates, registration books (เล่มทะเบียนรถ), or windscreen stickers.
3. **ISO 3779 VIN Decoder Engine (`VinDecoder.kt`)**:
   - **WMI (Characters 1–3)**: Identifies World Manufacturer & Country (e.g. `MR0` = Toyota Thailand, `MPA`/`MP1` = Isuzu Thailand, `MHR` = Honda Thailand, `MMT` = Mitsubishi Thailand, `AAV` = Thailand general, `1FA` = Ford USA, `JM0` = Mazda Japan).
   - **VDS (Characters 4–9)**: Decodes body type, engine model, and check digit.
   - **VIS (Characters 10–17)**:
     - 10th Character = Model Year (e.g. `L`=1990, `M`=1991, `N`=1992, `P`=1993, `R`=1994, `S`=1995, `T`=1996, `V`=1997, `W`=1998, `X`=1999, `Y`=2000, `1`=2001, `2`=2002 ... `K`=2019, `L`=2020 ... `T`=2026).
     - 11th Character = Assembly Plant (e.g. Gateway, Samrong, Ban Pho, Rayong).
     - 12th–17th Characters = Sequential Production Serial Number.
4. **Automatic Vehicle Matching & Auto-Population**:
   - Instant query against `ThaiCarDatabase` matching the decoded Make, Model, Generation, and Engine code.
   - Displays a vehicle card with engine displacement, oil capacity, tire pressures, baseline idle RPM, and OBD port location.
   - Option to automatically register the vehicle or link to current diagnostic session.

---

## Proposed Changes

### 1. Data Layer & Repository
- **`app/src/main/java/com/example/data/model/ThaiCarSpec.kt`**: Add fields for vintage OBD connector type (OBD-II 16-pin vs. OBD-I manufacturer specific), adapter diagram references, and pre-2000 generation codes.
- **`app/src/main/java/com/example/data/repository/ThaiCarDatabase.kt`**: Add 15+ iconic pre-2000 Thai market vehicles (Hilux Mighty-X, Hilux Tiger, Corolla AE101, Soluna AL50, Isuzu TFR มังกรทอง, Dragon Eyes, Honda Civic EG/EK, Nissan Big-M, Mitsubishi Strada, etc.).
- **`app/src/main/java/com/example/data/repository/VinDecoder.kt`**: Complete ISO 3779 VIN decoder with Thai WMI database and model year mapping.
- **`app/src/main/java/com/example/data/model/CloudRepairHistory.kt`**: Data models for repair logs, service records, and parts replacements.
- **`app/src/main/java/com/example/data/repository/CloudRepairRepository.kt`**: Interface and implementation for Firestore synchronization, cryptographic hashing, and local Room cache fallback.

### 2. Camera & Scanner Components
- **`app/src/main/java/com/example/ui/components/VinScannerModal.kt`**: Compose dialog featuring CameraX live scanner, scanning rect guide, flashlight toggle, gallery image selector, and manual VIN entry with instant validation.
- **`app/src/main/java/com/example/ui/components/CustomVehicleRegistrationDialog.kt`**: Dialog allowing users and mechanics to define and save custom or rare car models with tailored engine parameters.

### 3. UI Screens & ViewModels
- **`app/src/main/java/com/example/ui/MainViewModel.kt`**: Integrate VIN scanner state, decoded vehicle card, custom vehicle creation, and cloud repair log sync.
- **`app/src/main/java/com/example/ui/screens/MaintenanceScreen.kt`**: Enhance maintenance logging with cloud sync badges, repair invoice breakdown, next service calculator, and VIN auto-link.
- **`app/src/main/java/com/example/ui/screens/VehicleSpecsScreen.kt`**: Filter by year range (Pre-2000 classics, 2000-2015, 2015+ modern), show OBD-I adapter diagrams, and display VIN scanner button.

### 4. Build Configuration & Manifest
- **`app/build.gradle.kts`**: Enable CameraX dependencies (`camera2`, `lifecycle`, `view`, `core`) and ML Kit Text Recognition; enable Firestore and Credentials libraries.
- **`app/src/main/AndroidManifest.xml`**: Declare `<uses-permission android:name="android.permission.CAMERA" />` with `uses-feature android:name="android.hardware.camera" android:required="false"`.

---

## Verification Plan

### Automated & Unit Tests
1. **VIN Decoding Tests (`VinDecoderTest.kt`)**:
   - Verify decoding of Thai VINs (e.g., Toyota `MR0...`, Isuzu `MPA...`, Honda `MHR...`).
   - Validate model year calculation for 1990–2026.
   - Verify check digit validation and invalid character rejection (`I`, `O`, `Q`).
2. **Vehicle Database Coverage**:
   - Verify retrieval of newly added pre-2000 vehicles (Mighty-X, TFR, Civic EG, etc.).
   - Verify custom vehicle registration persistence.
3. **Compilation**:
   - Run `compile_applet` to ensure all Kotlin, Compose, CameraX, and ML Kit dependencies compile cleanly with zero errors.

### Manual Verification Flow
1. **VIN Scanner**:
   - Open Vehicle Specs or Diagnostic Screen -> Click "Scan VIN" button.
   - Test Camera preview with scanning target.
   - Test Gallery image upload with sample VIN images.
   - Verify auto-populated Make, Model, Year, and Engine Specs.
2. **Pre-2000 Vehicles & Adapter Guide**:
   - Filter vehicles by "Classic / Pre-2000" in the specs browser.
   - Verify Mighty-X, TFR มังกรทอง, and Civic EG display correct oil capacity, tire pressures, and OBD-I pinout instructions.
3. **Cloud Repair History**:
   - Add new repair record with parts, costs, and mileage.
   - Verify checksum generation and display of formatted service history.
