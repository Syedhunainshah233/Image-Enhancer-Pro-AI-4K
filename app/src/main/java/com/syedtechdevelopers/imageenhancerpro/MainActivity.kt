package com.syedtechdevelopers.imageenhancerpro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.syedtechdevelopers.imageenhancerpro.ads.Ads
import com.syedtechdevelopers.imageenhancerpro.ads.BannerAd
import com.syedtechdevelopers.imageenhancerpro.ai.ImageIO
import com.syedtechdevelopers.imageenhancerpro.ui.AdLoadingOverlay
import com.syedtechdevelopers.imageenhancerpro.ui.AppTheme
import com.syedtechdevelopers.imageenhancerpro.ui.C
import com.syedtechdevelopers.imageenhancerpro.ui.HomeScreen
import com.syedtechdevelopers.imageenhancerpro.ui.InfoDialog
import com.syedtechdevelopers.imageenhancerpro.ui.ResultScreen
import com.syedtechdevelopers.imageenhancerpro.ui.SaveDialog
import com.syedtechdevelopers.imageenhancerpro.ui.StopDialog
import com.syedtechdevelopers.imageenhancerpro.ui.UnlockDialog
import com.syedtechdevelopers.imageenhancerpro.ui.WorkingScreen
import com.syedtechdevelopers.imageenhancerpro.ui.findActivity
import kotlinx.coroutines.delay

const val PRIVACY_URL = "https://syedhunainshah233.github.io/image-enhancer-pro/privacy.html"

class MainActivity : ComponentActivity() {
    internal val vm: AppViewModel by viewModels()
    private var coldStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Logo splash stays up while the app open ad loads on launch.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        coldStart = savedInstanceState == null
        if (coldStart) Ads.beginLaunch(this)
        splash.setKeepOnScreenCondition { Ads.holdSplash() }
        enableEdgeToEdge()
        setContent { AppTheme { App(vm) } }
        Ads.start(this)
    }

    override fun onStart() {
        super.onStart()
        // App open ad on launch, or when the user really comes back (not on rotation / dark-mode switch).
        if (coldStart || wentToBackground) Ads.onAppStart(this, coldStart)
        coldStart = false
        wentToBackground = false
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) wentToBackground = true
    }

    companion object {
        private var wentToBackground = false
    }
}

@Composable
fun App(vm: AppViewModel) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) vm.pick(uri)
    }
    val pick = {
        Ads.suppressNextReturn() // coming back from the gallery shows no app open ad
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var pendingSave by remember { mutableIntStateOf(4096) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.save(pendingSave) else vm.message = "Allow storage access to save photos"
    }
    val save: (Int) -> Unit = { long ->
        vm.showSave = false
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = long
            Ads.suppressNextReturn()
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.save(long)
        }
    }

    val enhance = {
        if (!vm.limits.canEnhance) {
            vm.showUnlock = true
        } else {
            // The enhance starts only after the rewarded ad is watched to the end.
            vm.waitingForAd = true
            Ads.gateRewarded(
                activity,
                onEarned = { vm.waitingForAd = false; vm.startEnhance() },
                onNotEarned = { vm.waitingForAd = false; vm.message = "Watch the full ad to enhance your photo" },
                onUnavailable = { vm.waitingForAd = false; vm.startEnhance() },
            )
        }
    }

    val watchUnlockAd = {
        vm.waitingForAd = true
        Ads.showRewarded(
            activity,
            onReward = { if (vm.limits.adWatched()) vm.message = "⚡ Unlimited unlocked for 1 hour!" },
            onFailed = { vm.waitingForAd = false; vm.message = "No ad available right now. Please try again in a moment." },
            onClosed = { vm.waitingForAd = false },
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            vm.limits.tick(); delay(1_000)
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.message = null }
    }

    BackHandler(enabled = vm.screen != Screen.Home) {
        if (vm.screen == Screen.Working) vm.showStop = true else Ads.showInterstitial(activity) { vm.screen = Screen.Home }
    }

    if (vm.showStop && vm.screen == Screen.Working) StopDialog(vm)
    if (vm.showUnlock) UnlockDialog(vm, onWatch = watchUnlockAd)
    if (vm.showSave) {
        SaveDialog(
            vm,
            onSave4k = {
                vm.showSave = false
                Ads.showRewardedInterstitial(activity, onNotEarned = { save(2048) }) { save(4096) }
            },
            onSaveHd = { save(2048) },
        )
    }
    if (vm.showInfo) {
        InfoDialog(
            vm, BuildConfig.VERSION_NAME,
            showPrivacyOptions = activity?.let { Ads.privacyOptionsRequired(it) } ?: false,
            onPrivacy = { Ads.suppressNextReturn(); open(ctx, PRIVACY_URL) },
            onPrivacyOptions = { activity?.let { Ads.showPrivacyOptions(it) } },
            onRate = { Ads.suppressNextReturn(); open(ctx, "https://play.google.com/store/apps/details?id=${ctx.packageName}") },
        )
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = vm.screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(vm, onPick = pick, onEnhance = enhance, onUnlock = { vm.showUnlock = true })
                        Screen.Working -> WorkingScreen(vm)
                        Screen.Result -> ResultScreen(
                            vm,
                            onSave = { vm.showSave = true },
                            onShare = {
                                vm.result?.let { img ->
                                    Ads.suppressNextReturn()
                                    runCatching { ctx.startActivity(ImageIO.shareIntent(ctx, img)) }
                                }
                            },
                            onNew = { Ads.showInterstitial(activity) { vm.clearPhoto(); pick() } },
                        )
                    }
                }
            }
            // Bottom ads section
            BannerAd()
        }
        if (vm.waitingForAd && !vm.showUnlock) AdLoadingOverlay()
    }
}

private fun open(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
