/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: observer service layer.
 */
package com.xiaomo.androidforclaw.aidl;

import android.os.Parcel;
import android.os.Parcelable;

public class ViewNodeParcelable implements Parcelable {
    public final int index;
    public final String text;
    public final String resourceId;
    public final String className;
    public final String packageName;
    public final String contentDesc;
    public final boolean clickable;
    public final boolean enabled;
    public final boolean focusable;
    public final boolean focused;
    public final boolean scrollable;
    public final int centerX;
    public final int centerY;
    public final int left;
    public final int right;
    public final int top;
    public final int bottom;

    public ViewNodeParcelable(
        int index,
        String text,
        String resourceId,
        String className,
        String packageName,
        String contentDesc,
        boolean clickable,
        boolean enabled,
        boolean focusable,
        boolean focused,
        boolean scrollable,
        int centerX,
        int centerY,
        int left,
        int right,
        int top,
        int bottom
    ) {
        this.index = index;
        this.text = text;
        this.resourceId = resourceId;
        this.className = className;
        this.packageName = packageName;
        this.contentDesc = contentDesc;
        this.clickable = clickable;
        this.enabled = enabled;
        this.focusable = focusable;
        this.focused = focused;
        this.scrollable = scrollable;
        this.centerX = centerX;
        this.centerY = centerY;
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    protected ViewNodeParcelable(Parcel in) {
        index = in.readInt();
        text = in.readString();
        resourceId = in.readString();
        className = in.readString();
        packageName = in.readString();
        contentDesc = in.readString();
        clickable = in.readByte() != 0;
        enabled = in.readByte() != 0;
        focusable = in.readByte() != 0;
        focused = in.readByte() != 0;
        scrollable = in.readByte() != 0;
        centerX = in.readInt();
        centerY = in.readInt();
        left = in.readInt();
        right = in.readInt();
        top = in.readInt();
        bottom = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(index);
        dest.writeString(text);
        dest.writeString(resourceId);
        dest.writeString(className);
        dest.writeString(packageName);
        dest.writeString(contentDesc);
        dest.writeByte((byte) (clickable ? 1 : 0));
        dest.writeByte((byte) (enabled ? 1 : 0));
        dest.writeByte((byte) (focusable ? 1 : 0));
        dest.writeByte((byte) (focused ? 1 : 0));
        dest.writeByte((byte) (scrollable ? 1 : 0));
        dest.writeInt(centerX);
        dest.writeInt(centerY);
        dest.writeInt(left);
        dest.writeInt(right);
        dest.writeInt(top);
        dest.writeInt(bottom);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ViewNodeParcelable> CREATOR = new Creator<ViewNodeParcelable>() {
        @Override
        public ViewNodeParcelable createFromParcel(Parcel in) {
            return new ViewNodeParcelable(in);
        }

        @Override
        public ViewNodeParcelable[] newArray(int size) {
            return new ViewNodeParcelable[size];
        }
    };
}
