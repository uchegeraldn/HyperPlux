package com.imaginit.hyperplux.models;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Model class for product information retrieved from barcode lookup services
 */
public class ProductInfo implements Parcelable {
    private String id;
    private String barcode;
    private String name;
    private String description;
    private String brand;
    private String category;
    private String modelNumber;
    private double price;
    private String currency;
    private String imageUrl;
    private String manufacturer;
    private String dimensions;
    private String weight;
    private String color;

    public ProductInfo() {
        // Default constructor required for Firestore
    }

    public ProductInfo(String barcode, String name, String description, String brand) {
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.brand = brand;
    }

    protected ProductInfo(Parcel in) {
        id = in.readString();
        barcode = in.readString();
        name = in.readString();
        description = in.readString();
        brand = in.readString();
        category = in.readString();
        modelNumber = in.readString();
        price = in.readDouble();
        currency = in.readString();
        imageUrl = in.readString();
        manufacturer = in.readString();
        dimensions = in.readString();
        weight = in.readString();
        color = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(barcode);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(brand);
        dest.writeString(category);
        dest.writeString(modelNumber);
        dest.writeDouble(price);
        dest.writeString(currency);
        dest.writeString(imageUrl);
        dest.writeString(manufacturer);
        dest.writeString(dimensions);
        dest.writeString(weight);
        dest.writeString(color);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ProductInfo> CREATOR = new Creator<ProductInfo>() {
        @Override
        public ProductInfo createFromParcel(Parcel in) {
            return new ProductInfo(in);
        }

        @Override
        public ProductInfo[] newArray(int size) {
            return new ProductInfo[size];
        }
    };

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "ProductInfo{" +
                "id='" + id + '\'' +
                ", barcode='" + barcode + '\'' +
                ", name='" + name + '\'' +
                ", brand='" + brand + '\'' +
                ", price=" + price +
                ", currency='" + currency + '\'' +
                '}';
    }
}