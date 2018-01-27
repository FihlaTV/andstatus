/**
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.note;

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ConversationLoader<T extends ConversationItem<T>> extends SyncLoader<T> {
    private static final int MAX_INDENT_LEVEL = 19;
    
    protected final MyContext myContext;
    protected final MyAccount ma;
    private final long selectedMessageId;
    private boolean sync = false;
    private boolean conversationSyncRequested = false;
    protected boolean mAllowLoadingFromInternet = false;
    private final ReplyLevelComparator<T> replyLevelComparator = new ReplyLevelComparator<>();
    private final T tFactory;

    final Map<Long, T> cachedMessages = new ConcurrentHashMap<>();
    final List<T> msgList = new ArrayList<>();
    LoadableListActivity.ProgressPublisher mProgress;

    public List<T> getList() {
        return msgList;
    }

    final List<Long> idsOfTheMessagesToFind = new ArrayList<>();

    public ConversationLoader(T emptyItem, MyContext myContext, MyAccount ma, long selectedMessageId, boolean sync) {
        tFactory = emptyItem;
        this.myContext = myContext;
        this.ma = ma;
        this.selectedMessageId = selectedMessageId;
        this.sync = sync;
    }
    
    @Override
    public void load(ProgressPublisher publisher) {
        mProgress = publisher;
        cachedMessages.clear();
        idsOfTheMessagesToFind.clear();
        msgList.clear();
        if (sync) {
            requestConversationSync(selectedMessageId);
        }
        load2(newOMsg(selectedMessageId));
        addMissedFromCache();
        Collections.sort(msgList, replyLevelComparator);
        enumerateMessages();
    }

    protected abstract void load2(T oMsg);

    private void addMissedFromCache() {
        if (cachedMessages.isEmpty()) return;
        for (ConversationItem oMsg : msgList) {
            cachedMessages.remove(oMsg.getId());
            if (cachedMessages.isEmpty()) return;
        }
        MyLog.v(this, cachedMessages.size() + " cached messages are not connected to selected");
        for (T oMsg : cachedMessages.values()) {
            addMessageToList(oMsg);
        }
    }

    /** Returns true if message was added
     *          false in a case the message existed already 
     * */
    protected boolean addMessageIdToFind(long msgId) {
        if (msgId == 0) {
            return false;
        } else if (idsOfTheMessagesToFind.contains(msgId)) {
            MyLog.v(this, "findMessages cycled on the id=" + msgId);
            return false;
        }
        idsOfTheMessagesToFind.add(msgId);
        return true;
    }

    @NonNull
    protected T getOMsg(long msgId, int replyLevel) {
        T oMsg = cachedMessages.get(msgId);
        if (oMsg == null) {
            oMsg = newOMsg(msgId);
        }
        oMsg.replyLevel = replyLevel;
        return oMsg;
    }

    protected T newOMsg(long msgId) {
        T oMsg = tFactory.getNew();
        oMsg.setMyContext(myContext);
        oMsg.setMsgId(msgId);
        return oMsg;
    }

    protected void loadMessageFromDatabase(T oMsg) {
        if (oMsg.isLoaded() || oMsg.getMsgId() == 0 || cachedMessages.containsKey(oMsg.getMsgId())) {
            return;
        }
        Uri uri = MatchedUri.getTimelineItemUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, ma, 0, null), oMsg.getMsgId());
        boolean loaded = false;
        try (Cursor cursor = myContext.context().getContentResolver()
                .query(uri, oMsg.getProjection(), null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                oMsg.load(cursor);
                loaded = true;
            }
        }
        MyLog.v(this, (loaded ? "Loaded (" + oMsg.isLoaded() + ")"  : "Couldn't load") + " from a database msgId=" + oMsg.getMsgId());
    }

    protected boolean addMessageToList(T oMsg) {
        boolean added = false;
        if (msgList.contains(oMsg)) {
            MyLog.v(this, "Message id=" + oMsg.getMsgId() + " is in the list already");
        } else {
            msgList.add(oMsg);
            if (mProgress != null) {
                mProgress.publish(Integer.toString(msgList.size()));
            }
            added = true;
        }
        return added;
    }

    protected void loadFromInternet(long msgId) {
        if (requestConversationSync(msgId)) {
            return;
        }
        MyLog.v(this, "Message id=" + msgId + " will be loaded from the Internet");
        MyServiceManager.sendForegroundCommand(
                CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, msgId));
    }

    private boolean requestConversationSync(long msgId_in) {
        if (conversationSyncRequested) {
            return true;
        }
        if (ma.getConnection().isApiSupported(Connection.ApiRoutineEnum.GET_CONVERSATION)) {
            long msgId = selectedMessageId;
            String conversationOid = MyQuery.msgIdToConversationOid(msgId);
            if (TextUtils.isEmpty(conversationOid) && msgId_in != msgId) {
                msgId = msgId_in;
                conversationOid = MyQuery.msgIdToConversationOid(msgId);
            }
            if (!TextUtils.isEmpty(conversationOid)) {
                conversationSyncRequested = true;
                MyLog.v(this, "Conversation oid=" +  conversationOid + " for message id=" + msgId
                        + " will be loaded from the Internet");
                MyServiceManager.sendForegroundCommand(
                        CommandData.newItemCommand(CommandEnum.GET_CONVERSATION, ma, msgId));
                return true;
            }
        }
        return false;
    }

    private static class ReplyLevelComparator<T extends ConversationItem> implements Comparator<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(T lhs, T rhs) {
            int compared = rhs.replyLevel - lhs.replyLevel;
            if (compared == 0) {
                if (lhs.updatedDate == rhs.updatedDate) {
                    if ( lhs.getMsgId() == rhs.getMsgId()) {
                        compared = 0;
                    } else {
                        compared = (rhs.getMsgId() - lhs.getMsgId() > 0 ? 1 : -1);
                    }
                } else {
                    compared = (rhs.updatedDate - lhs.updatedDate > 0 ? 1 : -1);
                }
            }
            return compared;
        }
    }

    private static class OrderCounters {
        int list = -1;
        int history = 1;
    }
    
    private void enumerateMessages() {
        idsOfTheMessagesToFind.clear();
        for (ConversationItem oMsg : msgList) {
            oMsg.mListOrder = 0;
            oMsg.historyOrder = 0;
        }
        OrderCounters order = new OrderCounters();
        for (int ind = msgList.size()-1; ind >= 0; ind--) {
            ConversationItem oMsg = msgList.get(ind);
            if (oMsg.mListOrder < 0 ) {
                continue;
            }
            enumerateBranch(oMsg, order, 0);
        }
    }

    private void enumerateBranch(ConversationItem oMsg, OrderCounters order, int indent) {
        if (!addMessageIdToFind(oMsg.getMsgId())) {
            return;
        }
        int indentNext = indent;
        oMsg.historyOrder = order.history++;
        oMsg.mListOrder = order.list--;
        oMsg.indentLevel = indent;
        if ((oMsg.mNReplies > 1 || oMsg.mNParentReplies > 1)
                && indentNext < MAX_INDENT_LEVEL) {
            indentNext++;
        }
        for (int ind = msgList.size() - 1; ind >= 0; ind--) {
           ConversationItem reply = msgList.get(ind);
           if (reply.inReplyToMsgId == oMsg.getMsgId()) {
               reply.mNParentReplies = oMsg.mNReplies;
               enumerateBranch(reply, order, indentNext);
           }
        }
    }

    public void allowLoadingFromInternet() {
        this.mAllowLoadingFromInternet = true;
    }

    @Override
    public int size() {
        return msgList.size();
    }

}
