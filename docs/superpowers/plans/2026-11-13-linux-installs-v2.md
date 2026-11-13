# Linux Installs v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add distro versions, more install options, an install detail page (connection info, update, install packages, edit install info), a penguin drawer icon with bigger sizes, and metadata that is never auto-deleted.

**Architecture:** Extend the pure-Java core in `com.termux.app.linux` (catalog enums, validation, metadata, command building — all JUnit-tested with the local runner) and keep Android code thin. A new `LinuxInstallDetailActivity` and `LinuxInstallEditActivity` return `SessionRequest`s up the existing chain (detail → list → `TermuxActivity`).

**Tech Stack:** Java, Android SDK (minSdk 21, targetSdk 28), existing AndroidX/Material deps, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-13-linux-installs-v2-design.md` (builds on `docs/superpowers/specs/2026-11-13-linux-installs-design.md`)

## Global Constraints

- minSdk 21, targetSdk 28, Java only, **no new dependencies**; main code avoids `List.of/Set.of/Map.of`, `String.join`, `java.nio.file`, streams, `Map.merge`.
- Every new `linux_*` string key exists in both `app/src/main/res/values/strings.xml` (en) and `app/src/main/res/values-tr/strings.xml` (tr); apostrophes escaped (`\'`).
- No user-typed text except validated tokens reaches a shell string; values read from metadata are re-validated before use; passwords never pass through the app.
- Commands inside `bash -c '…'` contain no single quote.
- Hostname regex `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`; package token regex `^[a-z0-9][a-z0-9+.-]{0,63}$`, at most 30 tokens; password-label whitelist: letters (incl. Turkish), digits, space, `=`, `:`, `.`, `-`, `_`.
- Catalog: UBUNTU `24.04` "Ubuntu 24.04 LTS" `ubuntu:24.04` (default), `26.04` "Ubuntu 26.04 LTS" `ubuntu:26.04`, `22.04` "Ubuntu 22.04 LTS" `ubuntu:22.04`; DEBIAN `13` "Debian 13 (trixie)" `debian:13` (default), `12` "Debian 12 (bookworm)" `debian:12`. No moving tags.
- Timezones (fixed, this order): `Europe/Istanbul` (default), `UTC`, `Europe/London`, `Europe/Berlin`, `America/New_York`, `Asia/Tokyo`.
- Package groups (enum order): DEV `build-essential git`; PYTHON `python3 python3-pip python3-venv`; NODEJS `nodejs npm`; NGINX `nginx`; POSTGRESQL `postgresql`; MARIADB `mariadb-server`.
- Metadata is never deleted except by an explicit user action (Delete / Remove from list).
- Pure-Java tests run with `/tmp/claude-0/-home-user-termux-app/d08f1fd4-3559-5818-bf22-e37abfd5fedc/scratchpad/run-linux-tests.sh`; Android code cannot compile locally (CI verifies). Commit style `Added:/Changed:/Fixed:`.

## Review Focus

1. **Old metadata files (v1) without the new keys** → load with defaults (version unknown, no hostname, timezone empty, groups none), never throw. Test in Task 2 (`v1MetadataLoads`).
2. **Tampered metadata** (hostname `a;rm -rf`, package `x$(id)`, unknown group name, timezone `../../etc`) → the bad value is dropped (treated as empty), never reaches a command. Test in Task 2 (`tamperedMetadataSanitized`).
3. **Extra packages pasted with tabs/newlines/duplicate spaces** → split on any whitespace; empty tokens ignored; any invalid token → error. Test in Task 1 (`parsePackagesWhitespace`).
4. **Hostname set on an install whose SSH is off** → every typed `proot-distro login` still carries `--hostname`. Test in Task 3 (`hostnameOnEveryLogin`).
5. **Phone with no network / only mobile data / IPv6 only** → connection section says "Not connected to a network" or lists only IPv4 it has; no crash. Test in Task 6 (`HostAddressesTest.ipv6AndLoopbackIgnored`, `emptyWhenNone`).

