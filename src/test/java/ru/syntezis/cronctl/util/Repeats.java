package ru.syntezis.cronctl.util;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@UtilityClass
public class Repeats {

    public static void runnableRepeat(int times, Runnable runnable) {
        IntStream.range(0, times)
                .forEach(i -> runnable.run());
    }

    public static <T> List<T> supplierRepeat(int times, Supplier<T> runnable) {
        return IntStream.range(0, times)
                .mapToObj(i -> runnable.get())
                .toList();
    }
}
