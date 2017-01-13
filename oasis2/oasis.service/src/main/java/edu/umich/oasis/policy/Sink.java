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

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import edu.umich.oasis.helpers.Utils;
import edu.umich.oasis.service.TrustedAPI;

public abstract class Sink {
    public interface Factory {
        Sink createInstance();
    }

    public static Factory registerBasicSink(final String sinkName) {
        Factory fact = new Factory() {
            @Override
            public Sink createInstance() {
                return new Sink(sinkName) {
                    @Override
                    public Filter newFilter(XmlResourceParser parser, Resources resources)
                            throws XmlPullParserException, IOException {
                        Utils.skip(parser);
                        return new Filter.Typed<SinkRequest>(sinkName) { };
                    }
                };
            }
        };
        register(sinkName, fact);
        return fact;
    }

    private static final ConcurrentHashMap<String, Sink.Factory> g_mSinks = new ConcurrentHashMap<>();

    public static void register(String sinkName, Sink.Factory sinkFactory) {
        g_mSinks.putIfAbsent(sinkName, sinkFactory);
    }

    public static Sink forName(String sinkName) {
        Sink.Factory factory = g_mSinks.get(sinkName);
        return (factory == null) ? null : factory.createInstance();
    }

    static {
        TrustedAPI.registerSinks();
    }

    private final String sinkName;

    protected Sink(String sinkName) {
        this.sinkName = sinkName;
    }

    public final String getName() {
        return sinkName;
    }

    public abstract Filter newFilter(XmlResourceParser parser, Resources resources)
            throws XmlPullParserException, IOException;
}
