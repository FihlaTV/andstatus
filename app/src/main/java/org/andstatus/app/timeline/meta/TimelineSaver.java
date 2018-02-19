/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline.meta;

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Save changes to Timelines not on UI thread.
 * Optionally creates default timelines: for all or for one account.
 * @author yvolk@yurivolkov.com
 */
public class TimelineSaver extends MyAsyncTask<Void, Void, Void> {
    private static final AtomicBoolean executing = new AtomicBoolean(false);
    private final MyContext myContext;
    private boolean addDefaults = false;
    private MyAccount myAccount = MyAccount.EMPTY;

    public TimelineSaver(@NonNull MyContext myContext) {
        super(PoolEnum.QUICK_UI);
        this.myContext = myContext;
    }

    public TimelineSaver setAccount(@NonNull MyAccount myAccount) {
        this.myAccount = myAccount;
        return this;
    }

    public TimelineSaver setAddDefaults(boolean addDefaults) {
        this.addDefaults = addDefaults;
        return this;
    }

    @Override
    protected Void doInBackground2(Void... params) {
        executeSynchronously();
        return null;
    }

    public void executeNotOnUiThread() {
        if (isUiThread()) {
            AsyncTaskLauncher.execute(this, false, this);
        } else {
            executeSynchronously();
        }
    }

    private void executeSynchronously() {
        long count = 10;
        boolean lockReceived = false;
        while (count > 0) {
            lockReceived = executing.compareAndSet(false, true);
            if (lockReceived) {
                break;
            }
            count--;
            if (DbUtils.waitMs(this, 1000)) {
                break;
            }
        }
        if (lockReceived) {
            executingLockReceived();
        }
    }

    private void executingLockReceived() {
        try {
            if (addDefaults) {
               if (myAccount == MyAccount.EMPTY) {
                   addDefaultTimelinesIfNoneFound();
               } else {
                   addDefaultMyAccountTimelinesIfNoneFound(myAccount);
               }
            }
            for (Timeline timeline : timelines().values()) {
                timeline.save(myContext);
            }
        } finally {
            executing.set(false);
        }
    }

    private void addDefaultTimelinesIfNoneFound() {
        for (MyAccount ma : myContext.accounts().get()) {
            addDefaultMyAccountTimelinesIfNoneFound(ma);
        }
    }

    private void addDefaultMyAccountTimelinesIfNoneFound(MyAccount ma) {
        if (ma.isValid() && timelines().filter(false, TriState.FALSE, TimelineType.UNKNOWN, ma,
                Origin.EMPTY).isEmpty()) {
            addDefaultCombinedTimelinesIfNoneFound();
            addDefaultOriginTimelinesIfNoneFound(ma.getOrigin());

            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACTOR_ID + "=" + ma.getActorId());
            if (timelineId == 0) addDefaultForAccount(myContext, ma);
        }
    }

    private void addDefaultCombinedTimelinesIfNoneFound() {
        if (timelines().filter(false, TriState.TRUE, TimelineType.UNKNOWN, MyAccount.EMPTY,
                Origin.EMPTY).isEmpty()) {
            long timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    TimelineTable._ID, TimelineTable.ACTOR_ID + "=0 AND " + TimelineTable.ORIGIN_ID + "=0");
            if (timelineId == 0) addDefaultCombined();
        }
    }

    private void addDefaultOriginTimelinesIfNoneFound(Origin origin) {
        if (origin.isValid()) {
            long timelineId = MyQuery.conditionToLongColumnValue(
                    myContext.getDatabase(),
                    origin.getName(),
                    TimelineTable.TABLE_NAME,
                    TimelineTable._ID,
                    TimelineTable.ORIGIN_ID + "=" + origin.getId() + " AND " +
                            TimelineTable.TIMELINE_TYPE + "='" + TimelineType.EVERYTHING.save() + "'");
            if (timelineId == 0) addDefaultForOrigin(myContext, origin);
        }
    }

    public List<Timeline> addDefaultForAccount(MyContext myContext, MyAccount myAccount) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.getDefaultMyAccountTimelineTypes()) {
            final Timeline timeline = myContext.timelines().get(0, timelineType, myAccount.getActorId(), Origin.EMPTY, "");
            if (timeline.getId() == 0) {
                saveNewDefaultTimeline(timeline);
                timelines.add(timeline);
            }
        }
        return timelines;
    }

    private Collection<Timeline> addDefaultForOrigin(MyContext myContext, Origin origin) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.getDefaultOriginTimelineTypes()) {
            if (origin.getOriginType().isTimelineTypeSyncable(timelineType)
                    || timelineType.equals(TimelineType.EVERYTHING)) {
                saveNewDefaultTimeline(myContext.timelines().get(0, timelineType, 0, origin, ""));
            }
        }
        return timelines;
    }

    /**
     * @return Newly added timelines
     */
    public List<Timeline> addDefaultCombined() {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.values()) {
            if (timelineType.isSelectable()) {
                final Timeline timeline = myContext.timelines().get(0, timelineType, 0, Origin.EMPTY, "");
                if (timeline.getId() == 0) {
                    saveNewDefaultTimeline(timeline);
                    timelines.add(timeline);
                }
            }
        }
        return timelines;
    }

    private void saveNewDefaultTimeline(Timeline timeline) {
        timeline.setDisplayedInSelector(DisplayedInSelector.IN_CONTEXT);
        timeline.setSyncedAutomatically(timeline.getTimelineType().isSyncedAutomaticallyByDefault());
        timeline.save(myContext);
    }

    @NonNull
    private PersistentTimelines timelines() {
        return myContext.timelines();
    }

}
