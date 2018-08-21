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

import android.content.ComponentName;
import android.content.Context;

import org.apache.commons.lang3.ObjectUtils;

import java.util.regex.Pattern;

public final class FlowfenceConstants
{
    private FlowfenceConstants() { }
    public static final int NUM_SANDBOXES = ObjectUtils.CONST(16);

    public static final String JAVA_IDENTIFIER_PATTERN = "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*";
    public static final String JAVA_PACKAGE_PATTERN = JAVA_IDENTIFIER_PATTERN + "(?:\\."+JAVA_IDENTIFIER_PATTERN+")*";
    public static final String JAVA_TYPE_PATTERN = JAVA_PACKAGE_PATTERN + "(?:\\[\\])*";
    public static final String COMPONENT_NAME_PATTERN = JAVA_PACKAGE_PATTERN+"/\\.?"+JAVA_PACKAGE_PATTERN;
    public static final String ARGUMENT_LIST_PATTERN = JAVA_TYPE_PATTERN+"(?:, "+JAVA_TYPE_PATTERN+")*";

    @SuppressWarnings("deprecation")
    public static final int MODE_WORLD_READABLE = Context.MODE_WORLD_READABLE;

    @SuppressWarnings("deprecation")
    public static final int MODE_WORLD_WRITABLE = Context.MODE_WORLD_WRITEABLE;
}