---

### Task 1: Catalog, options and validation

**Files:**
- Create: `app/src/main/java/com/termux/app/linux/DistroVersion.java`, `PackageGroup.java`, `Timezones.java`, `RootSshLogin.java`
- Modify: `app/src/main/java/com/termux/app/linux/InstallOptions.java`, `InputValidator.java`
- Test: `app/src/test/java/com/termux/app/linux/CatalogTest.java`, `InputValidatorTest.java`

**Interfaces:**
- Produces:
  - `public enum DistroVersion { UBUNTU_24_04, UBUNTU_26_04, UBUNTU_22_04, DEBIAN_13, DEBIAN_12; Distro distro(); String id(); String label(); String imageRef(); boolean isDefault(); static List<DistroVersion> forDistro(Distro d) /* catalog order */; static DistroVersion defaultFor(Distro d); static DistroVersion fromId(Distro d, String id) /* null if unknown */ }` — values per Global Constraints.
  - `public enum PackageGroup { DEV, PYTHON, NODEJS, NGINX, POSTGRESQL, MARIADB; List<String> packages(); static PackageGroup fromName(String) /* null if unknown */ }`.
  - `public final class Timezones { static List<String> all(); static String DEFAULT = "Europe/Istanbul"; static boolean isKnown(String) }`.
  - `public enum RootSshLogin { NO("no"), KEY_ONLY("prohibit-password"), PASSWORD("yes"); String sshdValue(); static RootSshLogin fromName(String) }`.
  - `InstallOptions`: new public final fields `DistroVersion version` (non-null; must match `distro`), `String timezone` ("" = leave image default), `boolean localeTr`, `String hostname` ("" = none), `EnumSet<PackageGroup> groups` (copy), `List<String> extraPackages` (unmodifiable copy). New `public static final class Builder` with a setter per field (`distro`, `version`, `alias`, `user`, `sudo`, `basicTools`, `ssh`, `sshPort`, `sshAutoStart`, `rootPassword`, `rootKey`, `userKey`, `timezone`, `localeTr`, `hostname`, `groups`, `extraPackages`) and `build()`; builder defaults: version = `DistroVersion.defaultFor(distro)`, timezone "", groups empty, extras empty, port `InputValidator.DEFAULT_PORT`. Keep the existing 11-arg constructor (delegates with defaults) so v1 tests compile. `RootSshLogin rootSshLogin()` → PASSWORD if rootPassword, else KEY_ONLY if rootKey non-empty, else NO.
  - `InputValidator`: `boolean isValidHostname(String)`; `static List<String> parsePackages(String raw)` → tokens split on `\\s+`, empty input → empty list, returns `null` if any token invalid or more than 30 tokens; `static boolean isSafeLabel(String)`; `Error` gains `HOSTNAME, PACKAGES, VERSION, TIMEZONE` — `validate(InstallOptions)` adds: HOSTNAME when non-empty and invalid; PACKAGES when any extra package invalid or > 30; VERSION when `version.distro() != distro`; TIMEZONE when non-empty and not `Timezones.isKnown`.

