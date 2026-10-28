# PR / CI Recheck — 2026-10-29

## Scope and safety boundary

Rechecked draft PRs #3–#7, their latest head commits, and GitHub Actions workflow runs. This is a repository/CI review only. No PR or branch was merged, no production Firebase configuration was changed, and no physical vehicle was connected for hardware validation.

## Pull request and CI status

| PR | Topic | Current state | Workflow evidence |
|---|---|---|---|
| [#3](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/3) | Compact ELM327 DTC parser | Open, draft, mergeable | Run [40372612025](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40372612025) completed successfully; Unit Tests step completed successfully; Debug APK build succeeded. |
| [#4](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/4) | Distinguish scan failure from no DTC | Open, draft, mergeable | Run [40372621756](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40372621756) completed successfully; Unit Tests step completed successfully; Debug APK build succeeded. |
| [#5](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/5) | Mode 04 clear-DTC acknowledgement | Open, draft, mergeable | Run [40372628412](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40372628412) completed successfully; Unit Tests step completed successfully; Debug APK build succeeded. |
| [#6](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/6) | OBD capability profile foundation | Open, draft, mergeable | Run [40431039622](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40431039622) completed successfully; Unit Tests step completed successfully; Debug APK build succeeded. |
| [#7](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/pull/7) | Audit and remediation documentation | Open, draft, mergeable | Run [40768461102](https://github.com/watcharapongpuangjamp-del/ThaiAuto-OBD2v/actions/runs/40768461102) was still running at inspection; Unit Tests step was skipped on this run. Do not treat this as test validation. |

“Workflow completed successfully” means the configured CI steps succeeded. It does not establish compatibility with a physical ELM327 adapter or vehicle ECU.

## Confirmed follow-up risks on `main`

1. **Unit-test enforcement is not reliable on `main`:** `.github/workflows/build-apk.yml` runs `./gradlew testDebugUnitTest --continue || echo "Tests completed with warnings"`. The fallback echo can mask a non-zero test exit code. PR #3–#6 contain a change to remove this failure masking, but remain unmerged.
2. **Potential local data loss during schema upgrade:** `AppDatabase.kt` uses Room `fallbackToDestructiveMigration()` with `exportSchema = false`. Add explicit migrations and migration tests before a production update that could contain user records.
3. **Firestore rules have two competing files:** both root `firestore.rules` and `app/firestore.rules` exist and differ. Confirm which file Firebase deploy configuration actually uses, then establish one reviewed source of truth and test it with the Firebase Emulator.
4. **Cloud write contract needs verification:** `CloudSyncRepository.kt` catches write exceptions and delegates to `handleFirestoreError`, while public save methods return `Unit`. Inspect the handler and every caller to prove failures propagate and the UI cannot show success after a failed write. Until then, treat Cloud Sync success reporting as an unresolved risk.
5. **Release signing fallback is unsafe:** the current workflow can use a debug keystore when release secrets are absent. Release builds should fail fast unless a valid release keystore, alias, and passwords are configured.
6. **Mode 04 requires real-hardware validation:** PR #5 adds response checking and safeguards, but CI tests alone cannot prove the adapter/ECU exchange is correct. Keep physical clear-DTC behavior behind an explicit confirmation and verify the ECU response and a subsequent rescan on supported hardware before release.

## Next acceptance gates

- Confirm PR #7 workflow completion and ensure tests actually ran on a code-bearing PR.
- Review and merge PRs only after diff review and CI evidence; no merge was performed during this recheck.
- Add explicit Room migrations and migration tests before any schema-changing release.
- Trace `handleFirestoreError` through UI callers and add tests proving a failed cloud write is surfaced as failure.
- Resolve Firestore rules source-of-truth and test owner isolation/schema constraints in Firebase Emulator.
- Validate Mode 03/07/0A parsing and Mode 04 acknowledgement against captured adapter responses, then perform controlled physical-hardware testing.

