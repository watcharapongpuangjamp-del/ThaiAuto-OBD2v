# ThaiAuto-OBD2v — Documentation

เอกสารสนับสนุนการตรวจสอบ Main Branch ณ วันที่ 29 ตุลาคม 2026

## เอกสาร

- [รายงานตรวจสอบ Main Branch](audit/MAIN_BRANCH_AUDIT_2026-10-29.md) — ขอบเขตตรวจสอบ ข้อค้นพบ ความเสี่ยง และหลักฐานอ้างอิง
- [แผนแก้ไขและ Checklist](audit/REMEDIATION_PLAN.md) — งานเรียงตามความสำคัญและเกณฑ์ตรวจรับ
- [รายงานตรวจ PR และ CI รอบล่าสุด](audit/PR_CI_RECHECK_2026-10-29.md) — สถานะ PR #3–#7, ผล workflow ที่ตรวจได้ และความเสี่ยง Cloud Sync / data loss ที่ต้องปิดก่อน release

## ขอบเขต

เอกสารชุดนี้เป็นผลจาก static review ของ source code และ GitHub metadata ที่ตรวจพบ ไม่ใช่การรับรองว่าระบบทำงานกับรถจริงแล้ว ไม่ได้ใช้แทนผลทดสอบกับอะแดปเตอร์/ECU จริง

## วิธีอัปเดต

เมื่อมีการแก้ไขโค้ด ให้ปรับสถานะงานใน checklist พร้อมอ้างอิง commit, workflow run, test result และอุปกรณ์ที่ใช้ทดสอบ อย่าทำเครื่องหมายว่าผ่านจากการ build APK เพียงอย่างเดียว
