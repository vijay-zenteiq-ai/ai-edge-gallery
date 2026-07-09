/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.data.KEY_MODEL_COMMIT_HASH
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_FILE_NAME
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_MODEL_DIR
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_RATE
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.google.ai.edge.gallery.data.KEY_MODEL_DOWNLOAD_REMAINING_MS
import com.google.ai.edge.gallery.data.KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES
import com.google.ai.edge.gallery.data.KEY_MODEL_EXTRA_DATA_URLS
import com.google.ai.edge.gallery.data.KEY_MODEL_IS_IMPORTED
import com.google.ai.edge.gallery.data.KEY_MODEL_IS_ZIP
import com.google.ai.edge.gallery.data.KEY_MODEL_NAME
import com.google.ai.edge.gallery.data.KEY_MODEL_START_UNZIPPING
import com.google.ai.edge.gallery.data.KEY_MODEL_TOTAL_BYTES
import com.google.ai.edge.gallery.data.KEY_MODEL_UNZIPPED_DIR
import com.google.ai.edge.gallery.data.KEY_MODEL_URL
import com.google.ai.edge.gallery.data.TMP_FILE_EXT
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AGDownloadWorker"

data class UrlAndFileName(val url: String, val fileName: String)

private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "model_download_channel_foreground"
private var channelCreated = false

class DownloadWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  private val externalFilesDir = context.getExternalFilesDir(null)

  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  // Unique notification id.
  private val notificationId: Int = params.id.hashCode()

  init {
    if (!channelCreated) {
      // Create a notification channel for showing notifications for model downloading progress.
      val channel =
        NotificationChannel(
            FOREGROUND_NOTIFICATION_CHANNEL_ID,
            "Model Downloading",
            // Make it silent.
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = "Notifications for model downloading" }
      notificationManager.createNotificationChannel(channel)
      channelCreated = true
    }
  }

  override suspend fun doWork(): Result {
    val fileUrl = inputData.getString(KEY_MODEL_URL)
    val modelName = inputData.getString(KEY_MODEL_NAME) ?: "Model"
    val version = inputData.getString(KEY_MODEL_COMMIT_HASH)!!
    val fileName = inputData.getString(KEY_MODEL_DOWNLOAD_FILE_NAME)
    val modelDir = inputData.getString(KEY_MODEL_DOWNLOAD_MODEL_DIR)!!
    val isModelImported = inputData.getBoolean(KEY_MODEL_IS_IMPORTED, false)
    val isZip = inputData.getBoolean(KEY_MODEL_IS_ZIP, false)
    val unzippedDir = inputData.getString(KEY_MODEL_UNZIPPED_DIR)
    val extraDataFileUrls = inputData.getString(KEY_MODEL_EXTRA_DATA_URLS)?.split(",") ?: listOf()
    val extraDataFileNames =
      inputData.getString(KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES)?.split(",") ?: listOf()
    val totalBytes = inputData.getLong(KEY_MODEL_TOTAL_BYTES, 0L)
    val accessToken = inputData.getString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN)

    return withContext(Dispatchers.IO) {
      if (fileUrl == null || fileName == null) {
        Result.failure()
      } else {
        return@withContext try {
          // Set the worker as a foreground service immediately.
          setForeground(createForegroundInfo(progress = 0, modelName = modelName))

          // Collect data for all files.
          val allFiles: MutableList<UrlAndFileName> = mutableListOf()
          allFiles.add(UrlAndFileName(url = fileUrl, fileName = fileName))
          for (index in extraDataFileUrls.indices) {
            allFiles.add(
              UrlAndFileName(url = extraDataFileUrls[index], fileName = extraDataFileNames[index])
            )
          }
          Log.d(TAG, "About to download: $allFiles")

          // Download them in sequence.
          // TODO: maybe consider downloading them in parallel.
          var completedFilesBytes = 0L
          val bytesReadSizeBuffer: MutableList<Long> = mutableListOf()
          val bytesReadLatencyBuffer: MutableList<Long> = mutableListOf()
          for (file in allFiles) {
            var connection: HttpURLConnection? = null
            var lastException: IOException? = null

            val baseDir =
              applicationContext.getExternalFilesDir(null)
                ?: throw IOException("External storage is not available")

            val modelFolder = File(baseDir, modelDir)
            val outputDir = if (isModelImported) modelFolder else File(modelFolder, version)

            if (!outputDir.exists()) {
              val created = outputDir.mkdirs()
              Log.d(TAG, "Creating directory ${outputDir.absolutePath}. Success: $created")
            }

            val originalFilePath =
              File(outputDir, file.fileName).absolutePath.replace(".$TMP_FILE_EXT", "")
            val originalFile = File(originalFilePath)

            // If the file is already fully downloaded, skip it.
            if (originalFile.exists()) {
              Log.d(TAG, "File ${file.fileName} already exists. Skipping.")
              completedFilesBytes += originalFile.length()
              continue
            }

            val outputTmpFile = File(outputDir, "${file.fileName}.$TMP_FILE_EXT")
            val outputFileBytes = outputTmpFile.length()

            val hostsToTry =
              if (file.url.contains("huggingface.co")) {
                listOf("huggingface.co", "modelscope.cn")
              } else {
                listOf(null)
              }

            fun setupConnection(conn: HttpURLConnection, urlString: String) {
              // Standard browser headers for public downloads
              conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
              )
              conn.setRequestProperty("Accept", "*/*")
              conn.setRequestProperty("Connection", "keep-alive")
              conn.connectTimeout = 30000
              conn.readTimeout = 30000
              if (accessToken != null && urlString.contains("huggingface.co")) {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
              }
            }

            var currentFileDownloadedBytes: Long
            for (host in hostsToTry) {
              try {
                var urlString = file.url
                if (host == "modelscope.cn") {
                  val hfRegex =
                    Regex("https://huggingface.co/(.+)/resolve/([^/]+)/(.+)\\?download=true")
                  val match = hfRegex.find(file.url)
                  if (match != null) {
                    val modelId = match.groupValues[1]
                    val commitHash = match.groupValues[2]
                    val modelFile = match.groupValues[3]
                    urlString =
                      "https://modelscope.cn/api/v1/models/$modelId/repo?Revision=$commitHash&FilePath=$modelFile"
                  } else {
                    urlString = file.url.replace("huggingface.co", host)
                  }
                } else if (host != null) {
                  urlString = file.url.replace("huggingface.co", host)
                }

                Log.d(TAG, "Trying to connect to: $urlString")
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                setupConnection(conn, urlString)

                if (outputFileBytes > 0) {
                  Log.d(
                    TAG,
                    "File '${outputTmpFile.name}' partial size: ${outputFileBytes}. Trying to resume download",
                  )
                  conn.setRequestProperty("Range", "bytes=${outputFileBytes}-")
                  // Force the server to send non-compressed data to make download resuming work.
                  conn.setRequestProperty("Accept-Encoding", "identity")
                }

                conn.connect()
                connection = conn
                break
              } catch (e: IOException) {
                Log.w(TAG, "Connection attempt failed for host $host: ${e.message}")
                lastException = e
              }
            }

            if (connection == null) {
              throw lastException ?: IOException("Failed to connect to any host")
            }
            Log.d(TAG, "response code: ${connection.responseCode}")

            if (
              connection.responseCode == HttpURLConnection.HTTP_OK ||
                connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            ) {
              val contentRange = connection.getHeaderField("Content-Range")

              if (contentRange != null) {
                // Parse the Content-Range header
                val rangeParts = contentRange.substringAfter("bytes ").split("/")
                val byteRange = rangeParts[0].split("-")
                val startByte = byteRange[0].toLong()
                val endByte = byteRange[1].toLong()

                Log.d(
                  TAG,
                  "Content-Range: $contentRange. Start bytes: ${startByte}, end bytes: $endByte",
                )

                currentFileDownloadedBytes = startByte
              } else {
                Log.d(TAG, "Download starts from beginning.")
                currentFileDownloadedBytes = 0
              }
            } else {
              throw IOException("HTTP error code: ${connection.responseCode}")
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputTmpFile, true /* append */)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var lastSetProgressTs: Long = 0
            var deltaBytes = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
              outputStream.write(buffer, 0, bytesRead)
              currentFileDownloadedBytes += bytesRead
              deltaBytes += bytesRead

              val downloadedBytes = completedFilesBytes + currentFileDownloadedBytes

              // Report progress every 200 ms.
              val curTs = System.currentTimeMillis()
              if (curTs - lastSetProgressTs > 200) {
                // Calculate download rate.
                var bytesPerMs = 0f
                if (lastSetProgressTs != 0L) {
                  if (bytesReadSizeBuffer.size == 5) {
                    bytesReadSizeBuffer.removeAt(0)
                  }
                  bytesReadSizeBuffer.add(deltaBytes)
                  if (bytesReadLatencyBuffer.size == 5) {
                    bytesReadLatencyBuffer.removeAt(0)
                  }
                  bytesReadLatencyBuffer.add(curTs - lastSetProgressTs)
                  deltaBytes = 0L
                  bytesPerMs = bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                }

                // Calculate remaining seconds
                var remainingMs = 0f
                if (bytesPerMs > 0f && totalBytes > 0L) {
                  remainingMs = (totalBytes - downloadedBytes) / bytesPerMs
                }

                setProgress(
                  Data.Builder()
                    .putLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                    .putLong(KEY_MODEL_DOWNLOAD_RATE, (bytesPerMs * 1000).toLong())
                    .putLong(KEY_MODEL_DOWNLOAD_REMAINING_MS, remainingMs.toLong())
                    .build()
                )
                setForeground(
                  createForegroundInfo(
                    progress =
                      if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0,
                    modelName = modelName,
                  )
                )
                Log.d(TAG, "downloadedBytes: $downloadedBytes")
                lastSetProgressTs = curTs
              }
            }

            outputStream.close()
            inputStream.close()

            // Rename the tmp file to the original file name by removing the tmp file ext.
            if (originalFile.exists()) {
              originalFile.delete()
            }
            outputTmpFile.renameTo(originalFile)
            completedFilesBytes += originalFile.length()
            Log.d(TAG, "Download done")

            // Unzip if the downloaded file is a zip.
            if (isZip && unzippedDir != null) {
              setProgress(Data.Builder().putBoolean(KEY_MODEL_START_UNZIPPING, true).build())

              // Prepare target dir.
              val destDir =
                File(
                  externalFilesDir,
                  listOf(modelDir, version, unzippedDir).joinToString(File.separator),
                )
              if (!destDir.exists()) {
                destDir.mkdirs()
              }

              // Unzip.
              val unzipBuffer = ByteArray(4096)
              val zipFilePath =
                "${externalFilesDir}${File.separator}$modelDir${File.separator}$version${File.separator}${fileName}"
              val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath)))
              var zipEntry: ZipEntry? = zipIn.nextEntry

              while (zipEntry != null) {
                val filePath = destDir.absolutePath + File.separator + zipEntry.name

                // Extract files.
                if (!zipEntry.isDirectory) {
                  // extract file
                  val bos = FileOutputStream(filePath)
                  bos.use { curBos ->
                    var len: Int
                    while (zipIn.read(unzipBuffer).also { len = it } > 0) {
                      curBos.write(unzipBuffer, 0, len)
                    }
                  }
                }
                // Create dir.
                else {
                  val dir = File(filePath)
                  dir.mkdirs()
                }

                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
              }
              zipIn.close()

              // Delete the original file.
              val zipFile = File(zipFilePath)
              zipFile.delete()
            }
          }
          Result.success()
        } catch (e: IOException) {
          Log.e(TAG, e.message, e)
          var errorMessage = e.message ?: "Unknown error"
          if (errorMessage.contains("Unable to resolve host \"huggingface.co\"")) {
            errorMessage =
              "Unable to resolve Hugging Face. Please check your internet connection or use a mirror."
          }
          Result.failure(
            Data.Builder().putString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE, errorMessage).build()
          )
        }
      }
    }
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    // Initial progress is 0
    return createForegroundInfo(0)
  }

  /**
   * Creates a [ForegroundInfo] object for the download worker's ongoing notification. This
   * notification is used to keep the worker running in the foreground, indicating to the user that
   * an active download is in progress.
   */
  private fun createForegroundInfo(progress: Int, modelName: String? = null): ForegroundInfo {
    // Create a notification for the foreground service
    var title = "Downloading model"
    if (modelName != null) {
      title = "Downloading \"$modelName\""
    }
    val content = "Downloading in progress: $progress%"

    val intent =
      Intent(applicationContext, Class.forName("com.google.ai.edge.gallery.MainActivity")).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    val pendingIntent =
      PendingIntent.getActivity(
        applicationContext,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    val notification =
      NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(content)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true) // Makes the notification non-dismissable
        .setProgress(100, progress, false) // Show progress
        .setContentIntent(pendingIntent)
        .build()

    return ForegroundInfo(
      notificationId,
      notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }
}
