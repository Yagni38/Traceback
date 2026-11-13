package com.traceback.traceback;

import com.traceback.traceback.config.DatabaseUrlConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TracebackApplication {

	public static void main(String[] args) {
		// Turn a hosting provider's DATABASE_URL (postgres://...) into JDBC settings
		DatabaseUrlConverter.applyFromEnvironment();

		SpringApplication.run(TracebackApplication.class, args);
	}

}
