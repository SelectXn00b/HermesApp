package com.ai.assistance.shower;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShowerVideoSink extends IInterface {

    void onVideoFrame(byte[] data) throws RemoteException;

    abstract class Stub extends Binder implements IShowerVideoSink {

        private static final String DESCRIPTOR = "com.ai.assistance.shower.IShowerVideoSink";

        static final int TRANSACTION_onVideoFrame = IBinder.FIRST_CALL_TRANSACTION;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShowerVideoSink asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IShowerVideoSink) {
                return (IShowerVideoSink) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_onVideoFrame: {
                    data.enforceInterface(DESCRIPTOR);
                    byte[] frame = data.createByteArray();
                    onVideoFrame(frame);
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static final class Proxy implements IShowerVideoSink {

            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public void onVideoFrame(byte[] data) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeByteArray(data);
                    remote.transact(TRANSACTION_onVideoFrame, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
