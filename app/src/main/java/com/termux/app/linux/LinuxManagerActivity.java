package com.termux.app.linux;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LinuxManagerActivity extends AppCompatActivity {

    private static final String LOG_TAG = "LinuxManagerActivity";

    private LinearLayout mDistrosContainer;
    private LinearLayout mServersContainer;
    private TextView mNoDistrosView;

    private final List<ServerItem> mServers = new ArrayList<>();
    private final Map<String, TextView> mStatusViews = new HashMap<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_linux_manager);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_linux_manager);
        }

        mDistrosContainer = findViewById(R.id.distros_container);
        mServersContainer = findViewById(R.id.servers_container);
        mNoDistrosView = findViewById(R.id.no_distros_text);

        findViewById(R.id.button_update_packages).setOnClickListener(v ->
            LinuxManagerUtils.runShellCommandInSession(this, getString(R.string.action_update_packages),
                "pkg update && pkg upgrade -y"));
        findViewById(R.id.button_install_proot_distro).setOnClickListener(v ->
            LinuxManagerUtils.runShellCommandInSession(this, getString(R.string.action_install_proot_distro),
                "pkg install proot-distro -y"));
        findViewById(R.id.button_setup_storage).setOnClickListener(v ->
            LinuxManagerUtils.runShellCommandInSession(this, getString(R.string.action_setup_storage),
                "termux-setup-storage"));

        findViewById(R.id.button_install_ubuntu).setOnClickListener(v ->
            LinuxManagerUtils.runInTerminalSession(this, "proot-distro install ubuntu",
                LinuxManagerUtils.PROOT_DISTRO_EXECUTABLE, "install", "ubuntu"));
        findViewById(R.id.button_install_xfce_vnc).setOnClickListener(v ->
            LinuxManagerUtils.runInTerminalSession(this, "XFCE + VNC",
                LinuxManagerUtils.PROOT_DISTRO_EXECUTABLE, "login", "ubuntu", "sh", "-c",
                "apt update && apt install -y xfce4 xfce4-goodies tigervnc-standalone-server dbus-x11"));
        findViewById(R.id.button_refresh).setOnClickListener(v -> refreshAll());

        findViewById(R.id.button_add_custom_server).setOnClickListener(v -> showAddCustomServerDialog());

        loadServers();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadServers() {
        mServers.clear();
        mServers.addAll(LinuxManagerUtils.getPredefinedServers(this));
        mServers.addAll(LinuxManagerUtils.loadCustomServers(this));
        buildServerRows();
    }

    private void refreshAll() {
        refreshDistros();
        refreshServerStatuses();
    }

    private void refreshDistros() {
        mExecutor.execute(() -> {
            final List<String> distros = LinuxManagerUtils.listInstalledDistros();
            mMainHandler.post(() -> buildDistroRows(distros));
        });
    }

    private void buildDistroRows(List<String> distros) {
        mDistrosContainer.removeAllViews();
        mNoDistrosView.setVisibility(distros.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (String distro : distros) {
            View row = inflater.inflate(R.layout.item_linux_distro, mDistrosContainer, false);
            ((TextView) row.findViewById(R.id.distro_name)).setText(distro);
            row.findViewById(R.id.button_login_distro).setOnClickListener(v ->
                LinuxManagerUtils.runInTerminalSession(this, "proot-distro login " + distro,
                    LinuxManagerUtils.PROOT_DISTRO_EXECUTABLE, "login", distro));
            row.findViewById(R.id.button_backup_distro).setOnClickListener(v ->
                LinuxManagerUtils.runInTerminalSession(this, "proot-distro backup " + distro,
                    LinuxManagerUtils.PROOT_DISTRO_EXECUTABLE, "backup", distro,
                    "--output", distro + "-backup.tar.gz"));
            row.findViewById(R.id.button_remove_distro).setOnClickListener(v ->
                showRemoveDistroDialog(distro));
            mDistrosContainer.addView(row);
        }
    }

    private void showRemoveDistroDialog(final String distro) {
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.msg_distro_remove_confirm, distro))
            .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                LinuxManagerUtils.runShellCommandInBackground(this, "proot-distro remove " + distro,
                    "proot-distro remove " + distro);
                mMainHandler.postDelayed(this::refreshDistros, 2000);
            })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    private void buildServerRows() {
        mServersContainer.removeAllViews();
        mStatusViews.clear();
        LayoutInflater inflater = getLayoutInflater();
        for (final ServerItem server : mServers) {
            View row = inflater.inflate(R.layout.item_linux_server, mServersContainer, false);
            ((TextView) row.findViewById(R.id.server_name)).setText(server.name);
            ((TextView) row.findViewById(R.id.server_description)).setText(server.description);
            TextView portView = row.findViewById(R.id.server_port);
            portView.setText(server.port > 0 ? getString(R.string.server_port_label, server.port) : "");
            TextView statusView = row.findViewById(R.id.server_status);
            mStatusViews.put(server.id, statusView);
            setStatusView(statusView, null);
            row.findViewById(R.id.button_start_server).setOnClickListener(v -> {
                LinuxManagerUtils.runShellCommandInBackground(this, server.name, server.startCommand);
                Toast.makeText(this, getString(R.string.msg_server_started, server.name), Toast.LENGTH_SHORT).show();
                mMainHandler.postDelayed(this::refreshServerStatuses, 1500);
            });
            row.findViewById(R.id.button_stop_server).setOnClickListener(v -> {
                LinuxManagerUtils.runShellCommandInBackground(this, server.name, server.stopCommand);
                Toast.makeText(this, getString(R.string.msg_server_stopped, server.name), Toast.LENGTH_SHORT).show();
                mMainHandler.postDelayed(this::refreshServerStatuses, 1500);
            });
            Button deleteButton = row.findViewById(R.id.button_delete_server);
            if (server.custom) {
                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> removeCustomServer(server));
            }
            mServersContainer.addView(row);
        }
    }

    private void refreshServerStatuses() {
        final List<ServerItem> snapshot = new ArrayList<>(mServers);
        mExecutor.execute(() -> {
            final Map<String, Boolean> statuses = new HashMap<>();
            for (ServerItem server : snapshot) {
                statuses.put(server.id, LinuxManagerUtils.isProcessRunning(this, server.processPattern));
            }
            mMainHandler.post(() -> {
                for (Map.Entry<String, Boolean> entry : statuses.entrySet()) {
                    TextView statusView = mStatusViews.get(entry.getKey());
                    if (statusView != null)
                        setStatusView(statusView, entry.getValue());
                }
            });
        });
    }

    private void setStatusView(TextView statusView, Boolean running) {
        if (running == null) {
            statusView.setText(R.string.server_status_unknown);
            statusView.setTextColor(Color.GRAY);
        } else if (running) {
            statusView.setText(R.string.server_status_running);
            statusView.setTextColor(getResources().getColor(R.color.linux_manager_status_running));
        } else {
            statusView.setText(R.string.server_status_stopped);
            statusView.setTextColor(getResources().getColor(R.color.linux_manager_status_stopped));
        }
    }

    private void removeCustomServer(final ServerItem server) {
        List<ServerItem> customServers = LinuxManagerUtils.loadCustomServers(this);
        List<ServerItem> remaining = new ArrayList<>();
        for (ServerItem item : customServers) {
            if (!item.id.equals(server.id))
                remaining.add(item);
        }
        LinuxManagerUtils.saveCustomServers(this, remaining);
        loadServers();
        refreshServerStatuses();
    }

    private void showAddCustomServerDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_custom_server, null);
        final EditText nameInput = dialogView.findViewById(R.id.input_server_name);
        final EditText portInput = dialogView.findViewById(R.id.input_server_port);
        final EditText startInput = dialogView.findViewById(R.id.input_server_start_command);
        final EditText stopInput = dialogView.findViewById(R.id.input_server_stop_command);
        final EditText patternInput = dialogView.findViewById(R.id.input_process_pattern);
        final EditText descriptionInput = dialogView.findViewById(R.id.input_server_description);

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_custom_server_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                String startCommand = startInput.getText().toString().trim();
                String stopCommand = stopInput.getText().toString().trim();
                String pattern = patternInput.getText().toString().trim();
                if (DataUtils.isNullOrEmpty(name) || DataUtils.isNullOrEmpty(startCommand) ||
                    DataUtils.isNullOrEmpty(stopCommand) || DataUtils.isNullOrEmpty(pattern)) {
                    Toast.makeText(this, R.string.error_custom_server_invalid, Toast.LENGTH_LONG).show();
                    return;
                }
                int port = 0;
                String portText = portInput.getText().toString().trim();
                if (!DataUtils.isNullOrEmpty(portText)) {
                    try {
                        port = Integer.parseInt(portText);
                    } catch (NumberFormatException e) {
                        Logger.logError(LOG_TAG, "Invalid port input: " + portText);
                    }
                }
                ServerItem server = new ServerItem("custom_" + System.currentTimeMillis(), name,
                    descriptionInput.getText().toString().trim(), port, startCommand, stopCommand,
                    pattern, true);
                List<ServerItem> customServers = LinuxManagerUtils.loadCustomServers(this);
                customServers.add(server);
                LinuxManagerUtils.saveCustomServers(this, customServers);
                Toast.makeText(this, R.string.msg_custom_server_added, Toast.LENGTH_SHORT).show();
                loadServers();
                refreshServerStatuses();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

}
