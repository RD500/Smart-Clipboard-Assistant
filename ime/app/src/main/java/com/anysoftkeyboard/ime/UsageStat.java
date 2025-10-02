package com.anysoftkeyboard.ime;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "usage_stats")
public class UsageStat {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String textCategory; // e.g., "address"
    public String chosenAppPackage; // e.g., "com.google.android.apps.maps"
}
