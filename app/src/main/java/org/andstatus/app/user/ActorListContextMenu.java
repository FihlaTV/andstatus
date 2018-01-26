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

package org.andstatus.app.user;

import android.support.annotation.NonNull;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.msg.MessageEditorContainer;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ContextMenuHeader;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.view.MyContextMenu;

public class ActorListContextMenu extends MyContextMenu {
    public final MessageEditorContainer menuContainer;

    public ActorListContextMenu(MessageEditorContainer menuContainer, int menuGroup) {
        super(menuContainer.getActivity(), menuGroup);
        this.menuContainer = menuContainer;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        super.onCreateContextMenu(menu, v, menuInfo);
        if (getViewItem().isEmpty()) {
            return;
        }
        MyAccount myActor = getMyActor();
        if (!myActor.isValid() || !myActor.getOrigin().equals(getOrigin())) {
            setMyActor(getMyContext().persistentAccounts().getFirstSucceededForOrigin(
                    getOrigin()));
        }

        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu)
                    .setTitle(getViewItem().actor.toUserTitle(false))
                    .setSubtitle(getMyActor().getAccountName());
            String shortName = getViewItem().actor.getActorName();
            if (getViewItem().actor.isIdentified()) {
                ActorListContextMenuItem.USER_MESSAGES.addTo(menu, menuGroup, order++,
                        String.format(getActivity().getText(R.string.menu_item_user_messages).toString(), shortName));
                ActorListContextMenuItem.FRIENDS.addTo(menu, menuGroup, order++,
                        String.format(
                                getActivity().getText(R.string.friends_of).toString(), shortName));
                ActorListContextMenuItem.FOLLOWERS.addTo(menu, menuGroup, order++,
                        String.format(
                                getActivity().getText(R.string.followers_of).toString(), shortName));
                if (getViewItem().userIsFollowedBy(getMyActor())) {
                    ActorListContextMenuItem.STOP_FOLLOWING.addTo(menu, menuGroup, order++,
                            String.format(
                                    getActivity().getText(R.string.menu_item_stop_following_user).toString(), shortName));
                } else if (getViewItem().getUserId() != getMyActor().getUserId()) {
                    ActorListContextMenuItem.FOLLOW.addTo(menu, menuGroup, order++,
                            String.format(
                                    getActivity().getText(R.string.menu_item_follow_user).toString(), shortName));
                }
                if (!menuContainer.getMessageEditor().isVisible()) {
                    // TODO: Only if he follows me?
                    ActorListContextMenuItem.DIRECT_MESSAGE.addTo(menu, menuGroup, order++,
                            R.string.menu_item_private_message);
                }
                switch (getMyActor().numberOfAccountsOfThisOrigin()) {
                    case 0:
                    case 1:
                        break;
                    case 2:
                        ActorListContextMenuItem.ACT_AS_FIRST_OTHER_USER.addTo(menu, menuGroup, order++,
                                String.format(
                                        getActivity().getText(R.string.menu_item_act_as_user).toString(),
                                        getMyActor().firstOtherAccountOfThisOrigin().getShortestUniqueAccountName(getMyContext())));
                        break;
                    default:
                        ActorListContextMenuItem.ACT_AS.addTo(menu, menuGroup, order++, R.string.menu_item_act_as);
                        break;
                }

            }
            ActorListContextMenuItem.GET_USER.addTo(menu, menuGroup, order++, R.string.get_user);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }

    }

    public boolean onContextItemSelected(MenuItem item) {
        MyAccount ma = getMyActor();
        if (ma.isValid()) {
            ActorListContextMenuItem contextMenuItem = ActorListContextMenuItem.fromId(item.getItemId());
            MyLog.v(this, "onContextItemSelected: " + contextMenuItem + "; actor="
                    + ma.getAccountName() + "; user=" + getViewItem().actor.getNamePreferablyWebFingerId());
            return contextMenuItem.execute(this, ma);
        } else {
            return false;
        }
    }

    @NonNull
    public ActorViewItem getViewItem() {
        if (mViewItem.isEmpty()) {
            return ActorViewItem.EMPTY;
        }
        if (ActivityViewItem.class.isAssignableFrom(mViewItem.getClass())) {
            return getViewItem(((ActivityViewItem) mViewItem));
        }
        return (ActorViewItem) mViewItem;
    }

    @NonNull
    protected ActorViewItem getViewItem(ActivityViewItem activityViewItem) {
        return activityViewItem.getUser();
    }

    public Origin getOrigin() {
        return getViewItem().actor.origin;
    }
}
