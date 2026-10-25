package com.willian.mp3pro.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Baixa uma música da pasta do OneDrive para a pasta privada do app. */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val K_PATH = "path"
        private const val K_NAME = "name"
        private const val K_URL = "url"
        private const val K_EXT = "ext"

        fun enqueue(context: Context, entry: RemoteEntry) {
            val url = entry.contentUrl ?: return
            Offline.setProgress(entry.path, 0)
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        K_PATH to entry.path, K_NAME to entry.name,
                        K_URL to url, K_EXT to entry.extension
                    )
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("dl:${entry.path}", ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Offline.init(applicationContext)
        val path = inputData.getString(K_PATH) ?: return@withContext Result.failure()
        val name = inputData.getString(K_NAME) ?: return@withContext Result.failure()
        val url = inputData.getString(K_URL) ?: return@withContext Result.failure()
        val ext = inputData.getString(K_EXT) ?: "mp3"

        val target = Offline.targetFile(path, ext)
        val temp = Offline.tempFile(path)

        try {
            val req = Request.Builder().url(url).build()
            Http.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("resposta vazia")
                val total = body.contentLength()
                var done = 0L
                var lastPct = -1
                temp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    Offline.setProgress(path, pct)
                                }
                            }
                        }
                    }
                }
            }
            if (temp.length() == 0L) throw IOException("arquivo vazio")
            target.delete()
            if (!temp.renameTo(target)) throw IOException("não foi possível gravar o arquivo")
            Offline.register(path, name, target.length(), target)
            Offline.setProgress(path, null)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            temp.delete()
            Offline.setProgress(path, null)
            throw e
        } catch (e: Exception) {
            temp.delete()
            Offline.setProgress(path, null)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
