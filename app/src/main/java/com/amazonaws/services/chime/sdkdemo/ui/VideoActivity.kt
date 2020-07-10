package com.amazonaws.services.chime.sdkdemo.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoFacade
import com.amazonaws.services.chime.sdk.meetings.session.*
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.amazonaws.services.chime.sdkdemo.*
import com.amazonaws.services.chime.sdkdemo.data.JoinMeetingResponse
import com.google.gson.Gson

class VideoActivity : AppCompatActivity(),VideoFragment.RosterViewEventListener {

    private val logger = ConsoleLogger(LogLevel.DEBUG)
    private val gson = Gson()
    private val meetingSessionModel: MeetingSessionModel by lazy { ViewModelProvider(this)[MeetingSessionModel::class.java] }
    private lateinit var meetingId: String
    private lateinit var name: String
    private val TAG = "InMeetingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_root)

        meetingId = intent.getStringExtra(LoginActivity.MEETING_ID_KEY) as String
        name = intent.getStringExtra(LoginActivity.NAME_KEY) as String

        if (savedInstanceState == null) {
            val meetingResponseJson =
                intent.getStringExtra(LoginActivity.MEETING_RESPONSE_KEY) as String
            val sessionConfig = createSessionConfiguration(meetingResponseJson)
            val meetingSession = sessionConfig?.let {
                logger.info(TAG, "Creating meeting session for meeting Id: $meetingId")
                DefaultMeetingSession(
                    it,
                    logger,
                    applicationContext
                )
            }

            if (meetingSession == null) {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.user_notification_meeting_start_error),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } else {
                meetingSessionModel.setMeetingSession(meetingSession)
            }

            val videoViewFragment = VideoFragment.newInstance(meetingId)
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.root_layout, videoViewFragment, "rosterViewFragment")
                .commit()
        }
    }

    private fun createSessionConfiguration(response: String?): MeetingSessionConfiguration? {
        if (response.isNullOrBlank()) return null

        return try {
            val joinMeetingResponse = gson.fromJson(response, JoinMeetingResponse::class.java)
            MeetingSessionConfiguration(
                CreateMeetingResponse(joinMeetingResponse.joinInfo.meetingResponse.meeting),
                CreateAttendeeResponse(joinMeetingResponse.joinInfo.attendeeResponse.attendee),
                ::urlRewriter
            )
        } catch (exception: Exception) {
            logger.error(
                TAG,
                "Error creating session configuration: ${exception.localizedMessage}"
            )
            null
        }
    }

    private fun urlRewriter(url: String): String {
        // You can change urls by url.replace("example.com", "my.example.com")
        return url
    }

    fun getAudioVideo(): AudioVideoFacade = meetingSessionModel.audioVideo

    override fun onLeaveMeeting() {
       onBackPressed()
    }

    override fun onBackPressed() {
        meetingSessionModel.audioVideo.stop()
        super.onBackPressed()
    }

    class MeetingSessionModel : ViewModel() {
        private lateinit var meetingSession: MeetingSession

        fun setMeetingSession(meetingSession: MeetingSession) {
            this.meetingSession = meetingSession
        }

        val audioVideo: AudioVideoFacade
            get() = meetingSession.audioVideo
    }
}