- [ ] **Step 1: Write failing tests** — `CatalogTest`: `ubuntuVersionsInOrder` (`forDistro(UBUNTU)` = [24.04, 26.04, 22.04] ids), `defaults` (`defaultFor(UBUNTU)` = UBUNTU_24_04, `defaultFor(DEBIAN)` = DEBIAN_13), `imageRefs` (`DEBIAN_12.imageRef()` = `"debian:12"`, `UBUNTU_26_04.imageRef()` = `"ubuntu:26.04"`), `fromIdUnknownIsNull` (`fromId(UBUNTU,"latest")` null), `groupPackages` (`PYTHON.packages()` = [python3, python3-pip, python3-venv]), `timezonesDefaultFirst` (`all().get(0)` = "Europe/Istanbul", `isKnown("../../etc")` false), `rootSshLoginValues` (`KEY_ONLY.sshdValue()` = "prohibit-password"). `InputValidatorTest` additions: `hostnameRules` (valid `myserver`, `a`, `a-1`; invalid `-a`, `a-`, `My`, `a.b`, 64 chars, `a;b`), `parsePackagesWhitespace` (`" htop\ttmux\n\nhtop "` → [htop, tmux, htop]; `"x$(id)"` → null; 31 tokens → null; `""` → empty), `labelWhitelist` (`"== Şifre: deploy_bot =="` true; `"a\"b"`, `"$x"`, "a`b", `"a'b"` false), `versionMustMatchDistro` (DEBIAN distro + UBUNTU_24_04 → contains VERSION), `builderDefaults` (Builder with distro UBUNTU only + alias → version UBUNTU_24_04, port 2022, timezone "").
- [ ] **Step 2: Run, expect compile FAIL** — runner (see Global Constraints).
- [ ] **Step 3: Implement** the interfaces above.
- [ ] **Step 4: Run all tests, expect PASS.**
- [ ] **Step 5: Commit** — `Added: Add Linux distro versions, package groups and new install option validation`

---

### Task 2: Metadata v2 and statuses

