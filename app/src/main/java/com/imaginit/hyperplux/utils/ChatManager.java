package com.imaginit.hyperplux.utils;

import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.imaginit.hyperplux.models.ChatMessage;
import com.imaginit.hyperplux.models.ChatRoom;
import com.imaginit.hyperplux.models.Result;
import com.imaginit.hyperplux.models.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager class for handling all chat operations
 */
public class ChatManager {
    private static final String TAG = "ChatManager";
    private static final String CHAT_ROOMS_COLLECTION = "chatRooms";
    private static final String MESSAGES_COLLECTION = "messages";
    private static final String USERS_COLLECTION = "users";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final AnalyticsTracker analyticsTracker;

    private final Map<String, ListenerRegistration> messageListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> roomListeners = new HashMap<>();

    // Singleton instance
    private static ChatManager instance;

    /**
     * Get the singleton instance of ChatManager
     */
    public static synchronized ChatManager getInstance() {
        if (instance == null) {
            instance = new ChatManager();
        }
        return instance;
    }

    private ChatManager() {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.analyticsTracker = AnalyticsTracker.getInstance();
    }

    /**
     * Get the current user ID
     * @return The current user ID or null if not authenticated
     */
    @Nullable
    private String getCurrentUserId() {
        FirebaseUser currentUser = auth.getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }

    /**
     * Get all chat rooms for the current user
     * @return LiveData with the result containing a list of chat rooms
     */
    public LiveData<Result<List<ChatRoom>>> getChatRooms() {
        MutableLiveData<Result<List<ChatRoom>>> resultLiveData = new MutableLiveData<>();
        String userId = getCurrentUserId();

        if (userId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Clean up any existing listener
        if (roomListeners.containsKey(userId)) {
            roomListeners.get(userId).remove();
            roomListeners.remove(userId);
        }

        // Query for chat rooms where the current user is a participant
        Query query = firestore.collection(CHAT_ROOMS_COLLECTION)
                .whereArrayContains("participantIds", userId)
                .orderBy("lastMessageTime", Query.Direction.DESCENDING);

        ListenerRegistration listener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen for chat rooms failed", e);
                resultLiveData.setValue(new Result.Error<>(e));
                return;
            }

            if (snapshots == null || snapshots.isEmpty()) {
                resultLiveData.setValue(new Result.Success<>(new ArrayList<>()));
                return;
            }

            List<ChatRoom> chatRooms = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshots) {
                ChatRoom chatRoom = document.toObject(ChatRoom.class);
                chatRoom.setId(document.getId());
                chatRooms.add(chatRoom);
            }

