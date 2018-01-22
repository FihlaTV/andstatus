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

package org.andstatus.app.data.checker;

import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author yvolk@yurivolkov.com
 */
class MergeUsers extends DataChecker {

    @Override
    long fixInternal(boolean countOnly) {
        int changedCount = 0;
        for (MbActivity activity : getUsersToMerge()) {
            mergeUser(activity);
            changedCount++;
        }
        return changedCount;
    }

    private Set<MbActivity> getUsersToMerge() {
        final String method = "getUsersToMerge";

        Set<MbActivity> mergeActivities = new ConcurrentSkipListSet<>();
        String sql = "SELECT " + UserTable._ID
                + ", " + UserTable.ORIGIN_ID
                + ", " + UserTable.USER_OID
                + ", " + UserTable.WEBFINGER_ID
                + " FROM " + UserTable.TABLE_NAME
                + " ORDER BY " + UserTable.ORIGIN_ID
                + ", " + UserTable.USER_OID
                ;
        Cursor c = null;
        long rowsCount = 0;
        try {
            MbUser prev = null;
            c = myContext.getDatabase().rawQuery(sql, null);
            while (c.moveToNext()) {
                rowsCount++;
                MbUser user = MbUser.fromOriginAndUserOid(myContext.persistentOrigins().fromId(c.getLong(1)),
                        c.getString(2));
                user.userId = c.getLong(0);
                user.setWebFingerId(c.getString(3));
                if (isTheSameUser(prev, user)) {
                    MbActivity activity = whomToMerge(prev, user);
                    mergeActivities.add(activity);
                    prev = activity.getActor();
                } else {
                    prev = user;
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }

        logger.logProgress(method + " ended, " + rowsCount + " users, " + mergeActivities.size() + " to be merged");
        return mergeActivities;
    }

    private boolean isTheSameUser(MbUser prev, MbUser user) {
        if (prev == null || user == null) {
            return false;
        }
        if (!prev.origin.equals(user.origin)) {
            return false;
        }
        if (!prev.oid.equals(user.oid)) {
            return false;
        }
        return true;
    }

    @NonNull
    private MbActivity whomToMerge(@NonNull MbUser prev, @NonNull MbUser user) {
        MbActivity activity = MbActivity.from(MbUser.EMPTY, MbActivityType.UPDATE);
        activity.setUser(user);
        MbUser mergeWith = prev;
        if (myContext.persistentAccounts().fromUserId(user.userId).isValid()) {
            mergeWith = user;
            activity.setUser(prev);
        }
        activity.setActor(mergeWith);
        return activity;
    }

    private void mergeUser(MbActivity activity) {
        MbUser user = activity.getUser();
        String logMsg = "Merging " + user + " with " + activity.getActor();
        logger.logProgress(logMsg);
        // TODO: clean the code!
        updateColumn(logMsg, activity, ActivityTable.TABLE_NAME, ActivityTable.ACTOR_ID, false);
        updateColumn(logMsg, activity, ActivityTable.TABLE_NAME, ActivityTable.USER_ID, false);

        updateColumn(logMsg, activity, MsgTable.TABLE_NAME, MsgTable.AUTHOR_ID, false);
        updateColumn(logMsg, activity, MsgTable.TABLE_NAME, MsgTable.IN_REPLY_TO_USER_ID, false);

        updateColumn(logMsg, activity, AudienceTable.TABLE_NAME, AudienceTable.USER_ID, true);
        deleteRows(logMsg, user, AudienceTable.TABLE_NAME, AudienceTable.USER_ID);

        deleteRows(logMsg, user, FriendshipTable.TABLE_NAME, FriendshipTable.USER_ID);
        deleteRows(logMsg, user, FriendshipTable.TABLE_NAME, FriendshipTable.FRIEND_ID);

        deleteRows(logMsg, user, DownloadTable.TABLE_NAME, DownloadTable.USER_ID);

        deleteRows(logMsg, user, UserTable.TABLE_NAME, UserTable._ID);
    }

    private void updateColumn(String logMsg, MbActivity activity, String table, String column, boolean ignoreError) {
        String sql = "";
        try {
            sql = "UPDATE "
                    + table
                    + " SET "
                    + column + "=" + activity.getActor().userId
                    + " WHERE "
                    + column + "=" + activity.getUser().userId;
            myContext.getDatabase().execSQL(sql);
        } catch (Exception e) {
            if (!ignoreError) {
                logger.logProgress("Error: " + e.getMessage() + ", SQL:" + sql);
                MyLog.e(this, logMsg + ", SQL:" + sql, e);
            }
        }
    }

    private void deleteRows(String logMsg, MbUser user, String table, String column) {
        String sql = "";
        try {
            sql = "DELETE "
                    + " FROM "
                    + table
                    + " WHERE "
                    + column + "=" + user.userId;
            myContext.getDatabase().execSQL(sql);
        } catch (Exception e) {
            logger.logProgress("Error: " + e.getMessage() + ", SQL:" + sql);
            MyLog.e(this, logMsg + ", SQL:" + sql, e);
        }
    }
}
