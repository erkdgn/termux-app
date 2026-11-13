# Linux Installs Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native "Linux Installs" page in the Termux app that lists `proot-distro` installs, creates new Ubuntu/Debian installs from a form, and types the generated commands into a new terminal session.

**Architecture:** Pure-Java core in `com.termux.app.linux` (validation, command building, metadata store, session-request encoding) with JUnit tests. Two activities (list, form) never touch sessions directly: the list activity returns a list of `SessionRequest`s to `TermuxActivity` via `startActivityForResult`, and `TermuxTerminalSessionActivityClient` opens/writes sessions, queuing input until the shell pid exists.

**Tech Stack:** Java, Android SDK (minSdk 21, targetSdk 28), existing AndroidX/Material deps, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-11-13-linux-installs-design.md`

## Global Constraints

- minSdk 21, targetSdk 28, Java only, **no new dependencies**.
- Code, identifiers, comments in English. UI strings in `app/src/main/res/values/strings.xml` (English) and new `app/src/main/res/values-tr/strings.xml` (Turkish), every new key in both.
- Distros: Ubuntu (`ubuntu`), Debian (`debian`) only.
- Alias `^[a-z][a-z0-9-]{0,31}$`; username `^[a-z_][a-z0-9_-]{0,31}$` and not `root`; SSH port integer 1024–65535, default 2022.
- Public key: one line (after trimming whitespace/CR/LF) matching `^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)|sk-ssh-ed25519@openssh.com) [A-Za-z0-9+/]+={0,3}( [A-Za-z0-9@._-]{1,64})?$`.
- No user-typed text except validated tokens ever reaches a shell string. Key text goes only into staging files. Passwords never pass through the app.
- Interactive `passwd` steps are always last in a chain.
- Paths: rootfs dir `TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/proot-distro/installed-rootfs"`; metadata dir `TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/linux-installs"`.
- Session limit: reuse the existing `MAX_SESSIONS` (8) dialog in `TermuxTerminalSessionActivityClient`.
- Commit messages follow repo style: `Added: …`, `Changed: …`, `Fixed: …`.

## Review Focus

1. **Alias already exists** (rootfs dir or metadata present) → form shows an error on the alias field; Install stays disabled. Test in Task 3 (`isAliasTaken`), wired in Task 5.
2. **Session limit hit on a two-request action** (SSH auto-start + login) → the max-terminals dialog shows once, no partial typing into a wrong session. Covered in Task 4 (stop processing remaining requests when a session can't be created).
3. **Shared storage not set up when backing up** → backup goes to `~` instead of `~/storage/downloads`. Test in Task 2 (`backup` with `downloadsAvailable=false`).
4. **Pasted key with trailing newline / Windows CRLF / surrounding spaces** → accepted after trimming; a key with an embedded newline or `;`/`$(` is rejected. Tests in Task 1.
5. **Termux service not bound when the result arrives** (activity recreated) → requests are kept and applied in `onServiceConnected`. Covered in Task 7.

---

### Task 1: Options model and input validation

**Files:**
- Create: `app/src/main/java/com/termux/app/linux/Distro.java`
- Create: `app/src/main/java/com/termux/app/linux/InstallOptions.java`
- Create: `app/src/main/java/com/termux/app/linux/InputValidator.java`
- Test: `app/src/test/java/com/termux/app/linux/InputValidatorTest.java`

**Interfaces:**
- Produces:
  - `enum Distro { UBUNTU("ubuntu", "Ubuntu"), DEBIAN("debian", "Debian"); String id(); String label(); static Distro fromId(String) /* null if unknown */ }`
  - `final class InstallOptions` with public final fields: `Distro distro; String alias; String user /* "" = none */; boolean sudo; boolean basicTools; boolean ssh; int sshPort; boolean sshAutoStart; boolean rootPassword; String rootKey /* "" = none, already normalized */; String userKey /* "" = none */`, plus an all-args constructor and `boolean hasUser()`.
  - `final class InputValidator` (static methods):
    - `boolean isValidAlias(String)`, `boolean isValidUser(String)`, `boolean isValidPort(int)`
    - `String normalizeKey(String raw)` → trimmed single-line key, or `null` if invalid (empty input → `""`).
    - `enum Error { ALIAS, USER, PORT, ROOT_KEY, USER_KEY, SSH_NO_ACCESS, USER_KEY_WITHOUT_USER, SUDO_WITHOUT_USER }`
    - `EnumSet<Error> validate(InstallOptions o)` — empty set means valid. `SSH_NO_ACCESS` when `ssh && !hasUser() && !rootPassword && rootKey.isEmpty() && userKey.isEmpty()`.

- [ ] **Step 1: Write the failing tests** — `InputValidatorTest`:
  - `aliasRules`: valid `ubuntu-web`, `a`, 32-char `a` + 31×`b`; invalid `""`, `Ubuntu`, `1ubuntu`, `ubu ntu`, `ubu;rm`, 33 chars.
  - `userRules`: valid `erkan`, `_svc`, `a-b_c`; invalid `root`, `Erkan`, `e$(id)`, `""` (as user string for `isValidUser`).
  - `portRules`: `1024` and `65535` valid; `1023`, `65536`, `0` invalid.
  - `keyNormalization`: `"ssh-ed25519 AAAAC3Nza erkan@phone\r\n"` → `"ssh-ed25519 AAAAC3Nza erkan@phone"`; `"  ssh-rsa AAAA==  "` → `"ssh-rsa AAAA=="`; `""` → `""`; `"ssh-ed25519 AAAA\nssh-rsa BBBB"` → `null`; `"ssh-ed25519 AAAA; rm -rf /"` → `null`; `"ssh-ed25519 $(id)"` → `null`; `"ssh-dss AAAA"` → `null`.
  - `sshNeedsAccess`: options with `ssh=true`, no user, no root password, no keys → contains `SSH_NO_ACCESS`; same with `rootPassword=true` → empty set.
  - `sudoNeedsUser`: `sudo=true`, no user → contains `SUDO_WITHOUT_USER`.

- [ ] **Step 2: Run tests, expect compile failure**
  Run: `./gradlew :app:testDebugUnitTest --tests 'com.termux.app.linux.InputValidatorTest'`
  Expected: FAIL (classes missing).

- [ ] **Step 3: Implement** `Distro`, `InstallOptions`, `InputValidator` per the interfaces; regexes copied from Global Constraints. `validate` checks key fields by re-running `normalizeKey` and comparing to the stored value (non-null and equal).

- [ ] **Step 4: Run tests, expect PASS** (same command).

- [ ] **Step 5: Commit** — `Added: Add Linux install options and input validation`

---

### Task 2: Command builder

**Files:**
- Create: `app/src/main/java/com/termux/app/linux/LinuxPaths.java`
- Create: `app/src/main/java/com/termux/app/linux/CommandBuilder.java`
- Test: `app/src/test/java/com/termux/app/linux/CommandBuilderTest.java`

**Interfaces:**
- Consumes: `InstallOptions`, `Distro` (Task 1).
- Produces:
  - `final class LinuxPaths { LinuxPaths(String rootfsDir, String metaDir, String homeDir); static LinuxPaths defaults(); String rootfs(String alias); String metaFile(String alias) /* <meta>/<alias>.properties */; String doneFile(String alias) /* <meta>/<alias>.done */; String rootKeyFile(String alias) /* <meta>/<alias>.root.pub */; String userKeyFile(String alias) /* <meta>/<alias>.user.pub */; String rootfsDir(); String metaDir(); String homeDir(); }`
  - `final class CommandBuilder { CommandBuilder(LinuxPaths p); String install(InstallOptions o); String login(String alias); String loginUser(String alias, String user); String sshStart(String alias); static final String SSH_STOP = "\u0003"; static String sshSessionName(String alias) /* alias + "-ssh" */; String backup(String alias, boolean downloadsAvailable, String timestamp /* yyyyMMdd-HHmm */); String remove(String alias); String setPassword(String alias, String account); }`
  - All returned commands end with `"\n"` (except `SSH_STOP`).

**Exact command shapes** (tokens in `<>` are validated values; `A` = alias):

- `install(o)` = join with ` && `:
  1. `pkg update -y`
  2. `pkg install -y proot-distro`
  3. `proot-distro install --override-alias A <distro.id>`
  4. `proot-distro login A -- env DEBIAN_FRONTEND=noninteractive bash -c '<INNER>'`
  5. if rootKey: `install -D -m 600 <rootKeyFile> <rootfs>/root/.ssh/authorized_keys && chmod 700 <rootfs>/root/.ssh`
  6. if userKey: same into `<rootfs>/home/<user>/.ssh/authorized_keys` and `chmod 700 <rootfs>/home/<user>/.ssh`
  7. `touch <doneFile>`
  8. if user: `proot-distro login A -- passwd <user>`
  9. if rootPassword: `proot-distro login A -- passwd root`
- `INNER` = join with ` && `:
  - `apt-get update`
  - `apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade`
  - `apt-get install -y <pkgs>` only if pkgs non-empty; pkgs in this order, de-duplicated: basicTools → `sudo nano wget curl git net-tools`; sudo → `sudo`; ssh → `openssh-server`.
  - if ssh: `ssh-keygen -A && mkdir -p /run/sshd && printf "Port <port>\nPermitRootLogin <mode>\n" > /etc/ssh/sshd_config.d/termux.conf` where mode = `yes` if rootPassword, else `prohibit-password` if rootKey, else `no`.
  - if user: `useradd -m -s /bin/bash -G sudo <user>` (omit `-G sudo` when `!sudo`).
- `login(A)` = `proot-distro login A`; `loginUser` = `proot-distro login A --user <user>`.
- `sshStart(A)` = `exec proot-distro login A -- /usr/sbin/sshd -D -e`.
- `backup` = `proot-distro backup --output <dir>/A-<timestamp>.tar.gz A`, dir = `<home>/storage/downloads` if available else `<home>`.
- `remove(A)` = `proot-distro remove A && rm -f <metaFile> <doneFile> <rootKeyFile> <userKeyFile>`.
- `setPassword(A, acct)` = `proot-distro login A -- passwd <acct>`.

- [ ] **Step 1: Write the failing tests** — `CommandBuilderTest` with `new LinuxPaths("/R", "/M", "/H")`:
  - `minimalInstall`: Ubuntu, alias `u1`, no user, nothing checked → exactly
    `pkg update -y && pkg install -y proot-distro && proot-distro install --override-alias u1 ubuntu && proot-distro login u1 -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get update && apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade' && touch /M/u1.done\n`
  - `fullInstall`: Debian, alias `d1`, user `erkan`, sudo, basicTools, ssh port 2022, rootPassword, rootKey and userKey set → contains `apt-get install -y sudo nano wget curl git net-tools openssh-server` (one `sudo`), `PermitRootLogin yes`, `useradd -m -s /bin/bash -G sudo erkan`, `install -D -m 600 /M/d1.root.pub /R/d1/root/.ssh/authorized_keys`, `install -D -m 600 /M/d1.user.pub /R/d1/home/erkan/.ssh/authorized_keys`; and ends with `touch /M/d1.done && proot-distro login d1 -- passwd erkan && proot-distro login d1 -- passwd root\n`.
  - `rootKeyOnlyMode`: ssh + rootKey, no rootPassword → contains `PermitRootLogin prohibit-password`.
  - `noSudoGroup`: user without sudo → contains `useradd -m -s /bin/bash erkan'` and no `-G sudo`.
  - `keyTextNeverInCommand`: install with rootKey `ssh-ed25519 AAAAKEY x@y` → result does not contain `AAAAKEY`.
  - `backupFallsBackToHome`: `backup("u1", false, "20261113-1200")` = `proot-distro backup --output /H/u1-20261113-1200.tar.gz u1\n`; with `true` → `/H/storage/downloads/…`.
  - `removeCleansMetadata`, `sshStartUsesExec`, `sshSessionName` (`u1-ssh`), `setPassword`.

- [ ] **Step 2: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests 'com.termux.app.linux.CommandBuilderTest'`
- [ ] **Step 3: Implement** `LinuxPaths` (`defaults()` uses the constants in Global Constraints; home = `TermuxConstants.TERMUX_HOME_DIR_PATH`) and `CommandBuilder` per the shapes above.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `Added: Add proot-distro command builder for Linux installs`

---

### Task 3: Install store (metadata, key staging, scanning)

**Files:**
- Create: `app/src/main/java/com/termux/app/linux/InstallInfo.java`
- Create: `app/src/main/java/com/termux/app/linux/InstallStore.java`
- Test: `app/src/test/java/com/termux/app/linux/InstallStoreTest.java`

**Interfaces:**
- Consumes: `LinuxPaths`, `InstallOptions`, `Distro`.
- Produces:
  - `final class InstallInfo { enum Status { READY, INCOMPLETE, INSTALLING } final String alias; final Status status; final Distro distro /* null if unknown */; final String user /* "" if none */; final boolean ssh; final int sshPort; final boolean sshAutoStart; }`
  - `final class InstallStore { InstallStore(LinuxPaths p); void save(InstallOptions o) throws IOException /* writes .properties and .root.pub / .user.pub when keys non-empty; creates meta dir */; List<InstallInfo> scan(Set<String> openSessionNames) /* sorted by alias */; boolean isAliasTaken(String alias); }`
- Properties keys: `distro`, `user`, `sudo`, `ssh`, `sshPort`, `sshAutoStart`, `rootPassword`.
- `scan` rules, per alias = union of rootfs subdirs and `*.properties` names:
  - rootfs exists + done file → `READY`
  - rootfs exists, no done file → `INCOMPLETE`
  - no rootfs, metadata exists, `openSessionNames` contains alias → `INSTALLING`
  - no rootfs, metadata exists, no session → orphan: delete `.properties`, `.done`, `.root.pub`, `.user.pub`; not listed.
  - rootfs without metadata (manual install) → `distro=null`, `user=""`, `ssh=false`; status by done-file rule (so `INCOMPLETE` without done file — acceptable, they can still log in).
  - missing rootfs dir → treated as empty, no exception.

- [ ] **Step 1: Write failing tests** with JUnit `TemporaryFolder` building `LinuxPaths` from temp dirs:
  - `saveThenScanReady`: save options for `u1`, create `rootfs/u1` and `u1.done` → one `InstallInfo` READY with distro UBUNTU, user, ssh port.
  - `incompleteWithoutDone`, `installingWhenSessionOpen` (`Set.of("u1")`), `orphanMetadataRemoved` (files gone after scan, empty list).
  - `manualInstallListed`: only `rootfs/foo` → listed with `distro == null`.
  - `missingRootfsDirIsEmpty`.
  - `keysStaged`: save with rootKey → `u1.root.pub` contains the key + `\n`; no `u1.user.pub`.
  - `aliasTaken`: true for existing rootfs dir or properties file, false otherwise.
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests 'com.termux.app.linux.InstallStoreTest'`
- [ ] **Step 3: Implement** with `java.util.Properties` and `java.io.File` (no Android APIs, so it stays JVM-testable). Use `HashSet`/`Collections` rather than `Set.of` in main code (minSdk 21).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit** — `Added: Add Linux install metadata store`

---

### Task 4: Session requests and typing into sessions

**Files:**
- Create: `app/src/main/java/com/termux/app/linux/SessionRequest.java`
- Test: `app/src/test/java/com/termux/app/linux/SessionRequestTest.java`
- Modify: `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java` (`addNewSession` ~364, `setTerminalShellPid` ~238)

**Interfaces:**
- Produces:
  - `final class SessionRequest { enum Kind { OPEN, SEND } final Kind kind; final String sessionName; final String input; static String[] encode(List<SessionRequest>); static List<SessionRequest> decode(String[]) /* throws IllegalArgumentException on malformed */; static final String EXTRA_REQUESTS = "com.termux.linux.extra.REQUESTS"; static final String EXTRA_OPEN_SESSIONS = "com.termux.linux.extra.OPEN_SESSIONS"; }` — encoding: flat array of triples `kind, name, input`.
  - In `TermuxTerminalSessionActivityClient`:
    - `public boolean openSessionWithInput(String sessionName, String input)` — like `addNewSession(false, sessionName)` but returns `false` (after showing the existing max-terminals dialog) when no session was created; stores `input` in a `Map<TerminalSession, String> mPendingInput` and makes the new session current.
    - `public boolean sendToSession(String sessionName, String input)` — finds a session whose `mSessionName` equals `sessionName`, writes immediately via `TerminalSession.write(String)`; `false` if not found.
    - `public void applySessionRequests(List<SessionRequest> requests)` — processes in order, stops at the first `false`.
    - `public String[] getOpenSessionNames()`.
    - `setTerminalShellPid`: after the existing code, `String pending = mPendingInput.remove(terminalSession); if (pending != null) terminalSession.write(pending);`.
  - Refactor `addNewSession` to delegate to a private `TermuxSession createSession(boolean isFailSafe, String name)` returning `null` on limit, so both paths share the limit dialog.

- [ ] **Step 1: Write failing tests** — `SessionRequestTest`: round-trip of `[OPEN u1-ssh "exec …\n", OPEN u1 "proot-distro login u1\n", SEND u1-ssh "\u0003"]` keeps order and exact strings; `decode` of an array with length not divisible by 3 throws; unknown kind throws.
- [ ] **Step 2: Run, expect FAIL** — `./gradlew :app:testDebugUnitTest --tests 'com.termux.app.linux.SessionRequestTest'`
- [ ] **Step 3: Implement** `SessionRequest`, then the client changes above. Do **not** write to a session right after creating it — only via the pending map (the shell starts in `initializeEmulator()`; `write` drops data while `mShellPid == 0`, see `TerminalSession.java:178`).
- [ ] **Step 4: Run all unit tests, expect PASS** — `./gradlew :app:testDebugUnitTest`
- [ ] **Step 5: Commit** — `Added: Allow typing queued input into new terminal sessions`

---

### Task 5: New install form activity

**Files:**
- Create: `app/src/main/java/com/termux/app/activities/LinuxInstallFormActivity.java`
- Create: `app/src/main/res/layout/activity_linux_install_form.xml`
- Modify: `app/src/main/res/values/strings.xml`; Create: `app/src/main/res/values-tr/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml` (next to `SettingsActivity`, same theme, `exported="false"`, `parentActivityName=".app.activities.LinuxInstallsActivity"`)

**Interfaces:**
- Consumes: `Distro`, `InstallOptions`, `InputValidator`, `InstallStore.isAliasTaken`.
- Produces: returns `RESULT_OK` with extras `EXTRA_DISTRO, EXTRA_ALIAS, EXTRA_USER, EXTRA_SUDO, EXTRA_BASIC_TOOLS, EXTRA_SSH, EXTRA_SSH_PORT, EXTRA_SSH_AUTOSTART, EXTRA_ROOT_PASSWORD, EXTRA_ROOT_KEY, EXTRA_USER_KEY` (constants on the class, prefix `com.termux.linux.extra.`), plus `static InstallOptions optionsFromIntent(Intent)`.

Form fields (top to bottom, matching the agreed sketch): Distro spinner (Ubuntu default) · Install name (default `ubuntu` / `debian`, follows distro until edited) · Username · ☐ Grant sudo (enabled only with username) · ☑ Basic tools · ☐ SSH server → Port (2022) + ☐ Start automatically on login (shown when SSH checked) · Root access: ☐ Set root password, Root public key (multi-line) · User public key (shown with username) · Phantom-process note with a "Developer options" button (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`, shown on API ≥ 31) · Install button.

- [ ] **Step 1:** Add strings (en + tr) for every label, hint, error (one per `InputValidator.Error` plus "name already in use"), and the phantom note.
- [ ] **Step 2:** Build layout with `TextInputLayout` fields (Material) so errors show under fields.
- [ ] **Step 3:** Implement the activity: on every change, build `InstallOptions`, run `validate` + `isAliasTaken`, set field errors, enable Install only when valid. Install → `setResult(RESULT_OK, intent)` and `finish()`.
- [ ] **Step 4: Verify build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `Added: Add new Linux install form`

---

### Task 6: Installs list activity

**Files:**
- Create: `app/src/main/java/com/termux/app/activities/LinuxInstallsActivity.java`
- Create: `app/src/main/res/layout/activity_linux_installs.xml`, `app/src/main/res/layout/item_linux_install.xml`
- Modify: strings (en + tr), `AndroidManifest.xml` (`exported="false"`, `parentActivityName=".app.TermuxActivity"`)

**Interfaces:**
- Consumes: `InstallStore`, `CommandBuilder`, `SessionRequest`, `LinuxInstallFormActivity.optionsFromIntent`, `LinuxPaths.defaults()`.
- Produces: launched with `SessionRequest.EXTRA_OPEN_SESSIONS` (String[]); finishes with `RESULT_OK` + `SessionRequest.EXTRA_REQUESTS` (String[]) or `RESULT_CANCELED`.

Behavior:
- `onResume`: `store.scan(openSessions)`; `ListView` + simple adapter; empty state text when no installs. FAB/button "New install" → form via `startActivityForResult`.
- Row: name, `Distro.label()` (or "Unknown"), user, `SSH: <port>` when ssh, status badge for INCOMPLETE / INSTALLING. Buttons: **Login** → `[OPEN alias login(alias)]`; **User login** (enabled when user) → `[OPEN alias loginUser]`. When `sshAutoStart` and `alias-ssh` not in open sessions, prepend `[OPEN alias-ssh sshStart(alias)]`.
- Overflow menu: **Start SSH** / **Stop SSH** (by presence of `alias-ssh` in open sessions; stop = `[SEND alias-ssh SSH_STOP]`), **Set password** (dialog choosing user / root; root only), **Backup** (`downloadsAvailable` = `new File(home, "storage/downloads").isDirectory()`, timestamp `yyyyMMdd-HHmm` local time), **Delete** (AlertDialog naming the alias → `[OPEN alias remove(alias)]`). INSTALLING rows show only Delete disabled and no actions.
- Form result: `store.save(options)` (on `IOException` show a toast and stay), then return `[OPEN alias install(options)]`.

- [ ] **Step 1:** Strings (en + tr) for all labels, menu items, dialogs, empty state, status badges.
- [ ] **Step 2:** Layouts.
- [ ] **Step 3:** Implement the activity per Behavior.
- [ ] **Step 4: Verify build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `Added: Add Linux installs list page`

---

### Task 7: Drawer entry and request handling in TermuxActivity

**Files:**
- Modify: `app/src/main/res/layout/activity_termux.xml` (the `LinearLayout` holding `settings_button`, ~line 48)
- Create: `app/src/main/res/drawable/ic_linux_installs.xml` (24dp vector, terminal/box glyph, same style as `ic_settings.xml`)
- Modify: `app/src/main/java/com/termux/app/TermuxActivity.java` (button setup ~567, `onActivityResult` ~793, `onServiceConnected`)

**Interfaces:**
- Consumes: `SessionRequest`, `TermuxTerminalSessionActivityClient.applySessionRequests / getOpenSessionNames`.
- Produces: `static final int REQUEST_LINUX_INSTALLS = 1100;` in `TermuxActivity`.

- [ ] **Step 1:** Add `ImageButton @+id/linux_installs_button` after `settings_button` (40dp, `@null` background, tint `?attr/termuxActivityDrawerImageTint`, contentDescription string en + tr).
- [ ] **Step 2:** Click → `startActivityForResult(intent with EXTRA_OPEN_SESSIONS = client.getOpenSessionNames(), REQUEST_LINUX_INSTALLS)`.
- [ ] **Step 3:** `onActivityResult` for `REQUEST_LINUX_INSTALLS` + `RESULT_OK`: decode requests; if `getTermuxService() != null` apply now, else store in a field `mPendingLinuxRequests` and apply at the end of `onServiceConnected`. Close the drawer.
- [ ] **Step 4: Verify** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` → all tests pass, BUILD SUCCESSFUL.
- [ ] **Step 5: Manual check on a device** (owner, from the CI debug APK): new Ubuntu install with user + SSH types the chain into a session named after the alias and ends at the password prompt; list shows READY afterwards; Start SSH opens `<alias>-ssh`, Stop SSH closes it; Delete removes the row.
- [ ] **Step 6: Commit** — `Added: Add Linux installs entry to the drawer`

---

## Verification environment

This container has no Android SDK. If `./gradlew` cannot find one, push the branch and use the repo's GitHub Actions: `run_tests.yml` (unit tests) and `debug_build.yml` (APK artifact). Report which steps were verified locally vs. in CI.
