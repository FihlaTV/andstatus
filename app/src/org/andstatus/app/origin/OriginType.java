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

import org.andstatus.app.net.ConnectionEmpty;
import org.andstatus.app.net.ConnectionPumpio;
import org.andstatus.app.net.ConnectionTwitter1p1;
import org.andstatus.app.net.HttpConnectionBasic;
import org.andstatus.app.net.HttpConnectionEmpty;
import org.andstatus.app.net.HttpConnectionOAuthApache;
import org.andstatus.app.net.HttpConnectionOAuthJavaNet;
import org.andstatus.app.net.Connection.ApiEnum;
import org.andstatus.app.net.ConnectionTwitterGnuSocial;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

public enum OriginType {
    /**
     * Origin type for Twitter system 
     * <a href="https://dev.twitter.com/docs">Twitter Developers' documentation</a>
     */
    TWITTER(1, "Twitter", ApiEnum.TWITTER1P1),
    /**
     * Origin type for the pump.io system 
     * Till July of 2013 (and v.1.16 of AndStatus) the API was: 
     * <a href="http://status.net/wiki/Twitter-compatible_API">Twitter-compatible identi.ca API</a>
     * Since July 2013 the API is <a href="https://github.com/e14n/pump.io/blob/master/API.md">pump.io API</a>
     */
    PUMPIO(2, "Pump.io", ApiEnum.PUMPIO),
    GNUSOCIAL(3, "GNU social", ApiEnum.GNUSOCIAL_TWITTER),
    UNKNOWN(0, "?", ApiEnum.UNKNOWN_API);

    private static final String BASIC_PATH_DEFAULT = "api";
    private static final String OAUTH_PATH_DEFAULT = "oauth";
    private static final String USERNAME_REGEX_DEFAULT = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
    public static final OriginType ORIGIN_TYPE_DEFAULT = TWITTER;
    public static final OriginType ORIGIN_TYPE_DEFAULT_BEFORE_TWITTER = GNUSOCIAL;
    public static final int TEXT_LIMIT_MAXIMUM = 5000;

    private long id;
    private String title;

    private ApiEnum api;
    protected boolean canSetUrlOfOrigin = false;

    private Class<? extends Origin> originClass = Origin.class;
    private Class<? extends org.andstatus.app.net.Connection> connectionClass = ConnectionEmpty.class;
    private Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClassOauth = HttpConnectionEmpty.class;
    private Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClassBasic = HttpConnectionEmpty.class;

    /**
     * Default OAuth setting
     */
    protected boolean isOAuthDefault = true;
    /**
     * Can OAuth connection setting can be turned on/off from the default setting
     */
    protected boolean canChangeOAuth = false;

    protected boolean shouldSetNewUsernameManuallyIfOAuth = false;

    /**
     * Can user set username for the new user manually?
     * This is only for no OAuth
     */
    protected boolean shouldSetNewUsernameManuallyNoOAuth = false;
    protected String usernameRegEx = USERNAME_REGEX_DEFAULT;
    /**
     * Length of the link after changing to the shortened link
     * 0 means that length doesn't change
     * For Twitter.com see <a href="https://dev.twitter.com/docs/api/1.1/get/help/configuration">GET help/configuration</a>
     */
    protected int shortUrlLengthDefault = 0;
    
    protected boolean sslDefault = true;
    protected boolean canChangeSsl = false;

    protected boolean allowHtmlDefault = true;
    /**
     * Maximum number of characters in the message
     */
    protected int textLimitDefault = 0;
    protected URL urlDefault = null;
    protected String basicPath = BASIC_PATH_DEFAULT;
    protected String oauthPath = OAUTH_PATH_DEFAULT;
    
