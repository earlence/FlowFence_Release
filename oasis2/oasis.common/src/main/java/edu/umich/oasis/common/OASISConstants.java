package edu.umich.oasis.common;

import android.content.ComponentName;
import android.content.Context;

import org.apache.commons.lang3.ObjectUtils;

import java.util.regex.Pattern;

/**
 * Created by earlence on 4/26/15.
 */
public final class OASISConstants
{
    private OASISConstants() { }
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
