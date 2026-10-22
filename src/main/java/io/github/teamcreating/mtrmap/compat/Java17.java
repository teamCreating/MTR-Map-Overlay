package io.github.teamcreating.mtrmap.compat;
import java.util.List;
public final class Java17 {
 private Java17() {}
 public static double clamp(double value, double min, double max) { return Math.min(max, Math.max(min, value)); }
 public static int clamp(int value, int min, int max) { return Math.min(max, Math.max(min, value)); }
 public static <T> T last(List<T> list) { return list.get(list.size() - 1); }
}
