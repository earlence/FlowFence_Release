package edu.umich.oasis.helpers;


import android.util.AttributeSet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.UUID;

import edu.umich.oasis.service.BuildConfig;

public abstract class Utils
{
    private Utils() { }

    public static UUID genUUID()
    {
        return UUID.randomUUID();
    }

    public static String genUUIDString()
    {
        return UUID.randomUUID().toString();
    }

    /**
     * Advance an XmlPullParser to the end of the current tag, skipping all intermediate tags.
     * @param parser The parser to advance.
     * @throws XmlPullParserException
     * @throws IOException
     */
    public static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, null, null);
        skip(parser, parser.getDepth());
    }

    public static void skip(XmlPullParser parser, int targetDepth) throws XmlPullParserException, IOException {
        while (true) {
            int depthDelta = parser.getDepth() - targetDepth;
            if (depthDelta < 0) {
                throw new XmlPullParserException("Unexpected depth while skipping");
            } else if (depthDelta == 0 && parser.getEventType() == XmlPullParser.END_TAG) {
                return;
            }

            parser.next();
        }
    }

    public static final String OASIS_NAMESPACE = "http://schemas.android.com/apk/lib/"+BuildConfig.APPLICATION_ID;
}
