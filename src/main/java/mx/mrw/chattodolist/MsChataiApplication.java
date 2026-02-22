package mx.mrw.chattodolist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsChataiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsChataiApplication.class, args);
	}

}
