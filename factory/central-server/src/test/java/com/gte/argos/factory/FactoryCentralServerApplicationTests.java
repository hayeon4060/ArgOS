package com.gte.argos.factory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FactoryCentralServerApplicationTests {

    @Test
    void applicationClassIsAvailable() {
        assertThat(FactoryCentralServerApplication.class).isNotNull();
    }
}

