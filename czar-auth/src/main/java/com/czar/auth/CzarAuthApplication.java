package com.czar.auth;

import com.czar.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CzarAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(CzarAuthApplication.class, args);
    }
}
