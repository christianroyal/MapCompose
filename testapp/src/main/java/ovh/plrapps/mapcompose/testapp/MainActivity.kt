package ovh.plrapps.mapcompose.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ComposeView>(R.id.map).setContent {
            var text by remember {
                mutableStateOf("Hello")
            }

            Layout(
                content = { Text(text) },
                modifier = Modifier.clickable {
                    text += '!'
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { measurable ->
                    // Measure each children
                    measurable.measure(constraints)
                }

                println("max dim (${constraints.maxWidth}, ${constraints.maxHeight})")

                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEach { placeable ->
                        placeable.place(x = 0, y = 0)
                    }
                }
            }
        }
    }
}