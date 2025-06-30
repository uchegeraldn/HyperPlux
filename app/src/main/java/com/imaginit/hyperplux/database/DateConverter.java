package com.imaginit.hyperplux.database;

import androidx.room.TypeConverter;
import java.util.Date;

/**
 * Type converter for Room database to store Date objects
 */
public class DateConverter {
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}