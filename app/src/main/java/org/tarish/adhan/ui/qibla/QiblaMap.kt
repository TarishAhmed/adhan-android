package org.tarish.adhan.ui.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.tarish.adhan.BuildConfig
import org.tarish.adhan.R
import org.tarish.adhan.domain.QiblaMath

/** §8.4 item 4 — OSM map with user, Kaaba and a geodesic qibla line (BUG-41). */
@Composable
fun QiblaMap(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "org.tarish.adhan/${BuildConfig.VERSION_NAME}"
            osmdroidTileCache = java.io.File(ctx.cacheDir, "osmdroid")
            tileFileSystemCacheMaxBytes = 50L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 40L * 1024 * 1024
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 2.0
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    Box(modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
        AndroidView(
            factory = { map },
            update = { mv -> configure(mv, ctx, lat, lng, lineColor) },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Text(
                stringResource(R.string.qibla_map_attribution),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun configure(mv: MapView, ctx: Context, lat: Double, lng: Double, lineColor: Int) {
    mv.overlays.clear()
    val user = GeoPoint(lat, lng)
    val kaaba = GeoPoint(QiblaMath.KAABA_LAT, QiblaMath.KAABA_LNG)
    val pts = QiblaMath.greatCircle(lat, lng, QiblaMath.KAABA_LAT, QiblaMath.KAABA_LNG, 64).map { GeoPoint(it.first, it.second) }
    mv.overlays.add(Polyline(mv).apply {
        setPoints(pts)
        outlinePaint.color = lineColor
        outlinePaint.strokeWidth = 6f
        isGeodesic = false
    })
    mv.overlays.add(Marker(mv).apply {
        position = user
        icon = ContextCompat.getDrawable(ctx, R.drawable.ic_my_location)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
    })
    mv.overlays.add(Marker(mv).apply {
        position = kaaba
        icon = textDrawable(ctx, "🕋")
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
    })
    val box = BoundingBox.fromGeoPointsSafe(pts)
    mv.post {
        runCatching { mv.zoomToBoundingBox(box, false, 48) }
        if (mv.zoomLevelDouble > 4.0) mv.controller.setZoom(4.0)
    }
    mv.invalidate()
}

private fun textDrawable(ctx: Context, text: String): Drawable {
    val d = ctx.resources.displayMetrics.density
    val size = (28 * d).toInt()
    val bmp = createBitmap(size, size)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22 * d; textAlign = Paint.Align.CENTER; color = Color.BLACK }
    c.drawText(text, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
    return bmp.toDrawable(ctx.resources)
}
