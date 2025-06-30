package com.imaginit.hyperplux.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import android.os.Parcel;
import android.os.Parcelable;

import com.imaginit.hyperplux.database.DateConverter;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Entity(tableName = "assets")
@TypeConverters(DateConverter.class)
public class Asset implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    private int id;

    // Basic Information
    private String name;
    private int quantity;
    private String description;
    private String category;
    private String brand;
    private String model;
    private String serialNumber;

    // Financial Information
    private double cost;
    private String currency;
    private double currentValue;
    private Date purchaseDate;
    private String purchaseLocation;
    private String receiptImageUri;

    // Location Information
    private String currentLocation;
    private double latitude;
    private double longitude;
    private boolean isShared;

    // Status Information
    private String condition; // New, Good, Fair, Poor
    private boolean isForSale;
    private double askingPrice;
    private boolean isHidden;
    private boolean isLoanedOut;
    private String loanedTo;
    private Date loanDate;
    private Date returnDate;

    // Media
    private String imageUri;
    private List<String> additionalImageUris; // Will be converted using JSON
    private String documentUri;

    // Social Engagement
    private String userId;
    private int views;
    private int likes;
    private int dislikes;
    private int shares;
    private int comments;
    private Date lastInteractionDate;
    private double engagementScore;

    // Maintenance and Warranty
    private Date warrantyExpiration;
    private String warrantyInfo;
    private Date lastMaintenance;
    private Date nextMaintenance;

    // Will-related
    private String heirId;
    private String willInstructions;
    private boolean isBequest;

    // Constructor with minimum required fields
    public Asset(String name, int quantity, String userId) {
        this.name = name;
        this.quantity = quantity;
        this.userId = userId;
        this.views = 0;
        this.likes = 0;
        this.dislikes = 0;
        this.shares = 0;
        this.comments = 0;
        this.engagementScore = 0.0;
        this.isShared = false;
        this.isForSale = false;
        this.isHidden = false;
        this.isLoanedOut = false;
        this.isBequest = false;
        this.purchaseDate = new Date();
        this.lastInteractionDate = new Date();
    }

    // Full constructor
    public Asset(String name, int quantity, String description, String category,
                 String brand, String model, String serialNumber, double cost,
                 String currency, double currentValue, Date purchaseDate,
                 String purchaseLocation, String receiptImageUri, String currentLocation,
                 double latitude, double longitude, boolean isShared, String condition,
                 boolean isForSale, double askingPrice, boolean isHidden, boolean isLoanedOut,
                 String loanedTo, Date loanDate, Date returnDate, String imageUri,
                 List<String> additionalImageUris, String documentUri, String userId,
                 int views, int likes, int dislikes, int shares, int comments,
                 Date lastInteractionDate, double engagementScore, Date warrantyExpiration,
                 String warrantyInfo, Date lastMaintenance, Date nextMaintenance,
                 String heirId, String willInstructions, boolean isBequest) {

        this.name = name;
        this.quantity = quantity;
        this.description = description;
        this.category = category;
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.cost = cost;
        this.currency = currency;
        this.currentValue = currentValue;
        this.purchaseDate = purchaseDate;
        this.purchaseLocation = purchaseLocation;
        this.receiptImageUri = receiptImageUri;
        this.currentLocation = currentLocation;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isShared = isShared;
        this.condition = condition;
        this.isForSale = isForSale;
        this.askingPrice = askingPrice;
        this.isHidden = isHidden;
        this.isLoanedOut = isLoanedOut;
        this.loanedTo = loanedTo;
        this.loanDate = loanDate;
        this.returnDate = returnDate;
        this.imageUri = imageUri;
        this.additionalImageUris = additionalImageUris != null ? additionalImageUris : new ArrayList<>();
        this.documentUri = documentUri;
        this.userId = userId;
        this.views = views;
        this.likes = likes;
        this.dislikes = dislikes;
        this.shares = shares;
        this.comments = comments;
        this.lastInteractionDate = lastInteractionDate;
        this.engagementScore = engagementScore;
        this.warrantyExpiration = warrantyExpiration;
        this.warrantyInfo = warrantyInfo;
        this.lastMaintenance = lastMaintenance;
        this.nextMaintenance = nextMaintenance;
        this.heirId = heirId;
        this.willInstructions = willInstructions;
        this.isBequest = isBequest;
    }

    protected Asset(Parcel in) {
        id = in.readInt();
        name = in.readString();
        quantity = in.readInt();
        description = in.readString();
        category = in.readString();
        brand = in.readString();
        model = in.readString();
        serialNumber = in.readString();
        cost = in.readDouble();
        currency = in.readString();
        currentValue = in.readDouble();
        long tmpPurchaseDate = in.readLong();
        purchaseDate = tmpPurchaseDate == -1 ? null : new Date(tmpPurchaseDate);
        purchaseLocation = in.readString();
        receiptImageUri = in.readString();
        currentLocation = in.readString();
        latitude = in.readDouble();
        longitude = in.readDouble();
        isShared = in.readByte() != 0;
        condition = in.readString();
        isForSale = in.readByte() != 0;
        askingPrice = in.readDouble();
        isHidden = in.readByte() != 0;
        isLoanedOut = in.readByte() != 0;
        loanedTo = in.readString();
        long tmpLoanDate = in.readLong();
        loanDate = tmpLoanDate == -1 ? null : new Date(tmpLoanDate);
        long tmpReturnDate = in.readLong();
        returnDate = tmpReturnDate == -1 ? null : new Date(tmpReturnDate);
        imageUri = in.readString();
        additionalImageUris = in.createStringArrayList();
        documentUri = in.readString();
        userId = in.readString();
        views = in.readInt();
        likes = in.readInt();
        dislikes = in.readInt();
        shares = in.readInt();
        comments = in.readInt();
        long tmpLastInteractionDate = in.readLong();
        lastInteractionDate = tmpLastInteractionDate == -1 ? null : new Date(tmpLastInteractionDate);
        engagementScore = in.readDouble();
        long tmpWarrantyExpiration = in.readLong();
        warrantyExpiration = tmpWarrantyExpiration == -1 ? null : new Date(tmpWarrantyExpiration);
        warrantyInfo = in.readString();
        long tmpLastMaintenance = in.readLong();
        lastMaintenance = tmpLastMaintenance == -1 ? null : new Date(tmpLastMaintenance);
        long tmpNextMaintenance = in.readLong();
        nextMaintenance = tmpNextMaintenance == -1 ? null : new Date(tmpNextMaintenance);
        heirId = in.readString();
        willInstructions = in.readString();
        isBequest = in.readByte() != 0;
    }

    public static final Creator<Asset> CREATOR = new Creator<Asset>() {
        @Override
        public Asset createFromParcel(Parcel in) {
            return new Asset(in);
        }

        @Override
        public Asset[] newArray(int size) {
            return new Asset[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeInt(quantity);
        dest.writeString(description);
        dest.writeString(category);
        dest.writeString(brand);
        dest.writeString(model);
        dest.writeString(serialNumber);
        dest.writeDouble(cost);
        dest.writeString(currency);
        dest.writeDouble(currentValue);
        dest.writeLong(purchaseDate != null ? purchaseDate.getTime() : -1);
        dest.writeString(purchaseLocation);
        dest.writeString(receiptImageUri);
        dest.writeString(currentLocation);
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeByte((byte) (isShared ? 1 : 0));
        dest.writeString(condition);
        dest.writeByte((byte) (isForSale ? 1 : 0));
        dest.writeDouble(askingPrice);
        dest.writeByte((byte) (isHidden ? 1 : 0));
        dest.writeByte((byte) (isLoanedOut ? 1 : 0));
        dest.writeString(loanedTo);
        dest.writeLong(loanDate != null ? loanDate.getTime() : -1);
        dest.writeLong(returnDate != null ? returnDate.getTime() : -1);
        dest.writeString(imageUri);
        dest.writeStringList(additionalImageUris);
        dest.writeString(documentUri);
        dest.writeString(userId);
        dest.writeInt(views);
        dest.writeInt(likes);
        dest.writeInt(dislikes);
        dest.writeInt(shares);
        dest.writeInt(comments);
        dest.writeLong(lastInteractionDate != null ? lastInteractionDate.getTime() : -1);
        dest.writeDouble(engagementScore);
        dest.writeLong(warrantyExpiration != null ? warrantyExpiration.getTime() : -1);
        dest.writeString(warrantyInfo);
        dest.writeLong(lastMaintenance != null ? lastMaintenance.getTime() : -1);
        dest.writeLong(nextMaintenance != null ? nextMaintenance.getTime() : -1);
        dest.writeString(heirId);
        dest.writeString(willInstructions);
        dest.writeByte((byte) (isBequest ? 1 : 0));
    }

    // Calculate engagement score based on user interactions
    public void recalculateEngagementScore() {
        // Weight factors for each engagement type
        final double VIEW_WEIGHT = 0.2;
        final double LIKE_WEIGHT = 1.0;
        final double DISLIKE_WEIGHT = -0.5;
        final double SHARE_WEIGHT = 2.0;
        final double COMMENT_WEIGHT = 1.5;

        // Calculate raw score
        double rawScore = (views * VIEW_WEIGHT) +
                (likes * LIKE_WEIGHT) +
                (dislikes * DISLIKE_WEIGHT) +
                (shares * SHARE_WEIGHT) +
                (comments * COMMENT_WEIGHT);

        // Apply time decay factor (more recent interactions have higher value)
        long timeElapsed = new Date().getTime() - lastInteractionDate.getTime();
        double daysSinceLastInteraction = timeElapsed / (1000.0 * 60 * 60 * 24);
        double decayFactor = Math.exp(-0.05 * daysSinceLastInteraction); // Exponential decay

        // Update engagement score with time decay
        this.engagementScore = rawScore * decayFactor;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public double getCost() { return cost; }
    public void setCost(double cost) { this.cost = cost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }

    public Date getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Date purchaseDate) { this.purchaseDate = purchaseDate; }

    public String getPurchaseLocation() { return purchaseLocation; }
    public void setPurchaseLocation(String purchaseLocation) { this.purchaseLocation = purchaseLocation; }

    public String getReceiptImageUri() { return receiptImageUri; }
    public void setReceiptImageUri(String receiptImageUri) { this.receiptImageUri = receiptImageUri; }

    public String getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(String currentLocation) { this.currentLocation = currentLocation; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { isShared = shared; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public boolean isForSale() { return isForSale; }
    public void setForSale(boolean forSale) { isForSale = forSale; }

    public double getAskingPrice() { return askingPrice; }
    public void setAskingPrice(double askingPrice) { this.askingPrice = askingPrice; }

    public boolean isHidden() { return isHidden; }
    public void setHidden(boolean hidden) { isHidden = hidden; }

    public boolean isLoanedOut() { return isLoanedOut; }
    public void setLoanedOut(boolean loanedOut) { isLoanedOut = loanedOut; }

    public String getLoanedTo() { return loanedTo; }
    public void setLoanedTo(String loanedTo) { this.loanedTo = loanedTo; }

    public Date getLoanDate() { return loanDate; }
    public void setLoanDate(Date loanDate) { this.loanDate = loanDate; }

    public Date getReturnDate() { return returnDate; }
    public void setReturnDate(Date returnDate) { this.returnDate = returnDate; }

    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }

    public List<String> getAdditionalImageUris() { return additionalImageUris; }
    public void setAdditionalImageUris(List<String> additionalImageUris) { this.additionalImageUris = additionalImageUris; }

    public String getDocumentUri() { return documentUri; }
    public void setDocumentUri(String documentUri) { this.documentUri = documentUri; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public int getViews() { return views; }
    public void setViews(int views) {
        this.views = views;
        this.lastInteractionDate = new Date();
        recalculateEngagementScore();
    }

    public int getLikes() { return likes; }
    public void setLikes(int likes) {
        this.likes = likes;
        this.lastInteractionDate = new Date();
        recalculateEngagementScore();
    }

    public int getDislikes() { return dislikes; }
    public void setDislikes(int dislikes) {
        this.dislikes = dislikes;
        this.lastInteractionDate = new Date();
        recalculateEngagementScore();
    }

    public int getShares() { return shares; }
    public void setShares(int shares) {
        this.shares = shares;
        this.lastInteractionDate = new Date();
        recalculateEngagementScore();
    }

    public int getComments() { return comments; }
    public void setComments(int comments) {
        this.comments = comments;
        this.lastInteractionDate = new Date();
        recalculateEngagementScore();
    }

    public Date getLastInteractionDate() { return lastInteractionDate; }
    public void setLastInteractionDate(Date lastInteractionDate) {
        this.lastInteractionDate = lastInteractionDate;
        recalculateEngagementScore();
    }

    public double getEngagementScore() { return engagementScore; }

    public Date getWarrantyExpiration() { return warrantyExpiration; }
    public void setWarrantyExpiration(Date warrantyExpiration) { this.warrantyExpiration = warrantyExpiration; }

    public String getWarrantyInfo() { return warrantyInfo; }
    public void setWarrantyInfo(String warrantyInfo) { this.warrantyInfo = warrantyInfo; }

    public Date getLastMaintenance() { return lastMaintenance; }
    public void setLastMaintenance(Date lastMaintenance) { this.lastMaintenance = lastMaintenance; }

    public Date getNextMaintenance() { return nextMaintenance; }
    public void setNextMaintenance(Date nextMaintenance) { this.nextMaintenance = nextMaintenance; }

    public String getHeirId() { return heirId; }
    public void setHeirId(String heirId) { this.heirId = heirId; }

    public String getWillInstructions() { return willInstructions; }
    public void setWillInstructions(String willInstructions) { this.willInstructions = willInstructions; }

    public boolean isBequest() { return isBequest; }
    public void setBequest(boolean bequest) { isBequest = bequest; }

    // Custom equals method for proper comparison
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Asset asset = (Asset) o;
        return id == asset.id;
    }

    // Custom hashCode for proper collection handling
    @Override
    public int hashCode() {
        return id;
    }

    public void setEngagementScore(double personalizedScore) {

    }
}