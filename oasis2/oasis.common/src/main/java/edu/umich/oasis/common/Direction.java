package edu.umich.oasis.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

public enum Direction implements Parcelable {
	IN       (0, true,  false),
	OUT      (1, false, true ),
	INOUT    (2, true,  true ),
    REFINOUT (3, true,  true );

	
	private final boolean in;
	private final boolean out;
	private final int code;
	
	private static final SparseArray<Direction> codeMap;
	static {
		Direction[] dirs = Direction.class.getEnumConstants();
		codeMap = new SparseArray<Direction>(dirs.length);
		for (int i = 0; i < dirs.length; i++) {
			Direction dir = dirs[i];
			int code = dir.code;
			if (codeMap.get(code, null) != null) {
				throw new IllegalStateException(String.format("Duplicate Direction code %d", code));
			}
			codeMap.append(code, dir);
		}
	}
	
	private Direction(int parcelCode, boolean isIn, boolean isOut) {
		this.code = parcelCode;
		this.in = isIn;
		this.out = isOut;
	}
	
	public final int getCode() {
		return code;
	}
	
	public boolean isIn() {
		return in;
	}
	
	public boolean isOut() {
		return out;
	}
	
	public static final Direction fromCode(int code) {
		Direction d = fromCode(code, null);
		if (d == null) {
			throw new IllegalArgumentException(String.format("Unknown Direction code %d", code));
		} 
		return d;
	}
	
	public static final Direction fromCode(int code, Direction ifNotFound) {
		return codeMap.get(code, ifNotFound);
	}

	//region Parcelable implementation
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(code);
	}

	public static final Parcelable.Creator<Direction> CREATOR = new Parcelable.Creator<Direction>() {
		@Override
		public final Direction createFromParcel(Parcel source) {
			return Direction.fromCode(source.readInt());
		}

		@Override
		public final Direction[] newArray(int size) {
			return new Direction[size];
		}
	};
	//endregion
}
