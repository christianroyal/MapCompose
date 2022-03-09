package ovh.plrapps.mapcompose.demo.providers

import android.content.Context
import ovh.plrapps.mapcompose.core.TileStreamProvider

fun makeTileStreamProvider(appContext: Context) =
    TileStreamProvider { row, col, zoomLvl ->
        try {
            appContext.assets?.open("tiles/floor_862/$zoomLvl/tile_${col}_${row}.png")
        } catch (e: Exception) {
//            println("xxxxx fail to get tile $zoomLvl/$col/$row")
            null
        }
    }
