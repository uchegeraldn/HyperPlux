package com.imaginit.hyperplux.models;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class representing a chat room between users
 */
public class ChatRoom {
    private String id;
    private List<String> participantIds;
    private Map<String, String> participantNames;
    private Map<String, String> participantPhotos;
    private Date createdAt;
    private Date lastMessageTime;
    private String lastMessage;
    private Map<String, Integer> unreadCounts;
    private boolean hasActiveCall;
    private String activeCallId;

    public ChatRoom() {
        // Required empty constructor for Firestore
        this.createdAt = new Date();
        this.lastMessageTime = new Date();
        this.unreadCounts = new HashMap<>();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    public Map<String, String> getParticipantNames() {
        return participantNames;
    }

    public void setParticipantNames(Map<String, String> participantNames) {
        this.participantNames = participantNames;
    }

    public Map<String, String> getParticipantPhotos() {
        return participantPhotos;
    }

    public void setParticipantPhotos(Map<String, String> participantPhotos) {
        this.participantPhotos = participantPhotos;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Date lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public Map<String, Integer> getUnreadCounts() {
        return unreadCounts;
    }

    public void setUnreadCounts(Map<String, Integer> unreadCounts) {
        this.unreadCounts = unreadCounts;
    }

    public boolean isHasActiveCall() {
        return hasActiveCall;
    }

    public void setHasActiveCall(boolean hasActiveCall) {
        this.hasActiveCall = hasActiveCall;
    }

    public String getActiveCallId() {
        return activeCallId;
    }

    public void setActiveCallId(String activeCallId) {
        this.activeCallId = activeCallId;
    }

    /**
     * Get the other participant's ID (assuming 2-person chat)
     * @param currentUserId The current user's ID
     * @return The other participant's ID or null if not found
     */
    public String getOtherParticipantId(String currentUserId) {
        if (participantIds == null || participantIds.size() != 2) {
            return null;
        }

        return participantIds.get(0).equals(currentUserId) ?
                participantIds.get(1) : participantIds.get(0);
    }

    /**
     * Get the other participant's name (assuming 2-person chat)
     * @param currentUserId The current user's ID
     * @return The other participant's name
     */
    public String getOtherParticipantName(String currentUserId) {
        String otherId = getOtherParticipantId(currentUserId);
        if (otherId == null || participantNames == null) {
            return "Unknown User";
        }

        return participantNames.getOrDefault(otherId, "Unknown User");
    }

    /**
     * Get the other participant's photo URL (assuming 2-person chat)
     * @param currentUserId The current user's ID
     * @return The other participant's photo URL or null if not found
     */
    public String getOtherParticipantPhoto(String currentUserId) {
        String otherId = getOtherParticipantId(currentUserId);
        if (otherId == null || participantPhotos == null) {
            return null;
        }

        return participantPhotos.get(otherId);
    }

    /**
     * Get unread count for a specific user
     * @param userId The user ID
     * @return The unread count or 0 if not found
     */
    public int getUnreadCountForUser(String userId) {
        if (unreadCounts == null || !unreadCounts.containsKey(userId)) {
            return 0;
        }

        return unreadCounts.get(userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChatRoom chatRoom = (ChatRoom) o;

        return id != null ? id.equals(chatRoom.id) : chatRoom.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}