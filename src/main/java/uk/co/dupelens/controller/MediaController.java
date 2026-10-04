package uk.co.dupelens.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import uk.co.dupelens.repository.FileRepository;
import uk.co.dupelens.service.ThumbnailService;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class MediaController {
    private final FileRepository files; private final ThumbnailService thumbs;
    public MediaController(FileRepository files,ThumbnailService thumbs){this.files=files;this.thumbs=thumbs;}

    @GetMapping("/thumb/{id}") public ResponseEntity<FileSystemResource> thumb(@PathVariable long id){
        try{var f=files.findById(id).orElseThrow();Path p=thumbs.thumbnail(f);return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7))).body(new FileSystemResource(p));}
        catch(Exception e){return ResponseEntity.notFound().build();}
    }
    @GetMapping("/preview/{id}") public ResponseEntity<FileSystemResource> preview(@PathVariable long id){
        try{var f=files.findById(id).orElseThrow();Path p=Path.of(f.path());if(!Files.exists(p))return ResponseEntity.notFound().build();String ct=Files.probeContentType(p);MediaType mt=ct==null?MediaType.APPLICATION_OCTET_STREAM:MediaType.parseMediaType(ct);return ResponseEntity.ok().contentType(mt).body(new FileSystemResource(p));}
        catch(Exception e){return ResponseEntity.notFound().build();}
    }
}
