package com.czar.user;

import com.czar.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CzarUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CzarUserApplication.class, args);
    }
}
