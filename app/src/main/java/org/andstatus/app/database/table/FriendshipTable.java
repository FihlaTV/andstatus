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

package org.andstatus.app.database.table;

import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.data.DbUtils;

/**
 * IDs of Users the specified (by {@link FriendshipTable#ACTOR_ID}) actor is following
 * (otherwise known as their "friends").
 */
public final class FriendshipTable {
    public static final String TABLE_NAME = "friendship";
    private FriendshipTable() {
    }

    /**
     * Who is following
     */
    public static final String ACTOR_ID = ActorTable.ACTOR_ID;
    /**
     * Friend by {@link #ACTOR_ID} (is followed by {@link #ACTOR_ID})
     */
    public static final String FRIEND_ID = "friend_id";
    /**
     * boolean ( 1 / 0 ) flag showing
     * if {@link FriendshipTable#FRIEND_ID} is followed by {@link FriendshipTable#ACTOR_ID}
     */
    public static final String FOLLOWED = "followed";

    /** Alias of {@link #ACTOR_ID} */
    public static final String FOLLOWER_ID = "follower_id";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + ACTOR_ID + " INTEGER NOT NULL,"
                + FRIEND_ID + " INTEGER NOT NULL,"
                + FOLLOWED + " BOOLEAN NOT NULL,"

                + " CONSTRAINT pk_friendship PRIMARY KEY ("
                + ACTOR_ID + ", "
                + FRIEND_ID + ")"
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_followers ON " + TABLE_NAME + " ("
                + FRIEND_ID + ", "
                + ACTOR_ID
                + ")"
        );
    }
}
