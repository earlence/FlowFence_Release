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

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

public final class CallParam implements Parcelable {

    private static final String TAG = "OASIS.CallParam";

	public static final int TYPE_NULL        = 0x00;
	public static final int TYPE_DATA        = 0x01;
	public static final int TYPE_HANDLE      = 0x02;
	public static final int MASK_TYPE        = 0xFF;
	
	public static final int FLAG_RETURN      = 0x0100;
    public static final int FLAG_BY_REF      = 0x0200;
	public static final int MASK_FLAG        = 0xFF00;
	
	public static final int HANDLE_RELEASE   = 0x00010000;
	public static final int HANDLE_SYNC_ONLY = 0x00020000;
	public static final int MASK_TYPEFLAG    = 0xFFFF0000;
	
	private int header;
	private Object payload;
	
	public CallParam() {
		header = TYPE_NULL;
		payload = null;
	}
	
	public void readFromParcel(Parcel p) {
        int startPos = p.dataPosition();
		header = p.readInt();
		
		switch (getType()) {
		case TYPE_NULL:
			// NULL: no need to write any data
			payload = null;
			break;
		case TYPE_DATA:
			// DATA: write parceled data as byte[], so it can be skipped
			// without running untrusted code
			payload = ParceledPayload.fromParcel(p);
			break;
		case TYPE_HANDLE:
			payload = p.readStrongBinder();
			break;
		default:
			throw new IllegalArgumentException(
				String.format("Unknown CallParam type 0x%02x", header & MASK_TYPE));
		}
        //Log.d(TAG, String.format("Read (%d bytes @0x%x) %s", p.dataPosition() - startPos, startPos, this));
	}
	
	public void setType(int newType) {
		header = (header & ~MASK_TYPE) | (newType & MASK_TYPE);
	}
	
	public int getType() {
		return (header & MASK_TYPE);
	}

    public int getHeader() {
        return header;
    }
	
	public void setHandle(IBinder handle, int flags) {
		payload = handle;
		header = flags;
		setType(handle != null ? TYPE_HANDLE : TYPE_NULL);
	}
	
	public void setData(Object data, int flags) {
		payload = data;
		header = flags;
		setType(data != null ? TYPE_DATA : TYPE_NULL);
	}
	
	public void setNull(int flags) {
		payload = null;
		header = flags;
		setType(TYPE_NULL);
	}
	
	public Object getPayload() {
		return payload;
	}

    public Object decodePayload(ClassLoader loader) {
        if (payload instanceof ParceledPayload) {
            return ((ParceledPayload)payload).getValue(loader);
        }
        return payload;
    }

	@Override
    public String toString() {
        return toString(null);
    }

	public String toString(ClassLoader loader) {
        StringBuilder sb = new StringBuilder();
        sb.append("[param ");
        if ((header & FLAG_BY_REF) != 0) {
            sb.append("ref ");
        }
        if ((header & FLAG_RETURN) != 0) {
            sb.append("inout ");
        }
        switch (getType()) {
            case TYPE_NULL:
                sb.append("null");
                break;
            case TYPE_DATA:
                Object value = "decode-failure";
                sb.append("data ");
                try {
                    value = decodePayload(loader);
                } catch (Exception e) {
                    sb.append(payload);
                    break;
                }
                sb.append(ClassUtils.getShortClassName(value, "null"));
                if (value != null) {
                    sb.append(' ');
                    sb.append(ArrayUtils.toString(value));
                }
                break;
            case TYPE_HANDLE:
                sb.append("handle ");
                if ((header & HANDLE_RELEASE) != 0) {
                    sb.append("release ");
                }
                if ((header & HANDLE_SYNC_ONLY) != 0) {
                    sb.append("synconly ");
                }
                sb.append(payload);
                break;
            default:
                sb.append("<unknown>");
                break;
        }

        sb.append(']');
        return sb.toString();
    }

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
        int startPos = dest.dataPosition();
		if (payload == null) {
			setType(TYPE_NULL);
		}
		
		dest.writeInt(header);
		switch (getType()) {
		case TYPE_NULL:
			// NULL: no need to write any data
			break;
		case TYPE_DATA:
			// DATA: write parceled data as byte[], so it can be skipped
			// without running untrusted code
            ParceledPayload parceled = (payload instanceof ParceledPayload)
                    ? (ParceledPayload)payload
                    : ParceledPayload.create(payload);
            parceled.writeToParcel(dest, flags);
			break;
		case TYPE_HANDLE:
			dest.writeStrongBinder((IBinder)payload);
            break;
		default:
			throw new IllegalArgumentException(
				String.format("Unknown CallParam type 0x%02x", header & MASK_TYPE));
		}
        //Log.d(TAG, String.format("Wrote (%d bytes @0x%x) %s", dest.dataPosition() - startPos, startPos, this));
	}
	
	public static final Parcelable.Creator<CallParam> CREATOR = new Parcelable.Creator<CallParam>() {
		@Override
		public CallParam createFromParcel(Parcel source) {
			CallParam c = new CallParam();
			c.readFromParcel(source);
			return c;
		}

		@Override
		public CallParam[] newArray(int size) {
			return new CallParam[size];
		}
	};

}
