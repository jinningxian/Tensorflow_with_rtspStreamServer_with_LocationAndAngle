package com.ncs.rtspstream.Models;

import android.os.Parcel;
import android.os.Parcelable;

public class Answer implements Parcelable {
    public final String name;

    public Answer(String name) {
        this.name=name;
    }

    protected Answer(Parcel in) {
        name = in.readString();
    }

    public static final Creator<Answer> CREATOR = new Creator<Answer>() {
        @Override
        public Answer createFromParcel(Parcel in) {
            return new Answer(in);
        }

        @Override
        public Answer[] newArray(int size) {
            return new Answer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);

    }
}
