package edu.umich.oasis.common;

import android.os.Parcel;
import android.os.Parcelable;

import org.apache.commons.lang3.ClassUtils;

public final class ParamInfo implements Parcelable {
	private final String typeName;
	private final int paramIndex;
	private final Direction direction;
	
	public ParamInfo(String typeName, int paramIndex, Direction direction) {
		this.typeName = typeName;
		this.paramIndex = paramIndex;
		this.direction = direction;
	}
	
	public ParamInfo(Parcel source) {
		this.typeName = source.readString();
		this.paramIndex = source.readInt();
		this.direction = Direction.CREATOR.createFromParcel(source);
	}
	
	public Class<?> getType() throws ClassNotFoundException {
		return getType(getClass().getClassLoader());
	}
	
	public Class<?> getType(ClassLoader loader) throws ClassNotFoundException {
		return ClassUtils.getClass(loader, typeName, true);
	}
	
	public String getTypeName() {
		return typeName;
	}

	public int getParamIndex() { return paramIndex; }
	
	public Direction getDirection() {
		return direction;
	}

	@Override
	public String toString() {
		return String.format("Param#%d(%s %s)", paramIndex, direction.toString().toLowerCase(), typeName);
	}

    //region Parcelable
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(typeName);
		dest.writeInt(paramIndex);
		direction.writeToParcel(dest, flags);
	}
	
	public static final Parcelable.Creator<ParamInfo> CREATOR = new Parcelable.Creator<ParamInfo>() {
		@Override
		public ParamInfo createFromParcel(Parcel source) {
			return new ParamInfo(source);
		}

		@Override
		public ParamInfo[] newArray(int size) {
			return new ParamInfo[size];
		}
	};
    //endregion
}
