package edu.umich.oasis.common;

import android.content.ComponentName;

/**
 * Created by jpaupore on 2/7/16.
 */
public interface IEventChannelAPI {
    void fireEvent(ComponentName channelName, Object... args);
    void fireEvent(TaintSet extraTaint, ComponentName channelName, Object... args);
}
