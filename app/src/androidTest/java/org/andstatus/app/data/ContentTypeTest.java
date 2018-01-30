package org.andstatus.app.data;

import org.junit.Test;

import cz.msebera.android.httpclient.entity.ContentType;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class ContentTypeTest {
    @Test
    public void testApacheContentType() {
        ContentType contentType = ContentType.parse("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.create("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.parse("image/jpeg");
        assertEquals("image/jpeg", contentType.getMimeType());
    }

    @Test
    public void testMyContentType() {
        assertEquals("image/png", MyContentType.uri2MimeType(demoData.image1Url, null));
        assertEquals("image/jpeg", MyContentType.filename2MimeType("http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg", null));
    }
}
