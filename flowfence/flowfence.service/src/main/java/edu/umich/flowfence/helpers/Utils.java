/*
 * Copyright (C) 2017 The Regents of the University of Michigan
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

package edu.umich.flowfence.helpers;


import android.util.AttributeSet;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.UUID;

import edu.umich.flowfence.service.BuildConfig;

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

    public static final String FLOWFENCE_NAMESPACE = "http://schemas.android.com/apk/lib/"+BuildConfig.APPLICATION_ID;
}
