package edu.umich.oasis.policy;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

import edu.umich.oasis.helpers.Utils;

import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

/**
 * Created by jpaupore on 1/13/16.
 */
public abstract class Filter {
    private static final String TAG = "OASIS.Filter";

    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);
    public abstract boolean shouldAccept(SinkRequest request);

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
            this.sinkName = sinkName;
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
            String sinkName = parser.getAttributeValue(Utils.OASIS_NAMESPACE, "sink");

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
