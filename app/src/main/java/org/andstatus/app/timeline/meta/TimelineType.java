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

package org.andstatus.app.timeline.meta;

import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.lang.SelectableEnum;
import org.andstatus.app.net.social.Connection;

public enum TimelineType implements SelectableEnum {
    UNKNOWN("unknown", R.string.timeline_title_unknown, Connection.ApiRoutineEnum.DUMMY),
    /** The Home timeline and other information (replies...). */
    HOME("home", R.string.timeline_title_home, Connection.ApiRoutineEnum.HOME_TIMELINE),
    NOTIFICATIONS("notifications", R.string.notifications_title, Connection.ApiRoutineEnum.NOTIFICATIONS_TIMELINE),
    PUBLIC("public", R.string.timeline_title_public, Connection.ApiRoutineEnum.PUBLIC_TIMELINE),
    EVERYTHING("everything", R.string.timeline_title_everything, Connection.ApiRoutineEnum.DUMMY),
    SEARCH("search", R.string.options_menu_search, Connection.ApiRoutineEnum.SEARCH_NOTES),
    FAVORITES("favorites", R.string.timeline_title_favorites, Connection.ApiRoutineEnum.FAVORITES_TIMELINE),
    /** The Mentions timeline and other information (replies...). */
    MENTIONS("mentions", R.string.timeline_title_mentions, Connection.ApiRoutineEnum.MENTIONS_TIMELINE),
    /** Private notes (direct tweets, dents...) */
    PRIVATE("private", R.string.timeline_title_private, Connection.ApiRoutineEnum.PRIVATE_NOTES),
    /** Notes by the selected Actor (where he is an Author or an Actor only (e.g. for Reblog/Retweet).
     * This Actor is NOT one of our Accounts.
     * Hence this timeline type requires the Actor parameter. */
    ACTOR("user", R.string.timeline_title_user, Connection.ApiRoutineEnum.ACTOR_TIMELINE),
    /** Almost like {@link #ACTOR}, but for an Actor, who is one of my accounts. */
    SENT("sent", R.string.sent, Connection.ApiRoutineEnum.ACTOR_TIMELINE),
    /** Latest notes of every Friend of this Actor
     * (i.e of every actor, followed by this Actor).
     * So this is essentially a list of "Friends". See {@link FriendshipTable} */
    FRIENDS("friends", R.string.friends, Connection.ApiRoutineEnum.GET_FRIENDS),
    /** Same as {@link #FRIENDS} but for my accounts only */
    MY_FRIENDS("my_friends", R.string.friends, Connection.ApiRoutineEnum.GET_FRIENDS),
    FOLLOWERS("followers", R.string.followers, Connection.ApiRoutineEnum.GET_FOLLOWERS),
    /** Same as {@link #FOLLOWERS} but for my accounts only */
    MY_FOLLOWERS("my_followers", R.string.followers, Connection.ApiRoutineEnum.GET_FOLLOWERS),
    DRAFTS("drafts", R.string.timeline_title_drafts, Connection.ApiRoutineEnum.DUMMY),
    OUTBOX("outbox", R.string.timeline_title_outbox, Connection.ApiRoutineEnum.DUMMY),
    ACTORS("users", R.string.user_list, Connection.ApiRoutineEnum.DUMMY),
    CONVERSATION("conversation", R.string.label_conversation, Connection.ApiRoutineEnum.DUMMY),
    COMMANDS_QUEUE("commands_queue", R.string.commands_in_a_queue, Connection.ApiRoutineEnum.DUMMY),
    MANAGE_TIMELINES("manages_timelines", R.string.manage_timelines, Connection.ApiRoutineEnum.DUMMY)
    ;

    /** Code - identifier of the type */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    /** Api routine to download this timeline */
    private final Connection.ApiRoutineEnum connectionApiRoutine;

