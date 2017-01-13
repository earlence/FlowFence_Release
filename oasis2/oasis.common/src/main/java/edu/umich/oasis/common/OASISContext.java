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

package edu.umich.oasis.common;

import android.content.Context;
import android.content.ContextWrapper;

public abstract class OASISContext extends ContextWrapper {
    private static OASISContext mInstance = null;

    public static OASISContext getInstance() {
        return mInstance;
    }

    protected static void setInstance(OASISContext context) {
        mInstance = context;
    }

    public static boolean isInSoda() {
        return (mInstance != null);
    }

    protected OASISContext(Context baseCtx) {
        super(baseCtx);
    }

    public abstract Object getTrustedAPI(String apiName);
}
