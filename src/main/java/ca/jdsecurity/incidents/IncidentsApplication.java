package ca.jdsecurity.incidents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.sql.*;

@SpringBootApplication
@EnableScheduling
public class IncidentsApplication {

	public static void main(String[] args) throws SQLException {
		SpringApplication.run(IncidentsApplication.class, args);
	}

}
