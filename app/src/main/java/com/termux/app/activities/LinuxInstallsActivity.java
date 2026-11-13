package com.termux.app.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.linux.CommandBuilder;
import com.termux.app.linux.InputValidator;
import com.termux.app.linux.InstallInfo;
import com.termux.app.linux.InstallOptions;
import com.termux.app.linux.InstallStore;
import com.termux.app.linux.LinuxPaths;
import com.termux.app.linux.SessionNames;
import com.termux.app.linux.SessionRequest;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.theme.NightMode;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists the Linux (proot-distro) installs and turns the user's choices into {@link SessionRequest}s.
 * Started with startActivityForResult() and {@link SessionRequest#EXTRA_OPEN_SESSIONS}; finishes with
 * RESULT_OK and {@link SessionRequest#EXTRA_REQUESTS}, or RESULT_CANCELED.
 */
public class LinuxInstallsActivity extends AppCompatActivity {

    private static final int REQUEST_NEW_INSTALL = 1;

    private static final int MENU_SSH_START = 1;
    private static final int MENU_SSH_STOP = 2;
    private static final int MENU_SET_PASSWORD = 3;
    private static final int MENU_BACKUP = 4;
    private static final int MENU_DELETE = 5;

    private InstallStore mStore;
    private CommandBuilder mCommands;
    private final Set<String> mOpenSessions = new HashSet<>();
    private final List<InstallInfo> mInstalls = new ArrayList<>();
    private InstallsAdapter mAdapter;
    private View mEmptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_linux_installs);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.linux_installs_title);

        LinuxPaths paths = LinuxPaths.defaults();
        mStore = new InstallStore(paths);
        mCommands = new CommandBuilder(paths);

        // Snapshot taken when the page opens; the page is modal, so it cannot go stale while shown.
        String[] open = getIntent().getStringArrayExtra(SessionRequest.EXTRA_OPEN_SESSIONS);
        if (open != null) mOpenSessions.addAll(Arrays.asList(open));

        ListView list = findViewById(R.id.linux_installs_list);
        mEmptyView = findViewById(R.id.linux_installs_empty);
        mAdapter = new InstallsAdapter();
        list.setAdapter(mAdapter);

        findViewById(R.id.linux_new_install_button).setOnClickListener(v ->
            startActivityForResult(new Intent(this, LinuxInstallFormActivity.class), REQUEST_NEW_INSTALL));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mInstalls.clear();
        mInstalls.addAll(mStore.scan(mOpenSessions));
        mAdapter.notifyDataSetChanged();
        mEmptyView.setVisibility(mInstalls.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_NEW_INSTALL || resultCode != RESULT_OK) return;

        InstallOptions o = LinuxInstallFormActivity.optionsFromIntent(data);
        // Defence in depth: the form already validated, but the state may have changed meanwhile.
        if (o == null || !InputValidator.validate(o).isEmpty() || mStore.isAliasTaken(o.alias)
            || mOpenSessions.contains(SessionNames.install(o.alias))) {
            Toast.makeText(this, R.string.linux_install_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            mStore.save(o);
        } catch (IOException e) {
            Toast.makeText(this, R.string.linux_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // The install runs in a reserved session name; InstallStore.scan relies on that.
        finishWith(Collections.singletonList(SessionRequest.open(SessionNames.install(o.alias), mCommands.install(o))));
    }

    private void finishWith(List<SessionRequest> requests) {
        Intent result = new Intent();
        result.putExtra(SessionRequest.EXTRA_REQUESTS, SessionRequest.encode(requests));
        setResult(RESULT_OK, result);
        finish();
    }

    private void login(InstallInfo info) {
        List<SessionRequest> requests = new ArrayList<>();
        Set<String> taken = new HashSet<>(mOpenSessions);
        addSshAutoStart(info, requests, taken);
        requests.add(SessionRequest.open(SessionNames.firstFree(info.alias, taken), mCommands.login(info.alias)));
        finishWith(requests);
    }

    private void loginUser(InstallInfo info) {
        if (!InputValidator.isValidUser(info.user)) return;
        List<SessionRequest> requests = new ArrayList<>();
        Set<String> taken = new HashSet<>(mOpenSessions);
        addSshAutoStart(info, requests, taken);
        requests.add(SessionRequest.open(SessionNames.firstFree(info.alias, taken),
            mCommands.loginUser(info.alias, info.user)));
        finishWith(requests);
    }

    /** Prepends the sshd session request when SSH auto-start is on and its session is not running. */
    private void addSshAutoStart(InstallInfo info, List<SessionRequest> requests, Set<String> taken) {
        String sshName = SessionNames.ssh(info.alias);
        if (info.ssh && info.sshAutoStart && !mOpenSessions.contains(sshName)) {
            requests.add(SessionRequest.open(sshName, mCommands.sshStart(info.alias)));
            taken.add(sshName);
        }
    }

    private void openAction(InstallInfo info, String input) {
        finishWith(Collections.singletonList(
            SessionRequest.open(SessionNames.firstFree(info.alias, mOpenSessions), input)));
    }

    private void showMenu(InstallInfo info, View anchor) {
        String sshName = SessionNames.ssh(info.alias);
        boolean sshOpen = mOpenSessions.contains(sshName);

        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        if (info.status == InstallInfo.Status.READY) {
            if (info.ssh) {
                menu.add(Menu.NONE, sshOpen ? MENU_SSH_STOP : MENU_SSH_START, Menu.NONE,
                    sshOpen ? R.string.linux_ssh_stop : R.string.linux_ssh_start);
            }
            menu.add(Menu.NONE, MENU_SET_PASSWORD, Menu.NONE, R.string.linux_set_password);
            menu.add(Menu.NONE, MENU_BACKUP, Menu.NONE, R.string.linux_backup);
        }
        menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.linux_delete);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_SSH_START:
                    finishWith(Collections.singletonList(
                        SessionRequest.open(sshName, mCommands.sshStart(info.alias))));
                    return true;
                case MENU_SSH_STOP:
                    finishWith(Collections.singletonList(
                        SessionRequest.send(sshName, CommandBuilder.SSH_STOP)));
                    return true;
                case MENU_SET_PASSWORD:
                    chooseAccountForPassword(info);
                    return true;
                case MENU_BACKUP:
                    openAction(info, mCommands.backup(info.alias, downloadsAvailable(), timestamp()));
                    return true;
                case MENU_DELETE:
                    confirmDelete(info);
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void chooseAccountForPassword(InstallInfo info) {
        if (!InputValidator.isValidUser(info.user)) {
            openAction(info, mCommands.setPassword(info.alias, "root"));
            return;
        }
        final String[] accounts = {info.user, "root"};
        new AlertDialog.Builder(this)
            .setTitle(R.string.linux_password_for_title)
            .setItems(accounts, (dialog, which) ->
                openAction(info, mCommands.setPassword(info.alias, accounts[which])))
            .setNegativeButton(R.string.linux_cancel, null)
            .show();
    }

    private void confirmDelete(InstallInfo info) {
        for (String name : mOpenSessions) {
            if (SessionNames.belongsTo(name, info.alias)) {
                Toast.makeText(this, R.string.linux_close_sessions_first, Toast.LENGTH_LONG).show();
                return;
            }
        }
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.linux_delete_confirm, info.alias))
            .setPositiveButton(R.string.linux_delete, (dialog, which) ->
                openAction(info, mCommands.remove(info.alias)))
            .setNegativeButton(R.string.linux_cancel, null)
            .show();
    }

    private static boolean downloadsAvailable() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, "storage/downloads").isDirectory();
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    private class InstallsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mInstalls.size();
        }

        @Override
        public InstallInfo getItem(int position) {
            return mInstalls.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                : LayoutInflater.from(LinuxInstallsActivity.this).inflate(R.layout.item_linux_install, parent, false);
            InstallInfo info = getItem(position);

            ((TextView) row.findViewById(R.id.linux_item_name)).setText(info.alias);

            StringBuilder details = new StringBuilder();
            details.append(info.distro != null ? info.distro.label() : getString(R.string.linux_unknown_distro));
            if (InputValidator.isValidUser(info.user)) details.append(" · ").append(info.user);
            if (info.ssh) details.append(" · ").append(getString(R.string.linux_ssh_port_label, info.sshPort));
            ((TextView) row.findViewById(R.id.linux_item_details)).setText(details);

            TextView status = row.findViewById(R.id.linux_item_status);
            if (info.status == InstallInfo.Status.INCOMPLETE) {
                status.setText(info.failed ? R.string.linux_status_install_failed : R.string.linux_status_incomplete);
                status.setVisibility(View.VISIBLE);
            } else if (info.status == InstallInfo.Status.NOT_FOUND) {
                status.setText(info.failed ? R.string.linux_status_install_failed : R.string.linux_status_not_found);
                status.setVisibility(View.VISIBLE);
            } else if (info.status == InstallInfo.Status.INSTALLING) {
                status.setText(R.string.linux_status_installing);
                status.setVisibility(View.VISIBLE);
            } else {
                status.setVisibility(View.GONE);
            }

            // NOT_FOUND is treated like INSTALLING until the detail page handles it: no actions.
            boolean installing = info.status == InstallInfo.Status.INSTALLING
                || info.status == InstallInfo.Status.NOT_FOUND;

            Button login = row.findViewById(R.id.linux_item_login);
            login.setEnabled(!installing);
            login.setOnClickListener(v -> login(info));

            Button loginUser = row.findViewById(R.id.linux_item_login_user);
            loginUser.setEnabled(!installing && InputValidator.isValidUser(info.user));
            loginUser.setOnClickListener(v -> loginUser(info));

            ImageButton menu = row.findViewById(R.id.linux_item_menu);
            menu.setEnabled(!installing);
            menu.setVisibility(installing ? View.INVISIBLE : View.VISIBLE);
            menu.setOnClickListener(v -> showMenu(info, v));

            return row;
        }
    }
}
