package ovh.plrapps.mapcompose.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.enableRotation
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.shouldLoopScale
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.testapp.core.ui.MapComposeTestApp
import ovh.plrapps.mapcompose.testapp.core.ui.theme.MapComposeTheme
import ovh.plrapps.mapcompose.ui.MapUI
import ovh.plrapps.mapcompose.ui.state.MapState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ComposeView>(R.id.map).setContent {
            val tileStreamProvider = TileStreamProvider { row, col, _ ->
                applicationContext.assets?.open("tiles/test/tile_0_${col}_$row.png")
            }
            val state = MapState(4, 4096, 4096).apply {
                addLayer(tileStreamProvider)
            }
            MapUI(state = state)
        }
    }
}