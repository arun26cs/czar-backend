package com.czar.notes;

import com.czar.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CzarNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(CzarNotesApplication.class, args);
    }
}
