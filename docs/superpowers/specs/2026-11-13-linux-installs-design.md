# Linux Installs page — design

Date: 2026-11-13
Status: approved by owner 2026-11-13

## Intent

The owner wants to set up and run Ubuntu / Debian server environments
(via `proot-distro`) on their phone **without ever switching to another
app**. Today this means following a long manual guide and typing every
command by hand. This change adds a native "Linux Installs" page to the
Termux app itself: it lists existing installs, offers a "new install" form,
and on submit opens a new terminal session and **types the generated
commands into it**, so the owner watches the install happen in the
terminal and answers password prompts there.

This fork (`erkdgn/termux-app`) replaces the owner's existing Termux
install (same package name `com.termux`, different signing key → the old
app must be uninstalled first). That is accepted and out of scope for the
code.

## Goals

- Drawer button opens a **Linux Installs** activity.
- List installed distros found under
  `$PREFIX/var/lib/proot-distro/installed-rootfs/<alias>/`.
- Per install: **Login (root)**, **Login (user)**, overflow menu with
  **Start/Stop SSH**, **Backup**, **Delete** (confirm dialog).
- **New install** form:
  - Distro: Ubuntu, Debian (list easy to extend).
  - Install name (alias) — lets several installs of the same distro coexist
    (`proot-distro install --override-alias <alias> <distro>`).
  - Username + "grant sudo" checkbox (optional: empty username = root only).
    "Grant sudo" always installs the `sudo` package, independent of
    "Basic tools".
  - "Basic tools" checkbox: `sudo nano wget curl git net-tools`.
  - "SSH server" checkbox + port (default 2022) + "start SSH automatically
    on login" checkbox.
  - **Root access** section: "Set root password" checkbox and/or "Root
    public key" field. Also a "User public key" field when a username is
    given.
  - SSH requires at least one way in: a user (password), a root password, or
    a public key. Otherwise the SSH checkbox is disabled with a hint.
  - Info note about the Android 12+ phantom process killer ("signal 9"),
    with a button that opens Developer options.
- On **Install**: open a new terminal session named after the alias and
  write the command chain into it as if typed.

## Non-goals

- No desktop / GUI / VNC (explicitly dropped by the owner for now).
- No PulseAudio / sound setup.
- No distros beyond Ubuntu and Debian in this version.
- No Termux:Boot integration (start-on-phone-boot).
- No change to signing, package name or bootstrap.

## Anti-goals

- The owner must never need to leave the Termux app for the normal flow
  (the one-time Developer-options toggle is the only exception).
- Form input must never be able to inject shell commands.
- Passwords must never pass through the app.
- A failed step must not silently continue into later steps.

## Constraints

- minSdk 21, targetSdk 28, Java, existing AndroidX deps only; no new
  libraries.
- Code and identifiers in English; UI strings in `values/strings.xml`
  (English default) plus `values-tr/strings.xml` (Turkish).
- `proot-distro` login runs as fake root; non-root users cannot bind ports
  < 1024, hence default SSH port 2022.

## Approaches considered

1. **Native Android page that types commands into a new session — chosen.**
   Matches "pick from a page, watch it typed in the terminal". Most Java to
   write.
2. Bundled `dialog` TUI script launched from the drawer. Faster to build,
   but a text menu, not a page.
3. Native page that runs a generated script in the background
   (`RUN_COMMAND` / execution command). More robust, but the owner would not
   see the commands typed, which contradicts the request.

## How it works

### Delivering commands

- Reuse `TermuxTerminalSessionActivityClient.addNewSession(...)` logic
  (`TermuxService.createTermuxSession`) to create a session named `<alias>`
  and make it current.
- Write the command string plus `\n` with `TerminalSession.write(...)` —
  the same path keyboard input takes; bash echoes and runs it.
- **Timing (verified in code):** `TerminalSession.write()` drops data while
  `mShellPid == 0` (`TerminalSession.java:178`); the shell is only spawned in
  `initializeEmulator()`, called from `updateSize()` when `TerminalView` lays
  the session out. So the command is **queued** per session (pending-command
  map keyed by session handle) and flushed from
  `TermuxTerminalSessionActivityClient.setTerminalShellPid(...)` once the pid
  is set. Never write directly after `createTermuxSession()`.
- The activity returns to `TermuxActivity` (finish the installs activity /
  bring Termux activity to front with the new session selected).

### Avoiding the queued-input trap

Queued input is consumed by any command that reads stdin. Therefore:
- Every command is non-interactive (`-y`, `DEBIAN_FRONTEND=noninteractive`,
  `apt-get -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold`).
- The whole sequence is a single `&&` chain written once, so a failure stops
  it and the error stays on screen.
- `passwd <user>` (interactive) is always the **last** command.

### Generated command chain (new install)

