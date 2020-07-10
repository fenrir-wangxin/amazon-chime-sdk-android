package com.amazonaws.services.chime.sdkdemo.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.amazonaws.services.chime.sdk.meetings.audiovideo.*
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerdetector.ActiveSpeakerObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerpolicy.DefaultActiveSpeakerPolicy
import com.amazonaws.services.chime.sdk.meetings.audiovideo.metric.MetricsObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.metric.ObservableMetric
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.DefaultVideoRenderView
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoPauseState
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import com.amazonaws.services.chime.sdk.meetings.device.DeviceChangeObserver
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.device.MediaDeviceType
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatus
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatusCode
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.amazonaws.services.chime.sdkdemo.*
import com.amazonaws.services.chime.sdkdemo.data.RosterAttendee
import com.amazonaws.services.chime.sdkdemo.data.VideoCollectionTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


class VideoFragment : Fragment(),
    RealtimeObserver, AudioVideoObserver, VideoTileObserver,
    MetricsObserver, ActiveSpeakerObserver , DeviceChangeObserver {
    private val logger = ConsoleLogger(LogLevel.DEBUG)
    private val mutex = Mutex()
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private val rosterViewModel: RosterViewModel by lazy { ViewModelProvider(this)[RosterViewModel::class.java] }

    private lateinit var meetingId: String
    private lateinit var audioVideo: AudioVideoFacade
    private val audioDevices = mutableListOf<MediaDevice>()
    private lateinit var adapterSpeaker: ArrayAdapter<MediaDevice>
    private lateinit var listener: RosterViewEventListener
    override val scoreCallbackIntervalMs: Int? get() = 1000

    private val MAX_TILE_COUNT = 4
    private val LOCAL_TILE_ID = 0
    private val WEBRTC_PERMISSION_REQUEST_CODE = 1
    private val TAG = "VideoFragment"

    // Check if attendee Id contains this at the end to identify content share
    private val CONTENT_DELIMITER = "#content"

    // Append to attendee name if it's for content share
    private val CONTENT_NAME_SUFFIX = "<<Content>>"

    private val WEBRTC_PERM = arrayOf(
        Manifest.permission.CAMERA
    )

    private lateinit var buttonMute: ImageView
    private lateinit var buttonVideo: ImageView
    private lateinit var showVideo: DefaultVideoRenderView
    private lateinit var showOtherVideo: DefaultVideoRenderView
    private lateinit var frameLayout: FrameLayout
    private var clickVideoButton: Boolean = false

    companion object {
        fun newInstance(meetingId: String): VideoFragment {
            val fragment = VideoFragment()

            fragment.arguments =
                Bundle().apply { putString(LoginActivity.MEETING_ID_KEY, meetingId) }
            return fragment
        }
    }

    interface RosterViewEventListener {
        fun onLeaveMeeting()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is RosterViewEventListener) {
            listener = context
        } else {
            logger.error(TAG, "$context must implement RosterViewEventListener.")
            throw ClassCastException("$context must implement RosterViewEventListener.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view: View = inflater.inflate(R.layout.activity_video, container, false)
        val activity = activity as Context

        meetingId = arguments?.getString(LoginActivity.MEETING_ID_KEY) as String
        audioVideo = (activity as VideoActivity).getAudioVideo()

        buttonMute = view.findViewById(R.id.button_speaker)
        buttonMute.setImageResource(if (rosterViewModel.isMuted) R.drawable.ic_no_voice else R.drawable.ic_ic_microphone)
        buttonMute.setOnClickListener { toggleMuteMeeting() }

        buttonVideo = view.findViewById(R.id.button_video)
        buttonVideo.setImageResource(if (rosterViewModel.isCameraOn) R.drawable.ic_ic_video_call_highlight else R.drawable.ic_ic_video_call)
        buttonVideo.setOnClickListener {
            clickVideoButton = true
            toggleVideo()
        }

        adapterSpeaker = createSpinnerAdapter(context!!, audioDevices)
        view.findViewById<ImageView>(R.id.button_leave)?.setOnClickListener { listener.onLeaveMeeting() }
        frameLayout = view.findViewById(R.id.frame_layout)

        view.findViewById<ImageButton>(R.id.change_camera_button)?.setOnClickListener { audioVideo.switchCamera() }

        setupSubTabs(view)
        subscribeToAttendeeChangeHandlers()
        audioVideo.start()
        audioVideo.addDeviceChangeObserver(this)
        toggleVideo()
        uiScope.launch {
            populateDeviceList(listAudioDevices())
        }
        return view
    }


    private suspend fun listAudioDevices(): List<MediaDevice> {
        return withContext(Dispatchers.Default) {
            audioVideo.listAudioDevices()
        }
    }

    private fun setupSubTabs(view1: View) {
        showVideo = view1.findViewById(R.id.video_fragment_surface)
        showOtherVideo = view1.findViewById(R.id.video_surface1)
        showOtherVideo.setZOrderOnTop(true)
    }


    override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) {
        uiScope.launch {
            mutex.withLock {
                volumeUpdates.forEach { (attendeeInfo, volumeLevel) ->
                    rosterViewModel.currentRoster[attendeeInfo.attendeeId]?.let {
                        rosterViewModel.currentRoster[attendeeInfo.attendeeId] =
                            RosterAttendee(
                                it.attendeeId,
                                it.attendeeName,
                                volumeLevel,
                                it.signalStrength,
                                it.isActiveSpeaker
                            )
                    }
                }

//                rosterAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) {
        uiScope.launch {
            mutex.withLock {
                signalUpdates.forEach { (attendeeInfo, signalStrength) ->
                    rosterViewModel.currentRoster[attendeeInfo.attendeeId]?.let {
                        rosterViewModel.currentRoster[attendeeInfo.attendeeId] =
                            RosterAttendee(
                                it.attendeeId,
                                it.attendeeName,
                                it.volumeLevel,
                                signalStrength,
                                it.isActiveSpeaker
                            )
                    }
                }

                //rosterAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {
                attendeeInfo.forEach { (attendeeId, externalUserId) ->
                    rosterViewModel.currentRoster.getOrPut(
                        attendeeId,
                        {
                            RosterAttendee(
                                attendeeId,
                                getAttendeeName(attendeeId, externalUserId)
                            )
                        })
                }
                audioVideo.startRemoteVideo()
                // rosterAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {
                attendeeInfo.forEach { (attendeeId, _) ->
                    rosterViewModel.currentRoster.remove(attendeeId)
                }
                listener.onLeaveMeeting()
                //rosterAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) {
        attendeeInfo.forEach { (_, externalUserId) ->
            notify("$externalUserId dropped")
        }

        uiScope.launch {
            mutex.withLock {
                attendeeInfo.forEach { (attendeeId, _) -> rosterViewModel.currentRoster.remove(attendeeId) }

                //rosterAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) {
        attendeeInfo.forEach { (attendeeId, externalUserId) ->
            logger.info(
                TAG,
                "Attendee with attendeeId $attendeeId and externalUserId $externalUserId muted"
            )
        }
    }

    override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) {
        attendeeInfo.forEach { (attendeeId, externalUserId) ->
            logger.info(
                TAG,
                "Attendee with attendeeId $attendeeId and externalUserId $externalUserId unmuted"
            )
        }
    }

    override fun onActiveSpeakerDetected(attendeeInfo: Array<AttendeeInfo>) {
        uiScope.launch {
            mutex.withLock {
                var needUpdate = false
                val activeSpeakers = attendeeInfo.map { it.attendeeId }.toSet()
                rosterViewModel.currentRoster.values.forEach { attendee ->
                    if (activeSpeakers.contains(attendee.attendeeId) != attendee.isActiveSpeaker) {
                        rosterViewModel.currentRoster[attendee.attendeeId] =
                            RosterAttendee(
                                attendee.attendeeId,
                                attendee.attendeeName,
                                attendee.volumeLevel,
                                attendee.signalStrength,
                                !attendee.isActiveSpeaker
                            )
                        needUpdate = true
                    }
                }

                if (needUpdate) {
                    // rosterAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onActiveSpeakerScoreChanged(scores: Map<AttendeeInfo, Double>) {
        logger.debug(TAG, "Active Speakers scores are: $scores")
    }

    private fun getAttendeeName(attendeeId: String, externalUserId: String): String {
        val attendeeName = externalUserId.split('#')[1]

        return if (attendeeId.endsWith(CONTENT_DELIMITER)) {
            "$attendeeName $CONTENT_NAME_SUFFIX"
        } else {
            attendeeName
        }
    }

    private fun toggleMuteMeeting() {
        if (rosterViewModel.isMuted) unmuteMeeting() else muteMeeting()
        rosterViewModel.isMuted = !rosterViewModel.isMuted
    }

    private fun muteMeeting() {
        audioVideo.realtimeLocalMute()
        buttonMute.setImageResource(R.drawable.ic_no_voice)
    }

    private fun unmuteMeeting() {
        audioVideo.realtimeLocalUnmute()
        buttonMute.setImageResource(R.drawable.ic_ic_microphone)
    }

    private fun toggleVideo() {
        if (rosterViewModel.isCameraOn) stopCamera() else startCamera()
        rosterViewModel.isCameraOn = !rosterViewModel.isCameraOn
    }

    private fun startCamera() {
        if (hasPermissionsAlready()) {
            startLocalVideo()
        } else {
            requestPermissions(
                WEBRTC_PERM,
                WEBRTC_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startLocalVideo() {
        audioVideo.startLocalVideo()
        buttonVideo.setImageResource(R.drawable.ic_ic_video_call_highlight)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            WEBRTC_PERMISSION_REQUEST_CODE -> {
                val isMissingPermission: Boolean = grantResults.isEmpty() || grantResults.any { PackageManager.PERMISSION_GRANTED != it }

                if (isMissingPermission) {
                    Toast.makeText(context!!, getString(R.string.user_notification_permission_error), Toast.LENGTH_SHORT).show()
                } else {
                    startLocalVideo()
                }
                return
            }
        }
    }

    private fun hasPermissionsAlready(): Boolean {
        return WEBRTC_PERM.all {
            ContextCompat.checkSelfPermission(context!!, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun stopCamera() {
        audioVideo.stopLocalVideo()
        buttonVideo.setImageResource(R.drawable.ic_ic_video_call)
    }



    private fun showVideoTile(tileState: VideoTileState) {
        if (tileState.isContent) {
            rosterViewModel.currentScreenTiles[tileState.tileId] = createVideoCollectionTile(tileState)
        } else {
            rosterViewModel.currentVideoTiles[tileState.tileId] = createVideoCollectionTile(tileState)

            if (clickVideoButton) {
                clickVideoButton = false
                if (rosterViewModel.currentVideoTiles.values.size > 1) {
                    if (tileState.tileId == 0) {
                        frameLayout.visibility = View.VISIBLE
                        showOtherVideo.setZOrderOnTop(true)
                        frameLayout.getChildAt(0).visibility = View.VISIBLE
                        audioVideo.bindVideoView(showOtherVideo, 0)
                    } else {
                        showOtherVideo.setZOrderOnTop(true)
                        showVideo.visibility = View.VISIBLE
                        audioVideo.bindVideoView(showVideo, rosterViewModel.currentVideoTiles.values.elementAt(1).videoTileState.tileId)
                    }
                } else {
                    showVideo.visibility = View.VISIBLE
                    audioVideo.bindVideoView(showVideo,tileState.tileId)
                }
                return
            }
            if (rosterViewModel.currentVideoTiles.values.size > 1) {
                showOtherVideo.setZOrderOnTop(true)
                frameLayout.visibility = View.VISIBLE
                frameLayout.getChildAt(0).visibility = View.VISIBLE
                showVideo.visibility = View.VISIBLE
                audioVideo.bindVideoView(showVideo, rosterViewModel.currentVideoTiles.values.elementAt(1).videoTileState.tileId)
                audioVideo.bindVideoView(showOtherVideo, 0)
            } else {
                showVideo.visibility = View.VISIBLE
                audioVideo.bindVideoView(showVideo,tileState.tileId)
            }

//            videoTileAdapter.notifyDataSetChanged()
        }
    }

    private fun canShowMoreRemoteVideoTile(): Boolean {
        // Current max amount of tiles should preserve one spot for local video
        val currentMax = if (rosterViewModel.currentVideoTiles.containsKey(LOCAL_TILE_ID)) MAX_TILE_COUNT else MAX_TILE_COUNT - 1
        return rosterViewModel.currentVideoTiles.size < currentMax
    }

    private fun canShowMoreRemoteScreenTile(): Boolean {
        // only show 1 screen share tile
        return rosterViewModel.currentScreenTiles.isEmpty()
    }

    private fun createVideoCollectionTile(tileState: VideoTileState): VideoCollectionTile {
        val attendeeId = tileState.attendeeId
        attendeeId?.let {
            val attendeeName = rosterViewModel.currentRoster[attendeeId]?.attendeeName ?: ""
            return VideoCollectionTile(attendeeName, tileState)
        }

        return VideoCollectionTile("", tileState)
    }

    override fun onDestroy() {
        super.onDestroy()
        unsubscribeFromAttendeeChangeHandlers()
    }

    override fun onAudioSessionStartedConnecting(reconnecting: Boolean) =
        notify("Audio started connecting. reconnecting: $reconnecting")

    override fun onAudioSessionStarted(reconnecting: Boolean) =
        notify("Audio successfully started. reconnecting: $reconnecting")

    override fun onAudioSessionDropped() {
        notify("Audio session dropped")
    }

    override fun onAudioSessionStopped(sessionStatus: MeetingSessionStatus) {
        notify("Audio stopped for reason: ${sessionStatus.statusCode}")
        if (sessionStatus.statusCode != MeetingSessionStatusCode.OK) {
            listener.onLeaveMeeting()
        }
    }

    override fun onAudioSessionCancelledReconnect() = notify("Audio cancelled reconnecting")

    override fun onConnectionRecovered() = notify("Connection quality has recovered")

    override fun onConnectionBecamePoor() = notify("Connection quality has become poor")

    override fun onVideoSessionStartedConnecting() = notify("Video started connecting.")

    override fun onVideoSessionStarted(sessionStatus: MeetingSessionStatus) {
        if (sessionStatus.statusCode == MeetingSessionStatusCode.VideoAtCapacityViewOnly) {
            notify("Video encountered an error: ${sessionStatus.statusCode}")
        } else {
            notify("Video successfully started: ${sessionStatus.statusCode}")
        }
    }

    override fun onVideoSessionStopped(sessionStatus: MeetingSessionStatus) =
        notify("Video stopped for reason: ${sessionStatus.statusCode}")

    override fun onVideoTileAdded(tileState: VideoTileState) {
        uiScope.launch {
            logger.info(TAG, "Video track added, titleId: ${tileState.tileId}, attendeeId: ${tileState.attendeeId}" + ", isContent ${tileState.isContent}")

            if (tileState.isContent) {
                if (!rosterViewModel.currentScreenTiles.containsKey(tileState.tileId) && canShowMoreRemoteScreenTile()) {
                    showVideoTile(tileState)
                }
            } else {
                // For local video, should show it anyway
//                if (tileState.tileId == 0) {
//                    if (rosterViewModel.currentVideoTiles.values.size > 1) {
//                        frameLayout.visibility = View.VISIBLE
//                    }
//                } else {
//                    showVideo.visibility = View.VISIBLE
//                }
                if (tileState.isLocalTile) {
                    showVideoTile(tileState)
                } else if (!rosterViewModel.currentVideoTiles.containsKey(tileState.tileId)) {
                    if (canShowMoreRemoteVideoTile()) {
                        showVideoTile(tileState)
                    } else {
                        rosterViewModel.nextVideoTiles[tileState.tileId] = createVideoCollectionTile(tileState)
                    }
                }
            }
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        uiScope.launch {
            val tileId: Int = tileState.tileId

//            logger.info(TAG, "Video track removed, titleId: $tileId, attendeeId: ${tileState.attendeeId}")
            clickVideoButton = true
            if (rosterViewModel.currentVideoTiles.values.size >1 ) {
                if (tileState.tileId == 0) {
                    frameLayout.getChildAt(0).visibility = View.INVISIBLE
                    frameLayout.visibility = View.INVISIBLE
                } else {
                    showVideo.visibility = View.INVISIBLE
                }
            } else {
                showVideo.visibility = View.INVISIBLE
                if (tileState.tileId == 0) {
                    frameLayout.getChildAt(0).visibility = View.INVISIBLE
                    frameLayout.visibility = View.INVISIBLE
                }
            }
//            if (tileState.tileId == 0) {
//                frameLayout.getChildAt(0).visibility = View.INVISIBLE
//                frameLayout.visibility = View.INVISIBLE
//            } else {
//                showVideo.visibility = View.INVISIBLE
//            }
            audioVideo.unbindVideoView(tileId)
            if (rosterViewModel.currentVideoTiles.containsKey(tileId)) {
                rosterViewModel.currentVideoTiles.remove(tileId)
                if (rosterViewModel.currentVideoTiles.isNullOrEmpty()) {
                    clickVideoButton = false
                }
                // Show next video tileState if available
                if (rosterViewModel.nextVideoTiles.isNotEmpty() && canShowMoreRemoteVideoTile()) {
                    val nextTileState: VideoTileState =
                        rosterViewModel.nextVideoTiles.entries.iterator().next().value.videoTileState
                    showVideoTile(nextTileState)
                    rosterViewModel.nextVideoTiles.remove(nextTileState.tileId)
                }
//                videoTileAdapter.notifyDataSetChanged()
                notify("")
            } else if (rosterViewModel.nextVideoTiles.containsKey(tileId)) {
                rosterViewModel.nextVideoTiles.remove(tileId)
            } else if (rosterViewModel.currentScreenTiles.containsKey(tileId)) {
                rosterViewModel.currentScreenTiles.remove(tileId)
                //screenTileAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {
        if (tileState.pauseState == VideoPauseState.PausedForPoorConnection) {
            val attendeeName = rosterViewModel.currentRoster[tileState.attendeeId]?.attendeeName ?: ""
            notify(
                "Video for attendee $attendeeName " +
                        " has been paused for poor network connection," +
                        " video will automatically resume when connection improves"
            )
        }
    }

    override fun onVideoTileResumed(tileState: VideoTileState) {
        val attendeeName = rosterViewModel.currentRoster[tileState.attendeeId]?.attendeeName ?: ""
        notify("Video for attendee $attendeeName has been unpaused")
    }

    override fun onMetricsReceived(metrics: Map<ObservableMetric, Any>) {
        logger.debug(TAG, "Media metrics received: $metrics")
    }

    private fun notify(message: String) {
        uiScope.launch {
            if (message.isBlank()) {
                return@launch
            }
            activity?.let {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
            logger.info(TAG, message)
        }
    }

    private fun subscribeToAttendeeChangeHandlers() {
        audioVideo.addAudioVideoObserver(this)
        audioVideo.addMetricsObserver(this)
        audioVideo.addRealtimeObserver(this)
        audioVideo.addVideoTileObserver(this)
        audioVideo.addActiveSpeakerObserver(DefaultActiveSpeakerPolicy(), this)
    }

    private fun unsubscribeFromAttendeeChangeHandlers() {
        audioVideo.removeAudioVideoObserver(this)
        audioVideo.removeMetricsObserver(this)
        audioVideo.removeRealtimeObserver(this)
        audioVideo.removeVideoTileObserver(this)
        audioVideo.removeActiveSpeakerObserver(this)
    }

    class RosterViewModel : ViewModel() {
        val currentRoster = mutableMapOf<String, RosterAttendee>()
        val currentVideoTiles = mutableMapOf<Int, VideoCollectionTile>()
        val currentScreenTiles = mutableMapOf<Int, VideoCollectionTile>()
        val nextVideoTiles = LinkedHashMap<Int, VideoCollectionTile>()
        var isMuted = false
        var isCameraOn = false
    }

    override fun onAudioDeviceChanged(freshAudioDeviceList: List<MediaDevice>) {
        populateDeviceList(freshAudioDeviceList)
    }

    private fun populateDeviceList(freshAudioDeviceList: List<MediaDevice>) {
        audioDevices.clear()
        audioDevices.addAll(
            freshAudioDeviceList.filter {
                it.type != MediaDeviceType.OTHER
            }.sortedBy { it.order }
        )
        adapterSpeaker.notifyDataSetChanged()
        if (audioDevices.isNotEmpty()) {
            audioVideo.chooseAudioDevice(audioDevices[0])
        }
    }

    private fun createSpinnerAdapter(
        context: Context,
        list: List<MediaDevice>
    ): ArrayAdapter<MediaDevice> {
        return ArrayAdapter(context, android.R.layout.simple_spinner_item, list)
    }
}