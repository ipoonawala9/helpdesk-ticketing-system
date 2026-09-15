package com.ibrahim.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class HelpDeskApplication {

    /**
     * Timestamps are stored and returned without a zone, so the zone they mean
     * must be fixed rather than whatever the host happens to use. Everything
     * runs in UTC; clients convert to the viewer's local time.
     */
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }

}
