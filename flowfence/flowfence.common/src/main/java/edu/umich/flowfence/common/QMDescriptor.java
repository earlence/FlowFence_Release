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
import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.umich.flowfence.common.FlowfenceConstants.*;

public class QMDescriptor implements Parcelable {
    public static final int KIND_INSTANCE = 0;
    public static final int KIND_STATIC = 1;
    public static final int KIND_CTOR = 2;

    public final int kind;
    public final ComponentName definingClass;
    public final String methodName;
    public final List<String> paramTypes;

    private QMDescriptor(int kind, ComponentName definingClass, String methodName,
                         List<String> paramTypes, boolean makeCopy) {
        if (kind != KIND_INSTANCE && kind != KIND_STATIC && kind != KIND_CTOR) {
            throw new IllegalArgumentException("Unknown kind "+kind);
        }

        this.kind = kind;

        this.definingClass = Objects.requireNonNull(definingClass, "definingClass");
        this.methodName = (kind != KIND_CTOR) ?
                Objects.requireNonNull(methodName, "methodName") :
                null;
        this.paramTypes = (paramTypes != null) ?
                Collections.unmodifiableList(makeCopy ? new ArrayList<>(paramTypes) : paramTypes) :
                Collections.<String>emptyList();
    }

    public static QMDescriptor forInstance(ComponentName definingClass, String methodName, List<String> paramTypes) {
        return new QMDescriptor(KIND_INSTANCE, definingClass, methodName, paramTypes, true);
    }

    public static QMDescriptor forStatic(ComponentName definingClass, String methodName, List<String> paramTypes) {
        return new QMDescriptor(KIND_STATIC, definingClass, methodName, paramTypes, true);
    }

    public static QMDescriptor forConstructor(ComponentName definingClass, List<String> paramTypes) {
        return new QMDescriptor(KIND_CTOR, definingClass, null, paramTypes, true);
    }

    private static List<String> toNames(Class<?>... classes) {
        if (!ParceledPayload.canParcelTypes(classes)) {
            throw new ParcelFormatException("Can't parcel arguments");
        }
        // ClassUtils.classesTaClassNames doesn't work here, because it emits arrays
        // in their JNI form ([Ljava.lang.String;). Handle array type conversion manually.
        List<String> classNames = new ArrayList<>(classes.length);
        for (Class<?> clazz : classes) {
            int arrayDepth = 0;
            while (clazz.isArray()) {
                arrayDepth++;
                clazz = clazz.getComponentType();
            }
            classNames.add(clazz.getName()+StringUtils.repeat("[]", arrayDepth));
        }
        return classNames;
    }

    public static QMDescriptor forInstance(Context context, Class<?> definingClass, String methodName, Class<?>... paramTypes) {
        if (!ParceledPayload.canParcelType(definingClass)) {
            throw new ParcelFormatException("Can't parcel instance type");
        }
        return new QMDescriptor(KIND_INSTANCE, new ComponentName(context, definingClass),
                methodName, toNames(paramTypes), false);
    }

    public static QMDescriptor forStatic(Context context, Class<?> definingClass, String methodName, Class<?>... paramTypes) {
        return new QMDescriptor(KIND_STATIC, new ComponentName(context, definingClass),
                methodName, toNames(paramTypes), false);
    }

    public static QMDescriptor forConstructor(Context context, Class<?> definingClass, Class<?>... paramTypes) {
        if (!ParceledPayload.canParcelType(definingClass)) {
            throw new ParcelFormatException("Can't parcel constructed type");
        }
        return new QMDescriptor(KIND_CTOR, new ComponentName(context, definingClass),
                null, toNames(paramTypes), false);
    }

    public static QMDescriptor forMethod(Context context, Method method) {
        Class<?> definingClass = method.getDeclaringClass();
        String methodName = method.getName();
        Class<?>[] paramTypes = method.getParameterTypes();
        if ((method.getModifiers() & Modifier.STATIC) != 0) {
            return forStatic(context, definingClass, methodName, paramTypes);
        } else {
            return forInstance(context, definingClass, methodName, paramTypes);
        }
    }

