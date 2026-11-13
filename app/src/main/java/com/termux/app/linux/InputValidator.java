package com.termux.app.linux;

import java.util.EnumSet;
import java.util.regex.Pattern;

/**
 * Strict validation of user input. Only values accepted here may reach shell command strings.
 */
public final class InputValidator {

    private static final Pattern ALIAS = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern USER = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");
    private static final Pattern KEY = Pattern.compile(
        "^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)|sk-ssh-ed25519@openssh\\.com)"
            + " [A-Za-z0-9+/]+={0,3}( [A-Za-z0-9@._-]{1,64})?$");

    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;
    public static final int DEFAULT_PORT = 2022;

    public enum Error {
        ALIAS, USER, PORT, ROOT_KEY, USER_KEY, SSH_NO_ACCESS, USER_KEY_WITHOUT_USER, SUDO_WITHOUT_USER
    }

    private InputValidator() {}

    // Pattern "$" matches before a trailing line terminator, so use matches() on a pattern with
    // explicit checks: reject any string containing CR or LF first.
    private static boolean matches(Pattern p, String s) {
        return s != null && s.indexOf('\n') < 0 && s.indexOf('\r') < 0 && p.matcher(s).matches();
    }

    public static boolean isValidAlias(String alias) {
        return matches(ALIAS, alias);
    }

    public static boolean isValidUser(String user) {
        return matches(USER, user) && !"root".equals(user);
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
        return errors;
    }

    private static boolean keyOk(String stored) {
        if (stored.isEmpty()) return true;
        String n = normalizeKey(stored);
        return n != null && n.equals(stored);
    }
}
