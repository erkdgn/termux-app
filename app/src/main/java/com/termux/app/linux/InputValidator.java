package com.termux.app.linux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Strict validation of user input. Only values accepted here may reach shell command strings.
 */
public final class InputValidator {

    private static final Pattern ALIAS = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern USER = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");
    private static final Pattern HOSTNAME = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern PACKAGE = Pattern.compile("^[a-z0-9][a-z0-9+.-]{0,63}$");
    private static final Pattern KEY = Pattern.compile(
        "^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)|sk-ssh-ed25519@openssh\\.com)"
            + " [A-Za-z0-9+/]+={0,3}( [A-Za-z0-9@._-]{1,64})?$");

    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;
    public static final int DEFAULT_PORT = 2022;
    public static final int MAX_EXTRA_PACKAGES = 30;

    public enum Error {
        ALIAS, USER, PORT, ROOT_KEY, USER_KEY, SSH_NO_ACCESS, USER_KEY_WITHOUT_USER, SUDO_WITHOUT_USER,
        HOSTNAME, PACKAGES, VERSION, TIMEZONE
    }

    private InputValidator() {}

    // Matcher.matches() requires the whole string to match, so a trailing newline is already
    // rejected; the explicit CR/LF check is defence in depth against future pattern changes.
    private static boolean matches(Pattern p, String s) {
        return s != null && s.indexOf('\n') < 0 && s.indexOf('\r') < 0 && p.matcher(s).matches();
    }

    public static boolean isValidAlias(String alias) {
        return matches(ALIAS, alias);
    }

    public static boolean isValidUser(String user) {
        return matches(USER, user) && !"root".equals(user);
    }

    public static boolean isValidHostname(String hostname) {
        return matches(HOSTNAME, hostname);
    }

    public static boolean isValidPackage(String pkg) {
        return matches(PACKAGE, pkg);
    }

    /**
     * Splits on whitespace. Returns an empty list for blank input, or null if any token is invalid
     * or there are more than {@link #MAX_EXTRA_PACKAGES}. Duplicates are kept.
     */
    public static List<String> parsePackages(String raw) {
        if (raw == null) return Collections.emptyList();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length > MAX_EXTRA_PACKAGES) return null;
        List<String> out = new ArrayList<>(tokens.length);
        for (String t : tokens) {
            if (!isValidPackage(t)) return null;
            out.add(t);
        }
        return out;
    }

    /** Whitelist for password-label text: letters, digits, space, '=', ':', '.', '-', '_'. */
    public static boolean isSafeLabel(String label) {
        if (label == null || label.isEmpty()) return false;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            boolean ok = Character.isLetter(c) || (c >= '0' && c <= '9') || c == ' ' || c == '='
                || c == ':' || c == '.' || c == '-' || c == '_' || c == '(' || c == ')';
            if (!ok) return false;
        }
        return true;
    }

    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    /** Returns the trimmed single-line key, "" for empty input, or null if invalid. */
    public static String normalizeKey(String raw) {
        if (raw == null) return "";
        String key = raw.trim();
        if (key.isEmpty()) return "";
        return matches(KEY, key) ? key : null;
    }

    public static EnumSet<Error> validate(InstallOptions o) {
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        if (!isValidAlias(o.alias)) errors.add(Error.ALIAS);
        if (o.hasUser() && !isValidUser(o.user)) errors.add(Error.USER);
        if (o.ssh && !isValidPort(o.sshPort)) errors.add(Error.PORT);
        if (!keyOk(o.rootKey)) errors.add(Error.ROOT_KEY);
        if (!keyOk(o.userKey)) errors.add(Error.USER_KEY);
        if (!o.userKey.isEmpty() && !o.hasUser()) errors.add(Error.USER_KEY_WITHOUT_USER);
        if (o.sudo && !o.hasUser()) errors.add(Error.SUDO_WITHOUT_USER);
        if (o.ssh && !o.hasUser() && !o.rootPassword && o.rootKey.isEmpty() && o.userKey.isEmpty()) {
            errors.add(Error.SSH_NO_ACCESS);
        }
        if (!o.hostname.isEmpty() && !isValidHostname(o.hostname)) errors.add(Error.HOSTNAME);
        if (o.extraPackages.size() > MAX_EXTRA_PACKAGES) errors.add(Error.PACKAGES);
        else for (String p : o.extraPackages) {
            if (!isValidPackage(p)) {
                errors.add(Error.PACKAGES);
                break;
            }
        }
        if (o.version.distro() != o.distro) errors.add(Error.VERSION);
        if (!o.timezone.isEmpty() && !Timezones.isKnown(o.timezone)) errors.add(Error.TIMEZONE);
        return errors;
    }

    public static EnumSet<Error> validate(EditInfo e) {
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        if (!e.user.isEmpty() && !isValidUser(e.user)) errors.add(Error.USER);
        if (!e.hostname.isEmpty() && !isValidHostname(e.hostname)) errors.add(Error.HOSTNAME);
        if (e.ssh && !isValidPort(e.sshPort)) errors.add(Error.PORT);
        if (e.version != null && e.version.distro() != e.distro) errors.add(Error.VERSION);
        return errors;
    }

    private static boolean keyOk(String stored) {
        if (stored.isEmpty()) return true;
        String n = normalizeKey(stored);
        return n != null && n.equals(stored);
    }
}
