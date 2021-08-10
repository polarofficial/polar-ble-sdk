// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model;

import androidx.annotation.NonNull;

import java.util.Date;

/**
 * Polar exercise entry container.
 */
public class PolarExerciseEntry {
    /**
     * Resource path in device.
     */
    public final String path;
    /**
     * Date object contains the date and time of the exercise. Only valid with OH1 and Verity Sense.
     */
    public final Date date;
    /**
     * unique identifier. Only valid with H10
     */
    public final String identifier;

    public PolarExerciseEntry(@NonNull String path, @NonNull Date date, @NonNull String identifier) {
        this.path = path;
        this.date = date;
        this.identifier = identifier;
    }
}
