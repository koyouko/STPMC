package com.stp.missioncontrol.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static requests to index.html for SPA routing.
 * Only active when the frontend dist is served from the same Spring Boot JAR.
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = {
            "/",
            "/clusters/**",
            "/self-service/**",
            "/audit/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
