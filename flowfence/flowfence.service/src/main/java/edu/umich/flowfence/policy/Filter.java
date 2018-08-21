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

package edu.umich.flowfence.policy;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

import edu.umich.flowfence.helpers.Utils;

import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;


public abstract class Filter {
    private static final String TAG = "FF.Filter";

    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);
    public abstract boolean shouldAccept(SinkRequest request);

    private String filterValue;

    protected String getFilterValue() {
        return filterValue;
    }

    protected void setFilterValue(String filterValue){
        this.filterValue = filterValue;
    }

    public static final Filter ALWAYS = new Filter() {
        @Override
        public boolean shouldAccept(SinkRequest request) {
            return true;
        }

        @Override
        public String toString() {
            return "true";
        }
    };

    public static final Filter NEVER = new Filter() {
        @Override
        public boolean shouldAccept(SinkRequest request) {
            return false;
        }

        @Override
        public String toString() {
            return "false";
        }
    };

    /**
     * Filter the request, checking whether it should be accepted or not.
     * @param request Request to be filtered
     * @return True if accepted, false otherwise.
     */
    public boolean filter(SinkRequest request){
        String filter = getFilterValue();
        if(filter != null && !filter.isEmpty()){
            String requestUrl = ((NetworkSinkRequest)request).getUrl();
            return filter.equals(requestUrl);
        }

        return true;
    }

    public static abstract class Typed<TRequest extends SinkRequest> extends Filter {
        @SuppressWarnings("rawtypes")
        private static final TypeVariable<Class<Typed>> g_tRequestToken =
                Typed.class.getTypeParameters()[0];

        private final String sinkName;
        private final Type tRequestType;

        /**
         * Create a new typed filter.
         * @param sinkName The sink name to restrict to. If null, allow any sink matching TRequest.
         */
        protected Typed(String sinkName) {
            this(sinkName, "");
        }

        protected Typed(String sinkName, String filterValue){
            Log.i(TAG, String.format("Creating Filtered rule with sinkName = %s and filterValue = %s", sinkName, filterValue));
            this.sinkName = sinkName;
            this.setFilterValue(filterValue);

            Map<TypeVariable<?>, Type> typeMap = TypeUtils.getTypeArguments(getClass(), Typed.class);
            if (localLOGV) {
                Log.v(TAG, "Assignments for "+getClass()+":");
                for (TypeVariable<?> tv : typeMap.keySet()) {
                    Log.v(TAG, String.format("%s => %s", tv, typeMap.get(tv)));
                }
            }
            tRequestType = typeMap.get(g_tRequestToken);
            if (localLOGD) {
                Log.d(TAG, "TypedFilter: TRequest is "+tRequestType);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public final boolean shouldAccept(SinkRequest request) {
            if (sinkName != null && !sinkName.equals(request.getSinkName())) {
                return false;
            }

            if (TypeUtils.isInstance(request, tRequestType)) {
                return shouldAcceptFiltered((TRequest)request);
            } else {
                if (localLOGD) {
                    Log.d(TAG, request+" not instance of "+tRequestType);
                }
                return false;
            }
        }

        @Override
        public String toString() {
            return "sink="+sinkName+", requestType="+tRequestType;
        }

        protected boolean shouldAcceptFiltered(TRequest request) {
            return true;
        }
    }

    public static Filter parseFilter(XmlResourceParser parser, Resources resources)
            throws XmlPullParserException, IOException
    {
        Filter filter;
        parser.require(START_TAG, "", null);
        String tagName = parser.getName();
        int depth = parser.getDepth();

        try {
            String sinkName = parser.getAttributeValue(Utils.FLOWFENCE_NAMESPACE, "sink");
            if (sinkName != null) {
                // This rule is scoped to a sink. Try to look it up.
                Sink sink = Sink.forName(sinkName);
                if (sink == null) {
                    String msg = String.format("Unknown sink '%s' at %s",
                            sinkName, parser.getPositionDescription());
                    throw new PolicyParseException(msg);
                }

                filter = sink.newFilter(parser, resources);
            } else {
                filter = Filter.ALWAYS;
            }
            return filter;
        } finally {
            Utils.skip(parser, depth);
            parser.require(END_TAG, "", tagName);
        }
    }
}
