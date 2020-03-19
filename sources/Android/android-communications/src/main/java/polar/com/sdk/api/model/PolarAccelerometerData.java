// Copyright © 2019 Polar Electro Oy. All rights reserved.
package polar.com.sdk.api.model;

import java.util.List;

/**
 * For accelerometer data.
 */
public class PolarAccelerometerData {

	/**
	 * Static class containing the data of a single ACC sample. Data is received as a 16-bit short value.
	 */
	public static class PolarAccelerometerSample {
		/**
		 * Accelerometer x axis value in millig.
		 */
		public int x;

		/**
		 * Accelerometer y axis value millig.
		 */
		public int y;

		/**
		 * Accelerometer z axis value millig.
		 */
		public int z;

		public PolarAccelerometerSample(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
	}

	/**
     * Acceleration samples list. Each sample contains signed x,y,z axis value in millig
     */
    public List<PolarAccelerometerSample> samples;

    /**
     * Last sample timestamp in nanoseconds.
     */
    public long timeStamp;

	/**
     * Class constructor
     * @param 	samples list of Accelerometer data samples
     * @param 	timeStamp in nanoseconds
     */
    public PolarAccelerometerData(List<PolarAccelerometerSample> samples, long timeStamp) {
        this.samples = samples;
        this.timeStamp = timeStamp;
    }
}
