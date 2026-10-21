package tv.nelo.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.*

fun Context.dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
fun Context.rounded(color: Int, radius: Int = 16, border: Boolean = false) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius).toFloat()
    if (border) setStroke(dp(2), 0xFFE8F1FF.toInt())
}
fun Context.label(value: String, size: Int = 16, color: Int = Color.WHITE) = TextView(this).apply {
    text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    fontFeatureSettings = "kern"; includeFontPadding = false
}
fun Context.tvButton(value: String, action: () -> Unit) = Button(this).apply {
    id = View.generateViewId(); text = value; isAllCaps = false; textSize = 14f
    setTextColor(Color.WHITE); setPadding(dp(18), 0, dp(18), 0)
    background = rounded(0x332D3345, 20)
    setOnFocusChangeListener { view, focused -> view.background = rounded(if (focused) 0xAA65738B.toInt() else 0x332D3345, 20, focused) }
    setOnClickListener { action() }
    minimumHeight = 0; minHeight = 0
}

/** Original static artwork, drawn from gradients. No blur, texture download, or running animation. */
class AuroraDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var base: Shader? = null
    private var blue: Shader? = null
    private var amber: Shader? = null
    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat().coerceAtLeast(1f); val h = bounds.height().toFloat().coerceAtLeast(1f)
        base = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF101D32.toInt(), 0xFF23243B.toInt(), 0xFF121723.toInt()), null, Shader.TileMode.CLAMP)
        blue = RadialGradient(w * .73f, h * .2f, w * .6f, intArrayOf(0x995B86B1.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        amber = RadialGradient(w * .12f, h * .63f, w * .52f, intArrayOf(0x777D5D55, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    override fun draw(canvas: Canvas) {
        paint.shader = base; canvas.drawRect(bounds, paint)
        paint.shader = blue; canvas.drawRect(bounds, paint)
        paint.shader = amber; canvas.drawRect(bounds, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Framework API") override fun getOpacity() = PixelFormat.OPAQUE
}

/** The same card is recycled in the grid and used in the bounded five-slot favorites shelf. */
class AppCard(context: Context) : LinearLayout(context) {
    val face = FrameLayout(context)
    private val artwork = ImageView(context)
    private val caption = context.label("", 14).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private var token = 0
    private var focusedCard = false
    init {
        orientation = VERTICAL; clipChildren = false; clipToPadding = false
        face.background = context.rounded(0xFF222A39.toInt(), 15)
        face.clipToOutline = true
        face.outlineProvider = ViewOutlineProvider.BACKGROUND
        artwork.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        face.addView(artwork, FrameLayout.LayoutParams(-1, -1))
        addView(face, LayoutParams(-1, 0, 1f))
        addView(caption, LayoutParams(-1, context.dp(25)))
        setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(2))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun bind(app: AppEntry, favorite: Boolean): Int {
        token++; tag = app.key; contentDescription = app.label + if (favorite) ", favorita" else ""
        caption.text = app.label
        face.animate().cancel(); face.scaleX = 1f; face.scaleY = 1f; face.translationZ = 0f; face.alpha = 1f
        focusedCard = false; caption.alpha = 0f
        artwork.scaleType = ImageView.ScaleType.CENTER_INSIDE
        artwork.setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        artwork.setImageResource(R.drawable.ic_nelo)
        val palette = intArrayOf(0xFF344F67.toInt(), 0xFF654655.toInt(), 0xFF4C4D6A.toInt(), 0xFF385C59.toInt(), 0xFF735440.toInt())
        face.background = context.rounded(palette[(app.component.packageName.hashCode() and Int.MAX_VALUE) % palette.size], 15)
        return token
    }
    fun setArtwork(bitmap: Bitmap?, bindToken: Int) {
        if (bindToken != token || bitmap == null) return
        val banner = bitmap.width > bitmap.height * 1.4f
        artwork.setPadding(if (banner) 0 else context.dp(16), if (banner) 0 else context.dp(10), if (banner) 0 else context.dp(16), if (banner) 0 else context.dp(10))
        artwork.scaleType = if (banner) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.CENTER_INSIDE
        artwork.setImageBitmap(bitmap)

    }
    fun focusCard(focused: Boolean, animated: Boolean) {
        if (focusedCard == focused) return
        focusedCard = focused
        face.animate().cancel(); caption.animate().cancel()
        val duration = if (animated) 155L else 0L
        face.animate().scaleX(if (focused) 1.075f else 1f).scaleY(if (focused) 1.075f else 1f)
            .translationZ(if (focused) context.dp(12).toFloat() else 0f)
            .setDuration(duration).setInterpolator(DecelerateInterpolator(1.5f)).start()
        caption.animate().alpha(if (focused) 1f else 0f).setDuration(duration).start()
        face.foreground = if (focused) context.rounded(Color.TRANSPARENT, 15, true) else null
    }
    fun press(animated: Boolean, open: () -> Unit) {
        face.animate().cancel()
        if (!animated) { open(); return }
        face.animate().scaleX(1.01f).scaleY(1.01f).setDuration(70).withEndAction {
            // The caller cancels outstanding launch callbacks when leaving or destroying HOME.
            open()
            face.animate().scaleX(if (focusedCard) 1.075f else 1f).scaleY(if (focusedCard) 1.075f else 1f).setDuration(100).start()
        }.start()
    }
    fun cancelAnimations() { face.animate().withEndAction(null).cancel(); caption.animate().cancel() }
}

/** Matches the visible crop of the 16:9 HOME background, without stretching the source. */
class PhotoPreviewView(context: Context) : ImageView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(width * 9 / 16, heightMeasureSpec)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}
