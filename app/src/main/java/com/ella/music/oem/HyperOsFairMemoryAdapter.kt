package com.ella.music.oem

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import coil3.imageLoader
import com.ella.music.data.AppLogStore
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.clearArtworkModelMemoryCache
import com.ella.music.ui.player.clearPlayerPaletteMemoryCache

/** Xiaomi HyperOS fair-runtime-memory broadcast integration. */
internal object HyperOsFairMemoryAdapter {
    private const val TAG = "FairMemory"
    private const val ACTION_TRIM = "itgsa.intent.action.TRIM"
    private const val KEY_COMMON = "common"
    private const val KEY_EXTRA = "extra"
    private const val KEY_NOTIFY_TYPE = "notifyType"
    private const val KEY_NOTIFY_ID = "notifyId"
    private const val KEY_REASON = "reason"
    private const val KEY_ACTION = "action"
    private const val KEY_CALLBACK = "callback"
    private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION
    private const val RESULT_SUCCESS = 0

    @Volatile
    private var initialized = false

    fun initialize(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val worker = HandlerThread(TAG).apply { start() }
            // HyperOS sends both pressure and kill notifications through the same TRIM action;
            // `common.action` identifies the actual request. This receiver deliberately remains
            // runtime-registered, as required by Xiaomi's integration contract.
            val registered = runCatching {
                val filter = IntentFilter(ACTION_TRIM)
                val handler = Handler(worker.looper)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    application.registerReceiver(
                        receiver,
                        filter,
                        null,
                        handler,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    application.registerReceiver(receiver, filter, null, handler)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to register HyperOS fair-memory receiver", error)
                AppLogStore.error(application, TAG, "Fair-memory receiver registration failed", error)
                worker.quitSafely()
            }.isSuccess
            if (registered) {
                initialized = true
                Log.i(TAG, "Registered dynamic HyperOS fair-memory receiver: $ACTION_TRIM")
                AppLogStore.info(application, TAG, "Registered HyperOS fair-memory receiver")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_TRIM) return
            val common = intent.extras?.getBundle(KEY_COMMON) ?: return
            val callback = common.getBinder(KEY_CALLBACK) ?: run {
                Log.w(TAG, "Fair-memory callback binder missing")
                return
            }
            val notifyType = common.getInt(KEY_NOTIFY_TYPE)
            val notifyId = common.getInt(KEY_NOTIFY_ID)
            val declaredAction = common.getString(KEY_ACTION).orEmpty()
            val reason = common.getString(KEY_REASON).orEmpty()
            val isKill = declaredAction.contains("KILL", ignoreCase = true)
            val extra = intent.extras?.getBundle(KEY_EXTRA)

            val result = runCatching {
                releaseMemory(context.applicationContext)
                checkpointKillRequest(context.applicationContext, isKill, notifyType, notifyId, reason)
                RESULT_SUCCESS
            }.onFailure { error ->
                Log.e(TAG, "Failed to handle fair-memory request", error)
                AppLogStore.error(context, TAG, "Fair-memory handling failed", error)
            }.getOrDefault(1)

            Log.i(
                TAG,
                "Handled ${if (isKill) "KILL" else "TRIM"} type=$notifyType id=$notifyId " +
                    "pss=${extra?.getInt("pss", -1)} limit=${extra?.getInt("pssLimit", -1)} " +
                    "heap=${extra?.getInt("heapAlloc", -1)}/${extra?.getInt("heapCapacity", -1)}"
            )
            reply(callback, notifyType, notifyId, result, isKill)
        }
    }

    private fun releaseMemory(context: Context) {
        MusicRepository.clearMemoryCachesIfInitialized()
        clearArtworkModelMemoryCache()
        clearPlayerPaletteMemoryCache()
        runCatching { context.imageLoader.memoryCache?.clear() }
            .onFailure { Log.w(TAG, "Failed to clear Coil memory cache", it) }
    }

    private fun checkpointKillRequest(
        context: Context,
        isKill: Boolean,
        notifyType: Int,
        notifyId: Int,
        reason: String
    ) {
        if (!isKill) return
        // Playback queue/state, settings and library state are already persisted continuously.
        // Commit a synchronous checkpoint for diagnostics before HyperOS' three-second deadline.
        context.getSharedPreferences("hyperos_fair_memory", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_kill_request_at", System.currentTimeMillis())
            .putInt("last_notify_type", notifyType)
            .putInt("last_notify_id", notifyId)
            .putString("last_reason", reason)
            .commit()
    }

    private fun reply(
        callback: IBinder,
        notifyType: Int,
        notifyId: Int,
        result: Int,
        isKill: Boolean
    ) {
        val data = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(result)
            data.writeBundle(Bundle().apply {
                putString("reply", if (isKill) "state_checkpointed" else "memory_released")
            })
            callback.transact(
                TRANSACTION_EXCEPTION_REPLY,
                data,
                null,
                IBinder.FLAG_ONEWAY
            )
        } catch (error: Exception) {
            Log.e(TAG, "Fair-memory callback failed", error)
        } finally {
            data.recycle()
        }
    }
}
