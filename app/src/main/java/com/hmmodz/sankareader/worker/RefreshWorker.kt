package com.hmmodz.sankareader.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hmmodz.sankareader.data.ApiClient
import com.hmmodz.sankareader.data.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val api = ApiClient()
            val fresh = (api.home() + api.latestAll(page = 1)).distinctBy { it.provider + ":" + it.id }.take(180)
            // One page per documented active provider every six hours; the shared throttle spaces requests.
            applicationContext.getSharedPreferences("sanka_reader", Context.MODE_PRIVATE)
                .edit().putString("home_cache", com.google.gson.Gson().toJson(fresh)).putLong("home_cache_at", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            val status = (e as? com.hmmodz.sankareader.data.ApiException)?.httpCode ?: 0
            if (status == 403 || status == 429) Result.failure() else if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
