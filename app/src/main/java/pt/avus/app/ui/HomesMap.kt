package pt.avus.app.ui

import android.graphics.PointF
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.toString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pt.avus.app.data.CareHome
import pt.avus.app.data.GeoPoint

// OpenFreeMap: free OpenStreetMap vector tiles, no account or key.
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val SOURCE_HOMES = "homes"
private const val SOURCE_REFERENCE = "reference"
private const val LAYER_CLUSTERS = "homes-clusters"
private const val LAYER_POINTS = "homes-points"

private fun Color.hex() = String.format("#%06X", toArgb() and 0xFFFFFF)

/**
 * Map of the [homes] (MapLibre + OpenFreeMap). Pins are grouped into numbered clusters when zoomed
 * out; colours: selected, has free places, full. Tapping a pin calls [onOpen]; tapping a cluster zooms in.
 */
@Composable
fun HomesMap(
    homes: List<CareHome>,
    selectedIds: Set<String>,
    reference: GeoPoint?,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val colors = remember(scheme) {
        mapOf(
            "selected" to scheme.tertiary.hex(),
            "free" to scheme.primary.hex(),
            "full" to scheme.outline.hex(),
            "cluster" to scheme.primaryContainer.hex(),
            "clusterText" to scheme.onPrimaryContainer.hex(),
            "stroke" to scheme.surface.hex(),
            "reference" to scheme.error.hex(),
        )
    }
    val currentOnOpen by rememberUpdatedState(onOpen)

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    // Forward the screen's lifecycle to the MapView.
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // The map itself, once: gestures and the tap handler (registering it again would fire it twice).
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.addOnMapClickListener { latLng ->
                val screen: PointF = m.projection.toScreenLocation(latLng)
                m.queryRenderedFeatures(screen, LAYER_POINTS).firstOrNull()?.getStringProperty("id")?.let {
                    currentOnOpen(it)
                    return@addOnMapClickListener true
                }
                val cluster = m.queryRenderedFeatures(screen, LAYER_CLUSTERS).firstOrNull()
                val source = m.style?.getSourceAs<GeoJsonSource>(SOURCE_HOMES)
                if (cluster != null && source != null) {
                    val p = cluster.geometry() as Point
                    val zoom = source.getClusterExpansionZoom(cluster).toDouble()
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude(), p.longitude()), zoom))
                    true
                } else false
            }
            map = m
        }
    }

    // Style (light/dark) with the homes source and layers; reloaded when the theme changes.
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        run {
            m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { s ->
                s.addSource(
                    GeoJsonSource(
                        SOURCE_HOMES,
                        GeoJsonOptions().withCluster(true).withClusterRadius(48).withClusterMaxZoom(12),
                    )
                )
                s.addSource(GeoJsonSource(SOURCE_REFERENCE))
                // Under the homes, so it never hides a cluster's count.
                s.addLayer(
                    CircleLayer("reference", SOURCE_REFERENCE).withProperties(
                        circleColor(colors.getValue("reference")),
                        circleRadius(9f),
                        circleStrokeColor(colors.getValue("stroke")),
                        circleStrokeWidth(3f),
                    )
                )
                s.addLayer(
                    CircleLayer(LAYER_CLUSTERS, SOURCE_HOMES).withFilter(has("point_count")).withProperties(
                        circleColor(colors.getValue("cluster")),
                        circleRadius(step(get("point_count"), literal(16f), stop(10, 20f), stop(50, 26f), stop(200, 32f))),
                        circleStrokeColor(colors.getValue("free")),
                        circleStrokeWidth(2f),
                    )
                )
                s.addLayer(
                    SymbolLayer("homes-cluster-count", SOURCE_HOMES).withFilter(has("point_count")).withProperties(
                        textField(toString(get("point_count_abbreviated"))),
                        textFont(arrayOf("Noto Sans Bold")),
                        textSize(13f),
                        textColor(colors.getValue("clusterText")),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                    )
                )
                s.addLayer(
                    CircleLayer(LAYER_POINTS, SOURCE_HOMES).withFilter(not(has("point_count"))).withProperties(
                        circleColor(
                            match(
                                get("state"), literal(colors.getValue("full")),
                                stop("selected", colors.getValue("selected")),
                                stop("free", colors.getValue("free")),
                            )
                        ),
                        circleRadius(7f),
                        circleStrokeColor(colors.getValue("stroke")),
                        circleStrokeWidth(2f),
                    )
                )
                style = s
            }
        }
    }

    // Data: re-sent when the filtered homes or the selection change.
    // Keyed by the list's identity: [homes] is the screen's cached filtered list, so it only changes
    // identity when the filters change (comparing 2,600 homes by value on every redraw is wasteful).
    val homesKey = IdentityKey(homes) // cheap wrapper; compares by reference
    val located = remember(homesKey) { homes.filter { it.point != null } }
    LaunchedEffect(style, homesKey, selectedIds) {
        val s = style ?: return@LaunchedEffect
        val features = located.map { h ->
            Feature.fromGeometry(Point.fromLngLat(h.longitude!!, h.latitude!!)).apply {
                addStringProperty("id", h.id)
                addStringProperty(
                    "state",
                    when {
                        h.id in selectedIds -> "selected"
                        (h.freePlaces ?: 0) > 0 -> "free"
                        else -> "full"
                    },
                )
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_HOMES)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    LaunchedEffect(style, reference) {
        val s = style ?: return@LaunchedEffect
        val source = s.getSourceAs<GeoJsonSource>(SOURCE_REFERENCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(reference?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })
            )
        )
    }

    // Camera: fit the homes shown whenever the filtered set changes (e.g. another district).
    val fitKey = remember(homesKey) { located.size to located.firstOrNull()?.id }
    LaunchedEffect(map, style, fitKey) {
        val m = map ?: return@LaunchedEffect
        if (style == null || located.isEmpty()) return@LaunchedEffect
        if (located.size == 1) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(located[0].latitude!!, located[0].longitude!!), 13.0))
        } else {
            val bounds = LatLngBounds.Builder().apply { located.forEach { include(LatLng(it.latitude!!, it.longitude!!)) } }.build()
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Equality by reference, for using large lists as effect keys cheaply. */
private class IdentityKey(val ref: Any) {
    override fun equals(other: Any?) = other is IdentityKey && other.ref === ref
    override fun hashCode() = System.identityHashCode(ref)
}
