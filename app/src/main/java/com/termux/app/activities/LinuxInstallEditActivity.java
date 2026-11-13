package com.termux.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.linux.CommandBuilder;
import com.termux.app.linux.Distro;
import com.termux.app.linux.DistroVersion;
import com.termux.app.linux.EditInfo;
import com.termux.app.linux.InputValidator;
import com.termux.app.linux.InstallInfo;
import com.termux.app.linux.InstallStore;
import com.termux.app.linux.LinuxPaths;
import com.termux.app.linux.RootSshLogin;
import com.termux.app.linux.SessionNames;
import com.termux.app.linux.SessionRequest;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Form for editing the recorded info of an existing Linux install. Started with
 * {@link #EXTRA_ALIAS} and {@link SessionRequest#EXTRA_OPEN_SESSIONS}; finishes with RESULT_OK and
 * {@link SessionRequest#EXTRA_REQUESTS} when a command must be typed into a session, or with
 * RESULT_FIRST_USER (saved, refresh the list) otherwise. Stays open when the form is invalid or
 * saving fails.
 */
public class LinuxInstallEditActivity extends AppCompatActivity {

    public static final String EXTRA_ALIAS = "com.termux.linux.extra.ALIAS";

    /** Labels of {@link RootSshLogin} values in enum order. */
    private static final int[] ROOT_LOGIN_LABELS = {
        R.string.linux_root_login_no, R.string.linux_root_login_key_only, R.string.linux_root_login_password
    };

    private InstallStore mStore;
    private CommandBuilder mCommands;
    private final Set<String> mOpenSessions = new HashSet<>();
    private String mAlias;
    private InstallInfo mInfo;

    private Spinner mDistroSpinner, mVersionSpinner, mRootLoginSpinner;
    private TextInputLayout mUserLayout, mHostnameLayout, mPortLayout;
    private TextInputEditText mUserEdit, mHostnameEdit, mPortEdit;
    private CheckBox mSshCheck, mAutostartCheck;
    private View mSshGroup;
    private TextView mVersionError;
    /** Versions shown in the version spinner after the leading "Unknown" item. */
    private List<DistroVersion> mVersions = new ArrayList<>();
    @Nullable private Distro mVersionsDistro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_linux_install_edit);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.linux_edit_info);

        LinuxPaths paths = LinuxPaths.defaults();
        mStore = new InstallStore(paths);
        mCommands = LinuxActivityHelper.commands(this, paths);

        mAlias = getIntent().getStringExtra(EXTRA_ALIAS);
        String[] open = getIntent().getStringArrayExtra(SessionRequest.EXTRA_OPEN_SESSIONS);
        if (open != null) mOpenSessions.addAll(Arrays.asList(open));

        mInfo = null;
        if (mAlias != null) {
            for (InstallInfo i : mStore.scan(mOpenSessions)) {
                if (i.alias.equals(mAlias)) {
                    mInfo = i;
                    break;
                }
            }
        }
        if (mInfo == null) {
            Toast.makeText(this, R.string.linux_install_not_found, Toast.LENGTH_LONG).show();
            setResult(RESULT_FIRST_USER);
            finish();
            return;
        }

        mDistroSpinner = findViewById(R.id.linux_edit_distro_spinner);
        mVersionSpinner = findViewById(R.id.linux_edit_version_spinner);
        mVersionError = findViewById(R.id.linux_edit_version_error);
        mUserLayout = findViewById(R.id.linux_edit_user_layout);
        mUserEdit = findViewById(R.id.linux_edit_user_edit);
        mHostnameLayout = findViewById(R.id.linux_edit_hostname_layout);
        mHostnameEdit = findViewById(R.id.linux_edit_hostname_edit);
        mSshCheck = findViewById(R.id.linux_edit_ssh_check);
        mSshGroup = findViewById(R.id.linux_edit_ssh_group);
        mPortLayout = findViewById(R.id.linux_edit_port_layout);
        mPortEdit = findViewById(R.id.linux_edit_port_edit);
        mAutostartCheck = findViewById(R.id.linux_edit_autostart_check);
        mRootLoginSpinner = findViewById(R.id.linux_edit_root_login_spinner);

        // Distro: "Unknown" first, then the catalog distros.
        Distro[] distros = Distro.values();
        List<String> distroLabels = new ArrayList<>();
        distroLabels.add(getString(R.string.linux_unknown_distro));
        for (Distro d : distros) distroLabels.add(d.label());
        mDistroSpinner.setAdapter(adapter(distroLabels));

        List<String> rootLabels = new ArrayList<>();
        for (int label : ROOT_LOGIN_LABELS) rootLabels.add(getString(label));
        mRootLoginSpinner.setAdapter(adapter(rootLabels));

        // Prefill from the loaded info (only on first creation; Android restores view state itself).
        final InstallInfo info = mInfo;
        mDistroSpinner.setSelection(info.distro == null ? 0 : 1 + info.distro.ordinal());
        rebuildVersions(info.distro);
        if (info.version != null) {
            int idx = mVersions.indexOf(info.version);
            mVersionSpinner.setSelection(idx < 0 ? 0 : 1 + idx);
        }
        mUserEdit.setText(info.user);
        mHostnameEdit.setText(info.hostname);
        mSshCheck.setChecked(info.ssh);
        mSshGroup.setVisibility(info.ssh ? View.VISIBLE : View.GONE);
        mPortEdit.setText(String.valueOf(info.sshPort == 0 ? InputValidator.DEFAULT_PORT : info.sshPort));
        mAutostartCheck.setChecked(info.sshAutoStart);
        mRootLoginSpinner.setSelection(info.rootSshLogin.ordinal());

        mDistroSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Distro d = selectedDistro();
                // Only rebuild when the distro really changed; keeps the prefilled/restored version.
                if (d != mVersionsDistro) rebuildVersions(d);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        mSshCheck.setOnCheckedChangeListener((b, checked) ->
            mSshGroup.setVisibility(checked ? View.VISIBLE : View.GONE));

        findViewById(R.id.linux_edit_save_button).setOnClickListener(v -> save());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private ArrayAdapter<String> adapter(List<String> labels) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    @Nullable
    private Distro selectedDistro() {
        int pos = mDistroSpinner.getSelectedItemPosition();
        return pos <= 0 ? null : Distro.values()[pos - 1];
    }

    @Nullable
    private DistroVersion selectedVersion() {
        int pos = mVersionSpinner.getSelectedItemPosition();
        return pos <= 0 || pos > mVersions.size() ? null : mVersions.get(pos - 1);
    }

    /** Fills the version spinner with "Unknown" plus the catalog versions of the distro, if any. */
    private void rebuildVersions(@Nullable Distro distro) {
        mVersionsDistro = distro;
        mVersions = distro == null ? new ArrayList<DistroVersion>() : new ArrayList<>(DistroVersion.forDistro(distro));
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.linux_unknown_distro));
        for (DistroVersion v : mVersions) labels.add(v.label());
        mVersionSpinner.setAdapter(adapter(labels));
        mVersionSpinner.setSelection(0);
    }

    private void save() {
        final InstallInfo old = mInfo;
        String user = text(mUserEdit).trim();
        String hostname = text(mHostnameEdit).trim();
        boolean ssh = mSshCheck.isChecked();
        int port = old.sshPort == 0 ? InputValidator.DEFAULT_PORT : old.sshPort;
        if (ssh) {
            try {
                port = Integer.parseInt(text(mPortEdit).trim());
            } catch (NumberFormatException e) {
                port = 0;
            }
        }
        RootSshLogin mode = RootSshLogin.values()[mRootLoginSpinner.getSelectedItemPosition()];
        Distro distro = selectedDistro();
        DistroVersion version = selectedVersion();

        EditInfo edit = new EditInfo(distro, version, user, hostname, ssh, port,
            ssh && mAutostartCheck.isChecked(), mode);
        EnumSet<InputValidator.Error> errors = InputValidator.validate(edit);
        mUserLayout.setError(errors.contains(InputValidator.Error.USER) ? getString(R.string.linux_error_user) : null);
        mHostnameLayout.setError(errors.contains(InputValidator.Error.HOSTNAME)
            ? getString(R.string.linux_error_hostname) : null);
        mPortLayout.setError(errors.contains(InputValidator.Error.PORT) ? getString(R.string.linux_error_port) : null);
        boolean versionBad = errors.contains(InputValidator.Error.VERSION);
        mVersionError.setText(R.string.linux_error_version);
        mVersionError.setVisibility(versionBad ? View.VISIBLE : View.GONE);
        if (!errors.isEmpty()) return;

        try {
            mStore.saveEdit(mAlias, edit);
        } catch (IOException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.linux_save_failed, Toast.LENGTH_LONG).show();
            return;
        }

        boolean hostnameChanged = !hostname.equals(old.hostname);
        int oldPort = old.sshPort == 0 ? InputValidator.DEFAULT_PORT : old.sshPort;
        boolean sshChanged = ssh != old.ssh || port != oldPort || mode != old.rootSshLogin;
        String command = mCommands.applyEdit(mAlias, hostname, sshChanged, ssh, port, mode, hostnameChanged);
        if (command.isEmpty()) {
            setResult(RESULT_FIRST_USER);
            finish();
            return;
        }
        LinuxActivityHelper.finishWith(this, Collections.singletonList(
            SessionRequest.open(SessionNames.firstFree(mAlias, mOpenSessions), command)));
    }

    private static String text(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }
}
