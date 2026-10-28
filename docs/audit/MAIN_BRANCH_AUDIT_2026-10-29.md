# รายงานตรวจสอบ Main Branch — ThaiAuto-OBD2v

- วันที่ตรวจสอบ: 2026-10-29
- Branch: `main`
- Commit ที่ตรวจสอบ: `d7935939f92c76bfd8436ba762edeaa6812f836a`
- ขอบเขต: static review จาก GitHub source, workflow metadata, OBD diagnostic paths, Room database, Cloud Sync และ PR ที่เกี่ยวข้อง
- สถานะการเปลี่ยนแปลง: รอบตรวจสอบนี้ไม่ได้แก้โค้ดบน `main`, ไม่ได้ merge PR และไม่ได้ทดสอบกับรถจริง

## 1. สรุปผู้บริหาร

Build Android APK ล่าสุดที่ตรวจพบสำเร็จ แต่ workflow run เดียวกันแสดงขั้น Unit Tests เป็น `SKIPPED`; จึงยังไม่มีหลักฐานว่า unit tests ผ่านใน commit นี้ การ build สำเร็จไม่ยืนยันความถูกต้องของ OBD parser, ECU command, Cloud Sync หรือการรักษาข้อมูล

ประเด็นที่ควรจัดการก่อน release:
1. CI ต้องรัน tests จริงและล้มเหลวเมื่อ tests fail
2. แยกผลสแกนที่ไม่พบ DTC ออกจาก timeout/communication error
3. การล้าง DTC ต้องส่ง Mode 04 ไป ECU จริงและตรวจ acknowledgement
4. Cloud write failure ต้องส่งสถานะล้มเหลวกลับ caller
5. ป้องกัน Room destructive migration และจัดทำ migration tests
6. ตรวจ release signing และรวม Firestore rules ให้มีแหล่งอ้างอิงเดียว

## 2. Snapshot จาก GitHub

