package edu.umich.oasis.policy;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import edu.umich.oasis.helpers.Utils;
import edu.umich.oasis.service.TrustedAPI;

/**
 * Created by jpaupore on 1/13/16.
 */
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
