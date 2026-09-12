package com.example.wifidrop

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/** Real Android providers and Intents, without opening apps or touching existing downloads. */
class ReceivedFileOpenInstrumentation : Instrumentation() {
    private var flashSockets = false
    private var flashUdp = false
    private var flashPcHost = "127.0.0.1"

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        flashSockets = arguments?.getString("scenario") == "flash-sockets"
        flashUdp = arguments?.getString("scenario") == "flash-udp"
        flashPcHost = arguments?.getString("pc-host")?.trim()?.takeIf { it.isNotEmpty() } ?: "127.0.0.1"
        start()
    }

    override fun onStart() {
        if (flashUdp) {
            FlashUdpInstrumentationScenario(this).run()
            return
        }
        if (flashSockets) {
            FlashSocketInstrumentationScenario(this, flashPcHost).run()
            return
        }
        val results = Bundle()
        val checks = mutableListOf<String>()
        val context = targetContext
        val recorder = RecordingContext(context)
        val directory = File(context.cacheDir, "open-qa-${System.nanoTime()}").apply { mkdirs() }
        val exported = mutableListOf<Uri>()
        fun verify(name: String, block: () -> Unit) { block(); checks += name }
        fun export(file: File): Uri = DownloadsExport.exportToDownloads(context, file).getOrThrow().also(exported::add)
        try {
            val png = File(directory, "Foto QA ${System.nanoTime()}.PNG")
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            png.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            val pdf = File(directory, "Documento QA ${System.nanoTime()}.pdf")
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(200, 200, 1).create())
                page.canvas.drawText("Qetara QA", 20f, 40f, Paint())
                document.finishPage(page)
                pdf.outputStream().use(document::writeTo)
            } finally { document.close() }
            verify("image_name_mapping") {
                check(ReceivedFileMimeTypes.fromName("Foto con espacios.JPG") == "image/jpeg")
                check(ReceivedFileMimeTypes.fromName("Foto.PNG") == "image/png")
                check(ReceivedFileMimeTypes.fromName("Foto.HEIC") == "image/heic")
                check(ReceivedFileMimeTypes.fromName("Foto.HEIF") == "image/heif")
                check(ReceivedFileMimeTypes.fromName("Foto.AVIF") == "image/avif")
            }
            verify("png_content_uri_preserves_type_and_read_only_chooser") {
                val uri = export(png)
                check(context.contentResolver.getType(uri) == "image/png")
                ExternalOpenUtils.openRoute(recorder, uri.toString())
                recorder.assertChooser("image/png", uri)
            }
            verify("png_private_path_uses_content_uri") {
                ExternalOpenUtils.openRoute(recorder, png.absolutePath)
                recorder.assertChooser("image/png")
            }
            verify("legacy_file_uri_converted_for_android_24_plus") {
                ExternalOpenUtils.openRoute(recorder, Uri.fromFile(png).toString())
                recorder.assertChooser("image/png")
            }
            verify("public_provider_root_is_limited_to_qetara_downloads") {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val allowed = File(downloads, "Qetara/test.png")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", allowed)
                check(uri.path.orEmpty().startsWith("/qetara_downloads/"))
                check(runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(downloads, "outside.png"))
                }.exceptionOrNull() is IllegalArgumentException)
            }
            verify("pdf_content_uri_remains_pdf") {
                val uri = export(pdf)
                check(context.contentResolver.getType(uri) == "application/pdf")
                ExternalOpenUtils.openRoute(recorder, uri.toString())
                recorder.assertChooser("application/pdf", uri)
            }
            verify("share_png_has_specific_type_and_read_permission") {
                FileShareUtils.shareFile(recorder, png)
                val target = recorder.chooserTarget()
                check(target.action == Intent.ACTION_SEND && target.type == "image/png")
                check(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                check(target.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
            }
            verify("save_copy_preserves_image_and_document_types_and_names") {
                val contract = CreateReceivedDocument()
                val image = contract.createIntent(context, png.name)
                check(image.action == Intent.ACTION_CREATE_DOCUMENT && image.type == "image/png")
                check(image.getStringExtra(Intent.EXTRA_TITLE) == png.name)
                check(contract.createIntent(context, pdf.name).type == "application/pdf")
            }
            verify("unknown_type_is_not_mislabelled_as_image_or_pdf") {
                check(ReceivedFileMimeTypes.fromName("without_extension") == null)
                check(CreateReceivedDocument().createIntent(context, "without_extension").type == "application/octet-stream")
            }
            results.putString("result", "PASS")
        } catch (failure: Throwable) {
            results.putString("result", "FAIL")
            results.putString("failure", failure.stackTraceToString())
        } finally {
            val cleaned = exported.all { runCatching { context.contentResolver.delete(it, null, null) > 0 }.getOrDefault(false) }
            val privateCleaned = directory.deleteRecursively()
            results.putBoolean("created_fixtures_removed", cleaned && privateCleaned)
            if (!cleaned || !privateCleaned) results.putString("result", "FAIL")
            results.putInt("checks_passed", checks.size)
            results.putString("checks", checks.joinToString(","))
            finish(if (results.getString("result") == "PASS") Activity.RESULT_OK else Activity.RESULT_CANCELED, results)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        private var launched: Intent? = null
        override fun startActivity(intent: Intent) { launched = intent }
        @Suppress("DEPRECATION")
        fun chooserTarget(): Intent {
            val chooser = checkNotNull(launched)
            check(chooser.action == Intent.ACTION_CHOOSER)
            return checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        }
        fun assertChooser(mime: String, expectedUri: Uri? = null) {
            val target = chooserTarget()
            check(target.action == Intent.ACTION_VIEW && target.type == mime)
            val uri = checkNotNull(target.data)
            check(uri.scheme == "content")
            if (expectedUri != null) check(uri == expectedUri)
            check(target.`package` == null && target.component == null)
            check(target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            check(target.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
            check(target.clipData?.getItemAt(0)?.uri == uri)
            check(checkNotNull(launched).flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
    }
}
