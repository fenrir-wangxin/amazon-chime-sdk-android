package com.amazonaws.services.chime.sdkdemo.util

import android.view.View

class ItemClickListener<Data>(val listener: ((view: View, sData: Data, position: Int) -> Unit)) {

    fun onClick(view: View, sData: Data, position: Int) {
        listener(view, sData, position)
    }

}