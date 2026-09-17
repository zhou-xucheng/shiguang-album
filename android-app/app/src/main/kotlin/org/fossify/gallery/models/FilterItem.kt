package org.fossify.shiguang.models

import android.graphics.Bitmap
import com.zomato.photofilters.imageprocessors.Filter

data class FilterItem(var bitmap: Bitmap, val filter: Filter)
