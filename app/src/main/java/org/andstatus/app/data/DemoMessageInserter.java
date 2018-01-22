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

package org.andstatus.app.data;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DemoMessageInserter {
    public final MbUser accountUser;
    private final Origin origin;

    public DemoMessageInserter(MyAccount ma) {
        this(ma.toPartialUser());
    }

    public DemoMessageInserter(MbUser accountUser) {
        this.accountUser = accountUser;
        assertTrue(accountUser != null);
        origin = accountUser.origin;
        assertTrue("Origin exists for " + accountUser, origin.isValid());
    }

    public MbUser buildUser() {
        if (origin.getOriginType() == OriginType.PUMPIO) {
            return buildUserFromOid("acct:userOf" + origin.getName() + demoData.TESTRUN_UID);
        }
        return buildUserFromOid(demoData.TESTRUN_UID);
    }

    public MbUser buildUserFromOidAndAvatar(String userOid, String avatarUrlString) {
        MbUser mbUser = buildUserFromOid(userOid);
        mbUser.avatarUrl = avatarUrlString;
        return mbUser;
    }
    
    final MbUser buildUserFromOid(String userOid) {
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin, userOid);
        String username;
        String profileUrl;
        if (origin.getOriginType() == OriginType.PUMPIO) {
            ConnectionPumpio connection = new ConnectionPumpio();
            username = connection.userOidToUsername(userOid);
            profileUrl = "http://" + connection.usernameToHost(username) + "/"
                    + connection.usernameToNickname(username);
        } else {
            username = "userOf" + origin.getName() + userOid;
            profileUrl = "https://" + demoData.GNUSOCIAL_TEST_ORIGIN_NAME
                    + ".example.com/profiles/" + username;
        }
        mbUser.setUserName(username);
        mbUser.setProfileUrl(profileUrl);
        mbUser.setRealName("Real " + username);
        mbUser.setDescription("This is about " + username);
        mbUser.setHomepage("https://example.com/home/" + username + "/start/");
        mbUser.location = "Faraway place #" + demoData.TESTRUN_UID;
        mbUser.avatarUrl = mbUser.getHomepage() + "avatar.jpg";
        mbUser.bannerUrl = mbUser.getHomepage() + "banner.png";
        long rand = InstanceId.next();
        mbUser.msgCount = rand * 2 + 3;
        mbUser.favoritesCount = rand + 11;
        mbUser.followingCount = rand + 17;
        mbUser.followersCount = rand;
        return mbUser;
    }

    public MbActivity buildActivity(MbUser author, String body, MbActivity inReplyToActivity, String messageOidIn,
                                    DownloadStatus messageStatus) {
        final String method = "buildActivity";
        String messageOid = messageOidIn;
        if (TextUtils.isEmpty(messageOid) && messageStatus != DownloadStatus.SENDING) {
            if (origin.getOriginType() == OriginType.PUMPIO) {
                messageOid =  (author.isPartiallyDefined() ? "http://pumpiotest" + origin.getId()
                        + ".example.com/user/" + author.oid : author.getProfileUrl())
                        + "/" + (inReplyToActivity == null ? "note" : "comment")
                        + "/thisisfakeuri" + System.nanoTime();
            } else {
                messageOid = MyLog.uniqueDateTimeFormatted();
            }
        }
        MbActivity activity = buildActivity(author, MbActivityType.UPDATE, messageOid);
        MbMessage message = MbMessage.fromOriginAndOid(origin, messageOid, messageStatus);
        activity.setMessage(message);
        message.setUpdatedDate(activity.getUpdatedDate());
        message.setBody(body);
        message.via = "AndStatus";
        message.setInReplyTo(inReplyToActivity);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            message.url = message.oid;
        }
        DbUtils.waitMs(method, 10);
        return activity;
    }

    public MbActivity buildActivity(@NonNull MbUser actor, @NonNull MbActivityType type, String messageOid) {
        MbActivity activity = MbActivity.from(accountUser, type);
        activity.setTimelinePosition(
                (TextUtils.isEmpty(messageOid) ?  MyLog.uniqueDateTimeFormatted() : messageOid)
                + "-" + activity.type.name().toLowerCase());
        activity.setActor(actor);
        activity.setUpdatedDate(System.currentTimeMillis());
        return activity;
    }

    static void onActivityS(MbActivity activity) {
        new DemoMessageInserter(activity.accountUser).onActivity(activity);
    }

    static void increaseUpdateDate(MbActivity activity) {
        // In order for a message not to be ignored
        activity.setUpdatedDate(activity.getUpdatedDate() + 1);
        activity.getMessage().setUpdatedDate(activity.getMessage().getUpdatedDate() + 1);
    }

    public void onActivity(final MbActivity activity) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(accountUser.userId);
        assertTrue("Persistent account exists for " + accountUser + " " + activity, ma.isValid());
        final TimelineType timelineType = activity.getMessage().isPrivate() ? TimelineType.PRIVATE : TimelineType.HOME;
        DataUpdater di = new DataUpdater(new CommandExecutionContext(
                        CommandData.newTimelineCommand(CommandEnum.EMPTY, ma, timelineType)));
        di.onActivity(activity);
        checkActivityRecursively(activity, 1);
    }

    private void checkActivityRecursively(MbActivity activity, int level) {
        if (level == 1) {
            assertNotEquals( "Activity was not added: " + activity, 0, activity.getId());
        }
        if (level > 10 || activity.getId() == 0) {
            return;
        }
        assertNotEquals( "Account is unknown: " + activity, 0, activity.accountUser.userId);

        MbUser actor = activity.getActor();
        if (actor.nonEmpty()) {
            assertNotEquals( "Actor id not set for " + actor + " in activity " + activity, 0, actor.userId);
        }

        MbMessage message = activity.getMessage();
        if (message.nonEmpty()) {
            assertNotEquals( "Message was not added at level " + level + " " + activity, 0, message.msgId);

            String permalink = origin.messagePermalink(message.msgId);
            URL urlPermalink = UrlUtils.fromString(permalink);
            assertNotNull("Message permalink is a valid URL '" + permalink + "',\n" + message.toString()
                    + "\n origin: " + origin
                    + "\n author: " + activity.getAuthor().toString(), urlPermalink);
            if (origin.getUrl() != null && origin.getOriginType() != OriginType.TWITTER) {
                assertEquals("Message permalink has the same host as origin, " + message.toString(),
                        origin.getUrl().getHost(), urlPermalink.getHost());
            }
            if (!TextUtils.isEmpty(message.url)) {
                assertEquals("Message permalink", message.url, origin.messagePermalink(message.msgId));
            }

            MbUser author = activity.getAuthor();
            if (author.nonEmpty()) {
                assertNotEquals( "Author id for " + author + " not set in message " + message + " in activity " + activity, 0,
                        MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, message.msgId));
            }
        }

        if (activity.type == MbActivityType.LIKE) {
            List<MbUser> stargazers = MyQuery.getStargazers(MyContextHolder.get().getDatabase(), accountUser.origin, message.msgId);
            boolean found = false;
            for (MbUser stargazer : stargazers) {
                if (stargazer.userId == actor.userId) {
                    found = true;
                    break;
                }
            }
            assertTrue("User, who favorited, is not found among stargazers: " + activity
                    + "\nstargazers: " + stargazers, found);
        }

        if (activity.type == MbActivityType.ANNOUNCE) {
            List<MbUser> rebloggers = MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), accountUser.origin, message.msgId);
            boolean found = false;
            for (MbUser stargazer : rebloggers) {
                if (stargazer.userId == actor.userId) {
                    found = true;
                    break;
                }
            }
            assertTrue("Reblogger is not found among rebloggers: " + activity
                    + "\nrebloggers: " + rebloggers, found);
        }

        if (!message.replies.isEmpty()) {
            for (MbActivity replyActivity : message.replies) {
                if (replyActivity.nonEmpty()) {
                    assertNotEquals("Reply added at level " + level + " " + replyActivity, 0, replyActivity.getId());
                    checkActivityRecursively(replyActivity, level + 1);
                }
            }
        }

        if (activity.getUser().nonEmpty()) {
            assertNotEquals( "User was not added: " + activity.getUser(), 0, activity.getUser().userId);
        }
        if (activity.getActivity().nonEmpty()) {
            checkActivityRecursively(activity.getActivity(), level + 1);
        }
    }

    static void deleteOldMessage(@NonNull Origin origin, String messageOid) {
        long messageIdOld = MyQuery.oidToId(OidEnum.MSG_OID, origin.getId(), messageOid);
        if (messageIdOld != 0) {
            int deleted = MyProvider.deleteMessage(MyContextHolder.get().context(), messageIdOld);
            assertTrue( "Activities of Old message id=" + messageIdOld + " deleted: " + deleted, deleted > 0);
        }
    }
    
    public static MbActivity addMessageForAccount(MyAccount ma, String body, String messageOid, DownloadStatus messageStatus) {
        assertTrue("Is not valid: " + ma, ma.isValid());
        MbUser accountUser = ma.toPartialUser();
        DemoMessageInserter mi = new DemoMessageInserter(accountUser);
        MbActivity activity = mi.buildActivity(accountUser, body, null, messageOid, messageStatus);
        mi.onActivity(activity);
        return activity;
    }

    public static void assertNotified(MbActivity activity, TriState notified) {
        assertEquals("Should" + (notified == TriState.FALSE ? " not" : "") + " be notified " + activity,
                notified,
                MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.getId()));
    }
}
