package com.termux.app.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.termux.R;
import com.termux.app.linux.CommandBuilder;
import com.termux.app.linux.InstallInfo;
import com.termux.app.linux.LinuxPaths;
import com.termux.app.linux.SessionNames;
import com.termux.app.linux.SessionRequest;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pieces shared by the Linux installs list and detail activities. */
final class LinuxActivityHelper {

    private LinuxActivityHelper() {}

    static CommandBuilder commands(Context context, LinuxPaths paths) {
        return new CommandBuilder(paths, context.getString(R.string.linux_password_label_user),
            context.getString(R.string.linux_password_label_root));
    }

    /** Finishes with RESULT_OK and the encoded session requests. */
    static void finishWith(Activity activity, List<SessionRequest> requests) {
        Intent result = new Intent();
        result.putExtra(SessionRequest.EXTRA_REQUESTS, SessionRequest.encode(requests));
        activity.setResult(Activity.RESULT_OK, result);
        activity.finish();
    }

    /**
     * Requests for a login session: the sshd session first when SSH auto-start is on and it is not
     * running, then the login itself.
     *
     * @param loginCommand the already built login command line
     */
    static List<SessionRequest> loginRequests(CommandBuilder commands, InstallInfo info,
                                              Set<String> openSessions, String loginCommand) {
        List<SessionRequest> requests = new ArrayList<>();
        Set<String> taken = new HashSet<>(openSessions);
        String sshName = SessionNames.ssh(info.alias);
        if (info.ssh && info.sshAutoStart && !openSessions.contains(sshName)) {
            requests.add(SessionRequest.open(sshName, commands.sshStart(info.alias, info.hostname)));
            taken.add(sshName);
        }
        requests.add(SessionRequest.open(SessionNames.firstFree(info.alias, taken), loginCommand));
        return requests;
    }

    static boolean downloadsAvailable() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "storage/downloads").isDirectory();
    }

    static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }
}
