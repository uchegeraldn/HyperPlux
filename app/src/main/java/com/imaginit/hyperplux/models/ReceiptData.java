package com.imaginit.hyperplux.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Model class for receipt data extracted via OCR
 */
public class ReceiptData implements Parcelable {
    private double total;
    private Date date;
    private String store;
    private List<String> items;
    private String fullText;

    public ReceiptData() {
        this.items = new ArrayList<>();
    }

    protected ReceiptData(Parcel in) {
        total = in.readDouble();
        long tmpDate = in.readLong();
        date = tmpDate != -1 ? new Date(tmpDate) : null;
        store = in.readString();
        items = in.createStringArrayList();
        fullText = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(total);
        dest.writeLong(date != null ? date.getTime() : -1L);
        dest.writeString(store);
        dest.writeStringList(items);
        dest.writeString(fullText);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ReceiptData> CREATOR = new Creator<ReceiptData>() {
        @Override
        public ReceiptData createFromParcel(Parcel in) {
            return new ReceiptData(in);
        }

        @Override
        public ReceiptData[] newArray(int size) {
            return new ReceiptData[size];
        }
    };

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public void addItem(String item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    /**
     * Create an Asset from receipt data
     * @return A new Asset populated with receipt data
     */
    public Asset toAsset() {
        Asset asset = new Asset();

        // Use store name as asset name if available
        if (store != null && !store.isEmpty()) {
            asset.setName("Item from " + store);
        } else {
            asset.setName("Unnamed item");
        }

        // Use purchase date from receipt
        if (date != null) {
            asset.setPurchaseDate(date);
        } else {
            asset.setPurchaseDate(new Date()); // Default to current date
        }

        // Set purchase price
        if (total > 0) {
            asset.setPurchasePrice(total);
            // Default to USD
            asset.setCurrency("USD");
        }

        // Build description from items
        StringBuilder description = new StringBuilder();
        if (items != null && !items.isEmpty()) {
            description.append("Receipt items:\n");
            for (String item : items) {
                description.append("- ").append(item).append("\n");
            }
        }

        // Add full receipt text for reference
        if (fullText != null && !fullText.isEmpty()) {
            if (description.length() > 0) {
                description.append("\n");
            }
            description.append("Full receipt text:\n").append(fullText);
        }

        asset.setDescription(description.toString());

        // Set purchase location
        if (store != null && !store.isEmpty()) {
            asset.setPurchaseLocation(store);
        }

        return asset;
    }

    @Override
    public String toString() {
        return "ReceiptData{" +
                "total=" + total +
                ", date=" + date +
                ", store='" + store + '\'' +
                ", items=" + items +
                '}';
    }
}