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

package edu.umich.flowfence.testapp;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Random;

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
