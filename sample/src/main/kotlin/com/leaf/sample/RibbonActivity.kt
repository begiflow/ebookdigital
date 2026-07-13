package com.leaf.sample

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceView
import com.leaf.filament.FilamentHost

/**
 * M2 demo host: waving ribbon with per-frame vertex streaming (see RibbonDemo).
 * Watch `adb logcat -s LeafSample` for achieved fps.
 */
class RibbonActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var host: FilamentHost
    private lateinit var demo: RibbonDemo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        host = FilamentHost(surfaceView)
        demo = RibbonDemo(host, assets)
        host.frameListener = demo::frame
    }

    override fun onResume() {
        super.onResume()
        host.resume()
    }

    override fun onPause() {
        host.pause()
        super.onPause()
    }

    override fun onDestroy() {
        demo.destroy()
        host.destroy()
        super.onDestroy()
    }
}
