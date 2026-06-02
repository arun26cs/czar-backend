package com.czar.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CzarUserApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts cleanly with H2 test datasource,
        // no Pub/Sub emulator, no JWT key file required.
    }
}

