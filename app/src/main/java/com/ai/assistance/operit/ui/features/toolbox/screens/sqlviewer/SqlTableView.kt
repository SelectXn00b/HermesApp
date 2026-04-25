package com.ai.assistance.operit.ui.features.toolbox.screens.sqlviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import kotlin.math.roundToInt

class SqlTableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val headerBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val scroller = OverScroller(context)
    private val gestureDetector: GestureDetector
    private val scaleDetector: ScaleGestureDetector

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val baseCellWidth = 140f * density
    private val baseRowHeight = 32f * density
    private val baseHeaderHeight = 36f * density
    private val basePaddingX = 6f * density
    private val basePaddingY = 4f * density

    private var isScaling = false
    private var focusX = 0f
    private var focusY = 0f

    private var zoomScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    var columns: List<String> = emptyList()
        private set

    var rows: List<List<String>> = emptyList()
        private set

    var headerTextColor: Int = 0xFF000000.toInt()
    var bodyTextColor: Int = 0xFF000000.toInt()
    var headerBackgroundColor: Int = 0xFFE0E0E0.toInt()
    var gridLineColor: Int = 0xFFCCCCCC.toInt()
    var headerFontSizeSp: Float = 12f
    var bodyFontSizeSp: Float = 12f
    var minScale: Float = 0.6f
    var maxScale: Float = 2.2f

    init {
        headerPaint.typeface = Typeface.DEFAULT_BOLD
        bodyPaint.typeface = Typeface.DEFAULT

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isScaling) return false
                scrollByDistance(distanceX, distanceY)
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (isScaling) return false
                startFling(velocityX, velocityY)
                return true
            }
        })

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                focusX = detector.focusX
                focusY = detector.focusY
                applyScale(detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        })
    }

    fun updateStyle(
        headerTextColor: Int,
        bodyTextColor: Int,
        headerBackgroundColor: Int,
        gridLineColor: Int,
        headerFontSizeSp: Float,
        bodyFontSizeSp: Float
    ) {
        this.headerTextColor = headerTextColor
        this.bodyTextColor = bodyTextColor
        this.headerBackgroundColor = headerBackgroundColor
        this.gridLineColor = gridLineColor
        this.headerFontSizeSp = headerFontSizeSp
        this.bodyFontSizeSp = bodyFontSizeSp
        invalidate()
    }

    fun setData(columns: List<String>, rows: List<List<String>>) {
        this.columns = columns
        this.rows = rows
        clampOffset()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampOffset()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val cellWidth = baseCellWidth * zoomScale
        val rowHeight = baseRowHeight * zoomScale
        val headerHeight = baseHeaderHeight * zoomScale
        val paddingX = basePaddingX * zoomScale
        val paddingY = basePaddingY * zoomScale

        headerPaint.color = headerTextColor
        bodyPaint.color = bodyTextColor
        headerPaint.textSize = headerFontSizeSp * scaledDensity * zoomScale
        bodyPaint.textSize = bodyFontSizeSp * scaledDensity * zoomScale

        headerBackgroundPaint.color = headerBackgroundColor
        gridPaint.color = gridLineColor
        gridPaint.strokeWidth = 1f

        val maxOffset = computeMaxOffset(cellWidth, rowHeight, headerHeight)
        val clampedX = offsetX.coerceIn(0f, maxOffset.first)
        val clampedY = offsetY.coerceIn(0f, maxOffset.second)
        if (clampedX != offsetX || clampedY != offsetY) {
            offsetX = clampedX
            offsetY = clampedY
        }

        val viewportWidth = width.toFloat()
        val bodyViewportHeight = (height.toFloat() - headerHeight).coerceAtLeast(0f)

        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerBackgroundPaint)

        if (columns.isNotEmpty()) {
            val visibleStartCol = (clampedX / cellWidth).toInt().coerceAtLeast(0)
            val visibleEndCol = ((clampedX + viewportWidth) / cellWidth)
                .toInt()
                .coerceAtMost((columns.size - 1).coerceAtLeast(0))

            for (col in visibleStartCol..visibleEndCol) {
                val x = col * cellWidth - clampedX
                val headerText = columns.getOrNull(col).orEmpty()
                drawCellText(
                    canvas = canvas,
                    text = headerText,
                    paint = headerPaint,
                    cellLeft = x,
                    cellTop = 0f,
                    cellWidth = cellWidth,
                    cellHeight = headerHeight,
                    paddingX = paddingX,
                    paddingY = paddingY
                )
                canvas.drawLine(x + cellWidth, 0f, x + cellWidth, height.toFloat(), gridPaint)
            }
        }

        if (rows.isNotEmpty()) {
            val visibleStartRow = (clampedY / rowHeight).toInt().coerceAtLeast(0)
            val visibleEndRow = ((clampedY + bodyViewportHeight) / rowHeight)
                .toInt()
                .coerceAtMost((rows.size - 1).coerceAtLeast(0))

            val visibleStartCol = if (columns.isNotEmpty()) {
                (clampedX / cellWidth).toInt().coerceAtLeast(0)
            } else {
                0
            }
            val visibleEndCol = if (columns.isNotEmpty()) {
                ((clampedX + viewportWidth) / cellWidth)
                    .toInt()
                    .coerceAtMost((columns.size - 1).coerceAtLeast(0))
            } else {
                -1
            }

            for (rowIndex in visibleStartRow..visibleEndRow) {
                val rowTop = headerHeight + rowIndex * rowHeight - clampedY
                canvas.drawLine(0f, rowTop + rowHeight, width.toFloat(), rowTop + rowHeight, gridPaint)
                val row = rows.getOrNull(rowIndex) ?: continue
                if (visibleEndCol >= visibleStartCol) {
                    for (colIndex in visibleStartCol..visibleEndCol) {
                        val cellText = row.getOrNull(colIndex).orEmpty()
                        val x = colIndex * cellWidth - clampedX
                        drawCellText(
                            canvas = canvas,
                            text = cellText,
                            paint = bodyPaint,
                            cellLeft = x,
                            cellTop = rowTop,
                            cellWidth = cellWidth,
                            cellHeight = rowHeight,
                            paddingX = paddingX,
                            paddingY = paddingY
                        )
                    }
                }
            }
        }
    }

    private fun applyScale(scaleFactor: Float) {
        if (width == 0 || height == 0) return
        val newScale = (zoomScale * scaleFactor).coerceIn(minScale, maxScale)
        if (newScale == zoomScale) return
        val scaleChange = newScale / zoomScale
        val newOffsetX = (offsetX + focusX) * scaleChange - focusX
        val newOffsetY = (offsetY + focusY) * scaleChange - focusY
        zoomScale = newScale
        val maxOffset = computeMaxOffset(
            baseCellWidth * zoomScale,
            baseRowHeight * zoomScale,
            baseHeaderHeight * zoomScale
        )
        offsetX = newOffsetX.coerceIn(0f, maxOffset.first)
        offsetY = newOffsetY.coerceIn(0f, maxOffset.second)
        invalidate()
    }

    private fun scrollByDistance(distanceX: Float, distanceY: Float) {
        val maxOffset = computeMaxOffset(
            baseCellWidth * zoomScale,
            baseRowHeight * zoomScale,
            baseHeaderHeight * zoomScale
        )
        offsetX = (offsetX + distanceX).coerceIn(0f, maxOffset.first)
        offsetY = (offsetY + distanceY).coerceIn(0f, maxOffset.second)
        invalidate()
    }

    private fun startFling(velocityX: Float, velocityY: Float) {
        val maxOffset = computeMaxOffset(
            baseCellWidth * zoomScale,
            baseRowHeight * zoomScale,
            baseHeaderHeight * zoomScale
        )
        if (maxOffset.first == 0f && maxOffset.second == 0f) return
        scroller.fling(
            offsetX.roundToInt(),
            offsetY.roundToInt(),
            (-velocityX).roundToInt(),
            (-velocityY).roundToInt(),
            0,
            maxOffset.first.roundToInt(),
            0,
            maxOffset.second.roundToInt()
        )
        postInvalidateOnAnimation()
    }

    private fun clampOffset() {
        val maxOffset = computeMaxOffset(
            baseCellWidth * zoomScale,
            baseRowHeight * zoomScale,
            baseHeaderHeight * zoomScale
        )
        offsetX = offsetX.coerceIn(0f, maxOffset.first)
        offsetY = offsetY.coerceIn(0f, maxOffset.second)
    }

    private fun computeMaxOffset(
        cellWidth: Float,
        rowHeight: Float,
        headerHeight: Float
    ): Pair<Float, Float> {
        if (width == 0 || height == 0) return 0f to 0f
        val viewportWidth = width.toFloat().coerceAtLeast(0f)
        val viewportHeight = height.toFloat().coerceAtLeast(0f)
        val contentWidth = (columns.size * cellWidth).coerceAtLeast(0f)
        val contentHeight = (rows.size * rowHeight).coerceAtLeast(0f)
        val bodyViewportHeight = (viewportHeight - headerHeight).coerceAtLeast(0f)
        val maxX = (contentWidth - viewportWidth).coerceAtLeast(0f)
        val maxY = (contentHeight - bodyViewportHeight).coerceAtLeast(0f)
        return maxX to maxY
    }

    private fun drawCellText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        cellLeft: Float,
        cellTop: Float,
        cellWidth: Float,
        cellHeight: Float,
        paddingX: Float,
        paddingY: Float
    ) {
        if (text.isEmpty()) return
        val maxWidth = cellWidth - paddingX * 2
        if (maxWidth <= 0f) return
        val displayText = if (paint.measureText(text) <= maxWidth) {
            text
        } else {
            TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        }
        val baseline = cellTop + paddingY - paint.fontMetrics.ascent
        canvas.save()
        canvas.clipRect(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
        canvas.drawText(displayText, cellLeft + paddingX, baseline, paint)
        canvas.restore()
    }
}
