package com.termux.app.linux;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public final class LinuxManagerUtils {

    private static final String LOG_TAG = "LinuxManagerUtils";

    public static final String PREF_KEY_CUSTOM_SERVERS = "linux_manager_custom_servers";

    public static final String PROOT_DISTRO_EXECUTABLE = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/proot-distro";
    public static final String SHELL_EXECUTABLE = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh";
    public static final String PGREP_EXECUTABLE = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/pgrep";
    public static final String PROOT_DISTRO_INSTALLED_DIR_PATH = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/proot-distro/installed";

    private LinuxManagerUtils() {
    }

    public static void runInTerminalSession(@NonNull Context context, @NonNull String label,
                                            @NonNull String executable, String... arguments) {
        Uri uri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(executable).build();
        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, uri);
        intent.setClass(context, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arguments);
        intent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.TERMINAL_SESSION.getName());
        intent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, label);
        startExecutionIntent(context, intent);
    }

    public static void runShellCommandInSession(@NonNull Context context, @NonNull String label,
                                                @NonNull String shellCommand) {
        runInTerminalSession(context, label, SHELL_EXECUTABLE, "-c", shellCommand);
    }

    public static void runShellCommandInBackground(@NonNull Context context, @NonNull String label,
                                                   @NonNull String shellCommand) {
        Uri uri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(SHELL_EXECUTABLE).build();
        Intent intent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, uri);
        intent.setClass(context, TermuxService.class);
        intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-c", shellCommand});
        intent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        intent.putExtra(TERMUX_SERVICE.EXTRA_COMMAND_LABEL, label);
        startExecutionIntent(context, intent);
    }

    private static void startExecutionIntent(@NonNull Context context, @NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static boolean isProcessRunning(@NonNull Context context, @NonNull String pattern) {
        try {
            HashMap<String, String> environment = new TermuxShellEnvironment().getEnvironment(context, false);
            List<String> environ = ShellEnvironmentUtils.convertEnvironmentToEnviron(environment);
            Process process = Runtime.getRuntime().exec(new String[]{PGREP_EXECUTABLE, "-f", pattern},
                environ.toArray(new String[0]), new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
            return process.waitFor() == 0;
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to check process for pattern \"" + pattern + "\": " + e.getMessage());
            return false;
        }
    }

    @NonNull
    public static List<String> listInstalledDistros() {
        List<String> distros = new ArrayList<>();
        File[] files = new File(PROOT_DISTRO_INSTALLED_DIR_PATH).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory())
                    distros.add(file.getName());
            }
        }
        Collections.sort(distros);
        return distros;
    }

    @NonNull
    public static List<ServerItem> getPredefinedServers(@NonNull Context context) {
        List<ServerItem> servers = new ArrayList<>();
        servers.add(new ServerItem("ssh", context.getString(R.string.server_name_ssh),
            context.getString(R.string.server_desc_ssh), 8022,
            "ssh-keygen -A >/dev/null 2>&1; exec sshd -D -e", "pkill -x sshd", "sshd", false));
        servers.add(new ServerItem("http_python", context.getString(R.string.server_name_http_python),
            context.getString(R.string.server_desc_http_python), 8080,
            "exec python3 -m http.server 8080", "pkill -f 'http.server 8080'", "http.server 8080", false));
        servers.add(new ServerItem("http_busybox", context.getString(R.string.server_name_http_busybox),
            context.getString(R.string.server_desc_http_busybox), 8081,
            "exec busybox httpd -f -p 8081", "pkill -f 'busybox httpd'", "busybox httpd", false));
        servers.add(new ServerItem("vnc", context.getString(R.string.server_name_vnc),
            context.getString(R.string.server_desc_vnc), 5901,
            "proot-distro login ubuntu vncserver -localhost no :1",
            "proot-distro login ubuntu vncserver -kill :1", "Xvnc", false));
        return servers;
    }

    @NonNull
    public static List<ServerItem> loadCustomServers(@NonNull Context context) {
        List<ServerItem> servers = new ArrayList<>();
        String json = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY_CUSTOM_SERVERS, null);
        if (DataUtils.isNullOrEmpty(json)) return servers;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                servers.add(ServerItem.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Logger.logError(LOG_TAG, "Failed to load custom servers: " + e.getMessage());
        }
        return servers;
    }

    public static void saveCustomServers(@NonNull Context context, @NonNull List<ServerItem> customServers) {
        JSONArray array = new JSONArray();
        for (ServerItem server : customServers) {
            try {
                array.put(server.toJson());
            } catch (JSONException e) {
                Logger.logError(LOG_TAG, "Failed to serialize custom server: " + e.getMessage());
            }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_KEY_CUSTOM_SERVERS, array.toString()).apply();
    }

    @Nullable
    public static ServerItem findServerById(@NonNull List<ServerItem> servers, @NonNull String id) {
        for (ServerItem server : servers) {
            if (server.id.equals(id)) return server;
        }
        return null;
    }

}
