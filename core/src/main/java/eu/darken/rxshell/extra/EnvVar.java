package eu.darken.rxshell.extra;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class EnvVar<F, S> {
    public final @Nullable F first;
    public final @Nullable S second;

    public EnvVar(@Nullable F first, @Nullable S second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "EnvVar{" + String.valueOf(first) + " " + String.valueOf(second) + "}";
    }

    @NonNull
    public static <A, B> EnvVar<A, B> create(@Nullable A a, @Nullable B b) {
        return new EnvVar<>(a, b);
    }
}
