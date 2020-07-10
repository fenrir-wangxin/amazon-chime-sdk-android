package com.amazonaws.services.chime.sdkdemo.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.LayoutDirection
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.ContentLoadingProgressBar
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.amazonaws.services.chime.sdkdemo.R
import com.amazonaws.services.chime.sdkdemo.encodeURLParam
import com.amazonaws.services.chime.sdkdemo.util.Exclusive
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {
    private val WEBRTC_PERM = arrayOf(
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.RECORD_AUDIO
    )

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val WEBRTC_PERMISSION_REQUEST_CODE = 1
    private val MEETING_REGION = "us-east-1"
    private val TAG = "LoginActivity"
    private val logger = ConsoleLogger(LogLevel.INFO)
    private var loginButton: Button? = null
    private var doctorCheck: AppCompatRadioButton ?= null
    private var illnessCheck: AppCompatRadioButton ?= null
    private var processDialog: ContentLoadingProgressBar ?= null
    var personType: Int = -1
    private val uiScope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        loginButton = findViewById(R.id.login)
        doctorCheck = findViewById(R.id.doctor_check_button)
        illnessCheck = findViewById(R.id.illness_check_button)
        processDialog = findViewById(R.id.bar_processing)
        doctorCheck?.layoutDirection = LayoutDirection.RTL
        illnessCheck?.layoutDirection = LayoutDirection.RTL
        doctorCheck?.setOnCheckedChangeListener { _, boolean ->
            if (boolean) {
                illnessCheck?.isChecked = false
                loginButton?.isEnabled = true
                personType = PersonType.DOCTOR_PERSON.ordinal
            }
        }
        illnessCheck?.setOnCheckedChangeListener { _, boolean ->
            if (boolean) {
                doctorCheck?.isChecked = false
                loginButton?.isEnabled = true
                personType = PersonType.ILLNESS_PERSON.ordinal
            }
        }

        loginButton?.setOnClickListener {
            Exclusive.Normal().tap {
                when(personType) {
                    PersonType.DOCTOR_PERSON.ordinal -> {
                        processDialog?.visibility = View.VISIBLE
                        processDialog?.show()
                        joinMeeting()
                    }
                    PersonType.ILLNESS_PERSON.ordinal -> {
                        val intent = Intent(this, PatientActivity::class.java)
                        startActivity(intent)
                    }
                }
            }

        }

    }

    private fun joinMeeting() {
        if (hasPermissionsAlready()) {
            authenticate(getString(R.string.test_url), getString(R.string.attented_id), "Doctor")
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
            val intent = Intent(applicationContext, VideoActivity::class.java)

            if (meetingResponseJson == null) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.user_notification_meeting_start_error),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                intent.putExtra(MEETING_RESPONSE_KEY, meetingResponseJson)
                intent.putExtra(MEETING_ID_KEY, meetingId)
                intent.putExtra(NAME_KEY, attendeeName)
                startActivity(intent)
            }
        }

    companion object {
        enum class PersonType {
            DOCTOR_PERSON,
            ILLNESS_PERSON
        }

        const val MEETING_RESPONSE_KEY = "MEETING_RESPONSE"
        const val MEETING_ID_KEY = "MEETING_ID"
        const val NAME_KEY = "NAME"
    }
}