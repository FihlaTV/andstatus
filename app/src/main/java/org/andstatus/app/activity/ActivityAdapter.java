/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.msg.MessageAdapter;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.user.ActorAdapter;
import org.andstatus.app.util.MyUrlSpan;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActivityAdapter extends BaseTimelineAdapter<ActivityViewItem> {
    private ActivityContextMenu contextMenu;
    private final ActorAdapter actorAdapter;
    private final MessageAdapter messageAdapter;
    private final ActorAdapter objActorAdapter;

    public ActivityAdapter(ActivityContextMenu contextMenu, TimelineData<ActivityViewItem> listData) {
        super(contextMenu.message.getMyContext(), listData);
        this.contextMenu = contextMenu;
        actorAdapter = new ActorAdapter(contextMenu.actor, new TimelineDataActorWrapper(listData));
        messageAdapter = new MessageAdapter(contextMenu.message, new TimelineDataMessageWrapper(listData));
        objActorAdapter = new ActorAdapter(contextMenu.objActor, new TimelineDataUserWrapper(listData));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {
        ViewGroup view = getEmptyView(convertView);
        view.setOnClickListener(this);
        setPosition(view, position);
        ActivityViewItem item = getItem(position);
        showActor(view, item);
        final ViewGroup messageView = view.findViewById(R.id.message_wrapper);
        if (item.message.getId() == 0) {
            messageView.setVisibility(View.GONE);
        } else {
            messageAdapter.populateView(view, item.message, position);
            messageView.setOnCreateContextMenuListener(contextMenu.message);
            messageView.setOnClickListener(messageAdapter);
            messageView.setVisibility(View.VISIBLE);
        }
        final ViewGroup userView = view.findViewById(R.id.user_wrapper);
        if (item.getUser().getId() == 0) {
            userView.setVisibility(View.GONE);
        } else {
            objActorAdapter.populateView(userView, item.getUser(), position);
            userView.setOnCreateContextMenuListener(contextMenu.objActor);
            userView.setOnClickListener(objActorAdapter);
            userView.setVisibility(View.VISIBLE);
        }
        return view;
    }

    private ViewGroup getEmptyView(View convertView) {
        if (convertView == null) {
            final ViewGroup viewGroup = (ViewGroup) LayoutInflater.from(contextMenu.message.getActivity())
                    .inflate(R.layout.activity, null);
            messageAdapter.setupButtons(viewGroup);
            return viewGroup;
        }
        convertView.setBackgroundResource(0);
        View messageIndented = convertView.findViewById(R.id.message_indented);
        messageIndented.setBackgroundResource(0);
        if (showAvatars) {
            convertView.findViewById(R.id.actor_avatar_image).setVisibility(View.GONE);
            convertView.findViewById(R.id.avatar_image).setVisibility(View.GONE);
        }
        return (ViewGroup) convertView;
    }

    private void showActor(ViewGroup view, ActivityViewItem item) {
        final ViewGroup actorView = view.findViewById(R.id.action_wrapper);
        if (item.activityType == ActivityType.CREATE || item.activityType == ActivityType.UPDATE) {
            actorView.setVisibility(View.GONE);
        } else {
            item.message.hideActor(item.actor.getUserId());
            item.getUser().hideActor(item.actor.getUserId());
            if (showAvatars) {
                AvatarView avatarView = view.findViewById(R.id.actor_avatar_image);
                item.actor.showAvatar(contextMenu.actor.getActivity(), avatarView);
            }
            MyUrlSpan.showText(view, R.id.action_title, item.actor.getWebFingerIdOrUserName()
                    + " " + item.activityType.getActedTitle(contextMenu.actor.getActivity()), false, false);
            MyUrlSpan.showText(view, R.id.action_details, item.getDetails(contextMenu.actor.getActivity()), false, false);
            actorView.setOnCreateContextMenuListener(contextMenu.actor);
            actorView.setOnClickListener(actorAdapter);
            actorView.setVisibility(View.VISIBLE);
        }
    }

}
