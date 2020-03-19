// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.Date;

/**
 * Polar exercise entry container.
 */
public class PolarExerciseEntry {
    /**
     * Resource path in device.
     */
    public String path;
    /**
     * Date object contains the date and time of the exercise. Only valid with OH1.
     */
    public Date date;
    /**
     * unique identifier. Only valid with H10
     */
    public String identifier;

    public PolarExerciseEntry(String path, Date date, String identifier) {
        this.path = path;
        this.date = date;
        this.identifier = identifier;
    }
}