**Files:**
- Modify: `app/src/main/java/com/termux/app/linux/InstallInfo.java`, `InstallStore.java`, `LinuxPaths.java`
- Create: `app/src/main/java/com/termux/app/linux/EditInfo.java`
- Test: `app/src/test/java/com/termux/app/linux/InstallStoreTest.java`

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
  - `LinuxPaths.pkgFailedFile(String alias)` → `<meta>/<alias>.pkgfailed`.
  - `InstallInfo.Status { READY, INSTALLING, INCOMPLETE, NOT_FOUND }`; new public final fields: `DistroVersion version` (nullable), `String hostname`, `String timezone`, `boolean localeTr`, `RootSshLogin rootSshLogin` (non-null, default KEY_ONLY when unknown), `boolean failed`, `boolean packagesFailed`, `boolean hasMetadata`. Replace the constructor with a `Builder` (same style as InstallOptions).
  - `public final class EditInfo` (public final fields + all-args constructor): `Distro distro` (nullable = unknown), `DistroVersion version` (nullable), `String user`, `String hostname`, `boolean ssh`, `int sshPort`, `boolean sshAutoStart`, `RootSshLogin rootSshLogin`. `InputValidator.validate(EditInfo)` → `EnumSet<Error>` (USER, HOSTNAME, PORT when ssh, VERSION when version's distro ≠ distro).
  - `InstallStore`: `save(InstallOptions)` also writes `version` (id), `timezone`, `localeTr`, `hostname`, `rootSshLogin` (enum name from `o.rootSshLogin()`), `groups` (comma-separated names), `extraPackages` (space-separated) and deletes a stale `.pkgfailed`. `scan(Set<String>)` never deletes anything; status order: READY (rootfs + done) → INSTALLING (install session open and no failed marker) → INCOMPLETE (rootfs, or failed marker with install session open) → NOT_FOUND (otherwise; only reachable with metadata). `void saveEdit(String alias, EditInfo e) throws IOException` merges into existing properties (creates the file if missing; keeps keys it does not own), and touches the done marker when the rootfs exists. `void forget(String alias)` deletes `.properties .done .failed .pkgfailed .root.pub .user.pub`. Every value read from metadata is re-validated: invalid hostname/timezone → "", invalid user → "", unknown version/group/rootSshLogin → null/skipped/KEY_ONLY, invalid package tokens dropped.

- [ ] **Step 1: Write failing tests** in `InstallStoreTest`: replace `orphanMetadataRemoved` with `missingRootfsIsNotFoundAndKept` (metadata, no rootfs, no session → one NOT_FOUND entry, metadata file still exists); `failedWithoutSessionIsNotFoundFailed` (failed marker, no rootfs, no session → NOT_FOUND with `failed == true`); `v1MetadataLoads` (write a v1-style properties file by hand with only distro/user/ssh/sshPort → loads, `version == null`, `hostname == ""`, `rootSshLogin == KEY_ONLY`); `v2RoundTrip` (save options with version DEBIAN_12, timezone UTC, localeTr, hostname `box1`, groups {PYTHON, NGINX}, extras [htop] → scan returns the same values); `tamperedMetadataSanitized` (hand-written hostname `a;rm -rf`, timezone `../../etc`, user `e$(id)`, groups `PYTHON,EVIL`, extraPackages `htop x$(id)` → hostname "", timezone "", user "", groups {PYTHON}, packages [htop]); `pkgFailedFlag` (`.pkgfailed` present → `packagesFailed`); `saveEditCreatesAndMarksReady` (rootfs only → `saveEdit` → READY with user/hostname/ssh values); `saveEditKeepsUnownedKeys` (existing `timezone` survives `saveEdit`); `forgetRemovesAll`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Update existing tests that assumed orphan deletion.
- [ ] **Step 4: Run all tests, expect PASS** (also once as non-root: `runuser -u nobody`, as in v1, so `unlistableRootfsKeepsMetadata` runs).
- [ ] **Step 5: Commit** — `Changed: Keep Linux install metadata and add v2 fields and statuses`

---

### Task 3: Command builder v2

**Files:**
- Modify: `app/src/main/java/com/termux/app/linux/CommandBuilder.java`
- Test: `app/src/test/java/com/termux/app/linux/CommandBuilderTest.java`

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces (`hostname` "" = omit; every `proot-distro login` the builder emits is `proot-distro login <alias>[ --hostname <h>]…`):
  - `CommandBuilder(LinuxPaths p)` (English labels) and `CommandBuilder(LinuxPaths p, String userPasswordLabelFormat /* contains %s */, String rootPasswordLabel)`; throws `IllegalArgumentException` if a formatted label fails `InputValidator.isSafeLabel`.
  - `install(InstallOptions o)`: shape `if <CORE>; then touch <done>[ && echo "== <userLabel> ==" && <login> -- passwd <user>][ && echo "== <rootLabel> ==" && <login> -- passwd root][; <PKGS>]; else touch <failed>; fi\n` where CORE = v1 core chain with `proot-distro install --override-alias <alias> <version.imageRef()>`; INNER additionally (in this order, after `upgrade`): core `apt-get install -y` now also includes `tzdata` when timezone set and `locales` when localeTr; then timezone `ln -sf /usr/share/zoneinfo/<tz> /etc/localtime && echo <tz> > /etc/timezone`; locale — Ubuntu: `locale-gen tr_TR.UTF-8 && update-locale LANG=tr_TR.UTF-8`, Debian: `sed -i "s/^# *tr_TR.UTF-8 UTF-8/tr_TR.UTF-8 UTF-8/" /etc/locale.gen && locale-gen && update-locale LANG=tr_TR.UTF-8`; hosts (when hostname) `sed -i "/^127.0.1.1 /d" /etc/hosts && echo "127.0.1.1 <h>" >> /etc/hosts`; then v1 ssh config (PermitRootLogin from `o.rootSshLogin().sshdValue()`) and useradd. PKGS (only when groups/extras non-empty) = `<login> -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get install -y <pkgs>' || touch <pkgfailed>`.
  - `login(String alias, String hostname)`, `loginUser(String alias, String hostname, String user)`, `sshStart(String alias, String hostname)`, `setPassword(String alias, String hostname, String account)` — v1 shapes plus `--hostname`. Remove the old hostname-less overloads and update callers in `app/src/main/java/com/termux/app/activities/LinuxInstallsActivity.java` (pass `info.hostname`) and its `new CommandBuilder(...)` to the label constructor using `R.string.linux_password_label_user` ("Password for user %s" / "%s kullanıcısının şifresi") and `R.string.linux_password_label_root` ("Password for root" / "root şifresi") — add both keys (en + tr) in this task.
  - `update(String alias, String hostname)` → `<login> -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get update && apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade'\n`.
  - `installPackages(String alias, String hostname, Set<PackageGroup> groups, List<String> extras)` → `<login> -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get update && apt-get install -y <pkgs>' && rm -f <pkgfailed> || touch <pkgfailed>\n`; throws `IllegalArgumentException` when no packages.
  - `applyEdit(String alias, String hostname, boolean sshChanged, boolean ssh, int port, RootSshLogin mode, boolean hostnameChanged)` → `""` when nothing to do; else `<login> -- env DEBIAN_FRONTEND=noninteractive bash -c '<steps>'\n` with steps: when `sshChanged && ssh`: `(command -v sshd >/dev/null || (apt-get update && apt-get install -y openssh-server)) && ssh-keygen -A && mkdir -p /run/sshd && printf "Port <port>\nPermitRootLogin <mode>\n" > /etc/ssh/sshd_config.d/termux.conf`; when `hostnameChanged`: hostname non-empty → the hosts line command; empty → `sed -i "/^127.0.1.1 /d" /etc/hosts`.
  - `static String displaySshCommand(String user, int port, String ip)` → `ssh -p <port> <user or root>@<ip>` (display/copy only).
- [ ] **Step 1: Write failing tests**: `minimalInstallUsesImageRef` (exact full string for Builder(UBUNTU, alias u1) — contains `--override-alias u1 ubuntu:24.04` and the v1 tail `; then touch /M/u1.done; else touch /M/u1.failed; fi\n`), `timezoneAndLocaleUbuntu`, `localeDebianUsesLocaleGen` (contains the sed + `locale-gen && update-locale`), `hostsLineWhenHostname`, `hostnameOnEveryLogin` (hostname `box1`: every occurrence of `proot-distro login u1` is followed by ` --hostname box1`, including passwd and PKGS), `labeledPasswords` (`echo "== Password for user erkan ==" && proot-distro login`), `packagesStepAfterPasswordsNonFatal` (PKGS appears after `passwd root` and ends with `|| touch /M/u1.pkgfailed; else`), `noPackagesNoPkgStep`, `unsafeLabelRejected` (constructor with `"x$(id) %s"` throws), `updateShape`, `installPackagesShape`, `applyEditNothing` (`""`), `applyEditSshAndHost`, `applyEditClearsHost`, `displaySshCommandRootFallback`, `noSingleQuoteInsideInner` (every generated string has an even number of `'`). Update v1 tests to the new signatures.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Also run `bash -n` on a generated full install string (all options on) and paste the result in the report.
- [ ] **Step 4: Run all tests, expect PASS.**
- [ ] **Step 5: Commit** — `Added: Build v2 Linux install commands with versions, options and hostname`

---

### Task 4: Look — penguin icon and bigger sizes

**Files:**
- Modify: `app/src/main/res/drawable/ic_linux_installs.xml`, `app/src/main/res/layout/activity_termux.xml` (settings + linux buttons), `app/src/main/res/layout/item_linux_install.xml`, `app/src/main/res/layout/activity_linux_install_form.xml`

**Interfaces:** none (resources only).

- [ ] **Step 1:** Get the Material Design Icons "linux" glyph: `npm pack @mdi/svg@7.4.47` in the scratchpad, extract `package/svg/linux.svg`, copy its `path d` into `ic_linux_installs.xml` (24×24 viewport, same structure/tint as `ic_settings.xml`); add an XML comment: `Linux icon from Material Design Icons (Pictogrammers), Apache License 2.0`.
- [ ] **Step 2:** Drawer `settings_button` and `linux_installs_button`: `layout_width/height="48dp"`, `padding="8dp"`, `scaleType="fitCenter"` (glyph renders at 32dp).
- [ ] **Step 3:** `item_linux_install.xml`: name `?android:attr/textAppearanceLarge` bold; details and status `?android:attr/textAppearanceMedium`. Form section titles `textAppearanceMedium`.
- [ ] **Step 4: Verify** — every edited XML parses (`python3 -c "import xml.dom.minidom,sys;[xml.dom.minidom.parse(f) for f in sys.argv[1:]]" <files>`), no R reference changed.
- [ ] **Step 5: Commit** — `Changed: Use a Linux icon and larger sizes for Linux installs UI`

---

### Task 5: Install form v2

**Files:**
- Modify: `app/src/main/java/com/termux/app/activities/LinuxInstallFormActivity.java`, `app/src/main/res/layout/activity_linux_install_form.xml`, both `strings.xml`

**Interfaces:**
- Consumes: Task 1 (`DistroVersion`, `PackageGroup`, `Timezones`, `InstallOptions.Builder`, `InputValidator`), Task 3 labels.
- Produces: result extras gain `EXTRA_VERSION` (id), `EXTRA_TIMEZONE`, `EXTRA_LOCALE_TR`, `EXTRA_HOSTNAME`, `EXTRA_GROUPS` (String[] enum names), `EXTRA_EXTRA_PACKAGES` (String, normalized single-space); `optionsFromIntent(Intent)` builds via `InstallOptions.Builder`.
- New fields (after SSH section, before root access): Version spinner (filtered by distro, default preselected, refreshed on distro change); Timezone spinner (`Timezones.all()`, plus a first item "Image default" → ""; default selection Europe/Istanbul); "Turkish language (tr_TR.UTF-8)" checkbox; Hostname field; "Packages" section — one checkbox per `PackageGroup` (labels: "Development tools (build-essential, git)", "Python", "Node.js", "Web server (nginx)", "PostgreSQL", "MariaDB"; tr: "Geliştirme araçları (build-essential, git)", "Python", "Node.js", "Web sunucusu (nginx)", "PostgreSQL", "MariaDB"); "Extra packages" field (hint "e.g. htop tmux" / "ör. htop tmux"). Errors for HOSTNAME/PACKAGES/VERSION/TIMEZONE shown under fields.
- [ ] **Step 1:** Strings (en + tr). **Step 2:** Layout. **Step 3:** Activity wiring. **Step 4: Verify** — R/string cross-check, en/tr key-set diff empty, local runner still green. **Step 5: Commit** — `Added: Add version, timezone, locale, hostname and package options to Linux install form`

---

### Task 6: Install detail page and list changes

**Files:**
- Create: `app/src/main/java/com/termux/app/linux/HostAddresses.java`, test `HostAddressesTest.java`
- Create: `app/src/main/java/com/termux/app/activities/LinuxInstallDetailActivity.java`, `app/src/main/res/layout/activity_linux_install_detail.xml`
- Modify: `LinuxInstallsActivity.java`, `item_linux_install.xml` (remove ⋮; row click opens detail), `AndroidManifest.xml`, both `strings.xml`

**Interfaces:**
- Consumes: Tasks 1–3, `SessionRequest`, `SessionNames`.
- Produces:
  - `public final class HostAddresses { static List<String> ordered(List<String[]> ifaceAndIpv4) /* [name, ip] pairs; wlan* first, then others in input order; drops loopback 127.*, non-IPv4, duplicates */ ; static List<String[]> fromSystem() /* NetworkInterface enumeration, up && !loopback, Inet4Address only; never throws (returns empty on SocketException) */ }`.
  - `LinuxInstallDetailActivity`: extras `EXTRA_ALIAS` (String), `SessionRequest.EXTRA_OPEN_SESSIONS`; result `RESULT_OK` + `SessionRequest.EXTRA_REQUESTS`, or `RESULT_FIRST_USER` meaning "list changed, refresh" (after Remove from list / edit saved without requests).
  - List activity: row tap → detail (forResult); on detail RESULT_OK forward requests to TermuxActivity (finish with them); on RESULT_FIRST_USER rescan. Row keeps Login / User login quick buttons. NOT_FOUND rows: badge "not found" ("install failed" when `failed`), buttons disabled, tap opens detail.
- Detail page sections per spec sketch. Actions by status — READY/INCOMPLETE: Login, User login (valid user), Start/Stop SSH (when ssh), Update, Install packages, Set password, Edit install info, Backup, Delete (existing rules: refused while any `belongsTo` session open); INSTALLING: none; NOT_FOUND: Edit install info hidden, only "Remove from list" (confirm → `store.forget(alias)` → RESULT_FIRST_USER). Banner "Some packages could not be installed" when `packagesFailed`. Connection section only when ssh: rows from `HostAddresses.ordered(HostAddresses.fromSystem())`, each with `CommandBuilder.displaySshCommand(user, port, ip)` and a Copy button (`ClipboardManager`, toast "Copied"); none → "Not connected to a network". Install packages dialog: group checkboxes + extras field validated with `parsePackages`; OK disabled until ≥1 package. All typed commands pass the stored hostname.
- [ ] **Step 1: Write failing tests** `HostAddressesTest`: `wlanFirst` ([rmnet0,10.0.0.2],[wlan0,192.168.1.5] → [192.168.1.5, 10.0.0.2]), `ipv6AndLoopbackIgnored` ([lo,127.0.0.1],[wlan0,fe80::1] → []), `emptyWhenNone`, `duplicatesDropped`.
- [ ] **Step 2: Run, expect FAIL. Step 3: Implement** helper, then activities/layouts/strings/manifest (`exported=false`, parent `.app.activities.LinuxInstallsActivity`). **Step 4: Verify** — runner green, R/string cross-check, en/tr diff empty. **Step 5: Commit** — `Added: Add Linux install detail page with connection info, update and package install`

---

### Task 7: Edit install info

**Files:**
- Create: `app/src/main/java/com/termux/app/activities/LinuxInstallEditActivity.java`, `app/src/main/res/layout/activity_linux_install_edit.xml`
- Modify: `LinuxInstallDetailActivity.java` (button → edit, forResult), `AndroidManifest.xml`, both `strings.xml`

**Interfaces:**
- Consumes: `EditInfo`, `InputValidator.validate(EditInfo)`, `InstallStore.saveEdit`, `CommandBuilder.applyEdit`, `DistroVersion`, `RootSshLogin`.
- Produces: extras `EXTRA_ALIAS`; prefilled from the install's `InstallInfo`. Result: `RESULT_OK` + `SessionRequest.EXTRA_REQUESTS` when `applyEdit` returned a command (one `OPEN SessionNames.firstFree(alias, open)` request), else `RESULT_FIRST_USER`. Detail forwards either to the list.
- Form: Distro (Unknown / Ubuntu / Debian), Version (catalog of chosen distro + "Unknown"), Username (hint: "Must already exist in the install" / "Kurulumda zaten var olmalı"), Hostname, SSH enabled, Port, Auto-start, Root login over SSH (No / Key only / Password; default from info). Save: validate → `saveEdit` (IOException → toast, stay) → `applyEdit(alias, newHostname, sshChanged, ssh, port, mode, hostnameChanged)` where `sshChanged` = ssh/port/mode differ from the loaded values.
- [ ] **Step 1:** Strings (en + tr). **Step 2:** Layout. **Step 3:** Activity + detail wiring + manifest. **Step 4: Verify** — runner green, R/string cross-check, en/tr diff empty. **Step 5: Commit** — `Added: Add editing of Linux install info`

---

## Verification environment

No Android SDK here; Android code is verified by the PR's CI (`debug_build.yml`, `run_tests.yml`) after push. Report which checks ran locally vs in CI.
