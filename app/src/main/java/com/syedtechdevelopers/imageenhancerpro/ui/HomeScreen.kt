package com.syedtechdevelopers.imageenhancerpro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syedtechdevelopers.imageenhancerpro.AppViewModel
import com.syedtechdevelopers.imageenhancerpro.R
import com.syedtechdevelopers.imageenhancerpro.ads.BannerAd
import com.syedtechdevelopers.imageenhancerpro.ads.NativeAdCard
import com.syedtechdevelopers.imageenhancerpro.data.Limits

@Composable
fun TopBar(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.logo), null,
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Image Enhancer Pro", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = C.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(C.gold2).padding(horizontal = 6.dp, vertical = 1.dp),
                ) { Text("AI 4K", fontSize = 11.sp, fontWeight = FontWeight.Black, color = C.bg) }
                Spacer(Modifier.width(6.dp))
                Text("Blur → Crystal clear", fontSize = 12.sp, color = C.muted)
            }
        }
        Icon(
            Icons.Filled.Info, "Info", tint = C.muted,
            modifier = Modifier.clip(CircleShape).clickable { vm.showInfo = true }.padding(8.dp),
        )
    }
}

/** Free / PRO status with the live 24 h and 1 h timers. */
@Composable
fun StatusRow(vm: AppViewModel, onUnlock: () -> Unit) {
    val l = vm.limits
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.card)
            .clickable(onClick = onUnlock)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l.unlocked) {
            Text("⚡", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("UNLIMITED active", color = C.gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Ends in ${Limits.format(l.unlockLeftMs)}", color = C.muted, fontSize = 12.sp)
            }
        } else {
            Text("🎁", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Free: ${l.freeLeft}/${Limits.FREE_PER_DAY} enhances left", color = C.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (l.resetLeftMs > 0) "Resets in ${Limits.format(l.resetLeftMs)}" else "Resets every 24 hours",
                    color = C.muted, fontSize = 12.sp,
                )
            }
            Pill("⚡ 1h FREE", C.gold)
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, onPick: () -> Unit, onEnhance: () -> Unit, onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(vm)
        // Top ads section: banner + native box
        BannerAd()
        Spacer(Modifier.height(6.dp))
        NativeAdCard(Modifier.padding(horizontal = 16.dp), slot = "home")
        Spacer(Modifier.height(8.dp))
        StatusRow(vm, onUnlock)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            val preview = vm.sourcePreview
            if (preview == null) {
                UploadBox(vm.loadingPhoto, onPick)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(C.card)
                        .border(1.dp, C.line, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(preview, "Your photo", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(C.bg.copy(alpha = 0.75f))
                            .clickable(onClick = onPick)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Change", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        Icons.Filled.Close, "Remove", tint = C.text,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(C.bg.copy(alpha = 0.75f))
                            .clickable { vm.clearPhoto() }
                            .padding(6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!vm.net.online) OfflineStrip { vm.net.refresh() }
        GradientButton(
            if (vm.sourcePreview == null) "📷  Upload Photo" else "✨  Enhance",
            Modifier.padding(horizontal = 16.dp),
            onClick = { if (vm.sourcePreview == null) onPick() else onEnhance() },
        )
        // Bottom ads section: native box (+ the banner under every screen), kept apart from the button
        Spacer(Modifier.height(14.dp))
        NativeAdCard(Modifier.padding(horizontal = 16.dp), slot = "home_bottom")
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun UploadBox(loading: Boolean, onPick: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(C.card2, C.card)))
            .clickable(enabled = !loading, onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = C.grad,
                cornerRadius = CornerRadius(22.dp.toPx()),
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
            )
        }
        if (loading) {
            CircularProgressIndicator(color = C.purple)
        } else {
            UploadContent()
        }
    }
}

@Composable
private fun ColumnScope.UploadHint() {
    Text(
        "Blurry, old or low-quality photo?\nAI makes it sharp & clear in 4K",
        color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
    )
}

@Composable
private fun UploadContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(C.grad),
            contentAlignment = Alignment.Center,
        ) { Text("＋", fontSize = 40.sp, color = C.text, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
        Text("Tap to upload photo", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        UploadHint()
    }
}

/** Red strip above the Enhance button while there is no internet. */
@Composable
private fun OfflineStrip(onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.red.copy(alpha = 0.14f))
            .border(1.dp, C.red.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📡", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("No internet connection", color = C.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Connect to enhance photos", color = C.muted, fontSize = 12.sp)
        }
        Text(
            "Refresh", color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(C.red.copy(alpha = 0.6f)).clickable(onClick = onRefresh)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
