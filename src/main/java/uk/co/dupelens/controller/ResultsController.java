package uk.co.dupelens.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uk.co.dupelens.model.FileGroup;
import uk.co.dupelens.service.ResultService;

import java.util.List;

@Controller
public class ResultsController {
    private final ResultService results;
    public ResultsController(ResultService results){this.results=results;}
    @GetMapping("/results") public String results(@RequestParam(defaultValue="exact") String tab,
            @RequestParam(defaultValue="0") long scanId,Model model){
        if(scanId<=0)scanId=results.latestScanId(); List<FileGroup> groups=results.groups(scanId,tab);
        model.addAttribute("tab",tab);model.addAttribute("scanId",scanId);model.addAttribute("groups",groups);
        model.addAttribute("reclaimable",groups.stream().mapToLong(FileGroup::reclaimableBytes).sum());
        return "results";
    }
}