            resultLiveData.setValue(new Result.Success<>(chatRooms));
        });

        roomListeners.put(userId, listener);
        return resultLiveData;
    }

    /**
     * Create a chat room between two users
     * @param otherUserId The ID of the other user
     * @return LiveData with the result containing the created chat room
     */
    public LiveData<Result<ChatRoom>> createChatRoom(String otherUserId) {
        MutableLiveData<Result<ChatRoom>> resultLiveData = new MutableLiveData<>();
        String currentUserId = getCurrentUserId();

        if (currentUserId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (currentUserId.equals(otherUserId)) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Cannot create chat with yourself")));
            return resultLiveData;
        }

        // Check if a chat room already exists between these users
        firestore.collection(CHAT_ROOMS_COLLECTION)
                .whereArrayContains("participantIds", currentUserId)
                .get()
                .addOnSuccessListener(roomSnapshots -> {
                    for (DocumentSnapshot document : roomSnapshots.getDocuments()) {
                        ChatRoom chatRoom = document.toObject(ChatRoom.class);
                        if (chatRoom != null && chatRoom.getParticipantIds().contains(otherUserId)) {
                            chatRoom.setId(document.getId());
                            resultLiveData.setValue(new Result.Success<>(chatRoom));
                            return;
                        }
                    }

                    // No existing chat room found, create a new one
                    createNewChatRoom(currentUserId, otherUserId, resultLiveData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking for existing chat room", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });

        return resultLiveData;
    }

    /**
     * Create a new chat room between two users
     */
    private void createNewChatRoom(String currentUserId, String otherUserId,
                                   MutableLiveData<Result<ChatRoom>> resultLiveData) {
        // Fetch user data to populate the chat room
        firestore.collection(USERS_COLLECTION).document(currentUserId)
                .get()
                .addOnSuccessListener(currentUserDoc -> {
                    User currentUser = currentUserDoc.toObject(User.class);

                    firestore.collection(USERS_COLLECTION).document(otherUserId)
                            .get()
                            .addOnSuccessListener(otherUserDoc -> {
                                User otherUser = otherUserDoc.toObject(User.class);

                                if (currentUser == null || otherUser == null) {
                                    resultLiveData.setValue(new Result.Error<>(
                                            new Exception("One or both users not found")));
                                    return;
                                }

                                // Create chat room data
                                Map<String, Object> roomData = new HashMap<>();
                                roomData.put("participantIds", Arrays.asList(currentUserId, otherUserId));

                                Map<String, String> participantNames = new HashMap<>();
                                participantNames.put(currentUserId, currentUser.getDisplayName());
                                participantNames.put(otherUserId, otherUser.getDisplayName());
                                roomData.put("participantNames", participantNames);

                                Map<String, String> participantPhotos = new HashMap<>();
                                participantPhotos.put(currentUserId, currentUser.getPhotoUrl());
                                participantPhotos.put(otherUserId, otherUser.getPhotoUrl());
                                roomData.put("participantPhotos", participantPhotos);

                                roomData.put("createdAt", new Date());
                                roomData.put("lastMessageTime", new Date());
                                roomData.put("lastMessage", "");
                                roomData.put("unreadCounts", new HashMap<String, Integer>());

                                // Add chat room to Firestore
                                firestore.collection(CHAT_ROOMS_COLLECTION)
                                        .add(roomData)
                                        .addOnSuccessListener(documentReference -> {
                                            String roomId = documentReference.getId();
                                            ChatRoom chatRoom = new ChatRoom();
                                            chatRoom.setId(roomId);
                                            chatRoom.setParticipantIds(Arrays.asList(currentUserId, otherUserId));
                                            chatRoom.setParticipantNames(participantNames);
                                            chatRoom.setParticipantPhotos(participantPhotos);
                                            chatRoom.setCreatedAt(new Date());
                                            chatRoom.setLastMessageTime(new Date());
                                            chatRoom.setLastMessage("");
                                            chatRoom.setUnreadCounts(new HashMap<>());

                                            resultLiveData.setValue(new Result.Success<>(chatRoom));

                                            // Log creation event
                                            analyticsTracker.logEvent("chat_room_created", null);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error creating chat room", e);
                                            resultLiveData.setValue(new Result.Error<>(e));
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error fetching other user", e);
                                resultLiveData.setValue(new Result.Error<>(e));
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching current user", e);
                    resultLiveData.setValue(new Result.Error<>(e));
                });
    }

    /**
     * Get messages for a specific chat room
     * @param chatRoomId The ID of the chat room
     * @return LiveData with the result containing a list of messages
     */
    public LiveData<Result<List<ChatMessage>>> getMessages(String chatRoomId) {
        MutableLiveData<Result<List<ChatMessage>>> resultLiveData = new MutableLiveData<>();
        String userId = getCurrentUserId();

        if (userId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Clean up any existing listener
        if (messageListeners.containsKey(chatRoomId)) {
            messageListeners.get(chatRoomId).remove();
            messageListeners.remove(chatRoomId);
        }

        // Mark messages as read
        markMessagesAsRead(chatRoomId);

        // Query for messages in this chat room
        Query query = firestore.collection(CHAT_ROOMS_COLLECTION)
                .document(chatRoomId)
                .collection(MESSAGES_COLLECTION)
                .orderBy("timestamp", Query.Direction.ASCENDING);

        ListenerRegistration listener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen for messages failed", e);
                resultLiveData.setValue(new Result.Error<>(e));
                return;
            }

            if (snapshots == null) {
                resultLiveData.setValue(new Result.Success<>(new ArrayList<>()));
                return;
            }

            List<ChatMessage> messages = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshots) {
                ChatMessage message = document.toObject(ChatMessage.class);
                message.setId(document.getId());
                messages.add(message);
            }

            resultLiveData.setValue(new Result.Success<>(messages));
        });

        messageListeners.put(chatRoomId, listener);
        return resultLiveData;
    }

    /**
     * Send a text message
     * @param chatRoomId The ID of the chat room
     * @param text The message text
     * @return LiveData with the result containing the sent message
     */
    public LiveData<Result<ChatMessage>> sendTextMessage(String chatRoomId, String text) {
        MutableLiveData<Result<ChatMessage>> resultLiveData = new MutableLiveData<>();
        String userId = getCurrentUserId();

        if (userId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (text == null || text.trim().isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Message cannot be empty")));
            return resultLiveData;
        }

        // Reference to the chat room
        DocumentReference chatRoomRef = firestore.collection(CHAT_ROOMS_COLLECTION).document(chatRoomId);

        // Get the chat room to find other participants
        chatRoomRef.get().addOnSuccessListener(documentSnapshot -> {
            ChatRoom chatRoom = documentSnapshot.toObject(ChatRoom.class);
            if (chatRoom == null) {
                resultLiveData.setValue(new Result.Error<>(new Exception("Chat room not found")));
                return;
            }

            // Create the message
            ChatMessage message = new ChatMessage();
            message.setSenderId(userId);
            message.setText(text);
            message.setType(ChatMessage.TYPE_TEXT);
            message.setTimestamp(new Date());
            message.setRead(false);

            // Add the message to the chat room's messages collection
            chatRoomRef.collection(MESSAGES_COLLECTION)
                    .add(message)
                    .addOnSuccessListener(documentReference -> {
                        String messageId = documentReference.getId();
                        message.setId(messageId);

                        // Update the chat room's last message and time
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lastMessage", text);
                        updates.put("lastMessageTime", message.getTimestamp());

                        // Increment unread counts for other participants
                        for (String participantId : chatRoom.getParticipantIds()) {
                            if (!participantId.equals(userId)) {
                                updates.put("unreadCounts." + participantId, FieldValue.increment(1));
                            }
                        }

                        chatRoomRef.update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    resultLiveData.setValue(new Result.Success<>(message));

                                    // Log message sent event
                                    Map<String, Object> params = new HashMap<>();
                                    params.put("message_type", "text");
                                    analyticsTracker.logEvent("chat_message_sent", params);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating chat room", e);
                                    resultLiveData.setValue(new Result.Error<>(e));
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error sending message", e);
                        resultLiveData.setValue(new Result.Error<>(e));
                    });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error getting chat room", e);
            resultLiveData.setValue(new Result.Error<>(e));
        });

        return resultLiveData;
    }

    /**
     * Send an image message
     * @param chatRoomId The ID of the chat room
     * @param imageUrl The URL of the uploaded image
     * @return LiveData with the result containing the sent message
     */
    public LiveData<Result<ChatMessage>> sendImageMessage(String chatRoomId, String imageUrl) {
        MutableLiveData<Result<ChatMessage>> resultLiveData = new MutableLiveData<>();
        String userId = getCurrentUserId();

        if (userId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Image URL cannot be empty")));
            return resultLiveData;
        }

        // Reference to the chat room
        DocumentReference chatRoomRef = firestore.collection(CHAT_ROOMS_COLLECTION).document(chatRoomId);

        // Get the chat room to find other participants
        chatRoomRef.get().addOnSuccessListener(documentSnapshot -> {
            ChatRoom chatRoom = documentSnapshot.toObject(ChatRoom.class);
            if (chatRoom == null) {
                resultLiveData.setValue(new Result.Error<>(new Exception("Chat room not found")));
                return;
            }

            // Create the message
            ChatMessage message = new ChatMessage();
            message.setSenderId(userId);
            message.setImageUrl(imageUrl);
            message.setType(ChatMessage.TYPE_IMAGE);
            message.setTimestamp(new Date());
            message.setRead(false);

            // Add the message to the chat room's messages collection
            chatRoomRef.collection(MESSAGES_COLLECTION)
                    .add(message)
                    .addOnSuccessListener(documentReference -> {
                        String messageId = documentReference.getId();
                        message.setId(messageId);

                        // Update the chat room's last message and time
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("lastMessage", "📷 Image");
                        updates.put("lastMessageTime", message.getTimestamp());

                        // Increment unread counts for other participants
                        for (String participantId : chatRoom.getParticipantIds()) {
                            if (!participantId.equals(userId)) {
                                updates.put("unreadCounts." + participantId, FieldValue.increment(1));
                            }
                        }

                        chatRoomRef.update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    resultLiveData.setValue(new Result.Success<>(message));

                                    // Log message sent event
                                    Map<String, Object> params = new HashMap<>();
                                    params.put("message_type", "image");
                                    analyticsTracker.logEvent("chat_message_sent", params);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating chat room", e);
                                    resultLiveData.setValue(new Result.Error<>(e));
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error sending message", e);
                        resultLiveData.setValue(new Result.Error<>(e));
                    });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error getting chat room", e);
            resultLiveData.setValue(new Result.Error<>(e));
        });

        return resultLiveData;
    }

    /**
     * Mark all messages in a chat room as read
     * @param chatRoomId The ID of the chat room
     */
    public void markMessagesAsRead(String chatRoomId) {
        String userId = getCurrentUserId();
        if (userId == null) return;

        DocumentReference chatRoomRef = firestore.collection(CHAT_ROOMS_COLLECTION).document(chatRoomId);

        // Reset unread count for current user
        Map<String, Object> updates = new HashMap<>();
        updates.put("unreadCounts." + userId, 0);

        chatRoomRef.update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Error resetting unread count", e));
    }

    /**
     * Get total unread message count across all chat rooms
     * @return LiveData with the total unread count
     */
    public LiveData<Integer> getTotalUnreadCount() {
        MutableLiveData<Integer> countLiveData = new MutableLiveData<>();
        String userId = getCurrentUserId();

        if (userId == null) {
            countLiveData.setValue(0);
            return countLiveData;
        }

        firestore.collection(CHAT_ROOMS_COLLECTION)
                .whereArrayContains("participantIds", userId)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen for unread counts failed", e);
                        countLiveData.setValue(0);
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        countLiveData.setValue(0);
                        return;
                    }

                    int totalCount = 0;
                    for (QueryDocumentSnapshot document : snapshots) {
                        ChatRoom chatRoom = document.toObject(ChatRoom.class);
                        Map<String, Integer> unreadCounts = chatRoom.getUnreadCounts();
                        if (unreadCounts != null && unreadCounts.containsKey(userId)) {
                            totalCount += unreadCounts.get(userId);
                        }
                    }

                    countLiveData.setValue(totalCount);
                });

        return countLiveData;
    }

    /**
     * Clean up resources when no longer needed
     */
    public void cleanup() {
        for (ListenerRegistration listener : messageListeners.values()) {
            listener.remove();
        }
        messageListeners.clear();

        for (ListenerRegistration listener : roomListeners.values()) {
            listener.remove();
        }
        roomListeners.clear();
    }
}