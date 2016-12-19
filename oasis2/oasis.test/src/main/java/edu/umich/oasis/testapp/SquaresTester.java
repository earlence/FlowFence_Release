package edu.umich.oasis.testapp;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Random;

/**
 * Created by jpaupore on 11/18/15.
 */
public class SquaresTester implements Parcelable {
    private int chunkCount;
    private int chunkIndex;
    private int[] squares;

    public SquaresTester(int chunkCount, int size) {
        this.chunkCount = chunkCount;
        this.chunkIndex = 0;
        this.squares = new int[size];
        Random rand = new Random();
        // Prefill with gibberish.
        for (int i = 0; i < size; i++) {
            squares[i] = rand.nextInt();
        }
    }

    public void fillNextChunk() {
        if (chunkIndex == chunkCount) {
            throw new IllegalStateException("Already filled");
        }
        int startIndex = (chunkIndex * squares.length) / chunkCount;
        int endIndex = (++chunkIndex * squares.length) / chunkCount;

        for (int i = startIndex; i < endIndex; i++) {
            squares[i] = i*i;
        }
    }

    public SquaresTester(Parcel source) {
        this.chunkCount = source.readInt();
        this.squares = source.createIntArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(chunkCount);
        dest.writeIntArray(squares);
    }

    public static final Parcelable.Creator<SquaresTester> CREATOR = new Parcelable.Creator<SquaresTester>() {
        @Override
        public SquaresTester createFromParcel(Parcel source) {
            return new SquaresTester(source);
        }

        @Override
        public SquaresTester[] newArray(int size) {
            return new SquaresTester[size];
        }
    };
}
