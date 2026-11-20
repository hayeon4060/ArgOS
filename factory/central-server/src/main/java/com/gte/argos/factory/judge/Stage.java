package com.gte.argos.factory.judge;

/**
 * 센서값 하나의 단계. DB 의 stage 컬럼과 같은 숫자를 쓴다 (1=정상, 2=주의, 3=경보).
 */
public enum Stage {
    NORMAL(1),
    WARN(2),
    ALARM(3);

    private final int code;

    Stage(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public boolean atLeast(Stage other) {
        return code >= other.code;
    }

    public static Stage max(Stage a, Stage b) {
        return a.code >= b.code ? a : b;
    }
}
