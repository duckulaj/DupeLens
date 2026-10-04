package uk.co.dupelens.service;

import org.springframework.stereotype.Service;
import uk.co.dupelens.model.*;
import uk.co.dupelens.repository.FileRepository;
import uk.co.dupelens.repository.ScanRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResultService {
    private final FileRepository files;
    private final ScanRepository scans;
    private final SettingsService settings;

    public ResultService(FileRepository files, ScanRepository scans, SettingsService settings) {
        this.files=files; this.scans=scans; this.settings=settings;
    }

    public long latestScanId(){ return scans.latestCompletedScanId(); }

    public List<FileGroup> groups(long scanId,String tab){
        if(scanId<=0)return List.of();
        List<IndexedFile> all=files.findByScan(scanId);
        return switch(tab){
            case "images" -> grouped(all, IndexedFile::similarImageGroup,"image");
            case "videos" -> grouped(all, IndexedFile::similarVideoGroup,"video");
            case "ai" -> grouped(all, IndexedFile::aiGroup,"ai");
            default -> grouped(all, IndexedFile::duplicateGroup,"exact");
        };
    }

    private List<FileGroup> grouped(List<IndexedFile> all, Function<IndexedFile,String> keyFn,String type){
        Map<String,List<IndexedFile>> map=all.stream().filter(f->keyFn.apply(f)!=null).collect(Collectors.groupingBy(keyFn,LinkedHashMap::new,Collectors.toList()));
        List<FileGroup> out=new ArrayList<>();
        for(var e:map.entrySet()){
            List<IndexedFile> g=e.getValue().stream().sorted(Comparator.comparing(IndexedFile::path)).toList();
            if(g.isEmpty())continue;
            Set<Long> suggested=Set.of(); long reclaim=0; String title; String subtitle;
            if(type.equals("exact")){
                IndexedFile keeper=chooseKeeper(g);
                suggested=g.stream().filter(f->f.id()!=keeper.id()).map(IndexedFile::id).collect(Collectors.toCollection(LinkedHashSet::new));
                reclaim=suggested.stream().map(id->g.stream().filter(f->f.id()==id).findFirst().orElse(null)).filter(Objects::nonNull).mapToLong(IndexedFile::size).sum();
                title=g.size()+" exact copies"; subtitle="Safe to remove selected redundant copies • SHA-256 verified";
            } else if(type.equals("image")){
                title=g.size()+" visually similar images"; subtitle="Review only — similar files are never auto-selected";
            } else if(type.equals("video")){
                title=g.size()+" similar videos"; subtitle="Matched by duration and sampled frame fingerprints • review only";
            } else {
                IndexedFile f=g.getFirst(); title=cap(f.aiCategory())+" · "+cap(f.aiScene());
                subtitle=(f.aiKeywords()==null||f.aiKeywords().isBlank())?"AI-assisted local grouping":f.aiKeywords();
            }
            out.add(new FileGroup(e.getKey(),type,title,subtitle,g,suggested,reclaim));
        }
        return out.stream().sorted(Comparator.comparing(FileGroup::id)).toList();
    }

    private IndexedFile chooseKeeper(List<IndexedFile> g){
        AppSettings cfg=settings.get();
        List<String> preferred=Arrays.stream(cfg.preferredPaths().split("\\R")).map(String::trim).filter(s->!s.isBlank()).toList();
        String strategy=cfg.keeperStrategy()==null?"PREFERRED":cfg.keeperStrategy().toUpperCase(Locale.ROOT);
        Comparator<IndexedFile> cmp=switch(strategy){
            case "OLDEST" -> Comparator.comparingLong(IndexedFile::modified).thenComparingInt(f->f.path().length());
            case "NEWEST" -> Comparator.comparingLong(IndexedFile::modified).reversed().thenComparingInt(f->f.path().length());
            case "SHORTEST" -> Comparator.comparingInt((IndexedFile f)->f.path().length()).thenComparingLong(IndexedFile::modified);
            default -> Comparator.comparingInt((IndexedFile f)->preference(f.path(),preferred)).thenComparingLong(IndexedFile::modified).thenComparingInt(f->f.path().length());
        };
        return g.stream().min(cmp).orElse(g.getFirst());
    }
    private int preference(String p,List<String> preferred){ for(int i=0;i<preferred.size();i++) if(p.startsWith(preferred.get(i)))return i; return 10000; }
    private String cap(String s){ if(s==null||s.isBlank())return "Other"; return Character.toUpperCase(s.charAt(0))+s.substring(1); }
}
