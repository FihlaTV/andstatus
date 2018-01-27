/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TimelineSql {

    private TimelineSql() {
        // Empty
    }

    /**
     * @param uri the same as uri for
     *            {@link MyProvider#query(Uri, String[], String, String[], String)}
     * @param projection Projection
     * @return String for {@link SQLiteQueryBuilder#setTables(String)}
     */
    static String tablesForTimeline(Uri uri, String[] projection) {
        Timeline timeline = Timeline.fromParsedUri(MyContextHolder.get(), ParsedUri.fromUri(uri), "");
        SqlActorIds selectedAccounts = SqlActorIds.fromTimeline(timeline);
    
        Collection<String> columns = new java.util.HashSet<>(Arrays.asList(projection));

        SqlWhere activityWhere = new SqlWhere();
        activityWhere.append(ActivityTable.UPDATED_DATE + ">0");
        SqlWhere msgWhere = new SqlWhere();

        switch (timeline.getTimelineType()) {
            case FOLLOWERS:
            case MY_FOLLOWERS:
            case FRIENDS:
            case MY_FRIENDS:
                String fUserIdColumnName = FriendshipTable.FRIEND_ID;
                String fUserLinkedUserIdColumnName = FriendshipTable.ACTOR_ID;
                if (timeline.getTimelineType() == TimelineType.FOLLOWERS ||
                        timeline.getTimelineType() == TimelineType.MY_FOLLOWERS) {
                    fUserIdColumnName = FriendshipTable.ACTOR_ID;
                    fUserLinkedUserIdColumnName = FriendshipTable.FRIEND_ID;
                }
                // Select only the latest message from each Friend's timeline
                String activityIds = "SELECT " + ActorTable.ACTOR_ACTIVITY_ID
                        + " FROM " + ActorTable.TABLE_NAME + " AS u1"
                        + " INNER JOIN " + FriendshipTable.TABLE_NAME
                        + " ON (" + FriendshipTable.TABLE_NAME + "." + fUserIdColumnName + "=u1." + BaseColumns._ID
                        + " AND " + FriendshipTable.TABLE_NAME + "."
                        + fUserLinkedUserIdColumnName + selectedAccounts.getSql()
                        + " AND " + FriendshipTable.FOLLOWED + "=1"
                        + ")";
                activityWhere.append(BaseColumns._ID + " IN (" + activityIds + ")");
                break;
            case HOME:
                activityWhere.append(ActivityTable.SUBSCRIBED + "=" + TriState.TRUE.id);
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.PRIVATE + "!=" + TriState.TRUE.id);
                break;
            case PRIVATE:
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.PRIVATE + "=" + TriState.TRUE.id);
                break;
            case FAVORITES:
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.FAVORITED + "=" + TriState.TRUE.id);
                break;
            case MENTIONS:
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.MENTIONED + "=" + TriState.TRUE.id);
                break;
            case PUBLIC:
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.PRIVATE + "!=" + TriState.TRUE.id);
                break;
            case DRAFTS:
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.MSG_STATUS + "=" + DownloadStatus.DRAFT.save());
                break;
            case OUTBOX:
                msgWhere.append(ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.MSG_STATUS + "=" + DownloadStatus.SENDING.save());
                break;
            case USER:
            case SENT:
                SqlActorIds userIds = SqlActorIds.fromTimeline(timeline);
                // All actions by this User(s)
                activityWhere.append(ActivityTable.ACTOR_ID + userIds.getSql());
                break;
            case NOTIFICATIONS:
                activityWhere.append(ActivityTable.NOTIFIED + "=" + TriState.TRUE.id);
                break;
            default:
                break;
        }

        if (timeline.getTimelineType().isAtOrigin() && !timeline.isCombined()) {
            activityWhere.append(ActivityTable.ORIGIN_ID + "=" + timeline.getOrigin().getId());
        }
        if (timeline.getTimelineType().isForAccount() && !timeline.isCombined()) {
            activityWhere.append(ActivityTable.ACCOUNT_ID + "=" + timeline.getMyAccount().getActorId());
        }
        String  tables = "(SELECT * FROM " + ActivityTable.TABLE_NAME + activityWhere.getWhere()
                + ") AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS
                + (msgWhere.isEmpty() ? " LEFT" : " INNER") + " JOIN "
                + MsgTable.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS
                + " ON (" + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "="
                    + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.MSG_ID
                    + msgWhere.getAndWhere() + ")";

        if (columns.contains(ActorTable.AUTHOR_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + BaseColumns._ID + ", " 
                    + TimelineSql.userNameField() + " AS " + ActorTable.AUTHOR_NAME
                    + " FROM " + ActorTable.TABLE_NAME + ") AS author ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.AUTHOR_ID + "=author."
                    + BaseColumns._ID;

            if (columns.contains(DownloadTable.AVATAR_FILE_NAME)) {
                tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                        + DownloadTable.USER_ID + ", "
                        + DownloadTable.DOWNLOAD_STATUS + ", "
                        + DownloadTable.FILE_NAME
                        + " FROM " + DownloadTable.TABLE_NAME + ") AS " + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS
                        + " ON "
                        + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_STATUS
                        + "=" + DownloadStatus.LOADED.save() + " AND "
                        + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.USER_ID
                        + "=" + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.AUTHOR_ID;
            }
        }
        if (columns.contains(DownloadTable.IMAGE_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (" +
                    "SELECT "
                    + DownloadTable._ID + ", "
                    + DownloadTable.MSG_ID + ", "
                    + DownloadTable.CONTENT_TYPE + ", "
                    + (columns.contains(DownloadTable.IMAGE_URL) ? DownloadTable.URI + ", " : "")
                    + DownloadTable.FILE_NAME
                    + " FROM " + DownloadTable.TABLE_NAME
                    + " WHERE " + DownloadTable.MSG_ID + "!=0"
                    + ") AS " + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS
                    +  " ON "
                    + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.CONTENT_TYPE
                    + "=" + MyContentType.IMAGE.save() + " AND " 
                    + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.MSG_ID
                    + "=" + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.MSG_ID;
        }
        if (columns.contains(ActorTable.IN_REPLY_TO_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + ActorTable.IN_REPLY_TO_NAME
                    + " FROM " + ActorTable.TABLE_NAME + ") AS prevAuthor ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.IN_REPLY_TO_USER_ID
                    + "=prevAuthor." + BaseColumns._ID;
        }
        return tables;
    }

    /**
     * Table columns to use for activities
     */
    public static Set<String> getActivityProjection() {
        Set<String> columnNames = getTimelineProjection();
        columnNames.add(ActivityTable.INS_DATE);
        columnNames.add(ActivityTable.UPDATED_DATE);
        columnNames.add(ActivityTable.ACTIVITY_TYPE);
        columnNames.add(ActivityTable.ACTOR_ID);
        columnNames.add(ActivityTable.MSG_ID);
        columnNames.add(ActivityTable.USER_ID);
        return columnNames;
    }

    /** 
     * Table columns to use for messages content
     */
    public static Set<String> getTimelineProjection() {
        Set<String> columnNames = getBaseProjection();
        columnNames.add(ActivityTable.ACTIVITY_ID);
        columnNames.add(MsgTable.AUTHOR_ID);
        columnNames.add(ActivityTable.ACTOR_ID);
        columnNames.add(MsgTable.VIA);
        columnNames.add(MsgTable.REBLOGGED);
        return columnNames;
    }

    private static Set<String> getBaseProjection() {
        Set<String> columnNames = new HashSet<>();
        columnNames.add(ActivityTable.MSG_ID);
        columnNames.add(ActivityTable.ORIGIN_ID);
        columnNames.add(ActorTable.AUTHOR_NAME);
        columnNames.add(MsgTable.BODY);
        columnNames.add(MsgTable.IN_REPLY_TO_MSG_ID);
        columnNames.add(ActorTable.IN_REPLY_TO_NAME);
        columnNames.add(MsgTable.FAVORITED);
        columnNames.add(ActivityTable.INS_DATE); // ??
        columnNames.add(MsgTable.UPDATED_DATE);
        columnNames.add(MsgTable.MSG_STATUS);
        columnNames.add(ActivityTable.ACCOUNT_ID);
        if (MyPreferences.getShowAvatars()) {
            columnNames.add(MsgTable.AUTHOR_ID);
            columnNames.add(DownloadTable.AVATAR_FILE_NAME);
        }
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            columnNames.add(DownloadTable.IMAGE_ID);
            columnNames.add(DownloadTable.IMAGE_FILE_NAME);
        }
        if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, true)
                || SharedPreferencesUtil.getBoolean(
                MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false)) {
            columnNames.add(MsgTable.IN_REPLY_TO_USER_ID);
        }
        return columnNames;
    }

    public static String[] getConversationProjection() {
        Set<String> columnNames = getBaseProjection();
        columnNames.add(MsgTable.AUTHOR_ID);
        columnNames.add(ActivityTable.ACTOR_ID);
        columnNames.add(MsgTable.VIA);
        columnNames.add(MsgTable.REBLOGGED);
        return columnNames.toArray(new String[]{});
    }

    @NonNull
    public static String userColumnNameToNameAtTimeline(Cursor cursor, String columnName, boolean showOrigin) {
        return userColumnIndexToNameAtTimeline(cursor, cursor.getColumnIndex(columnName), showOrigin);
    }

    @NonNull
    public static String userColumnIndexToNameAtTimeline(Cursor cursor, int columnIndex, boolean showOrigin) {
        String userName = "";
        if (columnIndex >= 0) {
            userName = cursor.getString(columnIndex);
            if (TextUtils.isEmpty(userName)) {
                userName = "";
            }
        }
        if (showOrigin) {
            long originId = DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID);
            if (originId != 0) {
                Origin origin = MyContextHolder.get().persistentOrigins().fromId(originId);
                userName += " / " + origin.getName();
                if (origin.getOriginType() == OriginType.GNUSOCIAL &&
                        MyPreferences.isShowDebuggingInfoInUi()) {
                    long authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);
                    if (authorId != 0) {
                        userName += " id:" + MyQuery.idToOid(OidEnum.ACTOR_OID, authorId, 0);
                    }
                }
            }
        }
        return userName;
    }

    public static String userNameField() {
        ActorInTimeline actorInTimeline = MyPreferences.getUserInTimeline();
        return MyQuery.userNameField(actorInTimeline);
    }
    
}
