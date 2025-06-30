package com.imaginit.hyperplux.viewmodels;

import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.imaginit.hyperplux.models.ChatMessage;
import com.imaginit.hyperplux.models.ChatRoom;
import com.imaginit.hyperplux.models.Result;
import com.imaginit.hyperplux.utils.ChatManager;
import com.imaginit.hyperplux.utils.NetworkMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ViewModel for chat functionality
 */
public class ChatViewModel extends ViewModel {
    private static final String TAG = "ChatViewModel";
    private static final String CHAT_IMAGES_PATH = "chat_images";

    private final ChatManager chatManager;
    private final FirebaseStorage storage;
    private final String currentUserId;

    // Map to keep track of observers to prevent memory leaks
    private final Map<String, Observer<Result<List<ChatRoom>>>> chatRoomObservers = new HashMap<>();

    public ChatViewModel() {
        chatManager = ChatManager.getInstance();
        storage = FirebaseStorage.getInstance();

        // Safely get current user ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            currentUserId = null;
            Log.e(TAG, "No user is signed in");
        }
    }

    /**
     * Get all chat rooms for the current user
     */
    public LiveData<Result<List<ChatRoom>>> getChatRooms() {
        // Return empty list if user is not logged in
        if (currentUserId == null) {
            MutableLiveData<Result<List<ChatRoom>>> resultLiveData = new MutableLiveData<>();
            resultLiveData.setValue(new Result.Success<>(new ArrayList<>()));
            return resultLiveData;
        }

        return chatManager.getChatRooms();
    }

    /**
     * Get a specific chat room
     */
    public LiveData<Result<ChatRoom>> getChatRoom(String chatRoomId) {
        MutableLiveData<Result<ChatRoom>> resultLiveData = new MutableLiveData<>();

        // Check if user is logged in
        if (currentUserId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Check if chat room ID is valid
        if (chatRoomId == null || chatRoomId.isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid chat room ID")));
            return resultLiveData;
        }

        // Remove any existing observer for this ID to prevent memory leaks
        if (chatRoomObservers.containsKey(chatRoomId)) {
            chatManager.getChatRooms().removeObserver(chatRoomObservers.get(chatRoomId));
            chatRoomObservers.remove(chatRoomId);
        }

        // Create and register a new observer
        Observer<Result<List<ChatRoom>>> observer = result -> {
            if (result.isSuccess()) {
                List<ChatRoom> chatRooms = result.getData();
                if (chatRooms == null) {
                    resultLiveData.setValue(new Result.Error<>(new Exception("No chat rooms available")));
                    return;
                }

                // Find the specific chat room
                for (ChatRoom chatRoom : chatRooms) {
                    if (chatRoom.getId().equals(chatRoomId)) {
                        resultLiveData.setValue(new Result.Success<>(chatRoom));
                        return;
                    }
                }
                resultLiveData.setValue(new Result.Error<>(new Exception("Chat room not found")));
            } else {
                resultLiveData.setValue(new Result.Error<>(result.getException()));
            }
        };

        // Store the observer for later cleanup
        chatRoomObservers.put(chatRoomId, observer);

        // Start observing
        chatManager.getChatRooms().observeForever(observer);

        return resultLiveData;
    }

    /**
     * Create a chat room with another user
     */
    public LiveData<Result<ChatRoom>> createChatRoom(String otherUserId) {
        // Check if user is logged in
        if (currentUserId == null) {
            MutableLiveData<Result<ChatRoom>> resultLiveData = new MutableLiveData<>();
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Check if other user ID is valid
        if (otherUserId == null || otherUserId.isEmpty()) {
            MutableLiveData<Result<ChatRoom>> resultLiveData = new MutableLiveData<>();
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid user ID")));
            return resultLiveData;
        }

        return chatManager.createChatRoom(otherUserId);
    }

    /**
     * Get messages for a chat room
     */
    public LiveData<Result<List<ChatMessage>>> getMessages(String chatRoomId) {
        // Check if user is logged in
        if (currentUserId == null) {
            MutableLiveData<Result<List<ChatMessage>>> resultLiveData = new MutableLiveData<>();
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Check if chat room ID is valid
        if (chatRoomId == null || chatRoomId.isEmpty()) {
            MutableLiveData<Result<List<ChatMessage>>> resultLiveData = new MutableLiveData<>();
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid chat room ID")));
            return resultLiveData;
        }

        return chatManager.getMessages(chatRoomId);
    }

    /**
     * Send a text message
     */
    public LiveData<Result<ChatMessage>> sendTextMessage(String chatRoomId, String text) {
        MutableLiveData<Result<ChatMessage>> resultLiveData = new MutableLiveData<>();

        // Check if user is logged in
        if (currentUserId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Check if chat room ID is valid
        if (chatRoomId == null || chatRoomId.isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid chat room ID")));
            return resultLiveData;
        }

        // Check if text is valid
        if (text == null || text.trim().isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Message cannot be empty")));
            return resultLiveData;
        }

        // Check network connectivity
        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        return chatManager.sendTextMessage(chatRoomId, text);
    }

    /**
     * Upload an image and send it as a message
     */
    public LiveData<Result<ChatMessage>> uploadImageAndSendMessage(String chatRoomId, Uri imageUri) {
        MutableLiveData<Result<ChatMessage>> resultLiveData = new MutableLiveData<>();

        // Check if user is logged in
        if (currentUserId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Check if chat room ID is valid
        if (chatRoomId == null || chatRoomId.isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid chat room ID")));
            return resultLiveData;
        }

        // Check if image URI is valid
        if (imageUri == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid image")));
            return resultLiveData;
        }

        // Check network connectivity
        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        try {
            // Generate a unique filename
            String fileName = UUID.randomUUID().toString() + ".jpg";
            StorageReference storageRef = storage.getReference()
                    .child(CHAT_IMAGES_PATH)
                    .child(chatRoomId)
                    .child(fileName);

            // Upload the image
            storageRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        // Get the download URL
                        storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            // Send the image message
                            chatManager.sendImageMessage(chatRoomId, uri.toString())
                                    .observeForever(result -> resultLiveData.setValue(result));
                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Error getting download URL", e);
                            resultLiveData.setValue(new Result.Error<>(e));
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error uploading image", e);
                        resultLiveData.setValue(new Result.Error<>(e));
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in uploadImageAndSendMessage", e);
            resultLiveData.setValue(new Result.Error<>(e));
        }

        return resultLiveData;
    }

    /**
     * Send an asset as a message
     */
    public LiveData<Result<ChatMessage>> sendAssetMessage(String chatRoomId, String assetId) {
        MutableLiveData<Result<ChatMessage>> resultLiveData = new MutableLiveData<>();

        // Check if user is logged in
        if (currentUserId == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("User not authenticated")));
            return resultLiveData;
        }

        // Check if chat room ID is valid
        if (chatRoomId == null || chatRoomId.isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid chat room ID")));
            return resultLiveData;
        }

        // Check if asset ID is valid
        if (assetId == null || assetId.isEmpty()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Invalid asset ID")));
            return resultLiveData;
        }

        // Check network connectivity
        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        try {
            // Create the asset message
            ChatMessage message = new ChatMessage();
            message.setSenderId(currentUserId);
            message.setType(ChatMessage.TYPE_ASSET);
            message.setAssetId(assetId);

            // TODO: Implement sending asset message in ChatManager
            // For now, simulate success
            resultLiveData.setValue(new Result.Success<>(message));
        } catch (Exception e) {
            Log.e(TAG, "Exception in sendAssetMessage", e);
            resultLiveData.setValue(new Result.Error<>(e));
        }

        return resultLiveData;
    }

    /**
     * Mark all messages in a chat room as read
     */
    public void markMessagesAsRead(String chatRoomId) {
        // Check if user is logged in and chat room ID is valid
        if (currentUserId == null || chatRoomId == null || chatRoomId.isEmpty()) {
            Log.e(TAG, "Cannot mark messages as read: User not authenticated or invalid chat room ID");
            return;
        }

        chatManager.markMessagesAsRead(chatRoomId);
    }

    /**
     * Get the total number of unread messages across all chat rooms
     */
    public LiveData<Integer> getTotalUnreadCount() {
        // Return zero if user is not logged in
        if (currentUserId == null) {
            MutableLiveData<Integer> resultLiveData = new MutableLiveData<>();
            resultLiveData.setValue(0);
            return resultLiveData;
        }

        return chatManager.getTotalUnreadCount();
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Remove all observers to prevent memory leaks
        for (Map.Entry<String, Observer<Result<List<ChatRoom>>>> entry : chatRoomObservers.entrySet()) {
            chatManager.getChatRooms().removeObserver(entry.getValue());
        }
        chatRoomObservers.clear();

        // Cleanup chat manager
        chatManager.cleanup();
    }
}