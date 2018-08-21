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

import android.app.Notification;
import android.content.ComponentName;
import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.apache.commons.lang3.Validate;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TaintSet implements Parcelable {
    private static final String TAG = "FF.TaintSet";
    private static final boolean localLOGV = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    public static final float NOT_TAINTED = Float.NEGATIVE_INFINITY;
    public static final float UNKNOWN_TAINT = Float.POSITIVE_INFINITY;

    public static final class Builder {
        private final ConcurrentHashMap<ComponentName, Float> newTaints;

        public Builder() {
            this(null);
        }

        private Builder(TaintSet from) {
            if (from == null) {
                newTaints = new ConcurrentHashMap<>();
            } else {
                newTaints = new ConcurrentHashMap<>(from.taints);
            }
        }

        public Builder addTaint(String taintKind) {
            return addTaint(ComponentName.unflattenFromString(taintKind));
        }

        public Builder addTaint(ComponentName taintKind) {
            return addTaint(taintKind, UNKNOWN_TAINT);
        }

        public Builder addTaint(String taintKind, float amountToAdd) {
            return addTaint(ComponentName.unflattenFromString(taintKind), amountToAdd);
        }

        public Builder addTaint(ComponentName taintKind, float amountToAdd) {
            Objects.requireNonNull(taintKind);
            Float oldAmount, newAmount;
            do {
                Float absentAmount = 0.0f;
                oldAmount = newTaints.putIfAbsent(taintKind, absentAmount);
                if (oldAmount == null) {
                    oldAmount = absentAmount;
                }
                newAmount = Math.max(oldAmount + amountToAdd, 0.0f);
            } while (!newTaints.replace(taintKind, oldAmount, newAmount));
            return this;
        }

        public Builder removeTaint(String taintKind) {
            return removeTaint(ComponentName.unflattenFromString(taintKind));
        }

        public Builder removeTaint(ComponentName taintKind) {
            newTaints.remove(Objects.requireNonNull(taintKind));
            return this;
        }

        private void unionWith(Map<? extends ComponentName, ? extends Float> taintMap) {
            for (Map.Entry<? extends ComponentName, ? extends Float> entry : taintMap.entrySet()) {
                this.addTaint(entry.getKey(), entry.getValue());
            }
        }

        public Builder unionWith(TaintSet other) {
            if (other != null) {
                unionWith(other.taints);
            }
            return this;
        }

        public TaintSet build() {
            if (newTaints.isEmpty()) {
                return TaintSet.EMPTY;
            } else {
                return new TaintSet(new HashMap<>(newTaints));
            }
        }
    }

    private final Map<ComponentName, Float> taints;

    private TaintSet(Map<? extends ComponentName, ? extends Float> taintMap) {
        if (taintMap == null || taintMap.isEmpty()) {
            taints = Collections.emptyMap();
        } else {
            taints = Collections.unmodifiableMap(taintMap);
        }
    }

    private TaintSet(ComponentName singleTaint, float singleAmount) {
        taints = Collections.singletonMap(Objects.requireNonNull(singleTaint), singleAmount);
    }

    public static TaintSet EMPTY = new TaintSet(null);

    public static TaintSet singleton(String taintKind) {
        return singleton(ComponentName.unflattenFromString(taintKind));
    }

    public static TaintSet singleton(ComponentName taintKind) {
        return singleton(taintKind, UNKNOWN_TAINT);
    }

    public static TaintSet singleton(String taintKind, float amount) {
        return singleton(ComponentName.unflattenFromString(taintKind), amount);
    }

    public static TaintSet singleton(ComponentName taintKind, float amount) {
        return new TaintSet(taintKind, amount);
    }

    @Deprecated
    public boolean isTaintedWith(String taintKind) {
        return isTaintedWith(ComponentName.unflattenFromString(taintKind));
    }

    public boolean isTaintedWith(ComponentName taintKind) {
        return taints.containsKey(Objects.requireNonNull(taintKind));
    }

    @Deprecated
    public float getTaintAmount(String taintKind) {
        return getTaintAmount(ComponentName.unflattenFromString(taintKind));
    }

    @Deprecated
    public float getTaintAmount(String taintKind, float amountIfNotTainted) {
        return getTaintAmount(ComponentName.unflattenFromString(taintKind), amountIfNotTainted);
    }

    public float getTaintAmount(ComponentName taintKind) {
        return getTaintAmount(taintKind, NOT_TAINTED);
    }

    public float getTaintAmount(ComponentName taintKind, float amountIfNotTainted) {
        Float amount = taints.get(Objects.requireNonNull(taintKind));
        if (amount == null) {
            return amountIfNotTainted;
        } else {
            return amount;
        }
    }

    @Deprecated
    public Map<String, Float> getAllTaints() {
        if (taints.isEmpty()) {
            return Collections.emptyMap();
        }

        HashMap<String, Float> stringTaints = new HashMap<>(taints.size());
        for (Map.Entry<ComponentName, Float> entry : taints.entrySet()) {
            stringTaints.put(entry.getKey().flattenToShortString(), entry.getValue());
        }
        return stringTaints;
    }

    public Map<ComponentName, Float> asMap() {
        return taints;
    }

    /**
     * Returns whether the taints represented by this TaintSet are no more tainted
     * than the taints represented by other.
     * @param other The TaintSet to compare to.
     * @return True, if T[this] <= T[other]; false otherwise.
     */
    public boolean isSubsetOf(TaintSet other) {
        boolean isSubset = Objects.requireNonNull(other).taints.keySet().containsAll(taints.keySet());
        if (localLOGV) {
            Log.v(TAG, String.format("%s %s %s", this, isSubset ? "<=" : ">", other));
        }
        return isSubset;
    }

    public Builder asBuilder() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TaintSet{");
        Iterator<Map.Entry<ComponentName, Float>> it = taints.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ComponentName, Float> entry = it.next();
            sb.append(entry.getKey().flattenToShortString());
            float amount = entry.getValue();
            if (amount != UNKNOWN_TAINT) {
                sb.append('=');
                sb.append(amount);
            }

            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static final char SEPARATOR = ':';

    public static TaintSet fromStrings(Iterable<? extends String> taints) {
        if (taints == null) {
            return TaintSet.EMPTY;
        }
        TaintSet.Builder builder = new TaintSet.Builder();
        for (String taint : taints) {
            try {
                int index = taint.indexOf(SEPARATOR);
                float amount;
                String label;
                if (index == -1) {
                    amount = UNKNOWN_TAINT;
                    label = taint;
                } else {
                    amount = Float.parseFloat(taint.substring(0, index));
                    label = taint.substring(index + 1);
                }
                builder.addTaint(label, amount);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Couldn't parse taint tag", e);
            }
        }

        return builder.build();
    }

    public Set<String> toStringSet() {
        if (taints.isEmpty()) {
            return Collections.emptySet();
        }

        HashSet<String> stringSet = new HashSet<>();
        for (Map.Entry<ComponentName, Float> entry : taints.entrySet()) {
            stringSet.add(Float.toHexString(entry.getValue()) + SEPARATOR +
                    entry.getKey().flattenToShortString());
        }
        return Collections.unmodifiableSet(stringSet);
    }

    //region Parcelable implementation
	public static TaintSet readFromParcel(Parcel source) {
		int numTaints = source.readInt();
        if (numTaints == -1) {
            return null;
        }
        if (numTaints == 0) {
            return TaintSet.EMPTY;
        }
        Map<ComponentName, Float> taints = new HashMap<>(numTaints);
        while (numTaints-- > 0) {
            ComponentName taintKind = new ComponentName(source);
            float taintAmount = Math.max(source.readFloat(), 0.0f);
            taints.put(taintKind, taintAmount);
        }
        return new TaintSet(taints);
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		int numTaints = taints.size();
        dest.writeInt(numTaints);
        for (Map.Entry<ComponentName, Float> entry : taints.entrySet()) {
            entry.getKey().writeToParcel(dest, flags);
            dest.writeFloat(entry.getValue());
        }
	}

    public static void writeToParcel(TaintSet ts, Parcel dest, int flags) {
        if (ts == null) {
            dest.writeInt(-1);
        } else {
            ts.writeToParcel(dest, flags);
        }
    }

    public static TaintSet nullToEmpty(TaintSet ts) {
        return (ts != null) ? ts : TaintSet.EMPTY;
    }

	@Override
	public int describeContents() {
		return 0;
	}

	public static final Parcelable.Creator<TaintSet> CREATOR = new Parcelable.Creator<TaintSet>() {
		@Override
		public TaintSet createFromParcel(Parcel source) {
			return TaintSet.readFromParcel(source);
		}

		@Override
		public TaintSet[] newArray(int size) {
			return new TaintSet[size];
		}
	};
    //endregion

    @Override
    public boolean equals(Object o) {
        if (o != null) {
            return (o instanceof TaintSet) && taints.equals(((TaintSet)o).taints);
        } else {
            return taints.isEmpty();
        }
    }

    @Override
    public int hashCode() {
        return taints.hashCode();
    }
}
