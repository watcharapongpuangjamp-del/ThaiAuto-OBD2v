# ThaiAuto-OBD2v — Remediation Plan & Checklist

**ฐานตรวจสอบ:** `main` @ `d7935939f92c76bfd8436ba762edeaa6812f836a`  
**วันที่:** 2026-10-29  
**หลักการ:** แก้ใน branch แยก, เพิ่ม regression tests, ตรวจผล CI และไม่ merge โดยอาศัย build success เพียงอย่างเดียว

## Phase 0 — P0 ก่อน Merge/Release

- [ ] แก้ `.github/workflows/build-apk.yml`: รัน `testDebugUnitTest` ทุก push/PR และไม่กลบ exit code
- [ ] ทำให้ workflow ล้มเหลวเมื่อ Unit Tests ล้มเหลว
- [ ] ตั้ง branch protection / required status checks สำหรับ `main`
- [ ] ปิด Clear DTC ใน real-hardware mode จนกว่าจะส่ง Mode 04 จริงและตรวจ acknowledgement
- [ ] ปรับ OBD scan status ให้ timeout/error ของ Mode 03/07/0A ไม่กลายเป็น `NO_DTC`
- [ ] ปรับ Cloud write methods ให้คืน typed failure/Result หรือ rethrow หลังบันทึก log
- [ ] UI ห้ามแสดง Cloud Sync success หาก write ยังไม่สำเร็จ

## Phase 1 — OBD-II correctness

- [ ] ทำ parser เป็น pure functions พร้อม unit tests
- [ ] รองรับ spaced และ compact ELM327 responses
- [ ] จัดการ echo, ECU headers, prompt, padding, `NO DATA`, `ERROR`, `STOPPED`, timeout และ malformed hex
- [ ] เพิ่ม Mode 0A สำหรับ permanent DTC ตาม capability/response ของ ECU
- [ ] แยกผล `SUCCESS`, `NO_DTC`, `PARTIAL_RESULT`, `TIMEOUT`, `UNSUPPORTED`, `COMMUNICATION_ERROR`
- [ ] ทดสอบ USB และ Bluetooth paths แยกกัน
- [ ] ทดสอบ adapter disconnect ระหว่าง scan และ ECU no-response
- [ ] ปิด scan/clear controls เมื่อ connection/ECU state ยังไม่ verified
- [ ] เก็บ raw TX/RX, response status และ timing ต่อ session

## Phase 2 — Data integrity & Cloud Sync

- [ ] เพิ่ม stable UUID ให้ Vehicle, ScanRecord, MaintenanceLog, TelemetrySnapshot และ entity ที่ sync
- [ ] เลิกใช้ Room autoincrement ID เป็น Cloud identity
- [ ] เพิ่ม durable outbox/sync queue, retry policy, idempotency key และ sync state
- [ ] เพิ่ม `updatedAt`, `deletedAt`/tombstone และ conflict resolution policy
- [ ] เพิ่ม Cloud download/reconciliation หากระบบต้องรองรับ multi-device sync
- [ ] ห้ามลบ local record จน Cloud acknowledgement และ payload verification สำเร็จ
- [ ] ทดสอบ offline → online, duplicate upload, app kill/restart และ two-device conflict
- [ ] เปลี่ยน Room `fallbackToDestructiveMigration()` เป็น explicit migrations
- [ ] เปิด schema export และเพิ่ม migration tests จากทุก schema version ที่รองรับ
- [ ] รวม Firestore rules ให้เหลือ source of truth เดียว
- [ ] ทดสอบ rules ด้วย Firebase Emulator: user isolation, CRUD และ schema validation
- [ ] Redact UID/email/provider metadata จาก Android logs

## Phase 3 — CI/CD security

- [ ] แยก build/test workflow ออกจาก release workflow
- [ ] ใช้ `contents: read` สำหรับ CI; เปิด write เฉพาะ job ที่ต้อง publish release
- [ ] Release ต้อง fail-fast เมื่อ keystore, alias หรือ password ไม่ครบ/ไม่ตรง
- [ ] ห้ามใช้ debug keystore เป็น fallback สำหรับ release artifact
- [ ] เพิ่ม dependency/security scan และ secret scanning
- [ ] สร้าง APK checksum และเก็บ signing certificate fingerprint
- [ ] ตรวจว่า credentials/keystore/API secrets ไม่ถูก commit

## Phase 4 — Telemetry, predictive maintenance และเอกสาร

- [ ] เปลี่ยน sensor defaults เป็น disconnected/unknown; ห้าม default เป็นค่ารถที่ดูเหมือนจริง
- [ ] แยก simulator constructor/flag จาก real telemetry
- [ ] ระบุ predictive output ว่าเป็น heuristic หากไม่ได้มาจาก ML model ที่ผ่านการฝึก/calibration
- [ ] แสดงแหล่งข้อมูล, threshold, confidence และเหตุผลของ estimate
- [ ] ทำ feature matrix: implemented / simulated / partial / hardware validated
- [ ] ทำ compatibility matrix ตามรถ/ปี/ECU/protocol/adapter chipset และ firmware
- [ ] บันทึก hardware test evidence แยกจาก simulator tests

## Acceptance gates ก่อน Merge

- [ ] Tests รันจริงและผ่านบน commit ที่จะ merge
- [ ] CI fail เมื่อ test/build fail
- [ ] ไม่มี false `NO_DTC` จาก timeout/error/unsupported response
- [ ] Clear DTC ตรวจ ECU acknowledgement
- [ ] Cloud failure คงข้อมูล local และแสดงสถานะที่ถูกต้อง
- [ ] Room migration รักษาข้อมูลเดิม
- [ ] Firestore rules tests ผ่าน
- [ ] Release artifact เซ็นด้วย release key ที่ถูกต้อง
- [ ] มีผลทดสอบ adapter/รถจริงพร้อม raw evidence

## ลำดับ PR ที่ควรพิจารณา

1. PR #4 — scan failure classification
2. PR #3 — compact DTC parser
3. PR #5 — Mode 04 acknowledgement และ safety checks
4. PR #6 — capability profile หลังเชื่อมกับ transport และ tests

ลำดับนี้เป็นข้อเสนอเพื่อจัดลำดับความเสี่ยง ไม่ใช่การอนุมัติ PR; ตรวจ diff และ CI ของแต่ละ PR ก่อน merge

## Reference links

- [Main branch](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/tree/main)
- [Workflow run](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40461651001)
- [PR #3](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/3)
- [PR #4](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/4)
- [PR #5](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/5)
- [PR #6](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/6)
