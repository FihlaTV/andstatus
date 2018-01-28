/* 
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MyQuery {
    private static final String TAG = MyQuery.class.getSimpleName();

    private MyQuery() {
        // Empty
    }

    static String userNameField(ActorInTimeline actorInTimeline) {
        switch (actorInTimeline) {
            case AT_USERNAME:
                return "('@' || " + ActorTable.USERNAME + ")";
            case WEBFINGER_ID:
                return ActorTable.WEBFINGER_ID;
            case REAL_NAME:
                return ActorTable.REAL_NAME;
            case REAL_NAME_AT_USERNAME:
                return "(" + ActorTable.REAL_NAME + " || ' @' || " + ActorTable.USERNAME + ")";
            default:
                return ActorTable.USERNAME;
        }
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * 
     * @param originId - see {@link NoteTable#ORIGIN_ID}
     * @param oid - see {@link NoteTable#NOTE_OID}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link NoteTable#_ID} ). Or 0 if nothing was found.
     */
    public static long oidToId(OidEnum oidEnum, long originId, String oid) {
        return oidToId(null, oidEnum, originId, oid);
    }

    public static long oidToId(SQLiteDatabase database, OidEnum oidEnum, long originId, String oid) {
        if (TextUtils.isEmpty(oid)) {
            return 0;
        }
        String msgLog = "oidToId; " + oidEnum + ", origin=" + originId + ", oid=" + oid;
        String sql;
        switch (oidEnum) {
            case NOTE_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + NoteTable.TABLE_NAME
                        + " WHERE " + NoteTable.ORIGIN_ID + "=" + originId + " AND " + NoteTable.NOTE_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            case ACTOR_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + ActorTable.TABLE_NAME
                        + " WHERE " + ActorTable.ORIGIN_ID + "=" + originId + " AND " + ActorTable.ACTOR_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            case ACTIVITY_OID:
                sql = "SELECT " + BaseColumns._ID + " FROM " + ActivityTable.TABLE_NAME
                        + " WHERE " + ActivityTable.ORIGIN_ID + "=" + originId + " AND " + ActivityTable.ACTIVITY_OID
                        + "=" + quoteIfNotQuoted(oid);
                break;
            default:
                throw new IllegalArgumentException(msgLog + "; Unknown oidEnum");
        }
        return sqlToLong(database, msgLog, sql);
    }

    public static long sqlToLong(SQLiteDatabase databaseIn, String msgLogIn, String sql) {
        String msgLog = StringUtils.notNull(msgLogIn);
        SQLiteDatabase db = databaseIn == null ? MyContextHolder.get().getDatabase() : databaseIn;
        if (db == null) {
            MyLog.v(TAG, msgLog + "; database is null");
            return 0;
        }
        if (TextUtils.isEmpty(sql)) {
            MyLog.v(TAG, msgLog + "; sql is empty");
            return 0;
        }
        String msgLogSql = msgLog + (msgLog.contains(sql) ? "" : "; sql='" + sql +"'");
        long value = 0;
        SQLiteStatement statement = null;
        try {
            statement = db.compileStatement(sql);
            value = statement.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            MyLog.ignored(TAG, e);
            value = 0;
        } catch (Exception e) {
            MyLog.e(TAG, msgLogSql, e);
            value = 0;
        } finally {
            DbUtils.closeSilently(statement);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TAG, msgLogSql + " -> " + value);
        }
        return value;
    }

    /**
     * @return two single quotes for empty/null strings (Use single quotes!)
     */
    public static String quoteIfNotQuoted(String original) {
        if (TextUtils.isEmpty(original)) {
            return "\'\'";
        }
        String quoted = original.trim();
        int firstQuoteIndex = quoted.indexOf('\'');
        if (firstQuoteIndex < 0) {
            return '\'' + quoted + '\'';
        }
        int lastQuoteIndex = quoted.lastIndexOf('\'');
        if (firstQuoteIndex == 0 && lastQuoteIndex == quoted.length()-1) {
            // Already quoted, search quotes inside
            quoted = quoted.substring(1, lastQuoteIndex);
        }
        quoted = quoted.replace("'", "''");
        quoted = '\'' + quoted + '\'';
        return quoted;
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link NoteTable#_ID}
     * @param rebloggerActorId Is needed to find reblog by this actor
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link NoteTable#NOTE_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(OidEnum oe, long entityId, long rebloggerActorId) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, "idToOid: database is null, oe=" + oe + " id=" + entityId);
            return "";
        } else {
            return idToOid(db, oe, entityId, rebloggerActorId);
        }
    }

    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link NoteTable#_ID}
     * @param rebloggerActorId Is needed to find reblog by this actor
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link NoteTable#NOTE_OID} empty string in case of an error
     */
    @NonNull
    public static String idToOid(SQLiteDatabase db, OidEnum oe, long entityId, long rebloggerActorId) {
        String method = "idToOid";
        String oid = "";
        SQLiteStatement prog = null;
        String sql = "";
    
        if (entityId > 0) {
            try {
                switch (oe) {
                    case NOTE_OID:
                        sql = "SELECT " + NoteTable.NOTE_OID + " FROM "
                                + NoteTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId;
                        break;
    
                    case ACTOR_OID:
                        sql = "SELECT " + ActorTable.ACTOR_OID + " FROM "
                                + ActorTable.TABLE_NAME + " WHERE " + BaseColumns._ID + "="
                                + entityId;
                        break;
    
                    case REBLOG_OID:
                        if (rebloggerActorId == 0) {
                            MyLog.e(TAG, method + ": actorId was not defined");
                        }
                        sql = "SELECT " + ActivityTable.ACTIVITY_OID + " FROM "
                                + ActivityTable.TABLE_NAME + " WHERE "
                                + ActivityTable.MSG_ID + "=" + entityId + " AND "
                                + ActivityTable.ACTIVITY_TYPE + "=" + ActivityType.ANNOUNCE.id + " AND "
                                + ActivityTable.ACTOR_ID + "=" + rebloggerActorId;
                        break;
    
                    default:
                        throw new IllegalArgumentException(method + "; Unknown parameter: " + oe);
                }
                prog = db.compileStatement(sql);
                oid = prog.simpleQueryForString();
                
                if (TextUtils.isEmpty(oid) && oe == OidEnum.REBLOG_OID) {
                    // This not reblogged note
                    oid = idToOid(db, OidEnum.NOTE_OID, entityId, 0);
                }
                
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                oid = "";
            } catch (Exception e) {
                MyLog.e(TAG, method, e);
                oid = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + ": " + oe + " + " + entityId + " -> " + oid);
            }
        }
        return oid;
    }

    /** @return ID of the Reblog/Undo reblog activity and the type of the Activity */
    public static Pair<Long, ActivityType> msgIdToLastReblogging(SQLiteDatabase db, long msgId, long actorId) {
        return msgIdToLastOfTypes(db, msgId, actorId, ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE);
    }

    /** @return ID of the last LIKE/UNDO_LIKE activity and the type of the activity */
    @NonNull
    public static Pair<Long, ActivityType> msgIdToLastFavoriting(SQLiteDatabase db, long msgId, long actorId) {
        return msgIdToLastOfTypes(db, msgId, actorId, ActivityType.LIKE, ActivityType.UNDO_LIKE);
    }

    /** @return ID of the last type1 or type2 activity and the type of the activity for the selected actor */
    @NonNull
    public static Pair<Long, ActivityType> msgIdToLastOfTypes(
            SQLiteDatabase db, long msgId, long actorId, ActivityType type1, ActivityType type2) {
        String method = "msgIdToLastOfTypes";
        if (db == null || msgId == 0 || actorId == 0) {
            return new Pair<>(0L, ActivityType.EMPTY);
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable._ID
                + " FROM " + ActivityTable.TABLE_NAME
                + " WHERE " + ActivityTable.MSG_ID + "=" + msgId + " AND "
                + ActivityTable.ACTIVITY_TYPE
                + " IN(" + type1.id + "," + type2.id + ") AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToNext()) {
                return new Pair<>(cursor.getLong(1), ActivityType.fromId(cursor.getLong(0)));
            }
        } catch (Exception e) {
            MyLog.i(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return new Pair<>(0L, ActivityType.EMPTY);
    }

    public static List<Actor> getStargazers(SQLiteDatabase db, @NonNull Origin origin, long msgId) {
        return msgIdToActors(db, origin, msgId, ActivityType.LIKE, ActivityType.UNDO_LIKE);
    }

    public static List<Actor> getRebloggers(SQLiteDatabase db, @NonNull Origin origin, long msgId) {
        return msgIdToActors(db, origin, msgId, ActivityType.ANNOUNCE, ActivityType.UNDO_ANNOUNCE);
    }

    /** @return for each actor (actorId is a key): ID of the last type1 or type2 activity
     *  and the type of the activity */
    @NonNull
    public static List<Actor> msgIdToActors(
            SQLiteDatabase db, @NonNull Origin origin, long msgId, ActivityType typeToReturn, ActivityType undoType) {
        String method = "msgIdToLastOfTypes";
        final List<Long> foundActors = new ArrayList<>();
        final List<Actor> users = new ArrayList<>();
        if (db == null || !origin.isValid() || msgId == 0) {
            return users;
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.ACTOR_ID + ", "
                + ActorTable.WEBFINGER_ID + ", " + TimelineSql.userNameField() + " AS " + ActorTable.ACTIVITY_ACTOR_NAME
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " WHERE " + ActivityTable.MSG_ID + "=" + msgId + " AND "
                + ActivityTable.ACTIVITY_TYPE + " IN(" + typeToReturn.id + "," + undoType.id + ")"
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while(cursor.moveToNext()) {
                long actorId = DbUtils.getLong(cursor, ActivityTable.ACTOR_ID);
                if (!foundActors.contains(actorId)) {
                    foundActors.add(actorId);
                    ActivityType activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
                    if (activityType.equals(typeToReturn)) {
                        Actor actor = Actor.fromOriginAndActorId(origin, actorId);
                        actor.setRealName(DbUtils.getString(cursor, ActorTable.ACTIVITY_ACTOR_NAME));
                        actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID));
                        users.add(actor);
                    }
                }
            }
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return users;
    }

    @NonNull
    public static ActorToNote favoritedAndReblogged(
            SQLiteDatabase db, long msgId, long actorId) {
        String method = "favoritedAndReblogged";
        boolean favoriteFound = false;
        boolean reblogFound = false;
        ActorToNote actorToNote = new ActorToNote();
        if (db == null || msgId == 0 || actorId == 0) {
            return actorToNote;
        }
        String sql = "SELECT " + ActivityTable.ACTIVITY_TYPE + ", " + ActivityTable.SUBSCRIBED
                + " FROM " + ActivityTable.TABLE_NAME + " INNER JOIN " + ActorTable.TABLE_NAME
                + " ON " + ActivityTable.ACTOR_ID + "=" + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + " WHERE " + ActivityTable.MSG_ID + "=" + msgId + " AND "
                + ActivityTable.ACTOR_ID + "=" + actorId
                + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while(cursor.moveToNext()) {
                if (DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED) == TriState.TRUE) {
                    actorToNote.subscribed = true;
                }
                ActivityType activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
                switch (activityType) {
                    case LIKE:
                    case UNDO_LIKE:
                        if (!favoriteFound) {
                            favoriteFound = true;
                            actorToNote.favorited = activityType == ActivityType.LIKE;
                        }
                        break;
                    case ANNOUNCE:
                    case UNDO_ANNOUNCE:
                        if (!reblogFound) {
                            reblogFound = true;
                            actorToNote.reblogged = activityType == ActivityType.ANNOUNCE;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return actorToNote;
    }

    public static String msgIdToUsername(String userIdColumnName, long noteId, ActorInTimeline actorInTimeline) {
        final String method = "msgIdToUsername";
        String userName = "";
        if (noteId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                if (userIdColumnName.contentEquals(ActivityTable.ACTOR_ID)) {
                    // TODO:
                    throw new IllegalArgumentException( method + "; Not implemented \"" + userIdColumnName + "\"");
                } else if(userIdColumnName.contentEquals(NoteTable.AUTHOR_ID) ||
                        userIdColumnName.contentEquals(NoteTable.IN_REPLY_TO_ACTOR_ID)) {
                    sql = "SELECT " + userNameField(actorInTimeline) + " FROM " + ActorTable.TABLE_NAME
                            + " INNER JOIN " + NoteTable.TABLE_NAME + " ON "
                            + NoteTable.TABLE_NAME + "." + userIdColumnName + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + NoteTable.TABLE_NAME + "." + BaseColumns._ID + "=" + noteId;
                } else {
                    throw new IllegalArgumentException( method + "; Unknown name \"" + userIdColumnName + "\"");
                }
                SQLiteDatabase db = MyContextHolder.get().getDatabase();
                if (db == null) {
                    MyLog.v(TAG, method + "; Database is null");
                    return "";
                }
                prog = db.compileStatement(sql);
                userName = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                userName = "";
            } catch (Exception e) {
                MyLog.e(TAG, method, e);
                userName = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + "; " + userIdColumnName + ": " + noteId + " -> " + userName );
            }
        }
        return userName;
    }

    public static String actorIdToWebfingerId(long actorId) {
        return actorIdToName(actorId, ActorInTimeline.WEBFINGER_ID);
    }

    public static String actorIdToName(long actorId, ActorInTimeline actorInTimeline) {
        return idToStringColumnValue(ActorTable.TABLE_NAME, userNameField(actorInTimeline), actorId);
    }

    /**
     * Convenience method to get column value from {@link ActorTable} table
     * @param columnName without table name
     * @param systemId {@link ActorTable#ACTOR_ID}
     * @return 0 in case not found or error
     */
    public static long userIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(null, ActorTable.TABLE_NAME, columnName, systemId);
    }

    public static long idToLongColumnValue(SQLiteDatabase databaseIn, String tableName, String columnName, long systemId) {
        if (systemId == 0) {
            return 0;
        } else {
            return conditionToLongColumnValue(databaseIn, null, tableName, columnName, "t._id=" + systemId);
        }
    }

    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. {@link NoteTable#TABLE_NAME}
     * @param columnName without table name
     * @param condition WHERE part of SQL statement
     * @return 0 in case not found or error or systemId==0
     */
    public static long conditionToLongColumnValue(String tableName, String columnName, String condition) {
        return conditionToLongColumnValue(null, columnName, tableName, columnName, condition);
    }

    public static long conditionToLongColumnValue(SQLiteDatabase databaseIn, String msgLog,
                                                  String tableName, String columnName, String condition) {
        String sql = "SELECT t." + columnName +
                " FROM " + tableName + " AS t" +
                (TextUtils.isEmpty(condition) ? "" : " WHERE " + condition);
        long columnValue = 0;
        if (TextUtils.isEmpty(tableName)) {
            throw new IllegalArgumentException("tableName is empty: " + sql);
        } else if (TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("columnName is empty: " + sql);
        } else {
            columnValue = sqlToLong(databaseIn, msgLog, sql);
        }
        return columnValue;
    }

    @NonNull
    public static String msgIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(NoteTable.TABLE_NAME, columnName, systemId);
    }

    @NonNull
    public static String userIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(ActorTable.TABLE_NAME, columnName, systemId);
    }

    /**
     * Convenience method to get String column value from the 'tableName' table
     * @param tableName e.g. {@link NoteTable#TABLE_NAME}
     * @param columnName without table name
     * @param systemId tableName._id
     * @return not null; "" in a case not found or error or systemId==0
     */
    @NonNull
    private static String idToStringColumnValue(String tableName, String columnName, long systemId) {
        if (systemId == 0) {
            return "";
        }
        return conditionToStringColumnValue(tableName, columnName, "_id=" + systemId);
    }

    @NonNull
    public static String conditionToStringColumnValue(String tableName, String columnName, String condition) {
        String method = "cond2str";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, method + "; Database is null");
            return "";
        }
        String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE " + condition;
        String columnValue = "";
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(method + " tableName or columnName are empty");
        } else if (TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("columnName is empty: " + sql);
        } else {
            try (SQLiteStatement prog = db.compileStatement(sql)) {
                columnValue = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
            } catch (Exception e) {
                MyLog.e(TAG, method + " table='" + tableName + "', column='" + columnName + "'", e);
                return "";
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, method + "; '" + sql + "' -> " + columnValue );
            }
        }
        return TextUtils.isEmpty(columnValue) ? "" : columnValue;
    }

    public static long noteIdToActorId(String msgUserIdColumnName, long systemId) {
        long actorId = 0;
        try {
            if (msgUserIdColumnName.contentEquals(ActivityTable.ACTOR_ID) ||
                    msgUserIdColumnName.contentEquals(NoteTable.AUTHOR_ID) ||
                    msgUserIdColumnName.contentEquals(NoteTable.IN_REPLY_TO_ACTOR_ID)) {
                actorId = msgIdToLongColumnValue(msgUserIdColumnName, systemId);
            } else {
                throw new IllegalArgumentException("msgIdToUserId; Illegal column '" + msgUserIdColumnName + "'");
            }
        } catch (Exception e) {
            MyLog.e(TAG, "msgIdToUserId", e);
            return 0;
        }
        return actorId;
    }

    public static long msgIdToOriginId(long systemId) {
        return msgIdToLongColumnValue(NoteTable.ORIGIN_ID, systemId);
    }

    public static TriState activityIdToTriState(String columnName, long systemId) {
        return TriState.fromId(activityIdToLongColumnValue(columnName, systemId));
    }

    /**
     * Convenience method to get column value from {@link ActivityTable} table
     * @param columnName without table name
     * @param systemId  MyDatabase.MSG_TABLE_NAME + "." + Msg._ID
     * @return 0 in case not found or error
     */
    public static long activityIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(null, ActivityTable.TABLE_NAME, columnName, systemId);
    }

    public static TriState msgIdToTriState(String columnName, long systemId) {
        return TriState.fromId(msgIdToLongColumnValue(columnName, systemId));
    }

    /**
     * Convenience method to get column value from {@link NoteTable} table
     * @param columnName without table name
     * @param systemId  MyDatabase.MSG_TABLE_NAME + "." + Msg._ID
     * @return 0 in case not found or error
     */
    public static long msgIdToLongColumnValue(String columnName, long systemId) {
        switch (columnName) {
            case ActivityTable.ACTOR_ID:
            case ActivityTable.AUTHOR_ID:
            case ActivityTable.UPDATED_DATE:
            case ActivityTable.LAST_UPDATE_ID:
                return msgIdToLongActivityColumnValue(null, columnName, systemId);
            default:
                return idToLongColumnValue(null, NoteTable.TABLE_NAME, columnName, systemId);
        }
    }

    /** Data from the latest activity for this note... */
    public static long msgIdToLongActivityColumnValue(SQLiteDatabase databaseIn, String columnNameIn, long noteId) {
        final String method = "msgId2activity" + columnNameIn;
        final String columnName;
        final String condition;
        switch (columnNameIn) {
            case ActivityTable._ID:
            case ActivityTable.ACTOR_ID:
                columnName = columnNameIn;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ","
                        + ActivityType.ANNOUNCE.id + ","
                        + ActivityType.LIKE.id + ")";
                break;
            case ActivityTable.AUTHOR_ID:
                columnName = ActivityTable.ACTOR_ID;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ")";
                break;
            case ActivityTable.LAST_UPDATE_ID:
            case ActivityTable.UPDATED_DATE:
                columnName = columnNameIn.equals(ActivityTable.LAST_UPDATE_ID) ? ActivityTable._ID : columnNameIn;
                condition = ActivityTable.ACTIVITY_TYPE + " IN("
                        + ActivityType.CREATE.id + ","
                        + ActivityType.UPDATE.id + ","
                        + ActivityType.DELETE.id + ")";
                break;
            default:
                throw new IllegalArgumentException( method + "; Illegal column '" + columnNameIn + "'");
        }
        return MyQuery.conditionToLongColumnValue(databaseIn, method, ActivityTable.TABLE_NAME, columnName,
                ActivityTable.MSG_ID + "=" + noteId +  " AND " + condition
                        + " ORDER BY " + ActivityTable.UPDATED_DATE + " DESC LIMIT 1");
    }

    public static long webFingerIdToId(long originId, String webFingerId) {
        return actorColumnValueToId(originId, ActorTable.WEBFINGER_ID, webFingerId);
    }
    
    /**
     * Lookup the Actor's id based on the username in the Originating system
     * 
     * @param originId - see {@link NoteTable#ORIGIN_ID}
     * @param username - see {@link ActorTable#USERNAME}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link ActorTable#_ID} ), 0 if not found
     */
    public static long usernameToId(long originId, String username) {
        return actorColumnValueToId(originId, ActorTable.USERNAME, username);
    }

    private static long actorColumnValueToId(long originId, String columnName, String columnValue) {
        final String method = "actor" + columnName + "ToId";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(TAG, method + "; Database is null");
            return 0;
        }
        long id = 0;
        SQLiteStatement prog = null;
        String sql = "";
        try {
            sql = "SELECT " + BaseColumns._ID + " FROM " + ActorTable.TABLE_NAME
                    + " WHERE " + ActorTable.ORIGIN_ID + "=" + originId + " AND " + columnName + "='"
                    + columnValue + "'";
            prog = db.compileStatement(sql);
            id = prog.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            MyLog.ignored(MyQuery.TAG, e);
            id = 0;
        } catch (Exception e) {
            MyLog.e(MyQuery.TAG, method + ": SQL:'" + sql + "'", e);
            id = 0;
        } finally {
            DbUtils.closeSilently(prog);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(MyQuery.TAG, method + ":" + originId + "+" + columnValue + " -> " + id);
        }
        return id;
    }

    @NonNull
    public static Set<Long> getFollowersIds(long actorId) {
        String where = FriendshipTable.FRIEND_ID + "=" + actorId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.ACTOR_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;
        return getLongs(sql);
    }

    @NonNull
    public static Set<Long> getFriendsIds(long actorId) {
        String where = FriendshipTable.ACTOR_ID + "=" + actorId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.FRIEND_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;
        return getLongs(sql);
    }

    public static long getNumberOfNotificationEvents(@NonNull NotificationEventType event) {
        return getCountOfActivities(ActivityTable.NEW_NOTIFICATION_EVENT + "=" + event.id);
    }

    public static long getCountOfActivities(@NonNull String condition) {
        String sql = "SELECT COUNT(*) FROM " + ActivityTable.TABLE_NAME
                + (TextUtils.isEmpty(condition) ? "" : " WHERE " + condition);
        Set<Long> numbers = getLongs(sql);
        return numbers.isEmpty() ? 0 : numbers.iterator().next();
    }

    @NonNull
    public static Set<Long> getLongs(String sql) {
        return getLongs(MyContextHolder.get().getDatabase(), sql);
    }

    @NonNull
    public static Set<Long> getLongs(SQLiteDatabase db, String sql) {
        final String method = "getLongs";
        Set<Long> ids = new HashSet<>();
        if (db == null) {
            MyLog.v(TAG, method + "; Database is null");
            return ids;
        }
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
            }
        } catch (Exception e) {
            MyLog.i(TAG, method + "; SQL:'" + sql + "'", e);
        }
        return ids;
    }

    /**
     *  MyAccounts' actorIds, who follow the specified Actor
     */
    @NonNull
    public static Set<Long> getMyFollowersOf(long actorId) {
        SqlActorIds selectedAccounts = SqlActorIds.fromTimeline(Timeline.EMPTY);

        String where = FriendshipTable.ACTOR_ID + selectedAccounts.getSql()
                + " AND " + FriendshipTable.FRIEND_ID + "=" + actorId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.ACTOR_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;

        return getLongs(sql);
    }

    public static boolean isFollowing(long followerId, long friendId) {
        String where = FriendshipTable.ACTOR_ID + "=" + followerId
                + " AND " + FriendshipTable.FRIEND_ID + "=" + friendId
                + " AND " + FriendshipTable.FOLLOWED + "=1";
        String sql = "SELECT " + FriendshipTable.ACTOR_ID
                + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + where;

        return !getLongs(sql).isEmpty();
    }

    public static String msgInfoForLog(long msgId) {
        StringBuilder builder = new StringBuilder();
        I18n.appendWithComma(builder, "msgId:" + msgId);
        String oid = idToOid(OidEnum.NOTE_OID, msgId, 0);
        I18n.appendWithComma(builder, "oid" + (TextUtils.isEmpty(oid) ? " is empty" : ":'" + oid + "'"));
        String body = MyHtml.fromHtml(msgIdToStringColumnValue(NoteTable.BODY, msgId));
        I18n.appendAtNewLine(builder, "text:'" + body + "'");
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(msgIdToLongColumnValue(NoteTable.ORIGIN_ID, msgId));
        I18n.appendAtNewLine(builder, origin.toString());
        return builder.toString();
    }

    public static long conversationOidToId(long originId, String conversationOid) {
        return conditionToLongColumnValue(NoteTable.TABLE_NAME, NoteTable.CONVERSATION_ID,
                NoteTable.ORIGIN_ID + "=" + originId
                + " AND " + NoteTable.CONVERSATION_OID + "=" + quoteIfNotQuoted(conversationOid));
    }

    @NonNull
    public static String msgIdToConversationOid(long msgId) {
        if (msgId == 0) {
            return "";
        }
        String oid = msgIdToStringColumnValue(NoteTable.CONVERSATION_OID, msgId);
        if (!TextUtils.isEmpty(oid)) {
            return oid;
        }
        long conversationId = MyQuery.msgIdToLongColumnValue(NoteTable.CONVERSATION_ID, msgId);
        if (conversationId == 0) {
            return idToOid(OidEnum.NOTE_OID, msgId, 0);
        }
        oid = msgIdToStringColumnValue(NoteTable.CONVERSATION_OID, conversationId);
        if (!TextUtils.isEmpty(oid)) {
            return oid;
        }
        return idToOid(OidEnum.NOTE_OID, conversationId, 0);
    }
}
