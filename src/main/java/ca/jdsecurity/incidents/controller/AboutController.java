package ca.jdsecurity.incidents.controller;

import ca.jdsecurity.incidents.service.CityOfWinnipegService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Methodology page. The retention windows it quotes are read from configuration rather
 * than written into the copy, so the page cannot drift out of step with what the app does.
 */
@Controller
public class AboutController {

    private final CityOfWinnipegService cityOfWinnipegService;

    public AboutController(CityOfWinnipegService cityOfWinnipegService) {
        this.cityOfWinnipegService = cityOfWinnipegService;
    }

    @GetMapping("/about")
    public String about(Model model) {
        model.addAttribute("callWindowHours", cityOfWinnipegService.getCallWindowHours());
        model.addAttribute("closedWindowHours", cityOfWinnipegService.getClosedWindowHours());
        return "about";
    }
}
