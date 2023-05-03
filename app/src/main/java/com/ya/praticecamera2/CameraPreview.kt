package com.ya.praticecamera2

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView

/**
 * @author  : 周广亚
 * @version :
 * @Date    :  2023/5/1 15:07
 * @Desc    :
 *
 */
class CameraPreview  @JvmOverloads  constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int = 0) :
        TextureView(ctx, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width / 3 * 4);

    }

}