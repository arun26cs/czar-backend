package com.czar.planner;

import com.czar.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class CzarPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CzarPlannerApplication.class, args);
    }
}
