package uk.co.dupelens.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uk.co.dupelens.repository.ScanRepository;
import uk.co.dupelens.service.ResultService;
import uk.co.dupelens.service.ScanProgressService;
import uk.co.dupelens.service.SettingsService;

import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class HomeController {
    private final ScanProgressService progress; private final ResultService results; private final ScanRepository scans; private final SettingsService settings;
    public HomeController(ScanProgressService progress,ResultService results,ScanRepository scans,SettingsService settings){this.progress=progress;this.results=results;this.scans=scans;this.settings=settings;}
    @GetMapping("/") public String home(Model model){
        model.addAttribute("progress",progress.get()); model.addAttribute("latestScanId",results.latestScanId());
        model.addAttribute("history",scans.history().stream().limit(5).toList());
        Path pics=Path.of(System.getProperty("user.home"),"Pictures");
        String defaultRoot=Files.isDirectory(pics)?pics.toString():System.getProperty("user.home");
        var scanRoots=settings.getScanRoots();
        model.addAttribute("defaultRoot",scanRoots.isEmpty()?defaultRoot:String.join("\n",scanRoots));
        return "index";
    }
}
