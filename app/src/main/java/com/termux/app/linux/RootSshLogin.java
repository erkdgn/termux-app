package com.termux.app.linux;

/** Value of sshd PermitRootLogin. */
public enum RootSshLogin {
    NO("no"),
    KEY_ONLY("prohibit-password"),
    PASSWORD("yes");

    private final String mSshdValue;

    RootSshLogin(String sshdValue) {
        mSshdValue = sshdValue;
    }

    public String sshdValue() {
        return mSshdValue;
    }

    /** Returns the constant with the given enum name, or null if unknown. */
    public static RootSshLogin fromName(String name) {
        if (name == null) return null;
        for (RootSshLogin r : values()) {
            if (r.name().equals(name)) return r;
        }
        return null;
    }
}
