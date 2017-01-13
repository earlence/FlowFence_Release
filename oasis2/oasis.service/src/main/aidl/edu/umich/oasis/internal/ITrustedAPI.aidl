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

// ISandboxCallout.aidl
package edu.umich.oasis.internal;

// Declare any non-default types here with import statements
import edu.umich.oasis.kvs.IRemoteSharedPrefs;
import edu.umich.oasis.common.TaintSet;
import edu.umich.oasis.common.smartthings.SmartDevice;
import edu.umich.oasis.events.IEventChannelSender;
import android.content.ComponentName;
import java.util.List;

interface ITrustedAPI {
    void taintSelf(in TaintSet taint);
    TaintSet removeTaints(in TaintSet taints);

    IRemoteSharedPrefs openSharedPrefs(String packageName, String storeName, int mode);
    void showToast(CharSequence text, int duration);

    String sendPush(String title, String body);

    //SmartThings Bridge API
    List<SmartDevice> getSwitches();
    void switchOp(String op, String switchId);

    IEventChannelSender getEventChannel(in ComponentName channelName);
}
