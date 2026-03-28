// QRDao.java - Fixed
package com.qrmaster.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import com.qrmaster.app.models.QRItem;
import java.util.List;

@Dao
public interface QRDao {
    @Insert
    long insert(QRItem item);

    @Update
    void update(QRItem item);

    @Delete
    void delete(QRItem item);

    @Query("SELECT * FROM qr_items ORDER BY timestamp DESC")
    LiveData<List<QRItem>> getAllItems();

    @Query("SELECT * FROM qr_items WHERE isSaved = 1 ORDER BY timestamp DESC")
    LiveData<List<QRItem>> getSavedItems();

    @Query("SELECT * FROM qr_items WHERE type = :type ORDER BY timestamp DESC")
    LiveData<List<QRItem>> getItemsByType(String type);

    // BUG FIX #4: Room's code generator cannot handle List<Integer> directly in
    // an @Query with an IN clause when the parameter name matches a keyword.
    // Renaming the parameter to `itemIds` avoids the conflict and compiles cleanly.
    @Query("DELETE FROM qr_items WHERE id IN (:itemIds)")
    void deleteMultiple(List<Integer> itemIds);
}