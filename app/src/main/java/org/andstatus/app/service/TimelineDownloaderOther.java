/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

class TimelineDownloaderOther extends TimelineDownloader {
    private static final int YOUNGER_MESSAGES_TO_DOWNLOAD_MAX = 200;
    private static final int OLDER_MESSAGES_TO_DOWNLOAD_MAX = 40;
    private static final int LATEST_MESSAGES_TO_DOWNLOAD_MAX = 20;

    @Override
    public void download() throws ConnectionException {
        if (!getTimeline().isSyncable()) {
            throw new IllegalArgumentException("Timeline cannot be synced: " + getTimeline());
        }

        TimelineSyncTracker syncTracker = new TimelineSyncTracker(getTimeline(), isSyncYounger());
        long hours = MyPreferences.getDontSynchronizeOldMessages();
        boolean downloadingLatest = false;
        if (hours > 0 && RelativeTime.moreSecondsAgoThan(syncTracker.getPreviousSyncedDate(),
                TimeUnit.HOURS.toSeconds(hours))) {
            downloadingLatest = true;
            syncTracker.clearPosition();
        } else if (syncTracker.getPreviousPosition().isEmpty()) {
            downloadingLatest = true;
        }
        
        if (MyLog.isLoggable(this, MyLog.DEBUG)) {
            String strLog = "Loading "
            + (downloadingLatest ? "latest " : "")
            + execContext.getCommandData().toCommandSummary(execContext.getMyContext());
            if (syncTracker.getPreviousItemDate() > 0) { strLog +=
                "; last Timeline item at=" + (new Date(syncTracker.getPreviousItemDate()).toString())
                + "; last time downloaded at=" +  (new Date(syncTracker.getPreviousSyncedDate()).toString());
            }
            MyLog.d(this, strLog);
        }
        String actorOid = getActorOid();
        int toDownload = downloadingLatest ? LATEST_MESSAGES_TO_DOWNLOAD_MAX :
                (isSyncYounger() ? YOUNGER_MESSAGES_TO_DOWNLOAD_MAX : OLDER_MESSAGES_TO_DOWNLOAD_MAX);
        TimelinePosition previousPosition = syncTracker.getPreviousPosition();
        syncTracker.onTimelineDownloaded();

        DataUpdater di = new DataUpdater(execContext);
        for (int loopCounter=0; loopCounter < 100; loopCounter++ ) {
            try {
                int limit = execContext.getMyAccount().getConnection().fixedDownloadLimit(
                        toDownload, getTimeline().getTimelineType().getConnectionApiRoutine());
                List<AActivity> activities;
                switch (getTimeline().getTimelineType()) {
                    case SEARCH:
                        activities = execContext.getMyAccount().getConnection().searchNotes(
                                isSyncYounger() ? previousPosition : TimelinePosition.EMPTY,
                                isSyncYounger() ? TimelinePosition.EMPTY : previousPosition,
                                limit, getTimeline().getSearchQuery());
                        break;
                    default:
                        activities = execContext.getMyAccount().getConnection().getTimeline(
                                getTimeline().getTimelineType().getConnectionApiRoutine(),
                                isSyncYounger() ? previousPosition : TimelinePosition.EMPTY,
                                isSyncYounger() ? TimelinePosition.EMPTY : previousPosition,
                                limit, actorOid);
                        break;
                }
                for (AActivity activity : activities) {
                    toDownload--;
                    syncTracker.onNewMsg(activity.getTimelinePosition(), activity.getUpdatedDate());
                    if (!activity.isSubscribedByMe().equals(TriState.FALSE)
                        && activity.getUpdatedDate() > 0
                        && execContext.getTimeline().getTimelineType().isSubscribedByMe()) {
                        activity.setSubscribedByMe(TriState.TRUE);
                    }
                    di.onActivity(activity, false);
                }
                if (toDownload <= 0 || activities.isEmpty() || previousPosition.equals(syncTracker.getPreviousPosition())) {
                    break;
                }
                previousPosition = syncTracker.getPreviousPosition();
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                    throw e;
                }
                if (previousPosition.isEmpty()) {
                    throw ConnectionException.hardConnectionException("No last position", e);
                }
                MyLog.d(this, "The timeline was not found, last position='" + previousPosition +"'", e);
                previousPosition = TimelinePosition.EMPTY;
            }
        }
        di.saveLum();
    }

    @NonNull
    private String getActorOid() throws ConnectionException {
        long actorId = execContext.getCommandData().getUserId();
        if (actorId == 0) {
            if (getTimeline().getMyAccount().isValid()) {
                return getTimeline().getMyAccount().getActorOid();
            }
        } else {
            String actorOid =  MyQuery.idToOid(OidEnum.ACTOR_OID, actorId, 0);
            if (TextUtils.isEmpty(actorOid)) {
                throw new ConnectionException("User oId is not found for id=" + actorId + ", timeline:" + getTimeline());
            }
            return actorOid;
        }
        if (getTimeline().getTimelineType().isForActor()) {
            throw new ConnectionException("No user for the timeline:" + getTimeline());
        }
        return "";
    }
}
