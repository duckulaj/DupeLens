package uk.co.dupelens.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.co.dupelens.model.ScanRequest;
import uk.co.dupelens.service.DuplicateScannerService;
import uk.co.dupelens.service.ScanProgressService;
import uk.co.dupelens.service.SettingsService;

import java.util.Arrays;

@Controller
@RequestMapping("/scan")
public class ScanController {
    private final DuplicateScannerService scanner; private final ScanProgressService progress; private final SettingsService settings;
    public ScanController(DuplicateScannerService scanner,ScanProgressService progress,SettingsService settings){this.scanner=scanner;this.progress=progress;this.settings=settings;}

    @PostMapping("/start") public String start(@RequestParam String roots,
            @RequestParam(defaultValue="false") boolean recursive,
            @RequestParam(defaultValue="false") boolean includeHidden,
            @RequestParam(defaultValue="false") boolean aiEnabled, Model model){
        var list=Arrays.stream(roots.split("\\R")).map(String::trim).filter(s->!s.isBlank()).toList();
        settings.saveScanRoots(list);
        scanner.start(new ScanRequest(list,recursive,includeHidden,aiEnabled)); model.addAttribute("progress",progress.get()); return "fragments/scan-progress :: panel";
    }
    @GetMapping("/progress") public String poll(Model model){model.addAttribute("progress",progress.get());return "fragments/scan-progress :: panel";}
    @PostMapping("/cancel") public String cancel(Model model){scanner.cancel();model.addAttribute("progress",progress.get());return "fragments/scan-progress :: panel";}
    @PostMapping("/pause") public String pause(Model model){scanner.pause();model.addAttribute("progress",progress.get());return "fragments/scan-progress :: panel";}
    @PostMapping("/resume") public String resume(Model model){scanner.resume();model.addAttribute("progress",progress.get());return "fragments/scan-progress :: panel";}
}
