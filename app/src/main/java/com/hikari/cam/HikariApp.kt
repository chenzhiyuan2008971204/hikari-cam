package com.hikari.cam

import android.app.Application
import com.hikari.data.HikariDatabase
import com.hikari.engine.LookRepository
import com.hikari.engine.gl.OffscreenRenderer

/**
 * App-wide singletons: one look library, one off-screen GL renderer (editor previews, thumbnails,
 * every export) and the database. The live camera preview has its own GL context in its view.
 */
class HikariApp : Application() {
    val looks by lazy { LookRepository(this) }
    val renderer by lazy { OffscreenRenderer(assets) }
    val db by lazy { HikariDatabase.get(this) }
}
