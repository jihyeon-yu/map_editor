package com.caselab.forsk;

public class MapOptimization {
    public static native boolean mapRotation(String input_directory, String rot_map_directory, String map_name);
    public static native boolean lineOptimization(String rot_map_directory, String opt_map_directory, String map_name);
    public static native boolean mapSegmentation(String opt_map_directory, String seg_map_directory, String map_name);
}