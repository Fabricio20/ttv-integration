package net.notfab.ttvsi.client;

import com.formdev.flatlaf.themes.FlatMacLightLaf;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        FlatMacLightLaf.setup();
        new SpringApplicationBuilder(Application.class)
                .headless(false).run(args);
    }

}