```
pkg update -y && pkg install -y proot-distro && \
proot-distro install --override-alias <alias> <distro> && \
proot-distro login <alias> -- env DEBIAN_FRONTEND=noninteractive bash -c '
  apt-get update && apt-get -y upgrade &&
  [basic tools]   apt-get install -y sudo nano wget curl git net-tools &&
  [ssh]           apt-get install -y openssh-server && ssh-keygen -A &&
                  mkdir -p /run/sshd &&
                  printf "Port <port>\n" > /etc/ssh/sshd_config.d/termux.conf &&
  [user]          useradd -m -s /bin/bash [-G sudo] <user> &&
                  passwd <user>
'
```

(Exact quoting is the builder's job; the string is built from validated
tokens only, see Security.)

### Listing installs and metadata

- Installs = directories in `installed-rootfs/`. Distro type and options are
  not recoverable from the rootfs alone, so on Install the app writes a small
  metadata file `~/.termux/linux-installs/<alias>.properties`
  (`distro`, `user`, `ssh`, `sshPort`, `sshAutoStart`). Installs without
  metadata (made by hand) are still listed, as "unknown distro, no user".
- Metadata is written at submit time; an install whose rootfs dir does not
  exist yet is shown as "incomplete" rather than hidden.

### Failure and retry

- No resume. A failed install is shown as "incomplete" and offers **Delete**;
  the owner then reinstalls with the same alias. The form rejects an alias
  that already exists (rootfs dir or metadata).
- Per-user action **Set password** (types `proot-distro login <alias> --
  passwd <user>`) covers a mistyped password, the only interactive step.

### proot-distro version

- Trust whatever `pkg install proot-distro` installs (no pinning). The app
  relies only on: `install --override-alias`, `login [--user]`, `backup
  --output`, `remove`, and the `installed-rootfs/<alias>` layout. If
  `installed-rootfs/` is missing, the list shows an empty state instead of
  failing.

### Per-install actions

- **Login (root):** new session, types `proot-distro login <alias>`.
- **Login (user):** `proot-distro login <alias> --user <user>` (disabled
  when no user).
- **SSH start:** opens a dedicated session `<alias>-ssh` typing
  `exec proot-distro login <alias> -- /usr/sbin/sshd -D -e`. Because of
  `exec`, bash is replaced: when sshd exits (crash, bind failure, stop) the
  session ends, so **running = that session exists** is accurate.
- **SSH stop:** write Ctrl+C (`\u0003`) to that session; foreground `sshd -D`
  exits on SIGINT and the session ends. Do not use `finishIfRunning()`
  (SIGKILL to the top process leaves sshd alive via SIGHUP-reload).
- **SSH auto-start:** when Login is pressed and auto-start is on and no
  `<alias>-ssh` session exists, start it first.
- **Backup:** new session, `proot-distro backup <alias> --output
  ~/storage/downloads/<alias>-<date>.tar.gz` (falls back to `~/` when
  storage is not set up).
- **Delete:** confirm dialog naming the alias, then new session typing
  `proot-distro remove <alias> && rm -f <metadata file>`. On every list
  refresh, metadata files whose rootfs dir is gone and whose install
  session is not running are also removed.

### SSH authentication

- User password: `passwd <user>` (always offered when a user exists).
- Root password: `passwd root`, and `PermitRootLogin yes` in
  `/etc/ssh/sshd_config.d/termux.conf`.
- Root key only (no root password): `PermitRootLogin prohibit-password`.
- Public keys: the key text **never reaches the shell**. The app validates it
  and writes it to a staging file
  `~/.termux/linux-installs/<alias>.<root|user>.pub`; the typed chain only
  references that fixed path, copying it from the Termux side with
  `install -D -m 600 <staging> <rootfs>/<home>/.ssh/authorized_keys`
  (rootfs = `$PREFIX/var/lib/proot-distro/installed-rootfs/<alias>`), after
  the user was created. No chown needed: under proot-distro files appear
  root-owned, which sshd StrictModes accepts.
- Key validation: one line matching
  `^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)|sk-ssh-ed25519@openssh.com) [A-Za-z0-9+/]+={0,3}( [A-Za-z0-9@._-]{1,64})?$`.
- Interactive steps stay last: `passwd <user>` then `passwd root`. The chain
  is a single line already read by bash, so `passwd` reads fresh keyboard
  input only.
- Per-install actions: **Set password** offers user or root.

### Security

- Public keys: validated as above, passed via a staging file, never inline.
- Alias: `^[a-z][a-z0-9-]{0,31}$`. Username: `^[a-z_][a-z0-9_-]{0,31}$`,
  not `root`. Port: integer 1024–65535. Distro from a fixed enum.
- Invalid input → field error, Install disabled. No free text ever reaches
  the shell.
- Passwords are typed by the owner directly into the terminal.

## Left to the builder

- Layouts (RecyclerView/ListView, Material components already in deps).
- Exact quoting of the chain, handling of a session limit (`MAX_SESSIONS`)
  — show the existing "max terminals" dialog.
- Whether the new activity talks to `TermuxService` via binding or returns a
  result to `TermuxActivity` that creates the session.
- Unit tests for the command builder and input validation (pure Java).
