package com.syedtechdevelopers.imageenhancerpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.syedtechdevelopers.imageenhancerpro.AppViewModel
import com.syedtechdevelopers.imageenhancerpro.data.Limits

@Composable
private fun DialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(C.card)
                .border(1.dp, C.line, RoundedCornerShape(26.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text, color = C.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Daily limit used: watch 5 rewarded ads → 1 hour unlimited. */
@Composable
fun UnlockDialog(vm: AppViewModel, onWatch: () -> Unit) {
    val l = vm.limits
    DialogCard({ vm.showUnlock = false }) {
        if (l.unlocked) {
            Text("⚡", fontSize = 44.sp)
            Text("Unlimited is ON", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text("Enhance as many photos as you like.\nEnds in ${Limits.format(l.unlockLeftMs)}", color = C.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            GradientButton("Let's go!", onClick = { vm.showUnlock = false })
        } else {
            LockedContent(vm, onWatch)
        }
    }
}

@Composable
private fun LockedContent(vm: AppViewModel, onWatch: () -> Unit) {
    val l = vm.limits
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (l.freeLeft == 0) "🔒" else "⚡", fontSize = 44.sp)
        Text(
            if (l.freeLeft == 0) "Daily free limit used" else "Get 1 hour UNLIMITED",
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Watch ${Limits.ADS_TO_UNLOCK} short ads to unlock 1 hour of unlimited 4K enhancing. 100% free, no payment.",
            color = C.muted, textAlign = TextAlign.Center, fontSize = 14.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(Limits.ADS_TO_UNLOCK) { i ->
                val done = i < l.adsWatched
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (done) C.grad else androidx.compose.ui.graphics.SolidColor(C.card2)),
                    contentAlignment = Alignment.Center,
                ) { Text(if (done) "✓" else "${i + 1}", fontWeight = FontWeight.Bold, color = if (done) C.text else C.muted) }
            }
        }
        Spacer(Modifier.height(18.dp))
        GradientButton(
            if (vm.waitingForAd) "Loading ad…" else "▶  Watch Ad (${l.adsWatched + 1}/${Limits.ADS_TO_UNLOCK})",
            enabled = !vm.waitingForAd,
            onClick = onWatch,
        )
        Spacer(Modifier.height(10.dp))
        if (l.freeLeft == 0 && l.resetLeftMs > 0) {
            Text("Free enhances come back in ${Limits.format(l.resetLeftMs)}", color = C.muted, fontSize = 13.sp)
        } else {
            Text("You still have ${l.freeLeft} free enhances today", color = C.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        TextLink("Close") { vm.showUnlock = false }
    }
}

/** Intro screen for the rewarded interstitial (required by AdMob): user can watch or skip. */
@Composable
fun SaveDialog(vm: AppViewModel, onSave4k: () -> Unit, onSaveHd: () -> Unit) {
    DialogCard({ vm.showSave = false }) {
        Text("⬇", fontSize = 40.sp)
        Text("Save your photo", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Watch a short ad to save in full 4K Ultra HD, or save in HD now.", color = C.muted, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        GradientButton("▶  Save 4K Ultra HD", brush = C.gold2, textColor = C.bg, onClick = onSave4k)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Save HD (2K) – no ad", Modifier.fillMaxWidth(), onClick = onSaveHd)
        Spacer(Modifier.height(4.dp))
        TextLink("Cancel") { vm.showSave = false }
    }
}

@Composable
fun InfoDialog(vm: AppViewModel, version: String, showPrivacyOptions: Boolean, onPrivacy: () -> Unit, onPrivacyOptions: () -> Unit, onRate: () -> Unit) {
    DialogCard({ vm.showInfo = false }) {
        Text("Image Enhancer Pro – AI 4K", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text("Version $version • by SyedTechdevelopers", color = C.muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your photos are enhanced 100% on your phone. They are never uploaded to any server.",
            color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlineButton("⭐  Rate the app", Modifier.fillMaxWidth(), onClick = onRate)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Privacy Policy", Modifier.fillMaxWidth(), onClick = onPrivacy)
        if (showPrivacyOptions) {
            Spacer(Modifier.height(10.dp))
            OutlineButton("Ad privacy options", Modifier.fillMaxWidth(), onClick = onPrivacyOptions)
        }
        Spacer(Modifier.height(4.dp))
        TextLink("Close") { vm.showInfo = false }
    }
}

/** Dim overlay while the Enhance ad loads. */
@Composable
fun AdLoadingOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(C.bg.copy(alpha = 0.8f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = C.purple)
            Spacer(Modifier.height(14.dp))
            Text("Loading…", fontWeight = FontWeight.Bold)
            Text("Watch the full ad to start enhancing", color = C.muted, fontSize = 13.sp)
        }
    }
}

/** Back or Cancel while enhancing: ask first, so the work is not lost by mistake. */
@Composable
fun StopDialog(vm: AppViewModel) {
    DialogCard({ vm.showStop = false }) {
        Text("⚠️", fontSize = 40.sp)
        Text("Stop enhancing?", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Your photo is almost ready. If you stop now, this enhance is lost.", color = C.muted, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        GradientButton("Keep enhancing", onClick = { vm.showStop = false })
        Spacer(Modifier.height(4.dp))
        TextLink("Stop") { vm.cancel() }
    }
}
