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

package edu.umich.oasis.policy;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import static org.xmlpull.v1.XmlPullParser.*;

import org.apache.commons.lang3.StringUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import edu.umich.oasis.helpers.Utils;
import edu.umich.oasis.service.OASISApplication;
import edu.umich.oasis.service.R;

public abstract class Rule {
    private static final String TAG = "OASIS.Policy";

    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    public static abstract class Factory {
        protected final String ruleName;

        protected Factory(String ruleName) {
            this.ruleName = ruleName;
        }

        public abstract Rule createInstance(Policy policy, XmlResourceParser parser, Resources resources)
                throws XmlPullParserException, IOException;

        public final void register() {
            Rule.register(this.ruleName, this);
        }
    }

    public static abstract class SimpleFactory extends Factory {
        protected SimpleFactory(String ruleName) {
            super(ruleName);
        }

        @Override
        public Rule createInstance(Policy policy, XmlResourceParser parser, Resources resources)
                throws XmlPullParserException, IOException {
            return createInstance(policy, ruleName, Filter.parseFilter(parser, resources));
        }

        public abstract Rule createInstance(Policy policy, String ruleName, Filter filter);
    }

    private static final ConcurrentHashMap<String, Rule.Factory> g_mRules = new ConcurrentHashMap<>();

    public static void register(String ruleName, Rule.Factory ruleFactory) {
        g_mRules.putIfAbsent(ruleName, ruleFactory);
    }

    public static Rule parseRule(Policy policy, XmlResourceParser parser, Resources resources)
            throws XmlPullParserException, IOException {
        parser.require(START_TAG, "", null);
        String ruleName = parser.getName();
        int depth = parser.getDepth();
        Rule.Factory ruleFactory = g_mRules.get(ruleName);
        if (ruleFactory == null) {
            Log.w(TAG, String.format("Unknown action '%s' at %s",
                    ruleName, parser.getPositionDescription()));
            Utils.skip(parser);
            return null;
        }

        Rule rule;
        try {
            rule = ruleFactory.createInstance(policy, parser, resources);
        } catch (PolicyParseException e) {
            Log.w(TAG, e);
            rule = null;
        }
        Utils.skip(parser, depth);
        parser.require(END_TAG, "", ruleName);

        return rule;
    }

    private final String ruleName;
    private final Filter filter;
    private final Policy policy;

    protected Rule(Policy policy, String ruleName, Filter filter) {
        this.policy = policy;
        this.ruleName = ruleName;
        this.filter = filter;
    }

    public final String getRuleName() {
        return ruleName;
    }

    public final Policy getPolicy() {
        return policy;
    }

    public final Filter getFilter() { return filter; }

    /**
     * Check whether a request should be processed by this rule.
     * <p/>
     * Overriders should only return true here if the superclass also returns true.
     *
     * @param request The request to check.
     * @return True if the request should be subject to this rule, false to skip this rule.
     */
    protected boolean shouldProcess(SinkRequest request) {
        return filter.shouldAccept(request);
    }

    /**
     * Execute a rule on a request.
     *
     * @param request The request to run this rule on. May be mutated by the rule.
     * @return True to continue running rules in this policy; false to stop.
     */
    public final boolean process(SinkRequest request) {
        if (!shouldProcess(request)) {
            if (localLOGV) {
                Log.v(TAG, this+": filter not matched");
            }
            return true;
        }

        if (localLOGV) {
            Log.v(TAG, this+": filter matched");
        }
        return onProcess(request);
    }

    /**
     * Handle the logic of executing a request.
     *
     * @param request The request.
     * @return True to process more rules, false to stop.
     */
    protected abstract boolean onProcess(SinkRequest request);

    public String toString() {
        return ruleName+'('+filter+')';
    }

    protected boolean hasFilterValue(){
        return this.getFilter() != null && this.getFilter().getFilterValue() != null;
    }

    private static final class AllowRule extends Rule {
        @Override
        protected boolean onProcess(SinkRequest request) {
            if(request.getSinkName().equals("NETWORK") && this.hasFilterValue()){
                // Filter URL defined, should evaluate whether the request is accepted or not.
                boolean accepted  = this.getFilter().filter(request);
                if(!accepted){
                    Log.i(TAG, "Blocking network request");
                    String message = String.format("Request to URL %s blocked.", this.getFilter().getFilterValue());
                    request.addErrorMessage(this.getPolicy().getSource(), message);
                }
            }

            return false;
        }

        private AllowRule(Policy policy, String ruleName, Filter filter) {
            super(policy, ruleName, filter);
        }

        public static final Factory FACTORY = new Rule.SimpleFactory("allow") {
            @Override
            public Rule createInstance(Policy policy, String ruleName, Filter filter) {
                return new AllowRule(policy, ruleName, filter);
            }
        };
    }

    private static final class DropRule extends Rule {
        @Override
        protected boolean onProcess(SinkRequest request) {
            // We're done with this chain.
            request.reject();
            return false;
        }

        private DropRule(Policy policy, String ruleName, Filter filter) {
            super(policy, ruleName, filter);
        }

        public static final Factory FACTORY = new Rule.SimpleFactory("drop") {
            @Override
            public Rule createInstance(Policy policy, String ruleName, Filter filter) {
                return new DropRule(policy, ruleName, filter);
            }
        };
    }

    private static final class DenyRule extends Rule {
        private final String message;

        @Override
        protected boolean onProcess(SinkRequest request) {
            Source src = getPolicy().getSource();
            request.addErrorMessage(src, String.format(message,
                    src.getSourceName().flattenToShortString(), request.getSinkName()));
            return false;
        }

        private DenyRule(Policy policy, String ruleName, Filter filter, String message) {
            super(policy, ruleName, filter);
            this.message = message;
        }

        public static final Factory FACTORY = new Rule.Factory("deny") {
            @Override
            public Rule createInstance(Policy policy, XmlResourceParser parser, Resources resources)
                    throws XmlPullParserException, IOException {
                String message = parser.getAttributeValue(Utils.OASIS_NAMESPACE, "message");
                if (message == null) {
                    message = OASISApplication.getInstance().getResources()
                            .getString(R.string.default_permission_denied_message);
                }
                Filter filter = Filter.parseFilter(parser, resources);
                return new DenyRule(policy, ruleName, filter, message);
            }
        };

    }

    private static final class LogRule extends Rule {
        private final String message;

        @Override
        protected boolean onProcess(SinkRequest request) {
            Source src = getPolicy().getSource();
            Log.w(TAG, String.format(message, src.getSourceName().flattenToShortString(), request.getSinkName()));
            return true;
        }

        private LogRule(Policy policy, String ruleName, Filter filter, String message) {
            super(policy, ruleName, filter);
            this.message = message;
        }

        public static final Factory FACTORY = new Rule.Factory("log") {
            @Override
            public Rule createInstance(Policy policy, XmlResourceParser parser, Resources resources)
                    throws XmlPullParserException, IOException {
                String message = parser.getAttributeValue(Utils.OASIS_NAMESPACE, "message");
                if (message == null) {
                    message = OASISApplication.getInstance().getResources()
                            .getString(R.string.default_permission_audit_message);
                }
                Filter filter = Filter.parseFilter(parser, resources);

                return new LogRule(policy, ruleName, filter, message);
            }
        };
    }

    static {
        AllowRule.FACTORY.register();
        DenyRule.FACTORY.register();
        LogRule.FACTORY.register();
        DropRule.FACTORY.register();
    }
}