package org.gluu.agama.forgetusername;

public class CounterHelper {
    
    public static int increment(int currentValue) {
        return currentValue + 1;
    }
    
    
    public static int incrementBy(int currentValue, int incrementBy) {
        return currentValue + incrementBy;
    }
    
    public static boolean hasReachedMax(int currentValue, int maxValue) {
        return currentValue >= maxValue;
    }
}