| รายการ | สถานะที่ตรวจพบ |
|---|---|
| Main HEAD | `d7935939` |
| Workflow run | [Build Android APK #40461651001](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40461651001) |
| Build Android APK | Success |
| Run Unit Tests | Skipped |
| Branch protection | จาก metadata ที่ตรวจในรอบนี้ไม่พบ required checks ที่บังคับใช้; ควรตรวจยืนยันใน repository settings |
| PR #3 | Open / Draft — compact DTC parser |
| PR #4 | Open / Draft — scan failure classification |
| PR #5 | Open / Draft — Mode 04 acknowledgement |
| PR #6 | Open / Draft — OBD capability profile |

## 3. ข้อค้นพบ

### P0 — CI ไม่รับรอง Unit Tests

Workflow `.github/workflows/build-apk.yml` ใช้คำสั่งทดสอบที่มี `|| echo` ซึ่งสามารถกลบ exit code เมื่อการทดสอบล้มเหลวได้ และ workflow run ที่ตรวจพบแสดงขั้น Run Unit Tests เป็น Skipped

**ผลกระทบ:** APK อาจ build ผ่านแม้ tests ไม่ได้ทำงานหรือพบความผิดพลาด

**แนวทาง:** รัน `./gradlew testDebugUnitTest` โดยตรง, ไม่กลบ exit code, ทำให้ test failure ล้มเหลวทั้ง workflow และกำหนด required status checks สำหรับ PR/main

### P0 — Clear DTC ยังไม่ยืนยันการล้างที่ ECU จริง

ใน `MainViewModel.clearDtcFaultCodes()` มีการล้างรายการ active DTC ใน state และเรียก `emulatorService.clearDtcAndResetEcu()`; ไม่พบการส่ง Mode 04 ผ่าน real USB/Bluetooth transport ในเมธอดนี้

**ผลกระทบ:** ผู้ใช้อาจเข้าใจว่าล้างรหัสรถแล้ว ทั้งที่เปลี่ยนเพียงข้อมูลภายในแอป

**แนวทาง:** ปิด Clear DTC ใน real-hardware mode จนกว่าจะมีการส่งคำสั่งจริง, ตรวจ response/acknowledgement, จัดการ timeout/unsupported และมีการยืนยันจากผู้ใช้พร้อม safety interlock

### P0 — Cloud Sync กลืนข้อผิดพลาด

ใน `CloudSyncRepository.kt` เมธอดบันทึกหลายรายการจับ exception แล้วเรียก `handleFirestoreError()` แต่ไม่ throw ต่อหรือคืน typed failure ให้ caller

**ผลกระทบ:** UI หรือ flow ที่เรียกใช้อาจเข้าใจว่าการบันทึกสำเร็จ แม้ Firestore write ล้มเหลว

**แนวทาง:** คืน `Result` หรือ typed outcome, แยก cancellation ออกจาก failure, แสดง sync status ชัดเจน และคงข้อมูล local ไว้จนได้รับ server acknowledgement

### P1 — ผลสแกนอาจรายงาน NO_DTC ทั้งที่บางคำสั่งล้มเหลว

ใน `ObdDiagnosticEngine.executeDiagnosticScan()` ตรวจ link ผ่าน PID `0100` แต่หลังจากนั้น parse Mode 03/07/0A แล้วกำหนดสถานะสุดท้ายจากจำนวน DTC ที่ parse ได้ โดยไม่ได้รวมสถานะของทุกคำสั่งเข้ากับ final status

**ผลกระทบ:** timeout หรือ error ของคำสั่ง DTC อาจถูกตีความผิดว่าไม่มีรหัสข้อผิดพลาด

**แนวทาง:** สร้างผลลัพธ์แยก `SUCCESS`, `NO_DTC`, `PARTIAL_RESULT`, `TIMEOUT`, `UNSUPPORTED`, `COMMUNICATION_ERROR`; อนุญาต `NO_DTC` เมื่อคำสั่งที่จำเป็นสำเร็จและ response ถูก parse/ตรวจสอบได้เท่านั้น

### P1 — Parser ไม่รองรับ response แบบ compact อย่างครบถ้วน

Parser ใน `ObdDiagnosticEngine.kt` แยก token ตาม whitespace และคาดหวัง byte token ขนาดสองตัว จึงเสี่ยงกับ response ที่ adapter ส่งข้อมูลติดกันเมื่อปิดช่องว่าง

Bluetooth parser ใน `BluetoothObdService.kt` มีข้อจำกัดคล้ายกัน และเส้นทาง `fetchDtcCodes()` ที่ตรวจพบเรียก Mode 03 และ 07 โดยไม่รวม Mode 0A

**แนวทาง:** แยก parser เป็น pure functions, รองรับ spaced/compact response, echo, header, prompt, padding, malformed data และ unsupported response; เพิ่ม tests สำหรับ USB/Bluetooth

### P1 — Room destructive migration เสี่ยงลบข้อมูล

`AppDatabase.kt` ใช้ `fallbackToDestructiveMigration()` และ `exportSchema = false`

**ผลกระทบ:** เมื่อ schema version เปลี่ยนโดยไม่มี migration รองรับ Room อาจลบและสร้างฐานข้อมูลใหม่ ทำให้ข้อมูลรถ ประวัติซ่อม scan records, telemetry และ logs ในเครื่องสูญหาย

**แนวทาง:** เปิด schema export, สร้าง versioned migrations และทดสอบ migration จากทุกเวอร์ชันที่รองรับ

### P1 — Firestore rules มีสองตำแหน่ง

พบ `firestore.rules` ที่ root และ `app/firestore.rules`; `firebase.json` แต่ละตำแหน่งอ้างอิงคนละ path/configuration

**ผลกระทบ:** ผู้พัฒนาอาจแก้ rules ชุดหนึ่ง แต่ deploy อีกชุดหนึ่งโดยไม่ตั้งใจ

**แนวทาง:** กำหนด canonical rules file เพียงชุดเดียว, ลบ/เลิกใช้ config ที่ซ้ำอย่างมีแผน, ทดสอบ Firebase Emulator และตรวจว่า deployed rules ตรงกับ source

### P1 — Release signing และ workflow permissions

Gradle release signing กำหนด alias `upload`; workflow มี fallback ไปใช้ debug keystore ซึ่งโดยทั่วไปใช้ alias `androiddebugkey`. นอกจากนี้ workflow กำหนด `contents: write` กว้างกว่าที่ build job ต้องใช้

**แนวทาง:** release job ต้อง fail-fast เมื่อ release keystore/alias/password ไม่ครบหรือไม่ตรง ห้ามใช้ debug key เป็น fallback; ใช้ `contents: read` สำหรับ build/test และให้ write permission เฉพาะ release job ที่ต้องสร้าง release

### P1 — Log มีข้อมูลส่วนบุคคล

`FirestoreErrorUtils.kt` สร้าง log ที่รวม UID, email, email verification, tenant/provider metadata และ path

**แนวทาง:** redact PII, ไม่ log provider metadata ที่ไม่จำเป็น, จำกัดรายละเอียดใน release build และเก็บข้อมูลสำหรับ debug เท่าที่จำเป็น

### P1/P2 — Predictive maintenance และ default telemetry

`PredictiveMaintenanceEngine.kt` มีค่า default เช่น mileage 68,500 km และปีรถ 2021 รวมถึง risk probability/remaining distance ที่มาจากกฎคงที่ ไม่ใช่หลักฐานว่าเป็น ML model ที่ผ่านการฝึกและ calibration

`LiveSensorData.kt` มี default telemetry เช่น RPM, speed และอุณหภูมิที่ดูเหมือนค่าจากรถจริง

**แนวทาง:** ใช้สถานะ unknown/disconnected เป็นค่าเริ่มต้น, แยก simulator data ชัดเจน และระบุ heuristic estimate พร้อมข้อจำกัด/ความเชื่อมั่น

## 4. ความเสี่ยง Cloud Sync ที่ต้องทดสอบเพิ่มเติม

จาก static review ยังไม่สามารถยืนยัน end-to-end sync, conflict resolution, tombstone, retry queue และ multi-device consistency ได้ครบทุก entity

- Cloud document IDs ที่อิง Room numeric IDs อาจชนกันระหว่างอุปกรณ์ที่มีข้อมูลคนละชุดแต่ได้เลข ID เดียวกัน
- ต้องตรวจว่าระบบ sync เป็น upload-only หรือ bidirectional reconciliation
- ต้องไม่ลบ local data จนกว่า server acknowledgement และการตรวจข้อมูลปลายทางสำเร็จ
- ควรมี stable UUID, `updatedAt`, `deletedAt`/tombstone, `syncState`, retry/idempotency และ conflict policy
- ต้องทดสอบ offline → online, app kill/restart, duplicate upload และการแก้ record เดียวกันจากสองอุปกรณ์

## 5. PR ที่เกี่ยวข้อง

- [PR #3 — Parse compact ELM327 DTC responses](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/3) — Open/Draft
- [PR #4 — Distinguish OBD scan failures from no DTC](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/4) — Open/Draft
- [PR #5 — Send Mode 04 before clearing real DTCs](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/5) — Open/Draft
- [PR #6 — Add OBD capability profile foundation](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/6) — Open/Draft

ควรตรวจ diff และผล tests ของแต่ละ PR ก่อนตัดสินใจ merge; สถานะ Draft ไม่ถือว่าแก้ไขเสร็จหรือผ่านการทดสอบแล้ว

## 6. เกณฑ์รับงานก่อน Merge/Release

- Unit tests ทำงานจริงและผ่านบน commit ที่จะ merge; ไม่มีการกลบ exit code
- Parser tests ครอบคลุม spaced/compact, malformed response, zero padding และ unknown codes
- timeout/error/unsupported ไม่ถูกแสดงเป็น NO_DTC
- Clear DTC มี Mode 04 จริงและตรวจ ECU acknowledgement
- Cloud failure แสดงสถานะ FAILED/PENDING และข้อมูล local ไม่หาย
- Room migration ผ่านโดยรักษาข้อมูลเดิม
- Firestore rules tests ผ่านด้าน user isolation และ CRUD/schema validation
- Release APK ใช้ release key ที่ถูกต้องและตรวจ certificate/checksum
- ทดสอบกับอะแดปเตอร์และรถจริง โดยบันทึกรุ่นรถ ECU/protocol adapter และ raw evidence แยกจาก simulator

## 7. แหล่งอ้างอิง

- [Repository](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v)
- [Main commit](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/commit/d7935939f92c76bfd8436ba762edeaa6812f836a)
- [Workflow run](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40461651001)
- [Build workflow](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/blob/main/.github/workflows/build-apk.yml)
- [OBD diagnostic engine](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/blob/main/app/src/main/java/com/example/service/ObdDiagnosticEngine.kt)
- [CloudSyncRepository](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/blob/main/app/src/main/java/com/example/data/repository/CloudSyncRepository.kt)
- [AppDatabase](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/blob/main/app/src/main/java/com/example/data/local/AppDatabase.kt)
- [Firestore rules](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/blob/main/firestore.rules)
