package uk.co.dupelens.controller;

import uk.co.dupelens.model.FolderEntry;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.*;
import java.util.Comparator;

@Controller
public class FolderController {
    @GetMapping("/folders") public String folders(@RequestParam(required=false) String path,Model model){
        Path current;
        try{current=path==null||path.isBlank()?Path.of(System.getProperty("user.home")):Path.of(path).toAbsolutePath().normalize();if(!Files.isDirectory(current))current=Path.of(System.getProperty("user.home"));}
        catch(Exception e){current=Path.of(System.getProperty("user.home"));}
        try(var stream=Files.list(current)){model.addAttribute("dirs",stream.filter(Files::isDirectory).sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase())).limit(250).map(p->new FolderEntry(p.getFileName().toString(),p.toString())).toList());}
        catch(Exception e){model.addAttribute("dirs",java.util.List.of());}
        model.addAttribute("current",current);model.addAttribute("parent",current.getParent());return "fragments/folder-browser :: browser";
    }
}
