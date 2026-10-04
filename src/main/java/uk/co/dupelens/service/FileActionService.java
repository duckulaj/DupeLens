package uk.co.dupelens.service;

import org.springframework.stereotype.Service;
import uk.co.dupelens.model.IndexedFile;
import uk.co.dupelens.repository.FileRepository;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class FileActionService {
    private final FileRepository files;
    public FileActionService(FileRepository files){this.files=files;}

    public ActionResult trash(Collection<Long> ids) {
        List<IndexedFile> targets=files.findByIds(ids); List<Long> success=new ArrayList<>(); List<String> errors=new ArrayList<>();
        if(!commandExists("gio")) return new ActionResult(0,List.of("Linux Trash support (gio) is not available. Nothing was deleted."));
        for(IndexedFile f:targets){
            try{
                Path p=Path.of(f.path()); if(!Files.exists(p)){ success.add(f.id()); continue; }
                Process proc=new ProcessBuilder("gio","trash",p.toString()).redirectErrorStream(true).start();
                String output=new String(proc.getInputStream().readAllBytes());
                if(proc.waitFor(30,TimeUnit.SECONDS)&&proc.exitValue()==0)success.add(f.id()); else errors.add(f.name()+": "+output.trim());
            }catch(Exception e){errors.add(f.name()+": "+e.getMessage());}
        }
        files.deleteRows(success); return new ActionResult(success.size(),errors);
    }

    public ActionResult move(Collection<Long> ids,String destination) {
        List<IndexedFile> targets=files.findByIds(ids); List<Long> success=new ArrayList<>(); List<String> errors=new ArrayList<>();
        try{Files.createDirectories(Path.of(destination));}catch(Exception e){return new ActionResult(0,List.of("Cannot create destination: "+e.getMessage()));}
        for(IndexedFile f:targets){
            try{Path src=Path.of(f.path()), dest=unique(Path.of(destination).resolve(src.getFileName())); Files.move(src,dest); success.add(f.id());}
            catch(Exception e){errors.add(f.name()+": "+e.getMessage());}
        }
        files.deleteRows(success); return new ActionResult(success.size(),errors);
    }

    public void openContainingFolder(long id) throws Exception {
        IndexedFile f=files.findById(id).orElseThrow(); Path parent=Path.of(f.path()).getParent();
        if(parent==null)throw new IllegalArgumentException("No parent folder");
        new ProcessBuilder("xdg-open",parent.toString()).start();
    }

    private Path unique(Path p){ if(!Files.exists(p))return p; String n=p.getFileName().toString(); int dot=n.lastIndexOf('.'); String base=dot>0?n.substring(0,dot):n, ext=dot>0?n.substring(dot):""; int i=1; Path c; do{c=p.getParent().resolve(base+" ("+(i++)+")"+ext);}while(Files.exists(c));return c; }
    private boolean commandExists(String c){try{Process p=new ProcessBuilder("sh","-c","command -v "+c).start();return p.waitFor(2,TimeUnit.SECONDS)&&p.exitValue()==0;}catch(Exception e){return false;}}
    public record ActionResult(int succeeded,List<String> errors){}
}
