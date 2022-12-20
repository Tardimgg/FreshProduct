package com.example.freshproduct;

public class Result <T, E> {

    public final T value;
    public final E error;
    public final boolean isHaveValue;

    private Result(T value, E error) {
        this.value = value;
        this.error = error;
        isHaveValue = value != null;
    }

    public static <T1, E1> Result<T1, E1> some(T1 value) {
        return new Result<>(value, null);
    }

    public static <T1, E1> Result<T1, E1> error(E1 error) {
        return new Result<>(null, error);
    }
}
