package com.imaginit.hyperplux.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import android.os.Parcel;
import android.os.Parcelable;

import com.imaginit.hyperplux.database.DateConverter;
import com.imaginit.hyperplux.database.StringListConverter;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Entity(tableName = "users")
@TypeConverters({DateConverter.class, StringListConverter.class})
public class User implements Parcelable {
    @PrimaryKey
    @NonNull
    private String uid; // Firebase User ID

    // Basic Information
    private String email;
    private String displayName;
    private String phoneNumber;
    private String profileImageUri;
    private String bio;
    private Date creationDate;
    private Date lastLoginDate;

    // Social Information
    private List<String> followers;
    private List<String> following;
    private int totalAssets;
    private int totalLikes;
    private int totalViews;
    private double overallEngagementScore;

    // Privacy Settings
    private boolean publicProfile;
    private boolean showAssetsPublicly;
    private boolean allowMessages;
    private boolean notificationsEnabled;

    // Will Information
    private String nextOfKinId;
    private String nextOfKinEmail;
    private String nextOfKinName;
    private String nextOfKinPhone;
    private boolean willActivated;

    // User Preferences
    private List<String> interests;
    private String preferredCurrency;
    private String preferredLanguage;
    private String preferredTheme;

    // Constructor
    public User(@NonNull String uid, String email) {
        this.uid = uid;
        this.email = email;
        this.followers = new ArrayList<>();
        this.following = new ArrayList<>();
        this.interests = new ArrayList<>();
        this.creationDate = new Date();
        this.lastLoginDate = new Date();
        this.publicProfile = true;
        this.showAssetsPublicly = false;
        this.allowMessages = true;
        this.notificationsEnabled = true;
        this.willActivated = false;
        this.preferredCurrency = "USD";
        this.preferredLanguage = "en";
        this.preferredTheme = "light";
    }

    // Parcelable implementation
    protected User(Parcel in) {
        uid = in.readString();
        email = in.readString();
        displayName = in.readString();
        phoneNumber = in.readString();
        profileImageUri = in.readString();
        bio = in.readString();
        creationDate = new Date(in.readLong());
        lastLoginDate = new Date(in.readLong());
        followers = in.createStringArrayList();
        following = in.createStringArrayList();
        totalAssets = in.readInt();
        totalLikes = in.readInt();
        totalViews = in.readInt();
        overallEngagementScore = in.readDouble();
        publicProfile = in.readByte() != 0;
        showAssetsPublicly = in.readByte() != 0;
        allowMessages = in.readByte() != 0;
        notificationsEnabled = in.readByte() != 0;
        nextOfKinId = in.readString();
        nextOfKinEmail = in.readString();
        nextOfKinName = in.readString();
        nextOfKinPhone = in.readString();
        willActivated = in.readByte() != 0;
        interests = in.createStringArrayList();
        preferredCurrency = in.readString();
        preferredLanguage = in.readString();
        preferredTheme = in.readString();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uid);
        dest.writeString(email);
        dest.writeString(displayName);
        dest.writeString(phoneNumber);
        dest.writeString(profileImageUri);
        dest.writeString(bio);
        dest.writeLong(creationDate != null ? creationDate.getTime() : 0);
        dest.writeLong(lastLoginDate != null ? lastLoginDate.getTime() : 0);
        dest.writeStringList(followers);
        dest.writeStringList(following);
        dest.writeInt(totalAssets);
        dest.writeInt(totalLikes);
        dest.writeInt(totalViews);
        dest.writeDouble(overallEngagementScore);
        dest.writeByte((byte) (publicProfile ? 1 : 0));
        dest.writeByte((byte) (showAssetsPublicly ? 1 : 0));
        dest.writeByte((byte) (allowMessages ? 1 : 0));
        dest.writeByte((byte) (notificationsEnabled ? 1 : 0));
        dest.writeString(nextOfKinId);
        dest.writeString(nextOfKinEmail);
        dest.writeString(nextOfKinName);
        dest.writeString(nextOfKinPhone);
        dest.writeByte((byte) (willActivated ? 1 : 0));
        dest.writeStringList(interests);
        dest.writeString(preferredCurrency);
        dest.writeString(preferredLanguage);
        dest.writeString(preferredTheme);
    }

    // Helper method to recalculate engagement score
    public void recalculateEngagementScore() {
        // Formula: (totalLikes * 2 + totalViews * 0.5) * (1 + (followers.size() * 0.1))
        this.overallEngagementScore = (totalLikes * 2 + totalViews * 0.5) * (1 + (followers.size() * 0.1));
    }

    // Getters and Setters
    @NonNull
    public String getUid() { return uid; }
    public void setUid(@NonNull String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getProfileImageUri() { return profileImageUri; }
    public void setProfileImageUri(String profileImageUri) { this.profileImageUri = profileImageUri; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public Date getCreationDate() { return creationDate; }
    public void setCreationDate(Date creationDate) { this.creationDate = creationDate; }

    public Date getLastLoginDate() { return lastLoginDate; }
    public void setLastLoginDate(Date lastLoginDate) { this.lastLoginDate = lastLoginDate; }

    public List<String> getFollowers() { return followers; }
    public void setFollowers(List<String> followers) {
        this.followers = followers;
        recalculateEngagementScore();
    }

    public List<String> getFollowing() { return following; }
    public void setFollowing(List<String> following) { this.following = following; }

    public int getTotalAssets() { return totalAssets; }
    public void setTotalAssets(int totalAssets) { this.totalAssets = totalAssets; }

    public int getTotalLikes() { return totalLikes; }
    public void setTotalLikes(int totalLikes) {
        this.totalLikes = totalLikes;
        recalculateEngagementScore();
    }

    public int getTotalViews() { return totalViews; }
    public void setTotalViews(int totalViews) {
        this.totalViews = totalViews;
        recalculateEngagementScore();
    }

    public double getOverallEngagementScore() { return overallEngagementScore; }

    public boolean isPublicProfile() { return publicProfile; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }

    public boolean isShowAssetsPublicly() { return showAssetsPublicly; }
    public void setShowAssetsPublicly(boolean showAssetsPublicly) { this.showAssetsPublicly = showAssetsPublicly; }

    public boolean isAllowMessages() { return allowMessages; }
    public void setAllowMessages(boolean allowMessages) { this.allowMessages = allowMessages; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public String getNextOfKinId() { return nextOfKinId; }
    public void setNextOfKinId(String nextOfKinId) { this.nextOfKinId = nextOfKinId; }

    public String getNextOfKinEmail() { return nextOfKinEmail; }
    public void setNextOfKinEmail(String nextOfKinEmail) { this.nextOfKinEmail = nextOfKinEmail; }

    public String getNextOfKinName() { return nextOfKinName; }
    public void setNextOfKinName(String nextOfKinName) { this.nextOfKinName = nextOfKinName; }

    public String getNextOfKinPhone() { return nextOfKinPhone; }
    public void setNextOfKinPhone(String nextOfKinPhone) { this.nextOfKinPhone = nextOfKinPhone; }

    public boolean isWillActivated() { return willActivated; }
    public void setWillActivated(boolean willActivated) { this.willActivated = willActivated; }

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(String preferredTheme) { this.preferredTheme = preferredTheme; }
}