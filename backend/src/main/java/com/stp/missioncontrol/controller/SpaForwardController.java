package com.stp.missioncontrol.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards non-API routes to index.html for SPA client-side routing.
 * Only active when the frontend build is bundled in the JAR (static/index.html exists).
 * When running the backend as a standalone API, this controller is not loaded.
 */
@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaForwardController {

    @RequestMapping(value = {
            "/",
            "/clusters/**",
            "/metrics/**",
            "/audit/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