    TimelineType(String code, int resId, Connection.ApiRoutineEnum connectionApiRoutine) {
        this.code = code;
        this.titleResId = resId;
        this.connectionApiRoutine = connectionApiRoutine;
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static TimelineType load(String strCode) {
        for (TimelineType value : TimelineType.values()) {
            if (value.code.equals(strCode)) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public static TimelineType[] getDefaultMyAccountTimelineTypes() {
        return defaultMyAccountTimelineTypes;
    }

    public static TimelineType[] getDefaultOriginTimelineTypes() {
        return defaultOriginTimelineTypes;
    }

    /** String to be used for persistence */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "timelineType:" + code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** Localized title for UI */
    @Override
    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }
    
    public CharSequence getPrepositionForNotCombinedTimeline(Context context) {
        if (context == null) {
            return "";
        } else if (isAtOrigin()) {
            return context.getText(R.string.combined_timeline_off_origin);
        } else {
            return context.getText(R.string.combined_timeline_off_account);
        }
    }

    public boolean isSyncable() {
        return getConnectionApiRoutine() != Connection.ApiRoutineEnum.DUMMY;
    }

    public boolean isSyncedAutomaticallyByDefault() {
        switch (this) {
            case PRIVATE:
            case FAVORITES:
            case HOME:
            case NOTIFICATIONS:
            case SENT:
                return true;
            default:
                return false;
        }
    }

    public boolean isSelectable() {
        switch (this) {
            case COMMANDS_QUEUE:
            case CONVERSATION:
            case FOLLOWERS:
            case FRIENDS:
            case MANAGE_TIMELINES:
            case UNKNOWN:
            case ACTOR:
            case ACTORS:
                return false;
            default:
                return true;
        }
    }

    private static final TimelineType[] defaultMyAccountTimelineTypes = {
            PRIVATE,
            DRAFTS,
            FAVORITES,
            HOME,
            MENTIONS,
            MY_FOLLOWERS,
            MY_FRIENDS,
            NOTIFICATIONS,
            OUTBOX,
            SENT,
    };

    private static final TimelineType[] defaultOriginTimelineTypes = {
            EVERYTHING,
            PUBLIC,
    };

    public boolean isAtOrigin() {
        switch (this) {
            case CONVERSATION:
            case EVERYTHING:
            case FRIENDS:
            case FOLLOWERS:
            case PUBLIC:
            case SEARCH:
            case SENT:
            case ACTOR:
            case ACTORS:
                return true;
            default:
                return false;
        }
    }

    public boolean isForAccount() {
        return !isAtOrigin();
    }

    public boolean isForActor() {
        switch (this) {
            case FOLLOWERS:
            case FRIENDS:
            case MY_FOLLOWERS:
            case MY_FRIENDS:
            case SENT:
            case ACTOR:
                return true;
            default:
                return false;
        }
    }

    public boolean isForSearchQuery() {
        switch (this) {
            case SEARCH:
                return true;
            default:
                return false;
        }
    }

    public boolean canBeCombinedForOrigins() {
        switch (this) {
            case EVERYTHING:
            case PUBLIC:
            case SEARCH:
                return true;
            default:
                return false;
        }
    }

    public boolean canBeCombinedForMyAccounts() {
        switch (this) {
            case PRIVATE:
            case DRAFTS:
            case FAVORITES:
            case HOME:
            case MENTIONS:
            case MY_FRIENDS:
            case MY_FOLLOWERS:
            case NOTIFICATIONS:
            case OUTBOX:
            case SENT:
                return true;
            default:
                return false;
        }
    }

    public boolean isPersistable() {
        switch (this) {
            case COMMANDS_QUEUE:
            case CONVERSATION:
            case MANAGE_TIMELINES:
            case UNKNOWN:
            case ACTORS:
                return false;
            default:
                return true;
        }
    }

    public boolean showsActivities() {
        switch (this) {
            case DRAFTS:
            case EVERYTHING:
            case FOLLOWERS:
            case FRIENDS:
            case HOME:
            case MENTIONS:
            case MY_FOLLOWERS:
            case MY_FRIENDS:
            case NOTIFICATIONS:
            case OUTBOX:
            case PRIVATE:
            case PUBLIC:
            case SEARCH:
            case SENT:
            case ACTOR:
                return true;
            case FAVORITES:
            default:
                return false;
        }
    }

    public boolean isSubscribedByMe() {
        switch (this) {
            case PRIVATE:
            case FAVORITES:
            case HOME:
            case MENTIONS:
            case MY_FRIENDS:
            case NOTIFICATIONS:
            case SENT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int getDialogTitleResId() {
        return R.string.dialog_title_select_timeline;
    }

    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
}