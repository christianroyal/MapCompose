package ovh.plrapps.mapcompose.testapp.features.layerswitch

import androidx.compose.foundation.layout.Box
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.plrapps.mapcompose.ui.MapUI

@Composable
fun LayerSwitchTest(modifier: Modifier = Modifier, viewModel: LayerSwitchViewModel = viewModel()) {
    Box {
        MapUI(modifier, state = viewModel.state)
        Button(onClick = viewModel::changeMapType) {
            Text("Switch")
        }
    }
}
