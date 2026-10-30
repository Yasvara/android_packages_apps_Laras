/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.ui.theme.LarasTheme

/**
 * Depth Studio: picks the lock wallpaper's subject so it can stand in front of the clock.
 *
 * The subject is selected on arrival; the user then adds (＋) or takes away (−) what they point
 * at: a tap picks the object under the finger, a box picks what it frames, and the brush paints
 * by hand for the last details. Every step can be undone. Save keeps the project in the
 * [DepthLibrary] and hands the matte to SystemUI; opening the Studio on a picture saved before
 * resumes its project, steps and all (docs/specs/0013 §6).
 *
 * It opens on the lock screen's picture, or on a saved one named by [EXTRA_FINGERPRINT]. A new
 * picture that looks like a saved one (a re-encoded copy) is offered that one's depth.
 */
class DepthStudioActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LarasTheme { Studio(onDone = ::finish) } }
    }

    private enum class Tool { TAP, BOX, BRUSH, MOVE }

    private val library by lazy { DepthLibrary(this) }

    @Composable
    private fun Studio(onDone: () -> Unit) {
        val scope = rememberCoroutineScope()
        var picture by remember { mutableStateOf<WallpaperPicture?>(null) }
        var created by remember { mutableStateOf<Long?>(null) }
        var similar by remember { mutableStateOf<DepthProject?>(null) }
        var engine by remember { mutableStateOf<SegmentationEngine?>(null) }
        var busy by remember { mutableStateOf(true) }
        var selection by remember { mutableStateOf<Selection?>(null) }
        var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        var add by remember { mutableStateOf(true) }
        var tool by remember { mutableStateOf(Tool.TAP) }
        var brushSize by remember { mutableFloatStateOf(0.03f) }
        var subjectOnly by remember { mutableStateOf(false) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var stroke by remember { mutableStateOf<List<PointF>>(emptyList()) }
        var box by remember { mutableStateOf<RectF?>(null) }
        // The depth build (docs/specs/0013 §4): running for this picture, and what it found.
        var building by remember { mutableStateOf(false) }
        var buildSeen by remember { mutableStateOf(false) }
        var autoBuild by remember { mutableStateOf<DepthBuild?>(null) }
        var dropped by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
        var props by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
        val buildState by DepthBuildService.state.collectAsState()
        val tint = MaterialTheme.colorScheme.primary

        LaunchedEffect(Unit) {
            val saved = intent.getStringExtra(EXTRA_FINGERPRINT)
            val loaded = withContext(Dispatchers.IO) {
                if (saved == null) WallpaperPicture.current(this@DepthStudioActivity)
                else library.picture(saved).takeIf { it.isFile }?.readBytes()?.let(WallpaperPicture::decode)
            }
            if (loaded == null) {
                toast(R.string.depth_no_wallpaper)
                onDone()
                return@LaunchedEffect
            }
            picture = loaded
            val project = withContext(Dispatchers.IO) { library.load(loaded.fingerprint.sha) }
            created = project?.created
            // Without a project of its own: the depth the lock screen already has (made before the
            // library, or kept by SystemUI), so earlier work comes first; else what the build found.
            // The build stays one tap away (Select subject).
            val ops = project?.ops ?: withContext(Dispatchers.IO) {
                DepthSync.fetch(this@DepthStudioActivity, loaded.fingerprint.sha)?.let { listOf(it) }
                    ?: library.build(loaded.fingerprint.sha)?.let { listOf(SelectionOp.Auto(it)) }
            }
            if (ops == null) {
                similar = withContext(Dispatchers.IO) { library.similar(loaded.fingerprint, loaded.aspect) }
            }
            val (w, h) = workingSize(loaded.bitmap.width, loaded.bitmap.height)
            val sel = withContext(Dispatchers.Default) {
                Selection(w, h, Selection.guideOf(loaded.bitmap, w, h))
            }
            if (ops != null) {
                // Show the saved selection at once; the model is only needed for new taps.
                sel.restore(ops)
                selection = sel
                revision++
            }
            val ready = withContext(Dispatchers.Default) {
                runCatching { SegmentationEngine(this@DepthStudioActivity).also { it.encode(loaded.bitmap) } }
                    .onFailure { Log.e(TAG, "Segmentation unavailable", it) }
                    .getOrNull()
            }
            engine = ready
            if (ops == null) {
                if (DepthBuilder.available) {
                    DepthBuildService.request(this@DepthStudioActivity, loaded.fingerprint.sha, studio = true)
                    building = true
                } else if (ready != null) {
                    withContext(Dispatchers.Default) { autoSelect(ready)?.let { sel.push(it) } }
                }
                selection = sel
            }
            revision++
            busy = false
        }

        // Follow the build of this picture: show its result when done, step back if it stops.
        LaunchedEffect(buildState) {
            val sha = picture?.fingerprint?.sha ?: return@LaunchedEffect
            when (val s = buildState) {
                is DepthBuildService.State.Running, is DepthBuildService.State.Waiting ->
                    if (s.sha == sha) buildSeen = true
                is DepthBuildService.State.Done -> if (building && s.sha == sha) {
                    val built = withContext(Dispatchers.IO) { library.build(sha) }
                    building = false
                    buildSeen = false
                    if (built != null) {
                        selection?.clear()
                        selection?.push(SelectionOp.Auto(built))
                        revision++
                    }
                }
                is DepthBuildService.State.Failed -> if (building && s.sha == sha) {
                    building = false
                    buildSeen = false
                    toast(R.string.depth_build_failed)
                    engine?.let { e ->
                        withContext(Dispatchers.Default) { autoSelect(e) }?.let { selection?.push(it) }
                        revision++
                    }
                }
                // Stopped from the notification.
                DepthBuildService.State.Idle -> if (building && buildSeen) {
                    building = false
                    buildSeen = false
                }
            }
        }

        // Re-render the overlay whenever the selection changes.
        LaunchedEffect(revision) {
            val sel = selection ?: return@LaunchedEffect
            autoBuild = sel.operations.firstNotNullOfOrNull { (it as? SelectionOp.Auto)?.build }
            dropped = sel.droppedProps()
            overlay = withContext(Dispatchers.Default) {
                Selection.toBitmap(sel.render(), sel.width, sel.height).asImageBitmap()
            }
        }

        // The props' cut-outs, once per build.
        LaunchedEffect(autoBuild) {
            val pic = picture
            val built = autoBuild
            props = if (pic == null || built == null || built.props == 0) emptyList() else {
                withContext(Dispatchers.Default) {
                    propThumbs(pic, built, PROP_THUMB_PX).map { it.asImageBitmap() }
                }
            }
        }

        fun region(points: List<PromptEncoder.Point>, clip: RectF? = null) {
            val e = engine ?: return
            val sel = selection ?: return
            busy = true
            scope.launch {
                val decoded = withContext(Dispatchers.Default) { e.decode(points) }
                val coverage = clip?.let { Selection.clipTo(decoded.coverage, it) } ?: decoded.coverage
                sel.push(SelectionOp.Region(add, coverage))
                revision++
                busy = false
            }
        }

        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
                Text(
                    stringResource(R.string.depth_studio),
                    Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { subjectOnly = !subjectOnly }) {
                    Icon(Icons.Rounded.Visibility, null, tint = if (subjectOnly) tint else Color.White)
                }
                FilledTonalIconButton(
                    enabled = !busy && !building && selection?.isEmpty == false,
                    onClick = {
                        val sel = selection ?: return@FilledTonalIconButton
                        val pic = picture ?: return@FilledTonalIconButton
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { save(sel, pic, created) }
                            toast(if (ok) R.string.depth_saved else R.string.depth_save_failed)
                            busy = false
                            if (ok) onDone()
                        }
                    },
                ) { Icon(Icons.Rounded.Check, null) }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val pic = picture
                if (pic != null) {
                    val bmp = remember(pic) { pic.bitmap.asImageBitmap() }
                    // The picture fitted into the space, in local pixels.
                    val boxW = constraints.maxWidth.toFloat()
                    val boxH = constraints.maxHeight.toFloat()
                    val fit = minOf(boxW / pic.bitmap.width, boxH / pic.bitmap.height)
                    val shown = Size(pic.bitmap.width * fit, pic.bitmap.height * fit)
                    val origin = Offset((boxW - shown.width) / 2f, (boxH - shown.height) / 2f)
                    fun toPicture(p: Offset): PointF {
                        // Undo the zoom and pan applied around the centre.
                        val c = Offset(boxW / 2f, boxH / 2f)
                        val local = (p - c - pan) / zoom + c
                        return PointF(
                            ((local.x - origin.x) / shown.width).coerceIn(0f, 1f),
                            ((local.y - origin.y) / shown.height).coerceIn(0f, 1f),
                        )
                    }

                    Canvas(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }
                    ) {
                        val dst = IntSize(shown.width.roundToInt(), shown.height.roundToInt())
                        val at = IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
                        drawImage(
                            bmp, dstOffset = at, dstSize = dst,
                            alpha = if (building) 0.45f else if (subjectOnly) 0.25f else 1f,
                        )
                        (buildState as? DepthBuildService.State.Running)
                            ?.takeIf { building && it.sha == pic.fingerprint.sha }
                            ?.let { drawTiles(it, origin, shown, tint) }
                        overlay?.let {
                            drawImage(
                                it, dstOffset = at, dstSize = dst,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    tint.copy(alpha = if (subjectOnly) 0f else 0.5f),
                                    BlendMode.SrcIn,
                                ),
                            )
                        }
                        if (subjectOnly) {
                            overlay?.let { mask ->
                                // The subject alone: the picture seen through the mask.
                                drawIntoCanvas { canvas ->
                                    canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint())
                                    drawImage(bmp, dstOffset = at, dstSize = dst)
                                    drawImage(mask, dstOffset = at, dstSize = dst, blendMode = BlendMode.DstIn)
                                    canvas.restore()
                                }
                            }
                        }
                        box?.let {
                            drawRect(
                                Color.White,
                                topLeft = Offset(origin.x + it.left * shown.width, origin.y + it.top * shown.height),
                                size = Size(it.width() * shown.width, it.height() * shown.height),
                                style = Stroke(2.dp.toPx() / zoom),
                            )
                        }
                        stroke.lastOrNull()?.let {
                            drawCircle(
                                Color.White.copy(alpha = 0.7f),
                                radius = brushSize * shown.width,
                                center = Offset(origin.x + it.x * shown.width, origin.y + it.y * shown.height),
                                style = Stroke(1.5.dp.toPx() / zoom),
                            )
                        }
                    }

                    // Gestures, laid over the picture.
                    val gestures = when (tool) {
                        Tool.TAP -> Modifier.pointerInput(tool, add, busy) {
                            detectTapGestures { p ->
                                if (busy) return@detectTapGestures
                                val q = toPicture(p)
                                region(listOf(PromptEncoder.Point(q.x * PromptEncoder.INPUT, q.y * PromptEncoder.INPUT)))
                            }
                        }
                        Tool.BOX -> Modifier.pointerInput(tool, add, busy) {
                            var start = PointF()
                            detectDragGestures(
                                onDragStart = { start = toPicture(it); box = RectF(start.x, start.y, start.x, start.y) },
                                onDrag = { change, _ ->
                                    val q = toPicture(change.position)
                                    box = RectF(minOf(start.x, q.x), minOf(start.y, q.y), maxOf(start.x, q.x), maxOf(start.y, q.y))
                                },
                                onDragEnd = {
                                    box?.takeIf { it.width() > 0.01f && it.height() > 0.01f }?.let { region(Selection.boxCentre(it), clip = it) }
                                    box = null
                                },
                                onDragCancel = { box = null },
                            )
                        }
                        Tool.BRUSH -> Modifier.pointerInput(tool, add, brushSize) {
                            detectDragGestures(
                                onDragStart = { stroke = listOf(toPicture(it)) },
                                onDrag = { change, _ -> stroke = stroke + toPicture(change.position) },
                                onDragEnd = {
                                    selection?.push(SelectionOp.Brush(add, stroke, brushSize))
                                    stroke = emptyList()
                                    revision++
                                },
                                onDragCancel = { stroke = emptyList() },
                            )
                        }
                        Tool.MOVE -> Modifier.pointerInput(tool) {
                            detectTransformGestures { _, move, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 8f)
                                pan += move
                            }
                        }
                    }
                    // No editing while the build runs: its result replaces the selection.
                    Box(Modifier.fillMaxSize().then(if (building) Modifier else gestures))
                }
                if (busy) CircularProgressIndicator()
                similar?.let { offer ->
                    SimilarOffer(
                        offer,
                        onUse = {
                            building = false
                            selection?.restore(offer.ops)
                            created = null
                            similar = null
                            revision++
                        },
                        onDismiss = { similar = null },
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    )
                }
            }

            // Tools; while the build runs, its progress in their place.
            if (building) {
                val sha = picture?.fingerprint?.sha
                BuildProgress(
                    buildState.takeIf { it.sha == sha } ?: DepthBuildService.State.Idle,
                    Modifier.padding(16.dp),
                )
            } else Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                autoBuild?.takeIf { props.isNotEmpty() }?.let { built ->
                    PropsRow(props, dropped, enabled = !busy, onToggle = { index, drop ->
                        selection?.push(SelectionOp.Part(!drop, index, built))
                        revision++
                    })
                }
                if (tool == Tool.BRUSH) {
                    Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 0.008f..0.08f)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconToggleButton(checked = add, onCheckedChange = { add = true }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.depth_add))
                    }
                    FilledIconToggleButton(checked = !add, onCheckedChange = { add = false }) {
                        Icon(Icons.Rounded.Remove, stringResource(R.string.depth_remove))
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        val tools = listOf(
                            Tool.TAP to Icons.Rounded.TouchApp,
                            Tool.BOX to Icons.Rounded.CropFree,
                            Tool.BRUSH to Icons.Rounded.Brush,
                            Tool.MOVE to Icons.Rounded.PanTool,
                        )
                        tools.forEachIndexed { i, (t, icon) ->
                            SegmentedButton(
                                selected = tool == t,
                                onClick = { tool = t },
                                shape = SegmentedButtonDefaults.itemShape(i, tools.size),
                                icon = {},
                            ) { Icon(icon, null) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(enabled = selection?.canUndo == true && !busy, onClick = { selection?.undo(); revision++ }) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = Color.White)
                    }
                    IconButton(enabled = selection?.canRedo == true && !busy, onClick = { selection?.redo(); revision++ }) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, null, tint = Color.White)
                    }
                    IconButton(enabled = (engine != null || DepthBuilder.available) && !busy, onClick = {
                        val pic = picture ?: return@IconButton
                        busy = true
                        scope.launch {
                            // The build's answer when the ROM has its models; EdgeTAM's guess otherwise.
                            if (DepthBuilder.available) {
                                val built = withContext(Dispatchers.IO) { library.build(pic.fingerprint.sha) }
                                if (built != null) {
                                    selection?.clear()
                                    selection?.push(SelectionOp.Auto(built))
                                } else {
                                    DepthBuildService.request(this@DepthStudioActivity, pic.fingerprint.sha, studio = true)
                                    building = true
                                }
                            } else {
                                val e = engine
                                val op = e?.let { withContext(Dispatchers.Default) { autoSelect(it) } }
                                selection?.clear()
                                op?.let { selection?.push(it) }
                            }
                            revision++
                            busy = false
                        }
                    }) { Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.depth_auto), tint = Color.White) }
                    Text(
                        stringResource(R.string.depth_studio_summary),
                        Modifier.weight(1f).align(Alignment.CenterVertically),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    /** "You made depth for this picture": a saved copy's depth, offered for this picture. */
    @Composable
    private fun SimilarOffer(
        offer: DepthProject,
        onUse: () -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier,
    ) {
        val thumb by produceState<ImageBitmap?>(null, offer) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(library.thumb(offer.fingerprint.sha).path)?.asImageBitmap()
            }
        }
        val date = remember(offer) {
            DateUtils.formatDateTime(this, offer.modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        }
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    thumb?.let {
                        Image(
                            it, null,
                            Modifier.size(64.dp, 113.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.depth_similar_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.depth_similar_body, date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.depth_similar_fresh)) }
                    Button(onClick = onUse) { Text(stringResource(R.string.depth_similar_use)) }
                }
            }
        }
    }

    /**
     * Picks the most likely subject. One point often finds only a part (a skirt, a face), so
     * every candidate is a pair of points, one high and one low, where subjects usually stand;
     * the mask the model is surest of among those of a plausible size wins.
     */
    private fun autoSelect(engine: SegmentationEngine): SelectionOp.Region? {
        val s = PromptEncoder.INPUT
        val candidates = listOf(
            listOf(0.5f to 0.3f, 0.5f to 0.62f),
            listOf(0.5f to 0.22f, 0.5f to 0.5f),
            listOf(0.5f to 0.4f, 0.5f to 0.75f),
            listOf(0.42f to 0.35f, 0.58f to 0.65f),
            listOf(0.58f to 0.35f, 0.42f to 0.65f),
        )
        return candidates
            .map { pair -> engine.decode(pair.map { (x, y) -> PromptEncoder.Point(x * s, y * s) }) }
            .filter { d -> d.coverage.count { it > 0.5f }.toFloat() / d.coverage.size in 0.03f..0.75f }
            .maxByOrNull { it.score }
            ?.let { SelectionOp.Region(true, it.coverage) }
    }

    /**
     * Keeps the project in the library, hands its matte and subject to SystemUI and turns the
     * depth effect on (see [DepthSave]).
     */
    private fun save(selection: Selection, picture: WallpaperPicture, created: Long?): Boolean {
        val ok = DepthSave.save(this, library, selection, picture, created) != null
        if (ok) Settings.Secure.putInt(contentResolver, "yasvara_depth_enabled", 1)
        return ok
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "DepthStudio"
        private const val WORKING_LONG_SIDE = 768
        private const val PROP_THUMB_PX = 156

        /** A grid fine enough to edit on, at the proportions of a [width] x [height] picture. */
        fun workingSize(width: Int, height: Int): Pair<Int, Int> {
            val scale = WORKING_LONG_SIDE / maxOf(width, height).toFloat()
            return (width * scale).roundToInt() to (height * scale).roundToInt()
        }

        /** A saved picture to open instead of the lock screen's: its fingerprint. */
        const val EXTRA_FINGERPRINT = "org.yasvara.laras.depth.FINGERPRINT"
    }
}
