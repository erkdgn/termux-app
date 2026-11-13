package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.linux.CommandBuilder;
import com.termux.app.linux.HostAddresses;
import com.termux.app.linux.InputValidator;
import com.termux.app.linux.InstallInfo;
import com.termux.app.linux.InstallStore;
import com.termux.app.linux.LinuxPaths;
import com.termux.app.linux.PackageGroup;
import com.termux.app.linux.SessionNames;
import com.termux.app.linux.SessionRequest;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detail page of one Linux install: status, SSH connection commands and every action on it.
 * Started with {@link #EXTRA_ALIAS} and {@link SessionRequest#EXTRA_OPEN_SESSIONS}; finishes with
 * RESULT_OK and {@link SessionRequest#EXTRA_REQUESTS}, or RESULT_FIRST_USER meaning "the list
 * changed, refresh it".
 */
public class LinuxInstallDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ALIAS = "com.termux.linux.extra.ALIAS";

    /** Labels of the package groups, one per {@link PackageGroup} in enum order. */
    private static final int[] GROUP_LABELS = {
        R.string.linux_group_dev, R.string.linux_group_python, R.string.linux_group_nodejs,
        R.string.linux_group_nginx, R.string.linux_group_postgresql, R.string.linux_group_mariadb
    };

    private static final int REQUEST_EDIT = 1;

    private InstallStore mStore;
    private CommandBuilder mCommands;
    private final Set<String> mOpenSessions = new HashSet<>();
    private String mAlias;
    private InstallInfo mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_linux_install_detail);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        LinuxPaths paths = LinuxPaths.defaults();
        mStore = new InstallStore(paths);
        mCommands = LinuxActivityHelper.commands(this, paths);

        mAlias = getIntent().getStringExtra(EXTRA_ALIAS);
        String[] open = getIntent().getStringArrayExtra(SessionRequest.EXTRA_OPEN_SESSIONS);
        if (open != null) mOpenSessions.addAll(Arrays.asList(open));
        setTitle(mAlias);
    }

    @Override
    protected void onResume() {
        super.onResume();
        InstallInfo found = null;
        if (mAlias != null) {
            for (InstallInfo i : mStore.scan(mOpenSessions)) {
                if (i.alias.equals(mAlias)) {
                    found = i;
                    break;
                }
            }
        }
        if (found == null) {
            Toast.makeText(this, R.string.linux_install_not_found, Toast.LENGTH_LONG).show();
            setResult(RESULT_FIRST_USER);
            finish();
            return;
        }
        mInfo = found;
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EDIT) return;
        if (resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        } else if (resultCode == RESULT_FIRST_USER) {
            // Saved without a command to run: let the list refresh.
            setResult(RESULT_FIRST_USER);
            finish();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void render() {
        final InstallInfo info = mInfo;
        boolean ready = info.status == InstallInfo.Status.READY;
        boolean usable = ready || info.status == InstallInfo.Status.INCOMPLETE;
        boolean notFound = info.status == InstallInfo.Status.NOT_FOUND;
        boolean validUser = InputValidator.isValidUser(info.user);
        String sshName = SessionNames.ssh(info.alias);
        boolean sshOpen = mOpenSessions.contains(sshName);

        StringBuilder summary = new StringBuilder();
        summary.append(info.distro != null
            ? (info.version != null ? info.version.label() : info.distro.label())
            : getString(R.string.linux_unknown_distro));
        summary.append(" · ").append(getString(statusLabel(info)));
        ((TextView) findViewById(R.id.linux_detail_summary)).setText(summary);

        TextView userHost = findViewById(R.id.linux_detail_user_host);
        StringBuilder uh = new StringBuilder();
        if (validUser) uh.append(getString(R.string.linux_user_label, info.user));
        if (!info.hostname.isEmpty()) {
            if (uh.length() > 0) uh.append(" · ");
            uh.append(getString(R.string.linux_host_label, info.hostname));
        }
        userHost.setText(uh);
        userHost.setVisibility(uh.length() > 0 ? View.VISIBLE : View.GONE);

        TextView ssh = findViewById(R.id.linux_detail_ssh);
        ssh.setText(getString(R.string.linux_ssh_port_label, info.sshPort) + " ("
            + getString(sshOpen ? R.string.linux_ssh_running : R.string.linux_ssh_stopped) + ")");
        ssh.setVisibility(info.ssh ? View.VISIBLE : View.GONE);

        findViewById(R.id.linux_detail_banner).setVisibility(info.packagesFailed ? View.VISIBLE : View.GONE);

        renderConnection(info);

        Button login = findViewById(R.id.linux_detail_login);
        login.setVisibility(usable ? View.VISIBLE : View.GONE);
        login.setOnClickListener(v -> LinuxActivityHelper.finishWith(this, LinuxActivityHelper.loginRequests(
            mCommands, info, mOpenSessions, mCommands.login(info.alias, info.hostname))));

        Button loginUser = findViewById(R.id.linux_detail_login_user);
        loginUser.setVisibility(usable && validUser ? View.VISIBLE : View.GONE);
        loginUser.setOnClickListener(v -> LinuxActivityHelper.finishWith(this, LinuxActivityHelper.loginRequests(
            mCommands, info, mOpenSessions, mCommands.loginUser(info.alias, info.hostname, info.user))));

        Button sshToggle = findViewById(R.id.linux_detail_ssh_toggle);
        sshToggle.setVisibility(usable && info.ssh ? View.VISIBLE : View.GONE);
        sshToggle.setText(sshOpen ? R.string.linux_ssh_stop : R.string.linux_ssh_start);
        sshToggle.setOnClickListener(v -> LinuxActivityHelper.finishWith(this, Collections.singletonList(sshOpen
            ? SessionRequest.send(sshName, CommandBuilder.SSH_STOP)
            : SessionRequest.open(sshName, mCommands.sshStart(info.alias, info.hostname)))));

        bind(R.id.linux_detail_update, usable, v ->
            openAction(mCommands.update(info.alias, info.hostname)));
        bind(R.id.linux_detail_install_packages, usable, v -> showInstallPackagesDialog());
        bind(R.id.linux_detail_set_password, usable, v -> chooseAccountForPassword());
        bind(R.id.linux_detail_backup, usable, v ->
            openAction(mCommands.backup(info.alias, LinuxActivityHelper.downloadsAvailable(),
                LinuxActivityHelper.timestamp())));
        bind(R.id.linux_detail_delete, usable, v -> confirmDelete());
        bind(R.id.linux_detail_remove_from_list, notFound, v -> confirmRemoveFromList());

        bind(R.id.linux_detail_edit_info, usable, v -> {
            Intent edit = new Intent(this, LinuxInstallEditActivity.class);
            edit.putExtra(LinuxInstallEditActivity.EXTRA_ALIAS, info.alias);
            edit.putExtra(SessionRequest.EXTRA_OPEN_SESSIONS, mOpenSessions.toArray(new String[0]));
            startActivityForResult(edit, REQUEST_EDIT);
        });
    }

    private void bind(int id, boolean visible, View.OnClickListener listener) {
        View v = findViewById(id);
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
        v.setOnClickListener(listener);
    }

    private static int statusLabel(InstallInfo info) {
        switch (info.status) {
            case READY:
                return R.string.linux_status_ready;
            case INSTALLING:
                return R.string.linux_status_installing;
            case NOT_FOUND:
                return info.failed ? R.string.linux_status_install_failed : R.string.linux_status_not_found;
            default:
                return info.failed ? R.string.linux_status_install_failed : R.string.linux_status_incomplete;
        }
    }

    private void renderConnection(InstallInfo info) {
        View section = findViewById(R.id.linux_detail_connection_section);
        section.setVisibility(info.ssh && info.status != InstallInfo.Status.NOT_FOUND ? View.VISIBLE : View.GONE);
        LinearLayout list = findViewById(R.id.linux_detail_connection_list);
        list.removeAllViews();
        List<String> ips = HostAddresses.ordered(HostAddresses.fromSystem());
        findViewById(R.id.linux_detail_no_network).setVisibility(ips.isEmpty() ? View.VISIBLE : View.GONE);
        for (String ip : ips) {
            final String command = CommandBuilder.displaySshCommand(
                InputValidator.isValidUser(info.user) ? info.user : "", info.sshPort, ip);
            View row = LayoutInflater.from(this).inflate(R.layout.item_linux_connection, list, false);
            ((TextView) row.findViewById(R.id.linux_connection_command)).setText(command);
            row.findViewById(R.id.linux_connection_copy).setOnClickListener(v -> copy(command));
            list.addView(row);
        }
    }

    private void copy(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("ssh", text));
        Toast.makeText(this, R.string.linux_copied, Toast.LENGTH_SHORT).show();
    }

    private void openAction(String input) {
        LinuxActivityHelper.finishWith(this, Collections.singletonList(
            SessionRequest.open(SessionNames.firstFree(mInfo.alias, mOpenSessions), input)));
    }

    private void chooseAccountForPassword() {
        final InstallInfo info = mInfo;
        if (!InputValidator.isValidUser(info.user)) {
            openAction(mCommands.setPassword(info.alias, info.hostname, "root"));
            return;
        }
        final String[] accounts = {info.user, "root"};
        new AlertDialog.Builder(this)
            .setTitle(R.string.linux_password_for_title)
            .setItems(accounts, (dialog, which) ->
                openAction(mCommands.setPassword(info.alias, info.hostname, accounts[which])))
            .setNegativeButton(R.string.linux_cancel, null)
            .show();
    }

    private void confirmDelete() {
        final InstallInfo info = mInfo;
        for (String name : mOpenSessions) {
            if (SessionNames.belongsTo(name, info.alias)) {
                Toast.makeText(this, R.string.linux_close_sessions_first, Toast.LENGTH_LONG).show();
                return;
            }
        }
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.linux_delete_confirm, info.alias))
            .setPositiveButton(R.string.linux_delete, (dialog, which) ->
                openAction(mCommands.remove(info.alias)))
            .setNegativeButton(R.string.linux_cancel, null)
            .show();
    }

    private void confirmRemoveFromList() {
        final InstallInfo info = mInfo;
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.linux_remove_from_list_confirm, info.alias))
            .setPositiveButton(R.string.linux_remove_from_list, (dialog, which) -> {
                mStore.forget(info.alias);
                setResult(RESULT_FIRST_USER);
                finish();
            })
            .setNegativeButton(R.string.linux_cancel, null)
            .show();
    }

    private void showInstallPackagesDialog() {
        final InstallInfo info = mInfo;
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_linux_install_packages, null);
        LinearLayout groupsView = content.findViewById(R.id.linux_pkg_dialog_groups);
        final EditText extra = content.findViewById(R.id.linux_pkg_dialog_extra);
        final PackageGroup[] groups = PackageGroup.values();
        final CheckBox[] checks = new CheckBox[groups.length];
        for (int i = 0; i < groups.length; i++) {
            checks[i] = new CheckBox(this);
            checks[i].setText(GROUP_LABELS[i]);
            groupsView.addView(checks[i]);
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.linux_install_packages)
            .setView(content)
            .setPositiveButton(R.string.linux_install, null)
            .setNegativeButton(R.string.linux_cancel, null)
            .create();

        final Runnable refresh = () -> {
            List<String> parsed = InputValidator.parsePackages(extra.getText().toString());
            boolean invalid = parsed == null;
            extra.setError(invalid ? getString(R.string.linux_error_packages) : null);
            boolean any = false;
            for (CheckBox c : checks) any |= c.isChecked();
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) ok.setEnabled(!invalid && (any || !parsed.isEmpty()));
        };
        for (CheckBox c : checks) c.setOnCheckedChangeListener((b, checked) -> refresh.run());
        extra.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        });

        dialog.setOnShowListener(d -> {
            refresh.run();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<String> parsed = InputValidator.parsePackages(extra.getText().toString());
                if (parsed == null) return;
                EnumSet<PackageGroup> selected = EnumSet.noneOf(PackageGroup.class);
                for (int i = 0; i < groups.length; i++) {
                    if (checks[i].isChecked()) selected.add(groups[i]);
                }
                if (selected.isEmpty() && parsed.isEmpty()) return;
                dialog.dismiss();
                openAction(mCommands.installPackages(info.alias, info.hostname, selected, new ArrayList<>(parsed)));
            });
        });
        dialog.show();
    }
}
