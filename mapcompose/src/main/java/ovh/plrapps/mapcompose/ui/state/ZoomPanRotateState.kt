package ovh.plrapps.mapcompose.ui.state

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ovh.plrapps.mapcompose.api.Fill
import ovh.plrapps.mapcompose.api.Fit
import ovh.plrapps.mapcompose.api.Forced
import ovh.plrapps.mapcompose.api.MinimumScaleMode
import ovh.plrapps.mapcompose.ui.layout.GestureListener
import ovh.plrapps.mapcompose.ui.layout.LayoutSizeChangeListener
import ovh.plrapps.mapcompose.utils.AngleDegree
import ovh.plrapps.mapcompose.utils.lerp
import ovh.plrapps.mapcompose.utils.modulo
import ovh.plrapps.mapcompose.utils.toRad
import kotlin.math.*

internal class ZoomPanRotateState(
    val fullWidth: Int,
    val fullHeight: Int,
    val stateChangeListener: ZoomPanRotateStateListener
) : GestureListener, LayoutSizeChangeListener {
    private var scope: CoroutineScope? = null

    private val minimumScaleMode: MinimumScaleMode = Fit

    /* Only source of truth. Don't mutate directly, use appropriate setScale(), setRotation(), etc. */
    internal var scale by mutableStateOf(1f)
    internal var rotation: AngleDegree by mutableStateOf(0f)
    internal var scrollX by mutableStateOf(0f)
    internal var scrollY by mutableStateOf(0f)

    internal var centroidX: Double by mutableStateOf(0.0)
    internal var centroidY: Double by mutableStateOf(0.0)

    internal var layoutSize by mutableStateOf(IntSize(0, 0))
    var minScale = 0f
        set(value) {
            field = value
            setScale(scale)
        }
    var maxScale = 2f
        set(value) {
            field = value
            setScale(scale)
        }

    var shouldLoopScale = false

    /**
     * When scaled out beyond the scaled permitted by [Fill], these paddings are used by the layout.
     */
    internal var paddingX: Int by mutableStateOf(0)
    internal var paddingY: Int by mutableStateOf(0)

    /* Used for fling animation */
    private val scrollAnimatable: Animatable<Offset, AnimationVector2D> =
        Animatable(Offset.Zero, Offset.VectorConverter)
    private var isFlinging = false

    @Suppress("unused")
    fun setScale(scale: Float) {
        this.scale = constrainScale(scale)
        updatePadding()
        updateCentroid()
        stateChangeListener.onStateChanged()
    }

    @Suppress("unused")
    fun setScroll(scrollX: Float, scrollY: Float) {
        this.scrollX = constrainScrollX(scrollX)
        this.scrollY = constrainScrollY(scrollY)
        updateCentroid()
        stateChangeListener.onStateChanged()
    }

    @Suppress("unused")
    fun setRotation(angle: AngleDegree) {
        this.rotation = angle.modulo()
        updateCentroid()
        stateChangeListener.onStateChanged()
    }

    /**
     * Scales the layout with animated scale, without maintaining scroll position.
     *
     * @param scale The final scale value the layout should animate to.
     * @param animationSpec The [AnimationSpec] the animation should use.
     */
    @Suppress("unused")
    fun smoothScaleTo(
        scale: Float,
        animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
    ) {
        scope?.launch {
            val currScale = this@ZoomPanRotateState.scale
            if (currScale > 0) {
                Animatable(0f).animateTo(1f, animationSpec) {
                    setScale(lerp(currScale, scale, value))
                }
            }
        }
    }

    @Suppress("unused")
    fun smoothRotateTo(
        angle: AngleDegree,
        animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
    ) {
        scope?.launch {
            val currRotation = this@ZoomPanRotateState.rotation
            Animatable(0f).animateTo(1f, animationSpec) {
                setRotation(lerp(currRotation, angle, value))
            }
        }
    }

    /**
     * Animates the layout to the scale provided, and centers the viewport to the supplied scroll
     * position.
     *
     * @param scrollX Horizontal scroll of the destination point.
     * @param scrollY Vertical scroll of the destination point.
     * @param destScale The final scale value the layout should animate to.
     * @param animationSpec The [AnimationSpec] the animation should use.
     */
    @Suppress("unused")
    fun slideToAndCenterWithScale(
        scrollX: Float,
        scrollY: Float,
        destScale: Float,
        animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
    ) {
        val startScrollX = this.scrollX
        val startScrollY = this.scrollY
        val destScrollX = scrollX - layoutSize.width / 2
        val destScrollY = scrollY - layoutSize.height / 2

        val startScale = this.scale

        scope?.launch {
            Animatable(0f).animateTo(1f, animationSpec) {
                setScale(lerp(startScale, destScale, value))
                setScroll(
                    scrollX = lerp(startScrollX, destScrollX, value),
                    scrollY = lerp(startScrollY, destScrollY, value)
                )
            }
        }
    }

    /**
     * Animates the layout to the scale provided, while maintaining position determined by the
     * the provided focal point.
     *
     * @param focusX The horizontal focal point to maintain, relative to the layout.
     * @param focusY The vertical focal point to maintain, relative to the layout.
     * @param destScale The final scale value the layout should animate to.
     * @param animationSpec The [AnimationSpec] the animation should use.
     */
    @Suppress("unused")
    fun smoothScaleWithFocalPoint(
        focusX: Float,
        focusY: Float,
        destScale: Float,
        animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
    ) {
        val startScale = scale
        val startScrollX = scrollX
        val startScrollY = scrollY
        val destScrollX = getScrollAtOffsetAndScale(startScrollX, focusX, destScale / startScale)
        val destScrollY = getScrollAtOffsetAndScale(startScrollY, focusY, destScale / startScale)

        scope?.launch {
            Animatable(0f).animateTo(1f, animationSpec) {
                setScale(lerp(startScale, destScale, value))
                setScroll(
                    scrollX = lerp(startScrollX, destScrollX, value),
                    scrollY = lerp(startScrollY, destScrollY, value)
                )
            }
        }
    }

    override fun onScaleRatio(scaleRatio: Float, centroid: Offset) {
        val formerScale = scale
        setScale(scale * scaleRatio)

        /* Pinch and zoom magic */
        val effectiveScaleRatio = scale / formerScale
        setScroll(
            scrollX = getScrollAtOffsetAndScale(scrollX, centroid.x, effectiveScaleRatio),
            scrollY = getScrollAtOffsetAndScale(scrollY, centroid.y, effectiveScaleRatio)
        )
    }

    private fun getScrollAtOffsetAndScale(scroll: Float, offSet: Float, scaleRatio: Float): Float {
        return (scroll + offSet) * scaleRatio - offSet
    }

    override fun onRotationDelta(rotationDelta: Float) {
        setRotation(rotation + rotationDelta)
    }

    override fun onScrollDelta(scrollDelta: Offset) {
        var scrollX = scrollX
        var scrollY = scrollY

        val rotRad = -rotation.toRad()
        scrollX -= if (rotRad == 0f) scrollDelta.x else {
            scrollDelta.x * cos(rotRad) - scrollDelta.y * sin(rotRad)
        }
        scrollY -= if (rotRad == 0f) scrollDelta.y else {
            scrollDelta.x * sin(rotRad) + scrollDelta.y * cos(rotRad)
        }
        setScroll(scrollX, scrollY)
    }

    override fun onFling(velocity: Velocity) {
        isFlinging = true

        val rotRad = -rotation.toRad()
        val velocityX = if (rotRad == 0f) velocity.x else {
            velocity.x * cos(rotRad) - velocity.y * sin(rotRad)
        }
        val velocityY = if (rotRad == 0f) velocity.y else {
            velocity.x * sin(rotRad) + velocity.y * cos(rotRad)
        }

        scope?.launch {
            scrollAnimatable.snapTo(Offset(scrollX, scrollY))
            scrollAnimatable.animateDecay(
                initialVelocity = -Offset(velocityX, velocityY),
                animationSpec = FloatExponentialDecaySpec().generateDecayAnimationSpec(),
            ) {
                if (isFlinging) {
                    setScroll(
                        scrollX = value.x,
                        scrollY = value.y
                    )
                }
            }
        }
    }

    override fun onTap() {
        isFlinging = false
    }

    override fun onDoubleTap(offSet: Offset) {
        val destScale = (
            2.0.pow(floor(ln((scale * 2).toDouble()) / ln(2.0))).toFloat()
        ).let {
            if (shouldLoopScale && it > maxScale) minScale else it
        }

        val angleRad = -rotation.toRad()
        val offSetX = if (angleRad == 0f) offSet.x else {
            layoutSize.height / 2 * sin(angleRad) + layoutSize.width / 2 * (1 - cos(angleRad)) +
                    offSet.x * cos(angleRad) - offSet.y * sin(angleRad)
        }

        val offSetY = if (angleRad == 0f) offSet.y else {
            layoutSize.height / 2 * (1 - cos(angleRad)) - layoutSize.width / 2 * sin(angleRad) +
                    offSet.x * sin(angleRad) + offSet.y * cos(angleRad)
        }

        smoothScaleWithFocalPoint(
            offSetX,
            offSetY,
            destScale,
            SpringSpec(stiffness = Spring.StiffnessMedium)
        )
    }

    override fun onSizeChanged(composableScope: CoroutineScope, size: IntSize) {
        scope = composableScope
        layoutSize = size
        recalculateMinScale()
        setScale(scale)
    }

    private fun constrainScrollX(scrollX: Float): Float {
        return scrollX.coerceIn(0f, max(0f, fullWidth * scale - layoutSize.width))
    }

    private fun constrainScrollY(scrollY: Float): Float {
        return scrollY.coerceIn(0f, max(0f, fullHeight * scale - layoutSize.height))
    }

    private fun constrainScale(scale: Float): Float {
        return scale.coerceIn(max(minScale, Float.MIN_VALUE), maxScale)  // scale between 0+ and 2f
    }

    private fun updateCentroid() {
        centroidX = (scrollX + min(
            layoutSize.width.toDouble() / 2,
            fullWidth * scale.toDouble() / 2
        )) / (fullWidth * scale)
        centroidY = (scrollY + min(
            layoutSize.height.toDouble() / 2,
            fullHeight * scale.toDouble() / 2
        )) / (fullHeight * scale)
    }

    private fun recalculateMinScale() {
        val minScaleX = layoutSize.width.toFloat() / fullWidth
        val minScaleY = layoutSize.height.toFloat() / fullHeight
        minScale = when (minimumScaleMode) {
            Fit -> min(minScaleX, minScaleY)
            Fill -> max(minScaleX, minScaleY)
            is Forced -> minimumScaleMode.scale
        }
    }

    private fun updatePadding() {
        paddingX = if (fullWidth * scale >= layoutSize.width) {
            0
        } else {
            layoutSize.width / 2 - (fullWidth * scale).roundToInt() / 2
        }

        paddingY = if (fullHeight * scale >= layoutSize.height) {
            0
        } else {
            layoutSize.height / 2 - (fullHeight * scale).roundToInt() / 2
        }
    }
}

interface ZoomPanRotateStateListener {
    fun onStateChanged()
}