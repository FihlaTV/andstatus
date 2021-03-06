/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.account;

import org.andstatus.app.context.TestSuite;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MyAccountsTest {
    @Before
    public void setUp() {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void test() {
        MyAccounts accounts = myContextHolder.getNow().accounts();

        assertNotEquals(accounts.toString(), MyAccount.EMPTY,
                accounts.fromWebFingerId(demoData.pumpioTestAccountUniqueName.toLowerCase()));
        assertNotEquals(accounts.toString(), MyAccount.EMPTY,
                accounts.fromWebFingerId(demoData.conversationAccountSecondUniqueName.toLowerCase()));
        assertEquals(accounts.toString(), MyAccount.EMPTY,
                accounts.fromWebFingerId(demoData.conversationAuthorSecondUniqueName.toLowerCase()));
    }

}
