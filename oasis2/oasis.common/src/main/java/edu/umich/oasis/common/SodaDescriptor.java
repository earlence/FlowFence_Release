package edu.umich.oasis.common;

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

import static edu.umich.oasis.common.OASISConstants.*;

/**
 * Created by jpaupore on 1/27/15.
 */
public class SodaDescriptor implements Parcelable {
    public static final int KIND_INSTANCE = 0;
    public static final int KIND_STATIC = 1;
    public static final int KIND_CTOR = 2;

    public final int kind;
    public final ComponentName definingClass;
    public final String methodName;
    public final List<String> paramTypes;

    private SodaDescriptor(int kind, ComponentName definingClass, String methodName,
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

    public static SodaDescriptor forInstance(ComponentName definingClass, String methodName, List<String> paramTypes) {
        return new SodaDescriptor(KIND_INSTANCE, definingClass, methodName, paramTypes, true);
    }

    public static SodaDescriptor forStatic(ComponentName definingClass, String methodName, List<String> paramTypes) {
        return new SodaDescriptor(KIND_STATIC, definingClass, methodName, paramTypes, true);
    }

    public static SodaDescriptor forConstructor(ComponentName definingClass, List<String> paramTypes) {
        return new SodaDescriptor(KIND_CTOR, definingClass, null, paramTypes, true);
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

    public static SodaDescriptor forInstance(Context context, Class<?> definingClass, String methodName, Class<?>... paramTypes) {
        if (!ParceledPayload.canParcelType(definingClass)) {
            throw new ParcelFormatException("Can't parcel instance type");
        }
        return new SodaDescriptor(KIND_INSTANCE, new ComponentName(context, definingClass),
                methodName, toNames(paramTypes), false);
    }

    public static SodaDescriptor forStatic(Context context, Class<?> definingClass, String methodName, Class<?>... paramTypes) {
        return new SodaDescriptor(KIND_STATIC, new ComponentName(context, definingClass),
                methodName, toNames(paramTypes), false);
    }

    public static SodaDescriptor forConstructor(Context context, Class<?> definingClass, Class<?>... paramTypes) {
        if (!ParceledPayload.canParcelType(definingClass)) {
            throw new ParcelFormatException("Can't parcel constructed type");
        }
        return new SodaDescriptor(KIND_CTOR, new ComponentName(context, definingClass),
                null, toNames(paramTypes), false);
    }

    public static SodaDescriptor forMethod(Context context, Method method) {
        Class<?> definingClass = method.getDeclaringClass();
        String methodName = method.getName();
        Class<?>[] paramTypes = method.getParameterTypes();
        if ((method.getModifiers() & Modifier.STATIC) != 0) {
            return forStatic(context, definingClass, methodName, paramTypes);
        } else {
            return forInstance(context, definingClass, methodName, paramTypes);
        }
    }

    public static SodaDescriptor forConstructor(Context context, Constructor<?> ctor) {
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

    public static void writeToParcel(SodaDescriptor desc, Parcel dest, int flags) {
        if (desc == null) {
            dest.writeInt(KIND_NULL);
        } else {
            desc.writeToParcel(dest, flags);
        }
    }

    public static SodaDescriptor readFromParcel(Parcel source) {
        int kind = source.readInt();
        if (kind == KIND_NULL) {
            return null;
        }

        ComponentName definingClass = new ComponentName(source);
        String methodName = source.readString();
        ArrayList<String> paramTypes = source.createStringArrayList();

        return new SodaDescriptor(kind, definingClass, methodName, paramTypes, false);
    }

    public static final Creator<SodaDescriptor> CREATOR = new Creator<SodaDescriptor>() {
        @Override
        public SodaDescriptor createFromParcel(Parcel source) {
            return SodaDescriptor.readFromParcel(source);
        }

        @Override
        public SodaDescriptor[] newArray(int size) {
            return new SodaDescriptor[size];
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
                throw new IllegalArgumentException("Unknown SodaDescriptor kind");
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

    public static SodaDescriptor parse(String descriptorString) {
        Matcher matcher = NAME_PATTERN.matcher(Objects.requireNonNull(descriptorString, "descriptorString"));
        Validate.isTrue(matcher.matches(), "Can't parse SodaDescriptor '%s'", descriptorString);

        ComponentName component = ComponentName.unflattenFromString(matcher.group(1));
        String indicator = matcher.group(2);
        int kind = (indicator == null) ? KIND_CTOR : indicator.equals("#") ? KIND_INSTANCE : KIND_STATIC;
        String methodName = matcher.group(3);
        String[] typeNameArray = StringUtils.splitByWholeSeparator(matcher.group(4), ", ");
        List<String> typeNames = Arrays.asList(ArrayUtils.nullToEmpty(typeNameArray));

        return new SodaDescriptor(kind, component, methodName, typeNames, false);
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof SodaDescriptor)) {
            return false;
        }
        SodaDescriptor other = (SodaDescriptor)o;
        return kind == other.kind &&
                definingClass.equals(other.definingClass) &&
                Objects.equals(methodName, other.methodName) &&
                paramTypes.equals(other.paramTypes);
    }

    public int hashCode() {
        return Objects.hash(kind, definingClass, methodName, paramTypes);
    }
}
