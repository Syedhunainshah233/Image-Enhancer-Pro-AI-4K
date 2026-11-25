package com.syedtechdevelopers.imageenhancerpro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedtechdevelopers.imageenhancerpro.AppViewModel
import com.syedtechdevelopers.imageenhancerpro.ads.BannerAd
import com.syedtechdevelopers.imageenhancerpro.ads.NativeAdCard
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.syedtechdevelopers.imageenhancerpro.ai.Stage
import kotlin.math.min
import kotlin.math.roundToInt

private fun Stage.label() = when (this) {
    Stage.Preparing -> "Starting AI engine…"
    Stage.Faces -> "Finding faces…"
    Stage.Upscale -> "Removing blur & upscaling to 4K…"
    Stage.Restore -> "Restoring faces & details…"
    Stage.Finishing -> "Finishing touches…"
}

@Composable
fun WorkingScreen(vm: AppViewModel) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true // screen stays awake until the photo is ready
        onDispose { view.keepScreenOn = false }
    }
    val p by animateFloatAsState(vm.progress, tween(400), label = "progress")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "angle")
    val glow by spin.animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top ads section: banner + native box
        Spacer(Modifier.height(6.dp))
        BannerAd()
        Spacer(Modifier.height(6.dp))
        NativeAdCard(Modifier.padding(horizontal = 16.dp), slot = "work")
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                // Spinner ring
                Canvas(Modifier.fillMaxSize().rotate(angle)) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, C.blue, C.purple, C.pink)),
                        startAngle = 0f, sweepAngle = 300f, useCenter = false,
                        style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                // Progress ring
                Canvas(Modifier.size(116.dp)) {
                    drawArc(C.card2, -90f, 360f, false, style = Stroke(8.dp.toPx()))
                    drawArc(C.gold, -90f, 360f * p, false, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(p * 100).roundToInt()}%", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("✨", fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp), color = C.gold.copy(alpha = glow))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Enhancing your photo", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(vm.stage.label(), fontSize = 15.sp, color = C.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            // Progress bar
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(C.card2)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0.02f, 1f)).clip(RoundedCornerShape(50)).background(C.grad))
            }
            Spacer(Modifier.height(16.dp))
            // Warning: leaving the screen can stop the AI.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(C.gold.copy(alpha = 0.12f))
                    .border(1.dp, C.gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚠️  Do not close the app", color = C.gold, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Stay on this screen until your photo is ready. Leaving may stop the enhancement.",
                    color = C.text, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("🔒 100% on your phone • private", fontSize = 12.sp, color = C.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Cancel", color = C.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.showStop = true }.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
        }
        // Bottom ads section: native box (+ the banner under every screen)
        NativeAdCard(Modifier.padding(horizontal = 16.dp), slot = "work_bottom")
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun ResultScreen(vm: AppViewModel, onSave: () -> Unit, onShare: () -> Unit, onNew: () -> Unit) {
    val before = vm.sourcePreview
    val after = vm.resultPreview
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("✨ Enhanced!", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("${vm.resultSize} • Ultra HD 4K", fontSize = 13.sp, color = C.muted)
            }
            Pill("AI 4K", C.gold)
        }
        NativeAdCard(Modifier.padding(horizontal = 16.dp), slot = "result")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (before != null && after != null) BeforeAfter(before, after)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientButton(if (vm.saving) "Saving…" else "⬇  Save", Modifier.weight(1.4f), enabled = !vm.saving, onClick = onSave)
            OutlineButton("Share", Modifier.weight(1f).height(58.dp), onClick = onShare)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "＋  Enhance another photo", color = C.purple, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onNew)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

/** Drag the handle (or tap anywhere) to compare the original and the enhanced photo. */
@Composable
fun BeforeAfter(before: ImageBitmap, after: ImageBitmap) {
    var split by remember { mutableFloatStateOf(0.5f) }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(C.card),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
        ) {
            val s = min(size.width / after.width, size.height / after.height)
            val dw = after.width * s
            val dh = after.height * s
            val left = (size.width - dw) / 2
            val top = (size.height - dh) / 2
            val dst = IntOffset(left.roundToInt(), top.roundToInt())
            val dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
            drawImage(after, IntOffset.Zero, IntSize(after.width, after.height), dst, dstSize, filterQuality = FilterQuality.High)
            val x = size.width * split
            clipRect(right = x) {
                drawImage(before, IntOffset.Zero, IntSize(before.width, before.height), dst, dstSize, filterQuality = FilterQuality.Low)
            }
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            drawCircle(Color.White, 22.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(C.purple, 18.dp.toPx(), Offset(x, size.height / 2))
            val a = 6.dp.toPx()
            val cy = size.height / 2
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x - 4.dp.toPx(), cy - a); lineTo(x - 4.dp.toPx() - a, cy); lineTo(x - 4.dp.toPx(), cy + a); close()
                moveTo(x + 4.dp.toPx(), cy - a); lineTo(x + 4.dp.toPx() + a, cy); lineTo(x + 4.dp.toPx(), cy + a); close()
            }
            drawPath(path, Color.White)
        }
        Label("BEFORE", Color.White, Modifier.align(Alignment.TopStart).padding(10.dp))
        Label("AFTER ✨", C.gold, Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun Label(text: String, color: Color, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(C.bg.copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black) }
}
