package com.imaginit.hyperplux.models;

import java.util.Date;

/**
 * Model class representing a chat message
 */
public class ChatMessage {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_ASSET = "asset";
    public static final String TYPE_CALL_REQUEST = "call_request";
    public static final String TYPE_CALL_INFO = "call_info";

    private String id;
    private String senderId;
    private String type;
    private String text;
    private String imageUrl;
    private String assetId;
    private Date timestamp;
    private boolean read;

    // Call-related fields
    private boolean isVideoCall;
    private String callStatus;
    private String callDuration;
    private String callId;

    public ChatMessage() {
        // Required empty constructor for Firestore
        this.timestamp = new Date();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isVideoCall() {
        return isVideoCall;
    }

    public void setVideoCall(boolean videoCall) {
        isVideoCall = videoCall;
    }

    public String getCallStatus() {
        return callStatus;
    }

    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }

    public String getCallDuration() {
        return callDuration;
    }

    public void setCallDuration(String callDuration) {
        this.callDuration = callDuration;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    /**
     * Create a text message
     */
    public static ChatMessage createTextMessage(String senderId, String text) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setType(TYPE_TEXT);
        message.setText(text);
        message.setTimestamp(new Date());
        return message;
    }

    /**
     * Create an image message
     */
    public static ChatMessage createImageMessage(String senderId, String imageUrl) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setType(TYPE_IMAGE);
        message.setImageUrl(imageUrl);
        message.setTimestamp(new Date());
        return message;
    }

    /**
     * Create an asset message
     */
    public static ChatMessage createAssetMessage(String senderId, String assetId) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setType(TYPE_ASSET);
        message.setAssetId(assetId);
        message.setTimestamp(new Date());
        return message;
    }

    /**
     * Create a call request message
     */
    public static ChatMessage createCallRequestMessage(String senderId, boolean isVideoCall, String callId) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setType(TYPE_CALL_REQUEST);
        message.setVideoCall(isVideoCall);
        message.setCallId(callId);
        message.setCallStatus("requesting");
        message.setTimestamp(new Date());
        return message;
    }

    /**
     * Create a call info message
     */
    public static ChatMessage createCallInfoMessage(String senderId, boolean isVideoCall,
                                                    String callStatus, String callDuration) {
        ChatMessage message = new ChatMessage();
        message.setSenderId(senderId);
        message.setType(TYPE_CALL_INFO);
        message.setVideoCall(isVideoCall);
        message.setCallStatus(callStatus);
        message.setCallDuration(callDuration);
        message.setTimestamp(new Date());
        return message;
    }
}