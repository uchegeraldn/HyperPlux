package com.imaginit.hyperplux.models;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Model class for call data
 */
public class CallData {
    // Call status enum instead of string constants
    public enum CallStatus {
        RINGING("ringing"),
        ANSWERED("answered"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed"),
        MISSED("missed"),
        DECLINED("declined"),
        ERROR("error");

        private final String value;

        CallStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static CallStatus fromString(String text) {
            for (CallStatus status : CallStatus.values()) {
                if (status.value.equalsIgnoreCase(text)) {
                    return status;
                }
            }
            return ERROR; // Default to ERROR if not recognized
        }
    }

    // Keep the string constants for backward compatibility
    public static final String STATUS_RINGING = CallStatus.RINGING.getValue();
    public static final String STATUS_ANSWERED = CallStatus.ANSWERED.getValue();
    public static final String STATUS_IN_PROGRESS = CallStatus.IN_PROGRESS.getValue();
    public static final String STATUS_COMPLETED = CallStatus.COMPLETED.getValue();
    public static final String STATUS_MISSED = CallStatus.MISSED.getValue();
    public static final String STATUS_DECLINED = CallStatus.DECLINED.getValue();
    public static final String STATUS_ERROR = CallStatus.ERROR.getValue();

    private String callId;
    private String chatRoomId;
    private String callerId;
    private String recipientId;
    private Date startTime;
    private Date answerTime;
    private Date endTime;
    private long duration;
    private CallStatus status; // Changed from String to CallStatus
    private boolean isVideo;

    // WebRTC signaling data
    private String offer;
    private String answer;
    private Map<String, Object> callerCandidates;
    private Map<String, Object> recipientCandidates;

    public CallData() {
        // Required empty constructor for Firebase
        this.callerCandidates = new HashMap<>();
        this.recipientCandidates = new HashMap<>();
        this.status = CallStatus.RINGING; // Default status
    }

    // Update getters and setters for status
    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    // String version for compatibility
    public String getStatusString() {
        return status != null ? status.getValue() : null;
    }

    public void setStatusFromString(String statusString) {
        this.status = CallStatus.fromString(statusString);
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(String chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getAnswerTime() {
        return answerTime;
    }

    public void setAnswerTime(Date answerTime) {
        this.answerTime = answerTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideo(boolean video) {
        isVideo = video;
    }

    public String getOffer() {
        return offer;
    }

    public void setOffer(String offer) {
        this.offer = offer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Map<String, Object> getCallerCandidates() {
        return callerCandidates;
    }

    public void setCallerCandidates(Map<String, Object> callerCandidates) {
        this.callerCandidates = callerCandidates;
    }

    public Map<String, Object> getRecipientCandidates() {
        return recipientCandidates;
    }

    public void setRecipientCandidates(Map<String, Object> recipientCandidates) {
        this.recipientCandidates = recipientCandidates;
    }
}