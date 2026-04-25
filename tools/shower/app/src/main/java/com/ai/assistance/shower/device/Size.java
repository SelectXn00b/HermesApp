package com.ai.assistance.shower.device;

import android.graphics.Rect;

public final class Size {
    private final int width;
    private final int height;

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Rect toRect() {
        return new Rect(0, 0, width, height);
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
