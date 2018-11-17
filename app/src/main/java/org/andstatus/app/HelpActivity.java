/* 
 * Copyright (C) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.context.ExecutionMode;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.data.checker.DataChecker;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.ViewUtils;
import org.andstatus.app.util.Xslt;

import static org.andstatus.app.context.DemoData.demoData;

public class HelpActivity extends MyActivity implements ProgressLogger.ProgressCallback, DialogInterface.OnDismissListener {
    public static final String TAG = HelpActivity.class.getSimpleName();

    /**
     * integer - Index of Help screen to show first
     */
    public static final String EXTRA_HELP_PAGE_INDEX = ClassInApplicationPackage.PACKAGE_NAME + ".HELP_PAGE_ID";
    /**
     * boolean - If the activity is the first then we should provide means 
     * to start {@link TimelineActivity} from this activity
     */
    public static final String EXTRA_IS_FIRST_ACTIVITY = ClassInApplicationPackage.PACKAGE_NAME + ".IS_FIRST_ACTIVITY";
    public static final String EXTRA_CLOSE_ME = ClassInApplicationPackage.PACKAGE_NAME + ".CLOSE_ME";

    public static final int PAGE_LOGO = 0;
    public static final int PAGE_USER_GUIDE = 1;
    public static final int PAGE_CHANGELOG = 2;
    
    // Local objects
    private ViewFlipper mFlipper;
    /**
     * Stores state of {@link #EXTRA_IS_FIRST_ACTIVITY}
     */
    private boolean mIsFirstActivity = false;
    private boolean wasPaused = false;
    private volatile ProgressDialog progress = null;
    private static volatile boolean generatingDemoData = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.help;
        super.onCreate(savedInstanceState);
        if (isFinishing() || isCloseRequest(getIntent())) {
            return;
        }

        if (savedInstanceState != null) {
            mIsFirstActivity = savedInstanceState.getBoolean(EXTRA_IS_FIRST_ACTIVITY, false);
        }
        if (getIntent().hasExtra(EXTRA_IS_FIRST_ACTIVITY)) {
            mIsFirstActivity = getIntent().getBooleanExtra(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
        }

        showVersionText();
        ViewUtils.showView(this, R.id.system_info_section, MyPreferences.isShowDebuggingInfoInUi()
                || MyContextHolder.getExecutionMode() != ExecutionMode.DEVICE);
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            MyUrlSpan.showText(this, R.id.system_info,
                    MyContextHolder.getSystemInfo(this, false), false, false);
        }

        if (MyContextHolder.get().accounts().getCurrentAccount().nonValid()
                && MyContextHolder.getExecutionMode() == ExecutionMode.ROBO_TEST
                && !generatingDemoData) {
            generatingDemoData = true;
            demoData.addAsync("GenerateDemoData", MyContextHolder.get(), HelpActivity.this);
        }

        showChangeLog();
        showUserGuide();
        showRestoreButton();
        showGetStartedButton();
        setupHelpFlipper();

        if (getIntent().hasExtra(IntentExtra.CHECK_DATA.key) && savedInstanceState == null) {
            DataChecker.fixDataAsync(new ProgressLogger(this),
                    getIntent().getBooleanExtra(IntentExtra.FULL_CHECK.key, false),
                    getIntent().getBooleanExtra(IntentExtra.COUNT_ONLY.key, false)
            );
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        isCloseRequest(intent);
    }

    private boolean isCloseRequest(Intent intent) {
        if (isFinishing()) {
            return true;
        }
        if (intent.hasExtra(EXTRA_CLOSE_ME)) {
            finish();
            return true;
        }
        return false;
    }

    private void showVersionText() {
        TextView versionText = findViewById(R.id.splash_application_version);
        String text = MyContextHolder.getVersionText(this);
        if (!MyContextHolder.get().isReady()) {
            text += "\n" + MyContextHolder.get().state();
            text += "\n" + MyContextHolder.get().getLastDatabaseError();
        }
        versionText.setText(text);
        versionText.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("http://andstatus.org"));
            startActivity(intent);
        });
    }

    private void showChangeLog() {
        Xslt.toWebView(this, R.id.changelog, R.raw.changes, R.raw.changes2html);
    }

    private void showUserGuide() {
        Xslt.toWebView(this, R.id.user_guide, R.raw.user_guide, R.raw.fb2_2_html);
    }
    
    private void showRestoreButton() {
        Button restoreButton = findViewById(R.id.button_restore);
        if (!generatingDemoData
                && MyContextHolder.get().isReady() && MyContextHolder.get().accounts().isEmpty()) {
            restoreButton.setOnClickListener(v -> {
                startActivity(new Intent(HelpActivity.this, RestoreActivity.class));
                finish();
            });
        } else {
            restoreButton.setVisibility(View.GONE);
        }
    }

    private void showGetStartedButton() {
        //The button is always visible in order to avoid a User's confusion,
        final Button getStarted = findViewById(R.id.button_help_get_started);
        getStarted.setVisibility(generatingDemoData ? View.GONE : View.VISIBLE);
        getStarted.setOnClickListener(v -> {
            switch (MyContextHolder.get().state()) {
                case READY:
                    FirstActivity.checkAndUpdateLastOpenedAppVersion(HelpActivity.this, true);
                    if (MyContextHolder.get().accounts().getCurrentAccount().isValid()) {
                        startActivity(new Intent(HelpActivity.this, TimelineActivity.class));
                        finish();
                    } else {
                        startActivity(new Intent(HelpActivity.this, AccountSettingsActivity.class));
                        finish();
                    }
                    break;
                case NO_PERMISSIONS:
                    // Actually this is not used for now...
                    Permissions.checkPermissionAndRequestIt( HelpActivity.this,
                            Permissions.PermissionType.GET_ACCOUNTS);
                    break;
                case UPGRADING:
                    DialogFactory.showOkAlertDialog(HelpActivity.this, HelpActivity.this,
                            R.string.app_name, R.string.label_upgrading);
                    break;
                default:
                    DialogFactory.showOkAlertDialog(HelpActivity.this, HelpActivity.this,
                            R.string.app_name, R.string.loading);
                    break;
            }
        });
        if (MyContextHolder.get().accounts().getCurrentAccount().isValid()) {
            getStarted.setText(R.string.button_skip);
        }
    }

    private void setupHelpFlipper() {
        mFlipper = this.findViewById(R.id.help_flipper);
        

        if (ViewUtils.showView(this, R.id.button_help_learn_more, MyContextHolder.get().isReady())) {
            final Button learnMore = findViewById(R.id.button_help_learn_more);
            learnMore.setOnClickListener(v -> mFlipper.showNext());
        }

        if (getIntent().hasExtra(EXTRA_HELP_PAGE_INDEX)) {
            int pageToStart = getIntent().getIntExtra(EXTRA_HELP_PAGE_INDEX, 0);
            if (pageToStart > 0) {
                mFlipper.setDisplayedChild(pageToStart);
            }
        }
        
        AlphaAnimation anim = (AlphaAnimation) AnimationUtils.loadAnimation(HelpActivity.this, R.anim.fade_in);
        mFlipper.startAnimation(anim);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!generatingDemoData) {
            getMenuInflater().inflate(R.menu.help, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences_menu_id:
                startActivity(new Intent(this, MySettingsActivity.class));
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_IS_FIRST_ACTIVITY, mIsFirstActivity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyContextHolder.upgradeIfNeeded(this);
        if ( wasPaused && mIsFirstActivity
                &&  MyContextHolder.get().accounts().getCurrentAccount().isValid() ) {
            // We assume that user pressed back after adding first account
            Intent intent = new Intent(this, TimelineActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasPaused = true;
    }

    public static void startMe(Context context, boolean helpAsFirstActivity, int pageIndex) {
        Intent intent = new Intent(context, HelpActivity.class);
        if (helpAsFirstActivity) {
            intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
        }
        intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, pageIndex);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.v(TAG, () -> "Finishing " + context.getClass().getSimpleName() + " and starting " + TAG);
            ((Activity) context).finish();
        }
        context.getApplicationContext().startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        MyContextHolder.get().setExpired();
        this.recreate();
    }

    @Override
    public void finish() {
        cleanOnFinish();
        super.finish();
    }

    @Override
    public void onProgressMessage(final CharSequence message) {
        final String method = "onProgressMessage";
        try {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean shown = false;
                    if (isMyResumed()) {
                        try {
                            if (progress == null) {
                                progress = new ProgressDialog(HelpActivity.this, ProgressDialog.STYLE_SPINNER);
                                progress.setOnDismissListener(HelpActivity.this);
                                progress.setTitle(MyContextHolder.get().state() == MyContextState.UPGRADING ?
                                        R.string.label_upgrading : R.string.app_name);
                                progress.setMessage(message);
                                progress.show();
                            } else {
                                progress.setMessage(message);
                            }
                            shown = true;
                        } catch (Exception e) {
                            MyLog.d(this, method + " '" + message + "'", e);
                        }
                    }
                    if (!shown) {
                        try {
                            Toast.makeText(MyContextHolder.get().context(),
                                    getText(R.string.app_name) + "\n"
                                    + MyContextHolder.getVersionText(getBaseContext())
                                    + (MyContextHolder.get().state() == MyContextState.UPGRADING ?
                                            "\n" + getText(R.string.label_upgrading) : "")
                                    + "\n\n" + message,
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e2) {
                            MyLog.e(method, "Couldn't send toast with the text: " + method, e2);
                        }
                    }
                }
            });
        } catch (Exception e) {
            MyLog.d(this, method + " '" + message + "'", e);
        }
    }

    @Override
    public void onComplete(final boolean success) {
        try {
            this.runOnUiThread(() -> {
                TimelineActivity.goHome(HelpActivity.this);
                finish();
            });
        } catch (Exception e) {
            MyLog.d(this, "onComplete " + success, e);
        }
    }

    private void cleanOnFinish() {
        try {
            this.runOnUiThread(() -> {
                if (progress != null) {
                    DialogFactory.dismissSafely(progress);
                    progress = null;
                }
                generatingDemoData = false;
            });
        } catch (Exception e) {
            MyLog.d(this, "cleanOnFinish", e);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        DialogFactory.dismissSafely(progress);
        progress = null;
        MyLog.v(this, "Progress dialog dismissed");
    }
}
