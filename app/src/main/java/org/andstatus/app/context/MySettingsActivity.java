/* 
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.example.android.supportv7.app.AppCompatPreferenceActivity;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import java.util.List;

/** See http://developer.android.com/guide/topics/ui/settings.html
 *  Source of the {@link AppCompatPreferenceActivity} class is here:
 *  https://github.com/android/platform_development/blob/master/samples/Support7Demos/src/com/example/android/supportv7/app/AppCompatPreferenceActivity.java
 * */
public class MySettingsActivity extends AppCompatPreferenceActivity {

    public static final String ANDROID_FRAGMENT_ARGUMENTS_KEY = ":android:show_fragment_args";
    public static final String PREFERENCES_GROUPS_KEY = "preferencesGroup";
    public static final String ANDROID_NO_HEADERS_KEY = ":android:no_headers";
    private boolean restartApp = false;
    private long mPreferencesChangedAt = MyPreferences.getPreferencesChangeTime();
    private long mInstanceId = 0;
    private boolean resumedOnce = false;

    /**
     * Based on http://stackoverflow.com/questions/14001963/finish-all-activities-at-a-time
     */
    public static void closeAllActivities(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), MySettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(IntentExtra.FINISH.key, true);
        context.startActivity(intent);
    }

    protected void onCreate(Bundle savedInstanceState) {
        resumedOnce = false;
        MyTheme.loadTheme(this);
        super.onCreate(savedInstanceState);
        ViewGroup root = (ViewGroup) findViewById (android.R.id.content).getParent();
        if (root != null) {
            Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.action_bar, root, false);
            root.addView(bar, 0);
            setSupportActionBar(bar);
        }

        if (isRootScreen() && MyContextHolder.initializeThenRestartMe(this)) {
            return;
        }
        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getTitleResId());
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        boolean isNew = mInstanceId == 0;
        if (isNew) {
            mInstanceId = InstanceId.next();
        }
        logEvent("onCreate", isNew ? "" : "Reuse the same");
        parseNewIntent(getIntent());
    }

    private boolean isRootScreen() {
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            return !bundle.getBoolean(ANDROID_NO_HEADERS_KEY);
        }
        return true;
    }

    private int getTitleResId() {
        int titleResId = R.string.settings_activity_title;
        if (!isRootScreen()) {
            Bundle bundle = getIntent().getBundleExtra(ANDROID_FRAGMENT_ARGUMENTS_KEY);
            if (bundle != null) {
                MyPreferencesGroupsEnum preferencesGroup = MyPreferencesGroupsEnum.load(
                        bundle.getString(PREFERENCES_GROUPS_KEY));
                if (preferencesGroup != MyPreferencesGroupsEnum.UNKNOWN) {
                    titleResId = preferencesGroup.getTitleResId();
                }
            }
        }
        return titleResId;
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return MySettingsFragment.class.getName().equals(fragmentName);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        logEvent("onNewIntent", "");
        super.onNewIntent(intent);
        parseNewIntent(intent);
    }

    private void parseNewIntent(Intent intent) {
        if (intent.getBooleanExtra(IntentExtra.FINISH.key, false)) {
            logEvent("parseNewIntent", "finish requested");
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPreferencesChangedAt < MyPreferences.getPreferencesChangeTime() || !MyContextHolder.get().initialized()) {
            logEvent("onResume", "Recreating");
            MyContextHolder.initializeThenRestartMe(this);
            return;
        }
        if (isRootScreen()) {
            MyContextHolder.get().setInForeground(true);
            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();
        }
        resumedOnce = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        logEvent("onPause", "");
        if (isRootScreen()) {
            MyContextHolder.get().setInForeground(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (isRootScreen()) {
                    closeAndRestartApp();
                } else {
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void closeAndRestartApp() {
        logEvent("closeAndRestartApp", "");
        restartApp = true;
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isRootScreen()
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            closeAndRestartApp();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * See http://stackoverflow.com/questions/1397361/how-do-i-restart-an-android-activity
     */
    public static void restartMe(Activity activity) {
        Intent intent = activity.getIntent();
        activity.finish();
        activity.startActivity(intent);
    }

    @Override
    public void finish() {
        logEvent("finish", restartApp ? " and return" : "");
        super.finish();
        if (resumedOnce) {
            MyContextHolder.setExpiredIfConfigChanged();
            if (restartApp) {
                TimelineActivity.goHome(this);
            }
        }
    }

    private void logEvent(String method, String msgLog_in) {
        if (MyLog.isVerboseEnabled()) {
            String msgLog = msgLog_in + (isRootScreen() ? "; rootScreen" : "");
            Bundle bundle = getIntent().getBundleExtra(ANDROID_FRAGMENT_ARGUMENTS_KEY);
            if (bundle != null) {
                msgLog += "; preferenceGroup:" + bundle.getString(PREFERENCES_GROUPS_KEY);
            }
            MyLog.v(this, method + "; " + msgLog);
        }
    }
}
