# Linux Installs v2 — versions, install options, richer menu

Date: 2026-11-13
Status: builder-checked, awaiting owner approval
Builds on: `2026-11-13-linux-installs-design.md` (v1, shipped in PR erkdgn/termux-app#2)

## Intent

The owner runs Ubuntu/Debian servers inside the Termux app and does not want
to type shell commands — neither during the first install nor afterwards.
v1 works (install, login, SSH start/stop, backup, delete) but:

- the distro version cannot be chosen (`proot-distro install ubuntu` pulls
  `ubuntu:latest`, a moving tag — today 26.04 LTS);
- install options are thin; anything else (timezone, locale, packages) must be
  typed by hand;
- after install, the only actions are login/SSH/backup/delete — updating,
  installing packages or finding the phone's IP for SSH all need typing;
- the drawer entry is a small `>_` icon that is hard to find;
- the two password prompts at the end are unlabeled;
- v1 silently deleted an install's metadata when it could not find the rootfs
  (this already destroyed the owner's metadata once).

## Goals

1. **Look:** drawer icon becomes a Linux penguin; drawer icons and the installs
   list/form text get bigger.
2. **Distro versions:** pick distro *and* version in the new-install form.
3. **More install options:** timezone, Turkish locale, hostname, package groups,
   validated extra packages; labeled password prompts.
4. **Install detail page:** tapping an install opens a page with its info,
   connection info (phone IPs + ready SSH command with copy), and all actions,
   including the new **Update**, **Install packages** and **Edit install info**.
5. **Robustness:** never auto-delete metadata; an install whose rootfs is gone
   shows **Not found** and can be cleaned up from the app.

## Non-goals

- Distros other than Ubuntu and Debian (everything stays `apt`-based).
- Starting/stopping services such as nginx/PostgreSQL from the app (later).
- Diagnosing the owner's SSH connection problem (tracked separately; it looks
  network-side: "Connection timed out", not "refused").
- Live version lists from Docker Hub (the catalog is fixed in the app).
- Desktop/VNC.

## Anti-goals

- No free user text ever reaches a shell string (same rule as v1).
- Passwords never pass through the app.
- Metadata is never deleted without an explicit user action.
- The owner must not need to type a command for anything in Goals.

## Constraints

- minSdk 21, targetSdk 28, Java, no new library dependencies.
- UI strings in `values/strings.xml` (en) and `values-tr/strings.xml` (tr),
  identical key sets for `linux_*`.
- proot-distro is the current Python version (Termux `pkg`): installs pull
  Docker/OCI images; image refs like `ubuntu:24.04` are accepted by
  `proot-distro install`. Containers live in
  `$PREFIX/var/lib/proot-distro/containers/<alias>/rootfs` (legacy
  `installed-rootfs/<alias>` also supported, already implemented in v1 fix
  `e4bc940`). `login` accepts `--user`, `--hostname STRING` (not persisted —
  defaults to `localhost` on every login).
- Docker base images are minimal: `sudo`, `tzdata`, `locales`, `openssh-server`
  etc. are not preinstalled.
- Pure-Java core stays JUnit-testable with the local runner (javac + JUnit 4,
  stub `TermuxConstants`); Android code is verified by CI (`debug_build.yml`).

## Approaches considered

- **Version list:** (a) fixed catalog in the app — **chosen**: simple, works
  offline, predictable; cost: new releases need an app update. (b) live Docker
  Hub tag list — rejected: network + error states, and most tags are not
  useful choices.
- **Where the new actions live:** (a) a per-install **detail page** — **chosen**:
  room for connection info and many actions, the list stays readable.
  (b) a longer ⋮ menu on each row — rejected: no place for IP/command display,
  menu becomes too long.
- **Install packages after install:** reuse the same package-group model as the
  install form (one source of truth) — chosen.

## Design

### 1. Look

- Drawer: replace `ic_linux_installs.xml` with a penguin glyph. Use the
  Material Design Icons "linux" icon (Pictogrammers, Apache-2.0) as a vector
  drawable; add the license attribution in a comment in the drawable. (It can
  be taken from the `@mdi/svg` npm package; the npm registry is reachable.)
- Drawer buttons (`settings_button`, `linux_installs_button`): 48dp × 48dp
  touch area, glyph shown at 32dp (padding 8dp, `scaleType="fitCenter"`).
- Installs list item: name `textAppearanceLarge` bold, details
  `textAppearanceMedium`, status `textAppearanceMedium` italic; row buttons at
  default (not reduced) text size. Form section titles `textAppearanceMedium`.

### 2. Distro catalog

New pure-Java catalog (e.g. `DistroVersion`), fixed entries:

| distro | version id | label | image ref | default |
|---|---|---|---|---|
| UBUNTU | `24.04` | Ubuntu 24.04 LTS | `ubuntu:24.04` | yes |
| UBUNTU | `26.04` | Ubuntu 26.04 LTS | `ubuntu:26.04` | |
| UBUNTU | `22.04` | Ubuntu 22.04 LTS | `ubuntu:22.04` | |
| DEBIAN | `13` | Debian 13 (trixie) | `debian:13` | yes |
| DEBIAN | `12` | Debian 12 (bookworm) | `debian:12` | |

- No moving tags (`latest`, `rolling`): an install's recorded version must
  stay true. *Controller call:* default 24.04 LTS (most mature under proot;
  26.04 LTS is offered).
- Form: distro spinner, then a version spinner filtered to that distro, default
  preselected.
- Install command: `proot-distro install --override-alias <alias> <imageRef>`.
- Metadata stores `version` (the version id). List/detail show the label
  (e.g. "Ubuntu 24.04 LTS"); missing/unknown version → distro label only.

### 3. New install options

All are optional and validated; all run inside the existing
`bash -c '<INNER>'` (no single quotes inside, as in v1).

| option | form control | default | validation | effect inside the container |
|---|---|---|---|---|
| Timezone | spinner, fixed list: Europe/Istanbul, UTC, Europe/London, Europe/Berlin, America/New_York, Asia/Tokyo | Europe/Istanbul | must be one of the list | install `tzdata`; `/etc/localtime` → `/usr/share/zoneinfo/<tz>`; write `/etc/timezone` |
| Turkish locale | checkbox | off | — | install `locales`; generate `tr_TR.UTF-8`; set it as default `LANG` (Ubuntu and Debian differ — builder must make both work) |
| Hostname | text | empty (= proot-distro default) | `^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$` | stored in metadata and passed as `--hostname <h>` on **every** `proot-distro login` the app types (proot-distro only fakes the uname nodename); additionally a line `127.0.1.1 <h>` is added to the container's `/etc/hosts` (idempotent: remove any previous `127.0.1.1` line first) so `sudo` can resolve it |
| Package groups | checkboxes | none | fixed enum | `apt-get install -y` of the group's packages |
| Extra packages | text, space-separated | empty | each token `^[a-z0-9][a-z0-9+.-]{0,63}$`, max 30 tokens | appended to the same `apt-get install` |

Package groups (fixed enum, pure Java):

| group | packages |
|---|---|
| Development tools | `build-essential git` |
| Python | `python3 python3-pip python3-venv` |
| Node.js | `nodejs npm` |
| Web server (nginx) | `nginx` |
| PostgreSQL | `postgresql` |
| MariaDB | `mariadb-server` |

**Two package steps** (*controller call*, so a typo or a fragile
post-install script cannot break the core install):

1. *Core* (inside the main `if` chain, fatal): basic tools, sudo, ssh,
   tzdata / locales when needed — one de-duplicated `apt-get install -y`.
2. *Optional* (groups in enum order + extras in typed order, de-duplicated):
   runs **after** the done marker and the password prompts, as a separate
   non-fatal command: on failure it touches `<alias>.pkgfailed` and echoes a
   labeled message; the install stays READY. The detail page shows "Some
   packages could not be installed" while that marker exists and offers
   **Install packages** to retry; a successful Install packages run removes
   the marker.

**Labeled password prompts:** before each `passwd`, the chain prints a fixed
label: `echo "== <label> =="`. Labels come from string resources
(`linux_password_label_user` with the username, `linux_password_label_root`)
and are passed into the command builder; the builder rejects (throws) any
label containing characters outside letters (incl. Turkish letters), digits,
space, `=`, `:`, `.`, `-`, `_` — so resources can never inject shell syntax.

### 4. Install detail page

New activity, opened by tapping a list row (the row keeps its quick Login /
User login buttons; the ⋮ menu moves into the detail page). Sections:

```
┌──────────────────────────────────┐
│ ←  ubuntu                         │
├──────────────────────────────────┤
│ Ubuntu 24.04 LTS · ready          │
│ User: erkan · Host: myserver      │
│ SSH: 2022 (running / stopped)     │
├── Connection ─────────────────────┤
│ 192.168.55.103 (wlan0)            │
│ ssh -p 2022 erkan@192.168.55.103 [Copy] │
├── Actions ────────────────────────┤
│ [Login] [User login]              │
│ [Start SSH] / [Stop SSH]          │
│ [Update] [Install packages]       │
│ [Set password] [Edit install info]│
│ [Backup] [Delete]                 │
└──────────────────────────────────┘
```

- **Connection:** IPv4 addresses from `java.net.NetworkInterface` (up,
  non-loopback; `wlan*` first). No new permissions. One ready SSH command per
  address (user if set, else `root`), each with a Copy button
  (`ClipboardManager`). If no address: "Not connected to a network". Shown only
  when SSH is enabled for the install.
- **Update:** opens an action session typing
  `proot-distro login <alias> [--hostname h] -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get update && apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade'`.
- **Install packages:** dialog with the same group checkboxes + extra
  packages field (same validation); types one `apt-get update && apt-get install -y …`
  in an action session.
- **Edit install info (owner option B):** form with distro (Ubuntu / Debian /
  unknown — needed for installs found without metadata), version (catalog for
  the chosen distro, or "unknown"), username, hostname, SSH enabled / port /
  auto-start, and **root login over SSH** (no / key only / password; default
  from metadata, else "key only" — *controller call*: never opens password
  root login unless chosen). Saving writes metadata. If SSH settings changed and SSH is
  enabled, it also types (action session) a command that installs
  `openssh-server` if missing, runs `ssh-keygen -A`, `mkdir -p /run/sshd` and
  rewrites `/etc/ssh/sshd_config.d/termux.conf` with the new port and the
  chosen PermitRootLogin value. A hostname change also updates the
  container's `/etc/hosts` line (same command). Username is **recorded**, not created — the
  form explains that it must already exist in the install. Editing is allowed
  for installs without metadata (creates it). The "ready" marker: saving edit
  info for an install whose rootfs exists also writes the done marker, so a
  repaired install shows **Ready**.
- Existing actions (Login, User login, Start/Stop SSH, Set password, Backup,
  Delete) move here unchanged except that every `proot-distro login` gets
  `--hostname <h>` when a hostname is stored.
- Action sessions keep v1 naming (`SessionNames.firstFree(alias)`; SSH stays
  `"<alias> (ssh)"`, install `"<alias> (install)"`).

### 5. Metadata robustness

- Remove automatic orphan deletion from `InstallStore.scan` entirely.
- New status **NOT_FOUND**: metadata exists, no rootfs in either layout, no open
  install session — with or without a failed marker (*controller call*: an
  install that failed during the image pull and whose session was closed is
  NOT_FOUND, labeled "install failed" when the failed marker exists). Only
  action is **Remove from list** (deletes the app's metadata files; no shell
  command, confirm dialog).
- Status order: READY (rootfs + done) → INSTALLING (install session open, no
  failed marker) → INCOMPLETE (rootfs, or failed marker with session open) →
  NOT_FOUND.
- Delete (READY/INCOMPLETE) unchanged: `proot-distro remove` + `rm` metadata.

### 6. Metadata format (additive)

New keys: `version`, `timezone`, `localeTr`, `hostname`, `rootSshLogin`, `groups`
(comma-separated enum names), `extraPackages` (space-separated validated
tokens). Old files without them keep working (defaults: unknown version, no
hostname, etc.). Every value read back from metadata is re-validated before it
can reach a command (metadata files are app-writable but treated as untrusted).

## Security summary

Same boundary as v1: only validated tokens reach shell strings. New tokens:
image ref (from the fixed catalog), timezone (fixed list), hostname (regex),
package names (regex, count cap), group packages (fixed), password labels
(character whitelist). Values from metadata are re-validated on read.

## Left to the builder

- Exact per-distro locale commands (Ubuntu vs Debian) — must work on both.
- Layouts/visual details of the detail page within the sketch above.
- Whether the detail page is reached via row tap only or also a button.
- How the list refreshes after returning from the detail page.
- Unit tests for every new pure-Java piece (catalog, validators, command
  shapes including `--hostname`, NOT_FOUND status, metadata round-trip).
