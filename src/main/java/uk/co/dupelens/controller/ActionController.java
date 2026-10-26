package uk.co.dupelens.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.co.dupelens.service.FileActionService;

import java.util.List;

@RequestMapping("/actions")
@Controller
public class ActionController {
    private final FileActionService actions;
    public ActionController(FileActionService actions){this.actions=actions;}

    @PostMapping("/trash") public String trash(@RequestParam(required=false) List<Long> ids,
            @RequestParam(defaultValue="exact") String tab,RedirectAttributes ra){
        var r=actions.trash(ids==null?List.of():ids); ra.addFlashAttribute("message","Moved "+r.succeeded()+" file(s) to Trash"+(r.errors().isEmpty()?"":"; "+String.join(" • ",r.errors()))); return "redirect:/results?tab="+tab;
    }
    @PostMapping("/move") public String move(@RequestParam(required=false) List<Long> ids,@RequestParam String destination,
            @RequestParam(defaultValue="exact") String tab,RedirectAttributes ra){
        var r=actions.move(ids==null?List.of():ids,destination);ra.addFlashAttribute("message","Moved "+r.succeeded()+" file(s)"+(r.errors().isEmpty()?"":"; "+String.join(" • ",r.errors())));return "redirect:/results?tab="+tab;
    }
    @PostMapping("/open") public String open(@RequestParam long id,@RequestParam(defaultValue="exact") String tab,RedirectAttributes ra){
        try{actions.openContainingFolder(id);}catch(Exception e){ra.addFlashAttribute("message","Could not open folder: "+e.getMessage());}return "redirect:/results?tab="+tab;
    }
}
