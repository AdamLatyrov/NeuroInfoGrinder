package com.larbcorp.neuroinfogrinder.api.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/dashboard",
            "/accounts",
            "/groups",
            "/chat-viewer",
            "/pipeline",
            "/guides",
            "/rules",
            "/sources",
            "/classifiers",
            "/ai",
            "/ai-providers",
            "/prompts",
            "/test-lab",
            "/monitoring",
            "/traces",
            "/settings",
            "/login"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
