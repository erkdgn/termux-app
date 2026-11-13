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
import com.termux.app.linux.InputValidator;
import com.termux.app.linux.InstallOptions;
import com.termux.app.linux.InstallStore;
import com.termux.app.linux.LinuxPaths;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.EnumSet;

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

    private static final String STATE_ALIAS_EDITED = "alias_edited";

    private Spinner mDistroSpinner;
    private TextInputLayout mAliasLayout, mUserLayout, mPortLayout, mRootKeyLayout, mUserKeyLayout;
    private TextInputEditText mAliasEdit, mUserEdit, mPortEdit, mRootKeyEdit, mUserKeyEdit;
    private CheckBox mSudoCheck, mBasicToolsCheck, mSshCheck, mAutostartCheck, mRootPasswordCheck;
    private View mSshGroup;
    private TextView mSudoError, mSshError;
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

        Distro[] distros = Distro.values();
        String[] labels = new String[distros.length];
        for (int i = 0; i < distros.length; i++) labels[i] = distros[i].label();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDistroSpinner.setAdapter(adapter);
        mDistroSpinner.setSelection(Distro.UBUNTU.ordinal());

        if (savedInstanceState == null) {
            mPortEdit.setText(String.valueOf(InputValidator.DEFAULT_PORT));
            setAlias(Distro.UBUNTU.id());
        }

        mDistroSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mAliasEdited) setAlias(selectedDistro().id());
                update();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

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

        CompoundButtonListener checkListener = new CompoundButtonListener();
        mSudoCheck.setOnCheckedChangeListener(checkListener);
        mBasicToolsCheck.setOnCheckedChangeListener(checkListener);
        mSshCheck.setOnCheckedChangeListener(checkListener);
        mAutostartCheck.setOnCheckedChangeListener(checkListener);
        mRootPasswordCheck.setOnCheckedChangeListener(checkListener);

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
        if (mInstallButton == null || mUserKeyEdit == null) return;

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
        InstallOptions options = new InstallOptions(selectedDistro(), alias, user,
            hasUser && mSudoCheck.isChecked(), mBasicToolsCheck.isChecked(),
            ssh, port, ssh && mAutostartCheck.isChecked(), mRootPasswordCheck.isChecked(),
            rootKey, userKey);

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
        setResult(RESULT_OK, result);
        finish();
    }

    /** Rebuilds the options from a result intent of this activity; null if it is incomplete or unknown. */
    @Nullable
    public static InstallOptions optionsFromIntent(@Nullable Intent data) {
        if (data == null) return null;
        Distro distro = Distro.fromId(data.getStringExtra(EXTRA_DISTRO));
        if (distro == null) return null;
        return new InstallOptions(distro,
            data.getStringExtra(EXTRA_ALIAS),
            data.getStringExtra(EXTRA_USER),
            data.getBooleanExtra(EXTRA_SUDO, false),
            data.getBooleanExtra(EXTRA_BASIC_TOOLS, false),
            data.getBooleanExtra(EXTRA_SSH, false),
            data.getIntExtra(EXTRA_SSH_PORT, InputValidator.DEFAULT_PORT),
            data.getBooleanExtra(EXTRA_SSH_AUTOSTART, false),
            data.getBooleanExtra(EXTRA_ROOT_PASSWORD, false),
            data.getStringExtra(EXTRA_ROOT_KEY),
            data.getStringExtra(EXTRA_USER_KEY));
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
