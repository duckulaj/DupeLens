package uk.co.dupelens.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uk.co.dupelens.repository.ScanRepository;

@Controller
public class HistoryController {
    private final ScanRepository scans; public HistoryController(ScanRepository scans){this.scans=scans;}
    @GetMapping("/history") public String history(Model model){model.addAttribute("history",scans.history());return "history";}
}
