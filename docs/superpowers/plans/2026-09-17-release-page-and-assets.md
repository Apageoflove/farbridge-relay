# FarBridge Relay Release Page and Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish a polished bilingual, sanitized release page for 远桥 / FarBridge Relay, with the current iPhone PWA preview, no Android screenshot, and downloadable Android/iOS usage artifacts without changing the live server.

**Architecture:** Keep the GitHub repository as a sanitized source distribution. The Android client is distributed as a release APK, while iOS is distributed as a Safari/PWA workflow and bilingual setup document; no IPA is fabricated. README assets are static, fictional previews only, and all deployment secrets remain outside Git.

**Tech Stack:** Markdown, PNG assets, GitHub Contents/Release APIs, Android Gradle build outputs, Safari PWA and Web Push/Bark configuration.

**Spec:** User release request in the current task; existing repository contracts and `docs/RELEASE_SAFETY.md`.

## Global Constraints

- Preserve the live `/data/phone-mirror` deployment and all running services; perform read-only health/hash checks only.
- Never publish real IPs, domains, credentials, tokens, device IDs, Bark keys, VAPID private keys, databases, logs, or keystores.
- Keep the Chinese product ID `远桥` and add the English product ID `FarBridge Relay`; retain `yuanqiao-relay` only as the GitHub slug.
- Remove the Android 1.0.9 screenshot from README and repository assets; retain only the iPhone preview with native rounded corners and no square outer frame.
- State clearly that iOS is Safari/PWA and provide local setup steps; do not claim an IPA exists.

---

### Task 1: Replace and validate release imagery

**Files:**
- Replace: `docs/assets/iphone-ui-preview.png`
- Delete: `docs/assets/android-ui-preview.png`
- Modify: `README.md`

**Interfaces:**
- Consumes the existing sanitized iPhone UI concept image.
- Produces a transparent/trimmed iPhone-shaped preview used by README and GitHub.

- [ ] **Step 1: Inspect the existing iPhone image and record its safety boundary.**
- [ ] **Step 2: Generate an edited preview preserving the fictional UI while removing the square outer border and retaining the phone’s rounded silhouette.**
- [ ] **Step 3: Remove the Android screenshot asset and all README references to it.**
- [ ] **Step 4: Verify image files exist, render, and contain no real identifiers.**

### Task 2: Rewrite the bilingual README

**Files:**
- Modify: `README.md`
- Modify: `docs/RELEASE_SAFETY.md`

**Interfaces:**
- README links to release artifacts and Safari/PWA instructions.
- Release safety document defines the same no-secrets/no-live-server boundary.

- [ ] **Step 1: Write Chinese and English product identity, overview, and feature sections for `远桥` / `FarBridge Relay`.**
- [ ] **Step 2: Add a borderless iPhone preview figure and remove Android preview copy.**
- [ ] **Step 3: Add a GitHub Releases section linking the Android APK and bilingual iOS Safari/PWA setup artifact.**
- [ ] **Step 4: Add exact key-generation instructions for device keys, Argon2 credentials, Android keystore, VAPID, and Bark without embedding secrets.**
- [ ] **Step 5: Add bilingual Safari/PWA install, Web Push, Bark fallback, refresh, and troubleshooting steps.**
- [ ] **Step 6: Run a placeholder/secret scan and Markdown link/path validation.**

### Task 3: Publish release assets

**Files:**
- Create outside source tree: bilingual iOS setup markdown and `SHA256SUMS.txt`.
- Publish: GitHub Release `v1.0.9` under `Apageoflove/yuanqiao-relay`.

**Interfaces:**
- Release assets are independently downloadable and do not enter the source tree.
- APK checksum is published beside the APK for verification.

- [ ] **Step 1: Identify the latest local `1.0.9` APK and record size/hash without modifying it.**
- [ ] **Step 2: Create the bilingual Safari/PWA setup artifact with placeholders only.**
- [ ] **Step 3: Create the checksum artifact.**
- [ ] **Step 4: Create or update GitHub Release `v1.0.9` and upload APK, iOS setup, and checksum.**
- [ ] **Step 5: Verify release metadata and asset names through the GitHub API.**

### Task 4: Update the public repository without touching production

**Files:**
- Publish changed README, release safety doc, plan, iPhone image, and Android image deletion to GitHub `main`.

**Interfaces:**
- GitHub Contents API updates only the sanitized repository.
- Live server remains outside the mutation path.

- [ ] **Step 1: Commit local staging changes and confirm a clean worktree.**
- [ ] **Step 2: Update changed files using GitHub Contents API with current blob SHAs.**
- [ ] **Step 3: Verify remote tree, README, asset absence, and secret scan.**
- [ ] **Step 4: Re-run local checks and read-only server health/container/hash checks.**

### Self-review checklist

- [ ] The Android screenshot and caption are absent from README and the remote tree.
- [ ] The iPhone preview has no square four-corner outer border.
- [ ] README contains both Chinese and English instructions and both product IDs.
- [ ] Release contains a usable APK and an honest iOS Safari/PWA setup artifact; no fake IPA claim exists.
- [ ] No live server file, container, configuration, database, or secret was changed.
- [ ] Any failed local test is reported as residual risk, not as PASS.
