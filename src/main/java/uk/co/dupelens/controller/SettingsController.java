package uk.co.dupelens.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.co.dupelens.model.AppSettings;
import uk.co.dupelens.service.SettingsService;
import uk.co.dupelens.service.SystemStatusService;

@Controller
public class SettingsController {
    private final SettingsService settings; private final SystemStatusService status;
    public SettingsController(SettingsService settings,SystemStatusService status){this.settings=settings;this.status=status;}
    @GetMapping("/settings") public String page(Model model){model.addAttribute("settings",settings.get());model.addAttribute("status",status.status());return "settings";}
    @PostMapping("/settings") public String save(
            @RequestParam int imageHammingThreshold,
            @RequestParam int videoHammingThreshold,
            @RequestParam double videoDurationTolerance,
            @RequestParam String ollamaUrl,
            @RequestParam String aiModel,
            @RequestParam String preferredPaths,
            @RequestParam String exclusions,
            @RequestParam long minFileSizeBytes,
            @RequestParam long maxFileSizeBytes,
            @RequestParam String keeperStrategy,
            RedirectAttributes ra){
        settings.save(new AppSettings(imageHammingThreshold,videoHammingThreshold,videoDurationTolerance,ollamaUrl,aiModel,preferredPaths,exclusions,minFileSizeBytes,maxFileSizeBytes,keeperStrategy));
        ra.addFlashAttribute("message","Settings saved");return "redirect:/settings";
    }
}
