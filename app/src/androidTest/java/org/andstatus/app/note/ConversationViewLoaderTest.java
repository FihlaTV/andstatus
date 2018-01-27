package org.andstatus.app.note;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertTrue;

public class ConversationViewLoaderTest implements ProgressPublisher {
    private MyAccount ma;
    private long selectedMessageId;
    private long progressCounter = 0;

    @Before
    public void setUp() throws Exception {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        selectedMessageId = MyQuery.oidToId(OidEnum.MSG_OID, ma.getOriginId(), demoData.CONVERSATION_ENTRY_NOTE_OID);
        assertTrue("Selected message exists", selectedMessageId != 0);
        MyLog.i(this, "setUp ended");
    }

    @Test
    public void testLoad() {
        ConversationLoader<ConversationViewItem> loader =
                new ConversationLoaderFactory<ConversationViewItem>().getLoader(
                ConversationViewItem.EMPTY, MyContextHolder.get(), ma, selectedMessageId, false);
        progressCounter = 0;
        loader.load(this);
        List<ConversationViewItem> list = loader.getList();
        assertTrue("List is empty", !list.isEmpty());
        boolean indentFound = false;
        boolean orderFound = false;
        for( ConversationViewItem oMsg : list) {
            if (oMsg.indentLevel > 0) {
                indentFound = true;
            }
            if (oMsg.mListOrder != 0) {
                orderFound = true;
            }
        }
        assertTrue("Indented message found in " + list, indentFound);
        assertTrue("Ordered message found in " + list, orderFound);
        assertTrue(progressCounter > 0);
    }

    @Override
    public void publish(String progress) {
        progressCounter++;
    }
}
