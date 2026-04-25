package com.ai.assistance.shower;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

public class ShowerBinderContainer implements Parcelable {

    public static final Creator<ShowerBinderContainer> CREATOR = new Creator<ShowerBinderContainer>() {
        @Override
        public ShowerBinderContainer createFromParcel(Parcel in) {
            return new ShowerBinderContainer(in);
        }

        @Override
        public ShowerBinderContainer[] newArray(int size) {
            return new ShowerBinderContainer[size];
        }
    };

    private final IBinder binder;

    public ShowerBinderContainer(IBinder binder) {
        this.binder = binder;
    }

    protected ShowerBinderContainer(Parcel in) {
        this.binder = in.readStrongBinder();
    }

    public IBinder getBinder() {
        return binder;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(binder);
    }
}
