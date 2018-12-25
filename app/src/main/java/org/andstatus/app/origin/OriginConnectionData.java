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

package org.andstatus.app.origin;

import androidx.annotation.NonNull;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionEmpty;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.TriState;

import java.net.URL;

public class OriginConnectionData {
    private final AccountName accountName;
    private boolean isOAuth = true;
    private URL originUrl = null;

    private Actor accountActor = Actor.EMPTY;
    private AccountDataReader dataReader = null;
    
    private Class<? extends org.andstatus.app.net.http.HttpConnection> httpConnectionClass = HttpConnectionEmpty.class;

    public static OriginConnectionData fromAccountName(AccountName accountName, TriState triStateOAuth) {
        return new OriginConnectionData(accountName, triStateOAuth);
    }

    private OriginConnectionData(AccountName accountName, TriState triStateOAuth) {
        this.accountName = accountName;
        originUrl = accountName.getOrigin().getUrl();
        isOAuth = accountName.getOrigin().getOriginType().fixIsOAuth(triStateOAuth);
        httpConnectionClass = accountName.getOrigin().getOriginType().getHttpConnectionClass(isOAuth());
        dataReader = new AccountDataReaderEmpty();
    }

    @NonNull
    public Actor getAccountActor() {
        return accountActor;
    }

    public AccountName getAccountName() {
        return accountName;
    }

    public OriginType getOriginType() {
        return accountName.getOrigin().getOriginType();
    }

    public Origin getOrigin() {
        return accountName.getOrigin();
    }

    public boolean isSsl() {
        return accountName.getOrigin().isSsl();
    }

    public boolean isOAuth() {
        return isOAuth;
    }

    public URL getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(URL urlIn) {
        this.originUrl = urlIn;
    }

    @NonNull
    public Connection newConnection() throws ConnectionException {
        Connection connection;
        try {
            connection = accountName.getOrigin().getOriginType().getConnectionClass().newInstance();
            connection.enrichConnectionData(this);
            connection.setAccountData(this);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ConnectionException(accountName.getOrigin().toString(), e);
        }
        return connection;
    }

    public void setAccountActor(Actor accountActor) {
        if (accountActor != null) {
            this.accountActor = accountActor;
        }
    }

    public AccountDataReader getDataReader() {
        return dataReader;
    }

    public void setDataReader(AccountDataReader dataReader) {
        this.dataReader = dataReader;
    }

    public HttpConnection newHttpConnection(String logMsg) throws ConnectionException {
        HttpConnection http = MyContextHolder.get().getHttpConnectionMock();
        if (http == null) {
            try {
                http = httpConnectionClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new ConnectionException(logMsg, e);
            }
        }
        return http;
    }
}
