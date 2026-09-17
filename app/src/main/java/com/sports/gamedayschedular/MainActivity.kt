package com.sports.gamedayschedular

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sports.gamedayschedular.data.model.*
import com.sports.gamedayschedular.data.repository.FirestoreRepository
import com.sports.gamedayschedular.databinding.ActivityMainBinding
import com.sports.gamedayschedular.util.PrinterManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repository = FirestoreRepository()
    private lateinit var printerManager: PrinterManager
    private val printMutex = Mutex()
    private val processingJobIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        printerManager = PrinterManager(this)
        startSystemListeners()
    }

    /**
     * Re-enabled global listeners for the full chain:
     * Referee App -> Cloud (Firebase) -> GDS (Local Monitor) -> Local Printer
     */
    private fun startSystemListeners() {
        lifecycleScope.launch {
            val sharedPref = getSharedPreferences("GDS_PREFS", MODE_PRIVATE)
            
            var orgId: String? = null
            while (orgId == null) {
                orgId = sharedPref.getString("selected_org_id", null)
                if (orgId == null) {
                    Log.d("GDS_SYSTEM", "Waiting for organization selection...")
                    delay(5000L)
                }
            }

            Log.d("GDS_SYSTEM", "Starting listeners for Organization: $orgId")

            // 1. Listen for new Print Jobs arriving from the Cloud
            @OptIn(ExperimentalCoroutinesApi::class)
            launch {
                repository.getPrintJobs(orgId!!).collect { jobs ->
                    Log.d("GDS_PRINTER", "PrintJobs flow emitted: ${jobs.size} jobs found")
                    if (jobs.isNotEmpty()) {
                        handlePrintQueue(jobs, orgId!!)
                    }
                }
            }

            // 2. Monitoring Status & Sync Code Watchdog
            @OptIn(ExperimentalCoroutinesApi::class)
            launch {
                repository.getOrganizationFlow(orgId!!).collectLatest { org ->
                    if (org?.printerStatus == "PRINTING") {
                        delay(60000)
                        Log.w("GDS_WATCHDOG", "Printing status stale. Forcing IDLE reset.")
                        repository.resetOrganizationStatus(orgId!!)
                    }
                }
            }
        }
    }

    /**
     * Processes print jobs one-by-one and sends them to the local network printer.
     */
    private suspend fun handlePrintQueue(jobs: List<PrintJob>, orgId: String) {
        printMutex.withLock {
            try {
                val org = repository.getOrganization(orgId) ?: return
                val printerIp = org.printerIp
                
                if (printerIp.isEmpty()) {
                    Log.e("GDS_PRINTER", "No local printer IP configured for $orgId")
                    repository.resetOrganizationStatus(orgId)
                    return
                }

                jobs.forEach { job ->
                    Log.d("GDS_PRINTER", "Found job ${job.id} for referee ${job.refereeId}")
                    if (job.id in processingJobIds) {
                        Log.d("GDS_PRINTER", "Job ${job.id} already being processed")
                        return@forEach
                    }
                    processingJobIds.add(job.id)

                    try {
                        // 15s safety timeout for the network handshake + print
                        withTimeout(15000) {
                            Log.d("GDS_PRINTER", "Processing job ${job.id} locally...")
                            repository.updatePrintJobStatus(job.id, "Processing")
                            
                            val success = printerManager.print(job, printerIp, org.printerSettings)
                            
                            if (success) {
                                repository.updatePrintJobStatus(job.id, "Completed")
                            } else {
                                repository.updatePrintJobStatus(job.id, "Failed")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GDS_PRINTER", "Critical failure processing job ${job.id}: ${e.message}")
                        repository.updatePrintJobStatus(job.id, "Failed")
                    } finally {
                        processingJobIds.remove(job.id)
                    }
                    delay(500)
                }
            } finally {
                // Return to IDLE so GDS can rotate the sync code for the next person
                repository.resetOrganizationStatus(orgId)
            }
        }
    }
}
