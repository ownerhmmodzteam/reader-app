package com.hmmodz.sankareader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.*
import com.hmmodz.sankareader.ui.ReaderApp
import com.hmmodz.sankareader.worker.RefreshWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val work = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sanka-periodic-refresh", ExistingPeriodicWorkPolicy.KEEP, work)
        setContent { ReaderApp() }
    }
}
