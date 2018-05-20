/* 
 * Copyright (c) 2014-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.view.View;
import android.widget.ListView;

import org.andstatus.app.util.MyLog;

/**
 * Determines where to save / retrieve position in the list
 * Information on two rows is stored for each "position" hence two keys. 
 * Plus Query string is being stored for the search results.
 * 2014-11-15 We are storing {@link ViewItem#getDate()} for the last item to retrieve, not its ID as before
 * @author yvolk@yurivolkov.com
 */
class TimelinePositionStorage<T extends ViewItem<T>> {
    private static final int NOT_STORED = -1;

    private final BaseTimelineAdapter<T> adapter;
    private final ListView mListView;
    private final TimelineParameters mListParameters;

    public static int getYOfPosition(ListView list, BaseTimelineAdapter adapter, int position) {
        int zeroChildPosition = adapter.getPosition(list.getChildAt(list.getHeaderViewsCount()));
        View viewOfPosition = list.getChildAt(position - zeroChildPosition + list.getHeaderViewsCount());
        int y = viewOfPosition == null ? 0 : viewOfPosition.getTop() - list.getPaddingTop();
        if (MyLog.isVerboseEnabled() ) {
            MyLog.v(TimelinePositionStorage.class, "getYOfPosition; " + position
                    + " listFirstVisiblePos:" + list.getFirstVisiblePosition()
                    + ", listViews=" + list.getCount()
                    + ", headers:" + list.getHeaderViewsCount()
                    + ", items=" + adapter.getCount()
                    + ", zeroChildPos:" + zeroChildPosition
                    + (viewOfPosition == null ? " view not found" : " y=" + y)
//                    + "\n" + MyLog.getStackTrace(new Throwable())
            );
        }
        return y;
    }

    static class TLPosition {
        long firstVisibleItemId = NOT_STORED;
        long minSentDate = NOT_STORED;
        int y = NOT_STORED;
    }

    TimelinePositionStorage(BaseTimelineAdapter<T> listAdapter, ListView listView, TimelineParameters listParameters) {
        this.adapter = listAdapter;
        this.mListView = listView;
        this.mListParameters = listParameters;
    }

    void save() {
        final String method = "save" + mListParameters.timeline.getId();
        if (mListView == null || adapter == null || mListParameters.isEmpty() || adapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        int itemCount = adapter.getCount();
        int firstVisibleAdapterPosition = Integer.min(
                Integer.max(mListView.getFirstVisiblePosition() - mListView.getHeaderViewsCount(), 0),
                itemCount - 1);
        long firstVisibleItemId = adapter.getItemId(firstVisibleAdapterPosition);
        int y = getYOfPosition(mListView, adapter, firstVisibleAdapterPosition);
        int lastPosition = Integer.min(mListView.getLastVisiblePosition() + 10, itemCount - 1);
        long minDate = adapter.getItem(lastPosition).getDate();

        if (firstVisibleItemId <= 0) {
            clear();
        } else {
            saveTLPosition(firstVisibleItemId, minDate, y);
        }
        if (MyLog.isVerboseEnabled()) {
            String msgLog = "id:" + firstVisibleItemId
                    + ", y:" + y
                    + " at pos=" + firstVisibleAdapterPosition
                    + ", minDate=" + MyLog.formatDateTime(minDate)
                    + " at pos=" + lastPosition + " of " + itemCount
                    + ", listViews=" + mListView.getCount()
                    + " " + mListParameters.getTimeline();
            if (firstVisibleItemId <= 0) {
                MyLog.v(this, method + "; failed " + msgLog
                        + "\n no visible items");
            } else {
                MyLog.v(this, method + "; succeeded " + msgLog);
            }
        }

    }
    
    private void saveTLPosition(long firstVisibleItemId, long minDate, int y) {
        mListParameters.getTimeline().setVisibleItemId(firstVisibleItemId);
        mListParameters.getTimeline().setVisibleOldestDate(minDate);
        mListParameters.getTimeline().setVisibleY(y);
    }

    public TLPosition getTLPosition() {
        TLPosition tlPosition = new TLPosition();
        if (mListParameters.getTimeline().getVisibleItemId() > 0) {
            tlPosition.firstVisibleItemId = mListParameters.getTimeline().getVisibleItemId();
            tlPosition.y = mListParameters.getTimeline().getVisibleY();
            tlPosition.minSentDate = mListParameters.getTimeline().getVisibleOldestDate();
        }
        return tlPosition;
    }

    void clear() {
        mListParameters.getTimeline().setVisibleItemId(0);
        mListParameters.getTimeline().setVisibleOldestDate(0);
        mListParameters.getTimeline().setVisibleY(0);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Position forgot " + mListParameters.getTimeline());
        }
    }
    
    /**
     * Restore (the first visible item) position saved for this timeline
     */
    public void restore() {
        final String method = "restore" + mListParameters.timeline.getId();
        if (mListView == null || adapter == null || mListParameters.isEmpty() || adapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        boolean restored = false;
        int position = -1;
        TLPosition tlPosition = getTLPosition();
        try {
            if (tlPosition.firstVisibleItemId > 0) {
                position = getPositionById(tlPosition.firstVisibleItemId);
            }
            if (position >= 0) {
                mListView.setSelectionFromTop(position + mListView.getHeaderViewsCount(), tlPosition.y);
                restored = true;
            } else {
                // There is no stored position - starting from the Top
                position = 0;
                setPosition(mListView, position);
            }
        } catch (Exception e) {
            MyLog.v(this, method, e);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; " + (restored ? "succeeded" : "failed" )
                    + ", id:" + tlPosition.firstVisibleItemId + ", y:" + tlPosition.y
                    + ", at pos=" + position + " of " + adapter.getCount()
                    + ", listViews=" + mListView.getCount()
                    + " " + mListParameters.getTimeline() );
        }
        if (!restored) {
            clear();
        }
        adapter.setPositionRestored(true);
    }

    static void setPosition(ListView listView, int position) {
        if (listView == null) {
            return;
        }
        int viewHeight = listView.getHeight();
        int childHeight = 30;
        int y = position == 0 ? 0 : viewHeight - childHeight;
        int headerViewsCount = listView.getHeaderViewsCount();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TimelinePositionStorage.class, "Set position of " + position + " item to " + y + " px," +
                    " header views: " + headerViewsCount);
        }
        listView.setSelectionFromTop(position  + headerViewsCount, y);
    }

    /**
     * @return the position in the list or -1 if the item was not found
     */
    private int getPositionById(long itemId) {
        if (adapter == null) {
            return -1;
        }
        return adapter.getPositionById(itemId);
    }
}