    private OriginType(long id, String title, ApiEnum api) {
        this.id = id;
        this.title = title;
        this.api = api;
        switch (api) {
            case TWITTER1P1:
                isOAuthDefault = true;
                // Starting from 2010-09 twitter.com allows OAuth only
                canChangeOAuth = false;  
                canSetUrlOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                // TODO: Read from Config
                shortUrlLengthDefault = 23; 
                usernameRegEx = USERNAME_REGEX_DEFAULT;
                textLimitDefault = 140;
                urlDefault = UrlUtils.fromString("https://api.twitter.com");
                basicPath = "1.1";
                oauthPath = OAUTH_PATH_DEFAULT;
                originClass = OriginTwitter.class;
                connectionClass = ConnectionTwitter1p1.class;
                httpConnectionClassOauth = HttpConnectionOAuthApache.class;
                httpConnectionClassBasic = HttpConnectionBasic.class;
                break;
            case PUMPIO:
                isOAuthDefault = true;  
                canChangeOAuth = false;
                canSetUrlOfOrigin = false;
                shouldSetNewUsernameManuallyIfOAuth = true;
                shouldSetNewUsernameManuallyNoOAuth = false;
                usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+@[a-zA-Z_0-9/\\.\\-\\(\\)]+";
                // This is not a hard limit, just for convenience
                textLimitDefault = TEXT_LIMIT_MAXIMUM;
                basicPath = BASIC_PATH_DEFAULT;
                oauthPath = OAUTH_PATH_DEFAULT;
                originClass = OriginPumpio.class;
                connectionClass = ConnectionPumpio.class;
                httpConnectionClassOauth = HttpConnectionOAuthJavaNet.class;
                break;
            case GNUSOCIAL_TWITTER:
                isOAuthDefault = false;  
                canChangeOAuth = false; 
                canSetUrlOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                usernameRegEx = USERNAME_REGEX_DEFAULT;
                canChangeSsl = true;
                basicPath = BASIC_PATH_DEFAULT;
                oauthPath = BASIC_PATH_DEFAULT;
                originClass = OriginGnuSocial.class;
                connectionClass = ConnectionTwitterGnuSocial.class;
                httpConnectionClassOauth = HttpConnectionOAuthApache.class;
                httpConnectionClassBasic = HttpConnectionBasic.class;
                break;
            default:
                break;
        }
    }

    public Class<? extends Origin> getOriginClass() {
        return originClass;
    }
    
    public Class<? extends org.andstatus.app.net.Connection> getConnectionClass() {
        return connectionClass;
    }
    
    public Class<? extends org.andstatus.app.net.HttpConnection> getHttpConnectionClass(boolean isOAuth) {
        if (fixIsOAuth(isOAuth)) {
            return httpConnectionClassOauth;
        } else {
            return httpConnectionClassBasic;
        }
    }
    
    public long getId() {
        return id;
    }

    public int getEntriesPosition() {
        return ordinal();
    }
    
    public String getTitle() {
        return title;
    }
    
    public ApiEnum getApi() {
        return api;
    }

    public boolean canSetUrlOfOrigin() {
        return canSetUrlOfOrigin;
    }
    
    @Override
    public String toString() {
        return "OriginType [ id:" + id + "; code:" + title + "]";
    }
    
    public boolean fixIsOAuth(TriState triStateOAuth) {
        return fixIsOAuth(triStateOAuth.toBoolean(isOAuthDefault));
    }

    public boolean fixIsOAuth(boolean isOAuthIn) {
        boolean fixed = isOAuthIn;
        if (fixed != isOAuthDefault && !canChangeOAuth) {
            fixed = isOAuthDefault;
        }
        return fixed;
    }
    
    public static OriginType fromId( long id) {
        OriginType obj = UNKNOWN;
        for(OriginType val : values()) {
            if (val.id == id) {
                obj = val;
                break;
            }
        }
        return obj;
    }

    public static OriginType fromName( String name) {
        OriginType obj = UNKNOWN;
        for(OriginType val : values()) {
            if (val.title.equalsIgnoreCase(name)) {
                obj = val;
                break;
            }
        }
        return obj;
    }

    public static OriginType fromEntriesPosition( int position) {
        OriginType obj = UNKNOWN;
        for(OriginType val : values()) {
            if (val.ordinal() == position) {
                obj = val;
                break;
            }
        }
        return obj;
    }
}