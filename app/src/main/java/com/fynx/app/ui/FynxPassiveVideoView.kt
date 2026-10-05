package com.fynx.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.VideoView

/** VideoView wrapper used by Home/media surfaces to keep playback passive until explicitly started. */
class FynxPassiveVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Playback is controlled by the Compose host; don't let accidental taps
        // toggle playback while scrolling the Home feed.
        return true
    }
}
