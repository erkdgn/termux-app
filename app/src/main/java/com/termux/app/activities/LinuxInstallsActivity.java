package com.termux.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
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
import com.termux.shared.theme.NightMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists the Linux (proot-distro) installs and turns the user's choices into {@link SessionRequest}s.
 * Started with startActivityForResult() and {@link SessionRequest#EXTRA_OPEN_SESSIONS}; finishes with
 * RESULT_OK and {@link SessionRequest#EXTRA_REQUESTS}, or RESULT_CANCELED.
 */
public class LinuxInstallsActivity extends AppCompatActivity {

    private static final int REQUEST_NEW_INSTALL = 1;
    private static final int REQUEST_DETAIL = 2;

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
        mCommands = LinuxActivityHelper.commands(this, paths);

        // Snapshot taken when the page opens; the page is modal, so it cannot go stale while shown.
        String[] open = getIntent().getStringArrayExtra(SessionRequest.EXTRA_OPEN_SESSIONS);
        if (open != null) mOpenSessions.addAll(Arrays.asList(open));

        ListView list = findViewById(R.id.linux_installs_list);
        mEmptyView = findViewById(R.id.linux_installs_empty);
        mAdapter = new InstallsAdapter();
        list.setAdapter(mAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent detail = new Intent(this, LinuxInstallDetailActivity.class);
            detail.putExtra(LinuxInstallDetailActivity.EXTRA_ALIAS, mInstalls.get(position).alias);
            detail.putExtra(SessionRequest.EXTRA_OPEN_SESSIONS, mOpenSessions.toArray(new String[0]));
            startActivityForResult(detail, REQUEST_DETAIL);
        });

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
        if (requestCode == REQUEST_DETAIL) {
            // RESULT_FIRST_USER means "list changed"; onResume rescans.
            if (resultCode == RESULT_OK && data != null) {
                setResult(RESULT_OK, data);
                finish();
            }
            return;
        }
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
        LinuxActivityHelper.finishWith(this, requests);
    }

    private void login(InstallInfo info) {
        finishWith(LinuxActivityHelper.loginRequests(mCommands, info, mOpenSessions,
            mCommands.login(info.alias, info.hostname)));
    }

    private void loginUser(InstallInfo info) {
        if (!InputValidator.isValidUser(info.user)) return;
        finishWith(LinuxActivityHelper.loginRequests(mCommands, info, mOpenSessions,
            mCommands.loginUser(info.alias, info.hostname, info.user)));
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

            // No quick actions while installing or when the install is gone; the row still opens the detail page.
            boolean installing = info.status == InstallInfo.Status.INSTALLING
                || info.status == InstallInfo.Status.NOT_FOUND;

            Button login = row.findViewById(R.id.linux_item_login);
            login.setEnabled(!installing);
            login.setOnClickListener(v -> login(info));

            Button loginUser = row.findViewById(R.id.linux_item_login_user);
            loginUser.setEnabled(!installing && InputValidator.isValidUser(info.user));
            loginUser.setOnClickListener(v -> loginUser(info));

            return row;
        }
    }
}
