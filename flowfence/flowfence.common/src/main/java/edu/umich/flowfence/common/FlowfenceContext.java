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

package edu.umich.flowfence.common;

import android.content.Context;
import android.content.ContextWrapper;

public abstract class FlowfenceContext extends ContextWrapper {
    private static FlowfenceContext mInstance = null;

    public static FlowfenceContext getInstance() {
        return mInstance;
    }

    protected static void setInstance(FlowfenceContext context) {
        mInstance = context;
    }

    public static boolean isInQM() {
        return (mInstance != null);
    }

    protected FlowfenceContext(Context baseCtx) {
        super(baseCtx);
    }

    public abstract Object getTrustedAPI(String apiName);
}
