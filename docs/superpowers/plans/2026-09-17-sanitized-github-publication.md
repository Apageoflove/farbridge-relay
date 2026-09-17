# Sanitized GitHub Publication Implementation Plan

> **For agentic workers:** Keep the live deployment read-only while preparing this repository export.

**Goal:** Publish a reviewable, sanitized source snapshot without changing the running Phone Mirror deployment.

**Architecture:** The GitHub repository is a source/documentation copy. Runtime data, secrets, device credentials, databases, logs, release APKs, and server-only configuration remain outside Git and are supplied only through operator-managed files.

**Tech Stack:** Android/Kotlin client, Python API, web PWA, Docker Compose, Caddy at the operator site, GitHub for source control.

**Global constraints:**

- Do not write to or restart the live `/data/phone-mirror` deployment during publication.
- Do not commit real domains, IP addresses, usernames, passwords, tokens, device IDs, device secrets, Bark keys, private keys, databases, logs, APKs, or personal data.
- Use placeholders in examples and keep secrets in an ignored operator file such as `deploy/secrets.env`.
- Verify the export with a secret-pattern scan, `git diff --check`, and the repository's lightweight layout checks before pushing.

### Task 1: Prepare the isolated export

- Work only in `E:/agent/codex/phone-mirror-github-stage`.
- Keep generated/runtime directories excluded by `.gitignore`.
- Add the generic relay overview illustration under `docs/assets/`.

### Task 2: Rewrite public documentation

- Make `README.md` describe the architecture, local checks, operator setup boundary, and limitations without deployment identifiers.
- Add `docs/RELEASE_SAFETY.md` covering the separation between GitHub and production, secret handling, backup/rollback, and acceptance evidence.

### Task 3: Independently audit the export

- Search tracked text for real hostnames, IPs, credentials, tokens, private keys, device identifiers, and personal information.
- Confirm ignored runtime files are absent from the export and no generated APK/database/log is staged.
- Run the smallest available layout and syntax checks from the export.

### Task 4: Publish only after repository target confirmation

- Confirm the exact GitHub repository name and visibility before creating it.
- Create the repository and push the sanitized commit using an ephemeral credential path; never store the token in Git config, files, URLs, or logs.
- Use the available FastMCP GitHub integration for a post-push repository/commit verification or a documentation commit, then independently verify the final remote commit.

### Task 5: Verify production isolation

- Recheck the live health endpoint and service/container status read-only.
- Compare the live deployment marker before and after publication; any runtime change is a release blocker.

**Acceptance:** The GitHub tree is sanitized and reproducible, the remote commit is verified, and the live deployment remains healthy and unchanged.
