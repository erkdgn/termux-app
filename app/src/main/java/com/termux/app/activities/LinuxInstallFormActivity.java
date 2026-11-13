package com.termux.app.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.linux.Distro;
import com.termux.app.linux.DistroVersion;
import com.termux.app.linux.InputValidator;
import com.termux.app.linux.InstallOptions;
import com.termux.app.linux.InstallStore;
import com.termux.app.linux.LinuxPaths;
import com.termux.app.linux.PackageGroup;
import com.termux.app.linux.Timezones;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Form for choosing the options of a new Linux (proot-distro) install. Started with
 * startActivityForResult(); returns RESULT_OK with the EXTRA_* values, which
 * {@link #optionsFromIntent(Intent)} turns back into {@link InstallOptions}.
 */
public class LinuxInstallFormActivity extends AppCompatActivity {

    private static final String PREFIX = "com.termux.linux.extra.";
    public static final String EXTRA_DISTRO = PREFIX + "DISTRO";
    public static final String EXTRA_ALIAS = PREFIX + "ALIAS";
    public static final String EXTRA_USER = PREFIX + "USER";
    public static final String EXTRA_SUDO = PREFIX + "SUDO";
    public static final String EXTRA_BASIC_TOOLS = PREFIX + "BASIC_TOOLS";
    public static final String EXTRA_SSH = PREFIX + "SSH";
    public static final String EXTRA_SSH_PORT = PREFIX + "SSH_PORT";
    public static final String EXTRA_SSH_AUTOSTART = PREFIX + "SSH_AUTOSTART";
    public static final String EXTRA_ROOT_PASSWORD = PREFIX + "ROOT_PASSWORD";
    public static final String EXTRA_ROOT_KEY = PREFIX + "ROOT_KEY";
    public static final String EXTRA_USER_KEY = PREFIX + "USER_KEY";
    public static final String EXTRA_VERSION = PREFIX + "VERSION";
    public static final String EXTRA_TIMEZONE = PREFIX + "TIMEZONE";
    public static final String EXTRA_LOCALE_TR = PREFIX + "LOCALE_TR";
    public static final String EXTRA_HOSTNAME = PREFIX + "HOSTNAME";
    /** String[] of {@link PackageGroup} enum names. */
    public static final String EXTRA_GROUPS = PREFIX + "GROUPS";
    /** Parsed extra package tokens joined by a single space; empty when none. */
    public static final String EXTRA_EXTRA_PACKAGES = PREFIX + "EXTRA_PACKAGES";

    private static final String STATE_ALIAS_EDITED = "alias_edited";

    /** Check box ids, one per {@link PackageGroup} in enum order. */
    private static final int[] GROUP_CHECK_IDS = {
        R.id.linux_group_dev_check, R.id.linux_group_python_check, R.id.linux_group_nodejs_check,
        R.id.linux_group_nginx_check, R.id.linux_group_postgresql_check, R.id.linux_group_mariadb_check
    };

    private Spinner mDistroSpinner, mVersionSpinner, mTimezoneSpinner;
    private TextInputLayout mAliasLayout, mUserLayout, mPortLayout, mRootKeyLayout, mUserKeyLayout,
        mHostnameLayout, mExtraPackagesLayout;
    private TextInputEditText mAliasEdit, mUserEdit, mPortEdit, mRootKeyEdit, mUserKeyEdit,
        mHostnameEdit, mExtraPackagesEdit;
    private CheckBox mSudoCheck, mBasicToolsCheck, mSshCheck, mAutostartCheck, mRootPasswordCheck,
        mLocaleTrCheck;
    private final CheckBox[] mGroupChecks = new CheckBox[GROUP_CHECK_IDS.length];
    private View mSshGroup;
    private TextView mSudoError, mSshError, mVersionError, mTimezoneError;
    /** Versions shown in the version spinner, same order. */
    private List<DistroVersion> mVersions = new ArrayList<>();
    @Nullable private Distro mVersionsDistro;
    private Button mInstallButton;

    private InstallStore mStore;
    private boolean mAliasEdited;
    private boolean mUpdatingAlias;
    /** Result of the last {@link #update()}; null while the form is invalid. */
    @Nullable private InstallOptions mCurrentOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_linux_install_form);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.linux_form_title);

        mStore = new InstallStore(LinuxPaths.defaults());
        if (savedInstanceState != null) mAliasEdited = savedInstanceState.getBoolean(STATE_ALIAS_EDITED);

        mDistroSpinner = findViewById(R.id.linux_distro_spinner);
        mAliasLayout = findViewById(R.id.linux_alias_layout);
        mAliasEdit = findViewById(R.id.linux_alias_edit);
        mUserLayout = findViewById(R.id.linux_user_layout);
        mUserEdit = findViewById(R.id.linux_user_edit);
        mSudoCheck = findViewById(R.id.linux_sudo_check);
        mSudoError = findViewById(R.id.linux_sudo_error);
        mBasicToolsCheck = findViewById(R.id.linux_basic_tools_check);
        mSshCheck = findViewById(R.id.linux_ssh_check);
        mSshGroup = findViewById(R.id.linux_ssh_group);
        mPortLayout = findViewById(R.id.linux_port_layout);
        mPortEdit = findViewById(R.id.linux_port_edit);
        mAutostartCheck = findViewById(R.id.linux_autostart_check);
        mSshError = findViewById(R.id.linux_ssh_error);
        mRootPasswordCheck = findViewById(R.id.linux_root_password_check);
        mRootKeyLayout = findViewById(R.id.linux_root_key_layout);
        mRootKeyEdit = findViewById(R.id.linux_root_key_edit);
        mUserKeyLayout = findViewById(R.id.linux_user_key_layout);
        mUserKeyEdit = findViewById(R.id.linux_user_key_edit);
        mInstallButton = findViewById(R.id.linux_install_button);
        mVersionSpinner = findViewById(R.id.linux_version_spinner);
        mVersionError = findViewById(R.id.linux_version_error);
        mTimezoneSpinner = findViewById(R.id.linux_timezone_spinner);
        mTimezoneError = findViewById(R.id.linux_timezone_error);
        mLocaleTrCheck = findViewById(R.id.linux_locale_tr_check);
        mHostnameLayout = findViewById(R.id.linux_hostname_layout);
        mHostnameEdit = findViewById(R.id.linux_hostname_edit);
        mExtraPackagesLayout = findViewById(R.id.linux_extra_packages_layout);
        mExtraPackagesEdit = findViewById(R.id.linux_extra_packages_edit);
        for (int i = 0; i < GROUP_CHECK_IDS.length; i++) mGroupChecks[i] = findViewById(GROUP_CHECK_IDS[i]);

        Distro[] distros = Distro.values();
        String[] labels = new String[distros.length];
        for (int i = 0; i < distros.length; i++) labels[i] = distros[i].label();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDistroSpinner.setAdapter(adapter);
        mDistroSpinner.setSelection(Distro.UBUNTU.ordinal());

        rebuildVersions(Distro.UBUNTU);

        // First item is "image default" (empty value), followed by the fixed timezone list.
        List<String> tzLabels = new ArrayList<>();
        tzLabels.add(getString(R.string.linux_timezone_default));
        tzLabels.addAll(Timezones.all());
        ArrayAdapter<String> tzAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tzLabels);
        tzAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTimezoneSpinner.setAdapter(tzAdapter);
        mTimezoneSpinner.setSelection(1 + Timezones.all().indexOf(Timezones.DEFAULT));

        if (savedInstanceState == null) {
            mPortEdit.setText(String.valueOf(InputValidator.DEFAULT_PORT));
            setAlias(Distro.UBUNTU.id());
        }

        mDistroSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mAliasEdited) setAlias(selectedDistro().id());
                rebuildVersions(selectedDistro());
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AdapterView.OnItemSelectedListener updateSelectionListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        mVersionSpinner.setOnItemSelectedListener(updateSelectionListener);
        mTimezoneSpinner.setOnItemSelectedListener(updateSelectionListener);

        mAliasEdit.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!mUpdatingAlias) mAliasEdited = true;
                update();
            }
        });
        TextWatcher updateWatcher = new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                update();
            }
        };
        mUserEdit.addTextChangedListener(updateWatcher);
        mPortEdit.addTextChangedListener(updateWatcher);
        mRootKeyEdit.addTextChangedListener(updateWatcher);
        mUserKeyEdit.addTextChangedListener(updateWatcher);
        mHostnameEdit.addTextChangedListener(updateWatcher);
        mExtraPackagesEdit.addTextChangedListener(updateWatcher);

        CompoundButtonListener checkListener = new CompoundButtonListener();
        mSudoCheck.setOnCheckedChangeListener(checkListener);
        mBasicToolsCheck.setOnCheckedChangeListener(checkListener);
        mSshCheck.setOnCheckedChangeListener(checkListener);
        mAutostartCheck.setOnCheckedChangeListener(checkListener);
        mRootPasswordCheck.setOnCheckedChangeListener(checkListener);
        mLocaleTrCheck.setOnCheckedChangeListener(checkListener);
        for (CheckBox c : mGroupChecks) c.setOnCheckedChangeListener(checkListener);

        // Phantom process killer only exists on Android 12+ (API 31).
        if (Build.VERSION.SDK_INT >= 31) {
            findViewById(R.id.linux_phantom_group).setVisibility(View.VISIBLE);
            findViewById(R.id.linux_developer_options_button).setOnClickListener(v -> openDeveloperOptions());
        }

        mInstallButton.setOnClickListener(v -> install());

        update();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_ALIAS_EDITED, mAliasEdited);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /** Rebuilds {@link InstallOptions} from the form, shows errors and enables Install when valid. */
    private void update() {
        // Fields are created in onCreate in order; listeners may fire before all are set up.
        if (mInstallButton == null || mExtraPackagesEdit == null) return;

        String user = text(mUserEdit).trim();
        boolean hasUser = !user.isEmpty();
        boolean ssh = mSshCheck.isChecked();

        // Dependent controls.
        mSudoCheck.setEnabled(hasUser);
        if (!hasUser && mSudoCheck.isChecked()) mSudoCheck.setChecked(false); // re-enters update()
        mUserKeyLayout.setVisibility(hasUser ? View.VISIBLE : View.GONE);
        mSshGroup.setVisibility(ssh ? View.VISIBLE : View.GONE);

        String rootKeyRaw = text(mRootKeyEdit);
        String rootKeyNorm = InputValidator.normalizeKey(rootKeyRaw);
        String rootKey = rootKeyNorm != null ? rootKeyNorm : rootKeyRaw;
        String userKeyRaw = hasUser ? text(mUserKeyEdit) : "";
        String userKeyNorm = InputValidator.normalizeKey(userKeyRaw);
        String userKey = userKeyNorm != null ? userKeyNorm : userKeyRaw;

        int port = 0;
        if (ssh) {
            try {
                port = Integer.parseInt(text(mPortEdit).trim());
            } catch (NumberFormatException e) {
                port = 0;
            }
        }

        String alias = text(mAliasEdit).trim();
        EnumSet<PackageGroup> groups = EnumSet.noneOf(PackageGroup.class);
        PackageGroup[] allGroups = PackageGroup.values();
        for (int i = 0; i < allGroups.length && i < mGroupChecks.length; i++) {
            if (mGroupChecks[i].isChecked()) groups.add(allGroups[i]);
        }
        InstallOptions options = new InstallOptions.Builder()
            .distro(selectedDistro())
            .version(selectedVersion())
            .alias(alias)
            .user(user)
            .sudo(hasUser && mSudoCheck.isChecked())
            .basicTools(mBasicToolsCheck.isChecked())
            .ssh(ssh)
            .sshPort(port)
            .sshAutoStart(ssh && mAutostartCheck.isChecked())
            .rootPassword(mRootPasswordCheck.isChecked())
            .rootKey(rootKey)
            .userKey(userKey)
            .timezone(selectedTimezone())
            .localeTr(mLocaleTrCheck.isChecked())
            .hostname(text(mHostnameEdit).trim())
            .groups(groups)
            .extraPackages(packagesOrRawTokens(text(mExtraPackagesEdit)))
            .build();

        EnumSet<InputValidator.Error> errors = InputValidator.validate(options);
        boolean aliasTaken = !errors.contains(InputValidator.Error.ALIAS) && mStore.isAliasTaken(alias);

        if (aliasTaken) {
            mAliasLayout.setError(getString(R.string.linux_error_alias_taken));
        } else {
            mAliasLayout.setError(errors.contains(InputValidator.Error.ALIAS)
                ? getString(R.string.linux_error_alias) : null);
        }
        mUserLayout.setError(errors.contains(InputValidator.Error.USER) ? getString(R.string.linux_error_user) : null);
        mPortLayout.setError(errors.contains(InputValidator.Error.PORT) ? getString(R.string.linux_error_port) : null);
        mRootKeyLayout.setError(errors.contains(InputValidator.Error.ROOT_KEY)
            ? getString(R.string.linux_error_root_key) : null);
        mUserKeyLayout.setError(errors.contains(InputValidator.Error.USER_KEY)
            ? getString(R.string.linux_error_user_key) : null);
        setInlineError(mSshError, errors.contains(InputValidator.Error.SSH_NO_ACCESS)
            ? getString(R.string.linux_error_ssh_no_access) : null);
        // Both are prevented by the UI (hidden / disabled), shown only as a safety net.
        setInlineError(mSudoError, errors.contains(InputValidator.Error.SUDO_WITHOUT_USER)
            ? getString(R.string.linux_error_sudo_without_user) : null);
        if (errors.contains(InputValidator.Error.USER_KEY_WITHOUT_USER)) {
            mUserKeyLayout.setError(getString(R.string.linux_error_user_key_without_user));
        }
        mHostnameLayout.setError(errors.contains(InputValidator.Error.HOSTNAME)
            ? getString(R.string.linux_error_hostname) : null);
        mExtraPackagesLayout.setError(errors.contains(InputValidator.Error.PACKAGES)
            ? getString(R.string.linux_error_packages) : null);
        // Spinners only offer valid values, so these are a safety net.
        setInlineError(mVersionError, errors.contains(InputValidator.Error.VERSION)
            ? getString(R.string.linux_error_version) : null);
        setInlineError(mTimezoneError, errors.contains(InputValidator.Error.TIMEZONE)
            ? getString(R.string.linux_error_timezone) : null);

        boolean valid = errors.isEmpty() && !aliasTaken;
        mCurrentOptions = valid ? options : null;
        mInstallButton.setEnabled(valid);
    }

    private void install() {
        update();
        InstallOptions o = mCurrentOptions;
        if (o == null) return;

        Intent result = new Intent();
        result.putExtra(EXTRA_DISTRO, o.distro.id());
        result.putExtra(EXTRA_ALIAS, o.alias);
        result.putExtra(EXTRA_USER, o.user);
        result.putExtra(EXTRA_SUDO, o.sudo);
        result.putExtra(EXTRA_BASIC_TOOLS, o.basicTools);
        result.putExtra(EXTRA_SSH, o.ssh);
        result.putExtra(EXTRA_SSH_PORT, o.sshPort);
        result.putExtra(EXTRA_SSH_AUTOSTART, o.sshAutoStart);
        result.putExtra(EXTRA_ROOT_PASSWORD, o.rootPassword);
        result.putExtra(EXTRA_ROOT_KEY, o.rootKey);
        result.putExtra(EXTRA_USER_KEY, o.userKey);
        result.putExtra(EXTRA_VERSION, o.version.id());
        result.putExtra(EXTRA_TIMEZONE, o.timezone);
        result.putExtra(EXTRA_LOCALE_TR, o.localeTr);
        result.putExtra(EXTRA_HOSTNAME, o.hostname);
        String[] groupNames = new String[o.groups.size()];
        int gi = 0;
        for (PackageGroup g : o.groups) groupNames[gi++] = g.name();
        result.putExtra(EXTRA_GROUPS, groupNames);
        StringBuilder pkgs = new StringBuilder();
        for (String p : o.extraPackages) {
            if (pkgs.length() > 0) pkgs.append(' ');
            pkgs.append(p);
        }
        result.putExtra(EXTRA_EXTRA_PACKAGES, pkgs.toString());
        setResult(RESULT_OK, result);
        finish();
    }

    /** Rebuilds the options from a result intent of this activity; null if it is incomplete or unknown. */
    @Nullable
    public static InstallOptions optionsFromIntent(@Nullable Intent data) {
        if (data == null) return null;
        Distro distro = Distro.fromId(data.getStringExtra(EXTRA_DISTRO));
        if (distro == null) return null;
        // A missing version means the distro default; a present but unknown one is rejected.
        String versionId = data.getStringExtra(EXTRA_VERSION);
        DistroVersion version = versionId == null
            ? DistroVersion.defaultFor(distro) : DistroVersion.fromId(distro, versionId);
        if (version == null) return null;

        EnumSet<PackageGroup> groups = EnumSet.noneOf(PackageGroup.class);
        String[] groupNames = data.getStringArrayExtra(EXTRA_GROUPS);
        if (groupNames != null) {
            for (String name : groupNames) {
                PackageGroup g = PackageGroup.fromName(name);
                if (g == null) return null;
                groups.add(g);
            }
        }

        return new InstallOptions.Builder()
            .distro(distro)
            .version(version)
            .alias(data.getStringExtra(EXTRA_ALIAS))
            .user(data.getStringExtra(EXTRA_USER))
            .sudo(data.getBooleanExtra(EXTRA_SUDO, false))
            .basicTools(data.getBooleanExtra(EXTRA_BASIC_TOOLS, false))
            .ssh(data.getBooleanExtra(EXTRA_SSH, false))
            .sshPort(data.getIntExtra(EXTRA_SSH_PORT, InputValidator.DEFAULT_PORT))
            .sshAutoStart(data.getBooleanExtra(EXTRA_SSH_AUTOSTART, false))
            .rootPassword(data.getBooleanExtra(EXTRA_ROOT_PASSWORD, false))
            .rootKey(data.getStringExtra(EXTRA_ROOT_KEY))
            .userKey(data.getStringExtra(EXTRA_USER_KEY))
            .timezone(data.getStringExtra(EXTRA_TIMEZONE))
            .localeTr(data.getBooleanExtra(EXTRA_LOCALE_TR, false))
            .hostname(data.getStringExtra(EXTRA_HOSTNAME))
            .groups(groups)
            .extraPackages(packagesOrRawTokens(data.getStringExtra(EXTRA_EXTRA_PACKAGES)))
            .build();
    }

    /**
     * Parsed package tokens, or, when parsing fails, the raw whitespace-split tokens so that
     * {@link InputValidator#validate(InstallOptions)} still reports PACKAGES.
     */
    private static List<String> packagesOrRawTokens(@Nullable String raw) {
        List<String> parsed = InputValidator.parsePackages(raw);
        if (parsed != null) return parsed;
        List<String> tokens = new ArrayList<>();
        for (String t : raw.trim().split("\\s+")) tokens.add(t);
        return tokens;
    }

    /** Fills the version spinner for the distro with its default preselected; no-op if already shown. */
    private void rebuildVersions(Distro distro) {
        if (distro == mVersionsDistro) return;
        mVersionsDistro = distro;
        mVersions = new ArrayList<>(DistroVersion.forDistro(distro));
        String[] labels = new String[mVersions.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = mVersions.get(i).label();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mVersionSpinner.setAdapter(adapter);
        mVersionSpinner.setSelection(Math.max(0, mVersions.indexOf(DistroVersion.defaultFor(distro))));
    }

    private DistroVersion selectedVersion() {
        int pos = mVersionSpinner.getSelectedItemPosition();
        return pos >= 0 && pos < mVersions.size() ? mVersions.get(pos) : DistroVersion.defaultFor(selectedDistro());
    }

    /** Empty string for "image default". */
    private String selectedTimezone() {
        int pos = mTimezoneSpinner.getSelectedItemPosition();
        List<String> all = Timezones.all();
        return pos >= 1 && pos <= all.size() ? all.get(pos - 1) : "";
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.linux_developer_options_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private Distro selectedDistro() {
        int pos = mDistroSpinner.getSelectedItemPosition();
        Distro[] distros = Distro.values();
        return pos >= 0 && pos < distros.length ? distros[pos] : Distro.UBUNTU;
    }

    private void setAlias(String alias) {
        mUpdatingAlias = true;
        try {
            mAliasEdit.setText(alias);
        } finally {
            mUpdatingAlias = false;
        }
    }

    private static String text(TextInputEditText edit) {
        Editable e = edit.getText();
        return e == null ? "" : e.toString();
    }

    private static void setInlineError(TextView view, @Nullable String message) {
        view.setText(message);
        view.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private class CompoundButtonListener implements android.widget.CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
            update();
        }
    }
}