    public static QMDescriptor forConstructor(Context context, Constructor<?> ctor) {
        return forConstructor(context, ctor.getDeclaringClass(), ctor.getParameterTypes());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(kind);
        definingClass.writeToParcel(dest, flags);
        dest.writeString(methodName);
        dest.writeStringList(paramTypes);
    }

    private static final int KIND_NULL = -1;

    public static void writeToParcel(QMDescriptor desc, Parcel dest, int flags) {
        if (desc == null) {
            dest.writeInt(KIND_NULL);
        } else {
            desc.writeToParcel(dest, flags);
        }
    }

    public static QMDescriptor readFromParcel(Parcel source) {
        int kind = source.readInt();
        if (kind == KIND_NULL) {
            return null;
        }

        ComponentName definingClass = new ComponentName(source);
        String methodName = source.readString();
        ArrayList<String> paramTypes = source.createStringArrayList();

        return new QMDescriptor(kind, definingClass, methodName, paramTypes, false);
    }

    public static final Creator<QMDescriptor> CREATOR = new Creator<QMDescriptor>() {
        @Override
        public QMDescriptor createFromParcel(Parcel source) {
            return QMDescriptor.readFromParcel(source);
        }

        @Override
        public QMDescriptor[] newArray(int size) {
            return new QMDescriptor[size];
        }
    };

    private String getParamsForPrinting() {
        return StringUtils.join(paramTypes, ", ");
    }

    private String getNameFormat() {
        switch (kind) {
            case KIND_INSTANCE:
                return "%1$s#%2$s(%3$s)";
            case KIND_STATIC:
                return "%1$s::%2$s(%3$s)";
            case KIND_CTOR:
                return "new %1$s(%3$s)";
            default:
                throw new IllegalArgumentException("Unknown QMDescriptor kind");
        }
    }

    @Override
    public String toString() {
        String format = getNameFormat();
        return String.format(format, definingClass.flattenToShortString(),
                             methodName, getParamsForPrinting());
    }

    public String printCall(Object[] args) {
        String format = getNameFormat();
        String argString = ArrayUtils.toString(args, "{}");
        return String.format(format,
                ClassUtils.getShortClassName(definingClass.getClassName()),
                methodName,
                // strip off {}
                argString.substring(1, argString.length()-1));
    }

    public static final Pattern NAME_PATTERN = Pattern.compile(
            "^(?:new |(?=[\\p{javaJavaIdentifierPart}/.]+(?:#|::)))?" +
                    "(" + COMPONENT_NAME_PATTERN + ")(?:(#|::)(" + JAVA_IDENTIFIER_PATTERN + "))?"+
                    "\\((" + ARGUMENT_LIST_PATTERN + ")?\\)$"
    );

    public static QMDescriptor parse(String descriptorString) {
        Matcher matcher = NAME_PATTERN.matcher(Objects.requireNonNull(descriptorString, "descriptorString"));
        Validate.isTrue(matcher.matches(), "Can't parse QMDescriptor '%s'", descriptorString);

        ComponentName component = ComponentName.unflattenFromString(matcher.group(1));
        String indicator = matcher.group(2);
        int kind = (indicator == null) ? KIND_CTOR : indicator.equals("#") ? KIND_INSTANCE : KIND_STATIC;
        String methodName = matcher.group(3);
        String[] typeNameArray = StringUtils.splitByWholeSeparator(matcher.group(4), ", ");
        List<String> typeNames = Arrays.asList(ArrayUtils.nullToEmpty(typeNameArray));

        return new QMDescriptor(kind, component, methodName, typeNames, false);
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof QMDescriptor)) {
            return false;
        }
        QMDescriptor other = (QMDescriptor)o;
        return kind == other.kind &&
                definingClass.equals(other.definingClass) &&
                Objects.equals(methodName, other.methodName) &&
                paramTypes.equals(other.paramTypes);
    }

    public int hashCode() {
        return Objects.hash(kind, definingClass, methodName, paramTypes);
    }
}
