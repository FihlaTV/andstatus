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

package org.andstatus.app.net.social;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Twitter API implementations
 * https://dev.twitter.com/rest/public
 * @author yvolk@yurivolkov.com
 */
public abstract class ConnectionTwitterLike extends Connection {
    private static final String TAG = ConnectionTwitterLike.class.getSimpleName();

    /**
     * URL of the API. Not logged
     * @return URL or an empty string in a case the API routine is not supported
     */
    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "account/rate_limit_status.json";
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "account/verify_credentials.json";
                break;
            case CREATE_FAVORITE:
                url = "favorites/create/%noteId%.json";
                break;
            case DESTROY_FAVORITE:
                url = "favorites/destroy/%noteId%.json";
                break;
            case DESTROY_MESSAGE:
                url = "statuses/destroy/%noteId%.json";
                break;
            case PRIVATE_NOTES:
                url = "direct_messages.json";
                break;
            case FAVORITES_TIMELINE:
                url = "favorites.json";
                break;
            case FOLLOW_USER:
                url = "friendships/create.json";
                break;
            case GET_FOLLOWERS_IDS:
                url = "followers/ids.json";
                break;
            case GET_FRIENDS_IDS:
                url = "friends/ids.json";
                break;
            case GET_MESSAGE:
                url = "statuses/show.json" + "?id=%noteId%";
                break;
            case GET_USER:
                url = "users/show.json";
                break;
            case HOME_TIMELINE:
                url = "statuses/home_timeline.json";
                break;
            case MENTIONS_TIMELINE:
                url = "statuses/mentions.json";
                break;
            case POST_DIRECT_MESSAGE:
                url = "direct_messages/new.json";
                break;
            case POST_MESSAGE:
                url = "statuses/update.json";
                break;
            case POST_REBLOG:
                url = "statuses/retweet/%noteId%.json";
                break;
            case STOP_FOLLOWING_USER:
                url = "friendships/destroy.json";
                break;
            case USER_TIMELINE:
                url = "statuses/user_timeline.json";
                break;
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    public boolean destroyStatus(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.DESTROY_MESSAGE, statusId));
        if (jso != null && MyLog.isVerboseEnabled()) {
            try {
                MyLog.v(TAG, "destroyStatus response: " + jso.toString(2));
            } catch (JSONException e) {
                MyLog.e(this, e);
                jso = null;
            }
        }
        return jso != null;
    }
    
    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/create">POST friendships/create</a>
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/destroy">POST friendships/destroy</a>
     */
    @Override
    public AActivity followActor(String actorId, Boolean follow) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("user_id", actorId);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject user = postRequest(follow ? ApiRoutineEnum.FOLLOW_USER : ApiRoutineEnum.STOP_FOLLOWING_USER, out);
        return actorFromJson(user).act(Actor.EMPTY, data.getAccountActor(),
                follow ? ActivityType.FOLLOW : ActivityType.UNDO_FOLLOW);
    } 

    /**
     * Returns an array of numeric IDs for every user the specified user is following.
     * Current implementation is restricted to 5000 IDs (no paged cursors are used...)
     * @see <a href="https://dev.twitter.com/docs/api/1.1/get/friends/ids">GET friends/ids</a>
     */
    @Override
    public List<String> getFriendsIds(String actorId) throws ConnectionException {
        String method = "getFriendsIds";
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", actorId);
        List<String> list = new ArrayList<>();
        JSONArray jArr = getRequestArrayInObject(builder.build().toString(), "ids");
        try {
            for (int index = 0; jArr != null && index < jArr.length(); index++) {
                list.add(jArr.getString(index));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, method, e, jArr);
        }
        return list;
    }

    /**
     * Returns a cursored collection of user IDs for every user following the specified user.
     * @see <a
     *      href="https://dev.twitter.com/rest/reference/get/followers/ids">GET followers/ids</a>
     */
    @NonNull
    @Override
    public List<String> getFollowersIds(String actorId) throws ConnectionException {
        String method = "getFollowersIds";
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FOLLOWERS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", actorId);
        List<String> list = new ArrayList<>();
        JSONArray jArr = getRequestArrayInObject(builder.build().toString(), "ids");
        try {
            for (int index = 0; jArr != null && index < jArr.length(); index++) {
                list.add(jArr.getString(index));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, method, e, jArr);
        }
        return list;
    }

    /**
     * Returns a single status, specified by the id parameter below.
     * The status's author will be returned inline.
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/get/statuses/show/%3Aid">Twitter
     *      REST API Method: statuses/destroy</a>
     */
    @Override
    public AActivity getMessage1(String messageId) throws ConnectionException {
        JSONObject message = http.getRequest(getApiPathWithMessageId(ApiRoutineEnum.GET_MESSAGE, messageId));
        return activityFromJson(message);
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String actorId)
            throws ConnectionException {
        Uri.Builder builder = getTimelineUriBuilder(apiRoutine, limit, actorId);
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, builder.build().toString());
    }

    @NonNull
    protected Uri.Builder getTimelineUriBuilder(ApiRoutineEnum apiRoutine, int limit, String actorId) throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        if (!TextUtils.isEmpty(actorId)) {
            builder.appendQueryParameter("user_id", actorId);
        }
        return builder;
    }

    protected AActivity activityFromTwitterLikeJson(JSONObject jso) throws ConnectionException {
        return activityFromJson(jso);
    }

    @NonNull
    final AActivity activityFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        final AActivity mainActivity = activityFromJson2(jso);
        final AActivity rebloggedActivity = rebloggedMessageFromJson(jso);
        if (rebloggedActivity.isEmpty()) {
            return mainActivity;
        } else {
            return makeReblog(data.getAccountActor(), mainActivity, rebloggedActivity);
        }
    }

    @NonNull
    private AActivity makeReblog(Actor accountActor, @NonNull AActivity mainActivity,
                                 AActivity rebloggedActivity) {
        AActivity reblog = AActivity.from(accountActor, ActivityType.ANNOUNCE);
        reblog.setTimelinePosition(mainActivity.getMessage().oid);
        reblog.setUpdatedDate(mainActivity.getUpdatedDate());
        reblog.setActor(mainActivity.getActor());
        reblog.setActivity(rebloggedActivity);
        return reblog;
    }

    @NonNull
    AActivity newLoadedUpdateActivity(String oid, long updatedDate) throws ConnectionException {
        return AActivity.newPartialMessage(data.getAccountActor(), oid, updatedDate,
                DownloadStatus.LOADED );
    }

    AActivity rebloggedMessageFromJson(@NonNull JSONObject jso) throws ConnectionException {
        return activityFromJson2(jso.optJSONObject("retweeted_status"));
    }

    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        AActivity activity;
        try {
            String oid = jso.optString("id_str");
            if (TextUtils.isEmpty(oid)) {
                // This is for the Status.net
                oid = jso.optString("id");
            }
            activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"));

            Actor author = Actor.EMPTY;
            if (jso.has("sender")) {
                author = actorFromJson(jso.getJSONObject("sender"));
            } else if (jso.has("user")) {
                author = actorFromJson(jso.getJSONObject("user"));
            } else if (jso.has("from_user")) {
                // This is in the search results,
                // see https://dev.twitter.com/docs/api/1/get/search
                String senderName = jso.getString("from_user");
                String senderOid = jso.optString("from_user_id_str");
                if (SharedPreferencesUtil.isEmpty(senderOid)) {
                    senderOid = jso.optString("from_user_id");
                }
                if (!SharedPreferencesUtil.isEmpty(senderOid)) {
                    author = Actor.fromOriginAndActorOid(data.getOrigin(), senderOid);
                    author.setActorName(senderName);
                }
            }
            activity.setActor(author);

            Note message = activity.getMessage();
            setMessageBodyFromJson(message, jso);
            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                message.addRecipient(actorFromJson(recipient));
            }
            if (jso.has("source")) {
                message.via = jso.getString("source");
            }

            // If the Msg is a Reply to other message
            String inReplyToActorOid = "";
            if (jso.has("in_reply_to_user_id_str")) {
                inReplyToActorOid = jso.getString("in_reply_to_user_id_str");
            } else if (jso.has("in_reply_to_user_id")) {
                // This is for Status.net
                inReplyToActorOid = jso.getString("in_reply_to_user_id");
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                inReplyToActorOid = "";
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                String inReplyToMessageOid = "";
                if (jso.has("in_reply_to_status_id_str")) {
                    inReplyToMessageOid = jso.getString("in_reply_to_status_id_str");
                } else if (jso.has("in_reply_to_status_id")) {
                    // This is for StatusNet
                    inReplyToMessageOid = jso.getString("in_reply_to_status_id");
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                    // Construct Related message from available info
                    Actor inReplyToUser = Actor.fromOriginAndActorOid(data.getOrigin(), inReplyToActorOid);
                    if (jso.has("in_reply_to_screen_name")) {
                        inReplyToUser.setActorName(jso.getString("in_reply_to_screen_name"));
                    }
                    AActivity inReplyTo = AActivity.newPartialMessage(data.getAccountActor(), inReplyToMessageOid);
                    inReplyTo.setActor(inReplyToUser);
                    message.setInReplyTo(inReplyTo);
                }
            }

            if (!jso.isNull("favorited")) {
                message.addFavoriteBy(data.getAccountActor(),
                        TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favorited"))));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing message", e, jso);
        } catch (Exception e) {
            MyLog.e(this, "messageFromJson", e);
            return AActivity.EMPTY;
        }
        return activity;
    }

    protected void setMessageBodyFromJson(Note message, JSONObject jso) throws JSONException {
        if (jso.has("text")) {
            message.setBody(jso.getString("text"));
        }
    }
    
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return Actor.EMPTY;
        }
        String oid = "";
        if (jso.has("id_str")) {
            oid = jso.optString("id_str");
        } else if (jso.has("id")) {
            oid = jso.optString("id");
        } 
        if (SharedPreferencesUtil.isEmpty(oid)) {
            oid = "";
        }
        String userName = "";
        if (jso.has("screen_name")) {
            userName = jso.optString("screen_name");
            if (SharedPreferencesUtil.isEmpty(userName)) {
                userName = "";
            }
        }
        Actor user = Actor.fromOriginAndActorOid(data.getOrigin(), oid);
        user.setActorName(userName);
        user.setRealName(jso.optString("name"));
        if (!SharedPreferencesUtil.isEmpty(user.getRealName())) {
            user.setProfileUrl(data.getOriginUrl());
        }
        user.location = jso.optString("location");
        user.avatarUrl = UriUtils.fromAlternativeTags(jso,
                "profile_image_url_https", "profile_image_url").toString();
        user.bannerUrl = UriUtils.fromJson(jso, "profile_banner_url").toString();
        user.setDescription(jso.optString("description"));
        user.setHomepage(jso.optString("url"));
        // Hack for twitter.com
        user.setProfileUrl(http.pathToUrlString("/").replace("/api.", "/") + userName);
        user.msgCount = jso.optLong("statuses_count");
        user.favoritesCount = jso.optLong("favourites_count");
        user.followingCount = jso.optLong("friends_count");
        user.followersCount = jso.optLong("followers_count");
        user.setCreatedDate(dateFromJson(jso, "created_at"));
        if (!jso.isNull("following")) {
            user.followedByMe = TriState.fromBoolean(jso.optBoolean("following"));
        }
        if (!jso.isNull("status")) {
            try {
                final AActivity activity = activityFromJson(jso.getJSONObject("status"));
                activity.setActor(user);
                user.setLatestActivity(activity);
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, "getting status from user", e, jso);
            }
        }
        return user;
    }

    @NonNull
    @Override
    public List<AActivity> searchNotes(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_MESSAGES;
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!TextUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, url);
    }

    void appendPositionParameters(Uri.Builder builder, TimelinePosition youngest, TimelinePosition oldest) {
        if (youngest.nonEmpty()) {
            builder.appendQueryParameter("since_id", youngest.getPosition());
        } else if (oldest.nonEmpty()) {
            String maxIdString = oldest.getPosition();
            try {
                // Subtract 1, as advised at https://dev.twitter.com/rest/public/timelines
                long maxId = Long.parseLong(maxIdString);
                maxIdString = Long.toString(maxId - 1);
            } catch (NumberFormatException e) {
                MyLog.i(this, "Is not long number: '" + maxIdString + "'");
            }
            builder.appendQueryParameter("max_id", maxIdString);
        }
    }

    List<AActivity> jArrToTimeline(JSONArray jArr, ApiRoutineEnum apiRoutine, String url) throws ConnectionException {
        List<AActivity> timeline = new ArrayList<>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    AActivity item = activityFromTwitterLikeJson(jArr.getJSONObject(index));
                    timeline.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null);
                }
            }
        }
        if (apiRoutine.isMsgPrivate()) {
            setMessagesPrivate(timeline);
        }
        MyLog.d(this, apiRoutine + " '" + url + "' " + timeline.size() + " items");
        return timeline;
    }

    void setMessagesPrivate(List<AActivity> timeline) {
        for (AActivity item : timeline) {
            if (item.getObjectType() == AObjectType.NOTE) {
                item.getMessage().setPrivate(TriState.TRUE);
            }
        }
    }

    List<Actor> jArrToUsers(JSONArray jArr, ApiRoutineEnum apiRoutine, String url) throws ConnectionException {
        List<Actor> users = new ArrayList<>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    Actor item = actorFromJson(jso);
                    users.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null);
                }
            }
        }
        MyLog.d(this, apiRoutine + " '" + url + "' " + users.size() + " items");
        return users;
    }

    /**
     * @see <a href="https://dev.twitter.com/docs/api/1.1/get/users/show">GET users/show</a>
     */
    @Override
    public Actor getActor(String actorId, String actorName) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_USER));
        Uri.Builder builder = sUri.buildUpon();
        if (UriUtils.isRealOid(actorId)) {
            builder.appendQueryParameter("user_id", actorId);
        } else {
            builder.appendQueryParameter("screen_name", actorName);
        }
        JSONObject jso = http.getRequest(builder.build().toString());
        Actor actor = actorFromJson(jso);
        MyLog.v(this, "getUser oid='" + actorId + "', userName='" + actorName + "' -> " + actor.getRealName());
        return actor;
    }
    
    @Override
    public AActivity postPrivateMessage(String message, String statusId, String actorId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("text", message);
            if ( !TextUtils.isEmpty(actorId)) {
                formParams.put("user_id", actorId);
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_DIRECT_MESSAGE, formParams);
        return activityFromJson(jso);
    }
    
    @Override
    public AActivity postReblog(String rebloggedId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.POST_REBLOG, rebloggedId));
        return activityFromJson(jso);
    }

    /**
     * Check API requests status.
     * 
     * Returns the remaining number of API requests available to the requesting 
     * user before the API limit is reached for the current hour. Calls to 
     * rate_limit_status do not count against the rate limit.  If authentication 
     * credentials are provided, the rate limit status for the authenticating 
     * user is returned.  Otherwise, the rate limit status for the requester's 
     * IP address is returned.
     * @see <a
           href="https://dev.twitter.com/docs/api/1/get/account/rate_limit_status">GET 
           account/rate_limit_status</a>
     */
    @Override
    public RateLimitStatus rateLimitStatus() throws ConnectionException {
        JSONObject result = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS));
        RateLimitStatus status = new RateLimitStatus();
        if (result != null) {
            switch (data.getOriginType()) {
                case GNUSOCIAL:
                    status.remaining = result.optInt("remaining_hits");
                    status.limit = result.optInt("hourly_limit");
                    break;
                default:
                    JSONObject resources = null;
                    try {
                        resources = result.getJSONObject("resources");
                        JSONObject limitObject = resources.getJSONObject("statuses").getJSONObject("/statuses/home_timeline");
                        status.remaining = limitObject.optInt("remaining");
                        status.limit = limitObject.optInt("limit");
                    } catch (JSONException e) {
                        throw ConnectionException.loggedJsonException(this, "getting rate limits", e, resources);
                    }
                    break;
            }
        }
        return status;
    }
    
    @Override
    public AActivity updateStatus(String message, String statusId, String inReplyToId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", message);
            if ( !TextUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_status_id", inReplyToId);
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_MESSAGE, formParams);
        return activityFromJson(jso);
    }

    /**
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
     *      REST API Method: account verify_credentials</a>
     */
    @Override
    public Actor verifyCredentials() throws ConnectionException {
        JSONObject user = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return actorFromJson(user);
    }

    protected final JSONObject postRequest(ApiRoutineEnum apiRoutine, JSONObject formParams) throws ConnectionException {
        return postRequest(getApiPath(apiRoutine), formParams);
    }

    String getApiPathWithMessageId(ApiRoutineEnum routineEnum, String noteId) throws ConnectionException {
        return getApiPath(routineEnum).replace("%noteId%", noteId);
    }

    String getApiPathWithUserId(ApiRoutineEnum routineEnum, String actorId) throws ConnectionException {
        return getApiPath(routineEnum).replace("%actorId%", actorId);
    }

    @Override
    public List<Actor> getFollowers(String actorId) throws ConnectionException {
        return getActors(actorId, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public List<Actor> getFriends(String actorId) throws ConnectionException {
        return getActors(actorId, ApiRoutineEnum.GET_FRIENDS);
    }

    List<Actor> getActors(String actorId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public AActivity createFavorite(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.CREATE_FAVORITE, statusId));
        return activityFromJson(jso);
    }

    @Override
    public AActivity destroyFavorite(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.DESTROY_FAVORITE, statusId));
        return activityFromJson(jso);
    }

}
