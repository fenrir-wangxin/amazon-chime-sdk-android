package com.amazonaws.services.chime.sdkdemo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.ContentLoadingProgressBar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.amazonaws.services.chime.sdkdemo.R
import com.amazonaws.services.chime.sdkdemo.data.DoctorItem
import com.amazonaws.services.chime.sdkdemo.encodeURLParam
import com.amazonaws.services.chime.sdkdemo.ui.adapter.PatientAdapter
import com.amazonaws.services.chime.sdkdemo.util.Exclusive
import com.amazonaws.services.chime.sdkdemo.util.ItemClickListener
import com.google.android.material.tabs.TabItem
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PatientActivity : AppCompatActivity() {
    private val WEBRTC_PERM = arrayOf(
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.RECORD_AUDIO
    )
    var tabLayout: TabLayout ?= null
    var recyclerView: RecyclerView ?= null
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val WEBRTC_PERMISSION_REQUEST_CODE = 1
    private val MEETING_REGION = "us-east-1"
    private val TAG = "PatientActivity"
    private val logger = ConsoleLogger(LogLevel.INFO)
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var processDialog: ContentLoadingProgressBar?= null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient)
        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerView)
        processDialog = findViewById(R.id.bar_processing)
        init()
    }

    private fun init() {
        tabLayout?.newTab()?.setText("総合")?.let { tabLayout?.addTab(it, 0, true) }
        tabLayout?.newTab()?.setText("小児科")?.let { tabLayout?.addTab(it, 1, false) }
        tabLayout?.newTab()?.setText("産婦人科")?.let { tabLayout?.addTab(it, 2, false) }
        tabLayout?.newTab()?.setText("皮膚科")?.let { tabLayout?.addTab(it, 3, false) }
        tabLayout?.newTab()?.setText("整骨科")?.let { tabLayout?.addTab(it, 4, false) }
        tabLayout?.setTabTextColors(ContextCompat.getColor(this,R.color.color_929AB5), ContextCompat.getColor(this,R.color.colorBlue))

        recyclerView?.layoutManager = GridLayoutManager(this,2)

        val adapter = PatientAdapter(DoctorItem.makeDoctorItemList())
        recyclerView?.adapter = adapter
        adapter.setPatientItemClickListener(ItemClickListener{ _, _, position ->
            Exclusive.Normal().tap {
                processDialog?.visibility = View.VISIBLE
                processDialog?.show()
                joinMeeting()
            }
        })
    }

    private fun joinMeeting() {
        if (hasPermissionsAlready()) {
            authenticate(getString(R.string.test_url), getString(R.string.attented_id), "patient22")
        } else {
            ActivityCompat.requestPermissions(this, WEBRTC_PERM, WEBRTC_PERMISSION_REQUEST_CODE)
        }
    }

    private suspend fun joinMeeting(
        meetingUrl: String,
        meetingId: String?,
        attendeeName: String?
    ): String? {
        return withContext(ioDispatcher) {
            val serverUrl =
                URL(
                    "${meetingUrl}join?title=${encodeURLParam(meetingId)}&name=${encodeURLParam(
                        attendeeName
                    )}&region=${encodeURLParam(MEETING_REGION)}"
                )

            try {
                val response = StringBuffer()
                with(serverUrl.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true

                    BufferedReader(InputStreamReader(inputStream)).use {
                        var inputLine = it.readLine()
                        while (inputLine != null) {
                            response.append(inputLine)
                            inputLine = it.readLine()
                        }
                        it.close()
                    }

                    if (responseCode == 200) {
                        response.toString()
                    } else {
                        logger.error(TAG, "Unable to join meeting. Response code: $responseCode")
                        null
                    }
                }
            } catch (exception: Exception) {
                logger.error(TAG, "There was an exception while joining the meeting: $exception")
                null
            }
        }
    }

    private fun hasPermissionsAlready(): Boolean {
        return WEBRTC_PERM.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun authenticate(
        meetingUrl: String,
        meetingId: String?,
        attendeeName: String?
    ) =
        uiScope.launch {
            logger.info(TAG, "Joining meeting. meetingUrl: $meetingUrl, meetingId: $meetingId, attendeeName: $attendeeName")

            val meetingResponseJson: String? = joinMeeting(meetingUrl, meetingId, attendeeName)
            processDialog?.hide()
            processDialog?.visibility = View.GONE
            if (meetingResponseJson == null) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.user_notification_meeting_start_error),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val intent = Intent(applicationContext, VideoActivity::class.java)
                intent.putExtra(MEETING_RESPONSE_KEY, meetingResponseJson)
                intent.putExtra(MEETING_ID_KEY, meetingId)
                intent.putExtra(NAME_KEY, attendeeName)
                startActivity(intent)
            }
        }


    companion object {
        const val MEETING_RESPONSE_KEY = "MEETING_RESPONSE"
        const val MEETING_ID_KEY = "MEETING_ID"
        const val NAME_KEY = "NAME"
    }
}