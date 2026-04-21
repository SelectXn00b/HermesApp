package brut.androlib.res.decoder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import brut.androlib.exceptions.AndrolibException;
import brut.androlib.exceptions.NinePatchNotFoundException;
import brut.androlib.res.data.LayoutBounds;
import brut.androlib.res.data.NinePatchData;
import brut.util.BinaryDataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
import org.apache.commons.io.IOUtils;

public class ResNinePatchStreamDecoder implements ResStreamDecoder {
    @Override
    public void decode(InputStream inputStream, OutputStream outputStream) throws AndrolibException {
        Bitmap sourceBitmap = null;
        Bitmap outputBitmap = null;
        try {
            byte[] bytes = IOUtils.toByteArray(inputStream);
            if (bytes.length == 0) {
                return;
            }

            sourceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (sourceBitmap == null) {
                throw new IOException("Failed to decode nine-patch PNG");
            }

            int width = sourceBitmap.getWidth();
            int height = sourceBitmap.getHeight();
            outputBitmap = Bitmap.createBitmap(width + 2, height + 2, Bitmap.Config.ARGB_8888);
            new Canvas(outputBitmap).drawBitmap(sourceBitmap, 1.0f, 1.0f, null);

            NinePatchData ninePatchData = findNinePatchData(bytes);
            int bottomLineY = height + 1;
            drawHLine(outputBitmap, bottomLineY, ninePatchData.paddingLeft + 1, width - ninePatchData.paddingRight);
            int rightLineX = width + 1;
            drawVLine(outputBitmap, rightLineX, ninePatchData.paddingTop + 1, height - ninePatchData.paddingBottom);

            if (ninePatchData.xDivs.length == 0) {
                drawHLine(outputBitmap, 0, 1, width);
            } else {
                for (int index = 0; index < ninePatchData.xDivs.length; index += 2) {
                    drawHLine(outputBitmap, 0, ninePatchData.xDivs[index] + 1, ninePatchData.xDivs[index + 1]);
                }
            }

            if (ninePatchData.yDivs.length == 0) {
                drawVLine(outputBitmap, 0, 1, height);
            } else {
                for (int index = 0; index < ninePatchData.yDivs.length; index += 2) {
                    drawVLine(outputBitmap, 0, ninePatchData.yDivs[index] + 1, ninePatchData.yDivs[index + 1]);
                }
            }

            try {
                LayoutBounds layoutBounds = findLayoutBounds(bytes);
                for (int index = 0; index < layoutBounds.left; index += 1) {
                    outputBitmap.setPixel(index + 1, bottomLineY, LayoutBounds.COLOR_TICK);
                }
                for (int index = 0; index < layoutBounds.right; index += 1) {
                    outputBitmap.setPixel(width - index, bottomLineY, LayoutBounds.COLOR_TICK);
                }
                for (int index = 0; index < layoutBounds.top; index += 1) {
                    outputBitmap.setPixel(rightLineX, index + 1, LayoutBounds.COLOR_TICK);
                }
                for (int index = 0; index < layoutBounds.bottom; index += 1) {
                    outputBitmap.setPixel(rightLineX, height - index, LayoutBounds.COLOR_TICK);
                }
            } catch (NinePatchNotFoundException ignored) {
            }

            if (!outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("Failed to encode nine-patch PNG");
            }
        } catch (IOException | NullPointerException e) {
            throw new AndrolibException(e);
        } finally {
            if (sourceBitmap != null) {
                sourceBitmap.recycle();
            }
            if (outputBitmap != null) {
                outputBitmap.recycle();
            }
        }
    }

    private NinePatchData findNinePatchData(byte[] bytes) throws NinePatchNotFoundException, IOException {
        BinaryDataInputStream input = new BinaryDataInputStream(bytes, ByteOrder.BIG_ENDIAN);
        findChunk(input, NinePatchData.MAGIC);
        return NinePatchData.read(input);
    }

    private LayoutBounds findLayoutBounds(byte[] bytes) throws NinePatchNotFoundException, IOException {
        BinaryDataInputStream input = new BinaryDataInputStream(bytes, ByteOrder.BIG_ENDIAN);
        findChunk(input, LayoutBounds.MAGIC);
        return LayoutBounds.read(input);
    }

    private void findChunk(BinaryDataInputStream input, int chunkType) throws NinePatchNotFoundException, IOException {
        input.skipBytes(8);
        while (true) {
            try {
                int chunkSize = input.readInt();
                if (input.readInt() == chunkType) {
                    return;
                }
                input.skipBytes(chunkSize + 4);
            } catch (EOFException e) {
                throw new NinePatchNotFoundException();
            }
        }
    }

    private void drawHLine(Bitmap bitmap, int y, int startX, int endX) {
        for (int x = startX; x <= endX; x += 1) {
            bitmap.setPixel(x, y, NinePatchData.COLOR_TICK);
        }
    }

    private void drawVLine(Bitmap bitmap, int x, int startY, int endY) {
        for (int y = startY; y <= endY; y += 1) {
            bitmap.setPixel(x, y, NinePatchData.COLOR_TICK);
        }
    }
}
