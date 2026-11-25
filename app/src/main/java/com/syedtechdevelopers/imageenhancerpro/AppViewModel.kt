package com.syedtechdevelopers.imageenhancerpro

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syedtechdevelopers.imageenhancerpro.ai.Enhancer
import com.syedtechdevelopers.imageenhancerpro.ai.ImageIO
import com.syedtechdevelopers.imageenhancerpro.ai.MissingModelsException
import com.syedtechdevelopers.imageenhancerpro.ai.Stage
import com.syedtechdevelopers.imageenhancerpro.data.Limits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

enum class Screen { Home, Working, Result }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val limits = Limits(app)
    private val enhancer by lazy { Enhancer(app) }

    var screen by mutableStateOf(Screen.Home)

    /** Photo as loaded (max 2048 px) and its on-screen preview. */
    var source: Bitmap? = null; private set
    var sourcePreview by mutableStateOf<ImageBitmap?>(null); private set
    var loadingPhoto by mutableStateOf(false); private set

    var result: Bitmap? = null; private set
    var resultPreview by mutableStateOf<ImageBitmap?>(null); private set
    var resultSize by mutableStateOf("")

    var progress by mutableFloatStateOf(0f)
    var stage by mutableStateOf(Stage.Preparing)

    var showUnlock by mutableStateOf(false)
    var showSave by mutableStateOf(false)
    var showInfo by mutableStateOf(false)
    var waitingForAd by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false); private set

    private var job: Job? = null

    fun pick(uri: Uri) {
        loadingPhoto = true
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { ImageIO.decode(getApplication(), uri) }
                setSource(bmp)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                message = "Could not open this photo. Please try another one."
            } finally {
                loadingPhoto = false
            }
        }
    }

    /** Also used by screenshot tests. */
    fun setSource(bmp: Bitmap) {
        source?.takeIf { it !== bmp }?.recycle()
        source = bmp
        sourcePreview = ImageIO.scaledCopy(bmp, PREVIEW).asImageBitmap()
        clearResult()
        screen = Screen.Home
    }

    fun clearPhoto() {
        source?.recycle(); source = null; sourcePreview = null
        clearResult()
        screen = Screen.Home
    }

    private fun clearResult() {
        result?.recycle(); result = null; resultPreview = null
    }

    /** Runs after the Enhance ad was watched (or no ad was available). */
    fun startEnhance() {
        val src = source ?: return
        if (job?.isActive == true) return
        if (!enhancer.modelsPresent()) {
            message = "AI files are missing. Please install the app from Google Play."
            return
        }
        limits.useEnhance()
        progress = 0f
        stage = Stage.Preparing
        screen = Screen.Working
        job = viewModelScope.launch {
            try {
                val out = enhancer.enhance(src) { p, s ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (p > progress) progress = p
                        stage = s
                    }
                }
                setResult(out)
                progress = 1f
            } catch (e: CancellationException) {
                screen = Screen.Home
            } catch (e: MissingModelsException) {
                message = "AI files are missing. Please install the app from Google Play."
                screen = Screen.Home
            } catch (e: OutOfMemoryError) {
                message = "Not enough memory on this phone. Close other apps and try again."
                screen = Screen.Home
            } catch (e: Throwable) {
                message = "Something went wrong. Please try again."
                screen = Screen.Home
            }
        }
    }

    fun setResult(out: Bitmap) {
        clearResult()
        result = out
        resultPreview = ImageIO.scaledCopy(out, PREVIEW).asImageBitmap()
        resultSize = "${out.width} × ${out.height}"
        screen = Screen.Result
    }

    fun cancel() {
        job?.cancel()
        screen = Screen.Home
    }

    fun save(long: Int) {
        val img = result ?: return
        saving = true
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) { ImageIO.saveToGallery(getApplication(), img, long) }
                if (long >= 4096) "Saved in 4K to Gallery › Pictures › Image Enhancer Pro" else "Saved in HD to Gallery › Pictures › Image Enhancer Pro"
            } catch (e: Throwable) {
                "Could not save the photo. Please try again."
            }
            saving = false
        }
    }

    companion object {
        const val PREVIEW = 1440
    }
}
