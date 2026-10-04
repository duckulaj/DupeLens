package uk.co.dupelens.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import uk.co.dupelens.model.*;
import uk.co.dupelens.repository.FileRepository;
import uk.co.dupelens.repository.ScanRepository;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DuplicateScannerService {
    private static final Logger log = LoggerFactory.getLogger(DuplicateScannerService.class);
    private static final int AI_REQUEST_CONCURRENCY = 2;
    private final FileRepository files;
    private final ScanRepository scans;
    private final SettingsService settings;
    private final FileClassifier classifier;
    private final HashService hashes;
    private final PerceptualHashService perceptual;
    private final VideoFingerprintService videos;
    private final OllamaAiService ai;
    private final ScanProgressService progress;
    private final MeterRegistry meters;
    private final Counter aiAnalyzed;
    private final Counter aiCached;
    private final Counter aiFailed;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile String phaseBeforePause = "DISCOVERING";
    private volatile String messageBeforePause = "Scanning";

    public DuplicateScannerService(FileRepository files, ScanRepository scans, SettingsService settings,
                                   FileClassifier classifier, HashService hashes, PerceptualHashService perceptual,
                                   VideoFingerprintService videos, OllamaAiService ai, ScanProgressService progress,
                                   MeterRegistry meters) {
        this.files=files; this.scans=scans; this.settings=settings; this.classifier=classifier; this.hashes=hashes;
        this.perceptual=perceptual; this.videos=videos; this.ai=ai; this.progress=progress; this.meters=meters;
        this.aiAnalyzed=Counter.builder("dupelens.ai.images").tag("outcome","analyzed").register(meters);
        this.aiCached=Counter.builder("dupelens.ai.images").tag("outcome","cached").register(meters);
        this.aiFailed=Counter.builder("dupelens.ai.images").tag("outcome","failed").register(meters);
    }

    public synchronized boolean start(ScanRequest request) {
        if(progress.isRunning()) return false;
        List<String> roots=request.roots().stream().map(String::trim).filter(s->!s.isBlank()).distinct().toList();
        if(roots.isEmpty()) return false;
        long id=scans.create(roots); cancel.set(false); paused.set(false); progress.begin(id);
        executor.submit(() -> run(id,new ScanRequest(roots,request.recursive(),request.includeHidden(),request.aiEnabled())));
        return true;
    }

    public void cancel(){ cancel.set(true); paused.set(false); }
    public void pause(){
        if(!progress.isRunning() || paused.get()) return;
        var s=progress.get(); phaseBeforePause=s.phase(); messageBeforePause=s.message(); paused.set(true);
        progress.phase("PAUSED","Scan paused",s.progressPercent());
    }
    public void resume(){
        if(!progress.isRunning() || !paused.get()) return;
        paused.set(false); progress.phase(phaseBeforePause,messageBeforePause,progress.get().progressPercent());
    }

    private void run(long scanId, ScanRequest request) {
        try {
            AppSettings cfg=settings.get();
            timedStage(scanId,"discovery",()->{discover(scanId,request,cfg);return null;});
            checkCancel();
            files.clearGroups(scanId);
            ResultCounts exact=timedStage(scanId,"exact_duplicates",()->findExact(scanId));
            progress.counts(exact.groups,exact.files,exact.reclaimable,0,0,0);
            checkCancel();
            int imageGroups=timedStage(scanId,"similar_images",()->findSimilarImages(scanId,cfg.imageHammingThreshold()));
            progress.counts(exact.groups,exact.files,exact.reclaimable,imageGroups,0,0);
            checkCancel();
            int videoGroups=timedStage(scanId,"similar_videos",()->findSimilarVideos(scanId,cfg.videoHammingThreshold(),cfg.videoDurationTolerance()));
            progress.counts(exact.groups,exact.files,exact.reclaimable,imageGroups,videoGroups,0);
            checkCancel();
            int aiGroups=request.aiEnabled()?timedStage(scanId,"ai_grouping",()->runAi(scanId)):0;
            progress.counts(exact.groups,exact.files,exact.reclaimable,imageGroups,videoGroups,aiGroups);
            timedStage(scanId,"finalising",()->{
                progress.phase("FINALISING","Finalising scan",99);
                progress.done(false,null);
                scans.finish(progress.get(),"COMPLETED");
                return null;
            });
        } catch(CancelledException e) {
            progress.done(true,null); scans.finish(progress.get(),"CANCELLED");
        } catch(Exception e) {
            log.error("Scan failed",e); progress.done(false,e.getMessage()); scans.finish(progress.get(),"FAILED");
        }
    }

    private <T> T timedStage(long scanId,String stage,TimedOperation<T> operation) throws Exception {
        Timer.Sample sample=Timer.start(meters);
        long started=System.nanoTime();
        String outcome="completed";
        try {
            return operation.run();
        } catch(Exception e) {
            outcome="failed";
            throw e;
        } finally {
            sample.stop(meters.timer("dupelens.scan.phase","phase",stage));
            log.info("Scan {} stage {} {} in {} ms",scanId,stage,outcome,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        }
    }

    @FunctionalInterface
    private interface TimedOperation<T> {
        T run() throws Exception;
    }

    private void discover(long scanId, ScanRequest request, AppSettings cfg) throws IOException {
        checkCancel();
        progress.phase("DISCOVERING","Discovering files",-1);
        Set<String> exclusions=Arrays.stream(cfg.exclusions().split("\\R")).map(String::trim).filter(s->!s.isBlank()).collect(Collectors.toSet());
        AtomicLong count=new AtomicLong(), bytes=new AtomicLong();
        for(String rootText:request.roots()) {
            checkCancel();
            Path root=Path.of(rootText).toAbsolutePath().normalize();
            if(!Files.isDirectory(root)) { log.warn("Skipping non-directory root {}",root); continue; }
            Files.walkFileTree(root,new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                    if(cancel.get()) return FileVisitResult.TERMINATE;
                    checkCancel();
                    if(!dir.equals(root) && (!request.recursive() || excluded(dir,exclusions) || (!request.includeHidden() && hidden(dir)))) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) {
                    if(cancel.get()) return FileVisitResult.TERMINATE;
                    checkCancel();
                    try {
                        if(!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.size()<cfg.minFileSizeBytes() || (cfg.maxFileSizeBytes()>0 && attrs.size()>cfg.maxFileSizeBytes())) return FileVisitResult.CONTINUE;
                        if(excluded(file,exclusions) || (!request.includeHidden() && hidden(file))) return FileVisitResult.CONTINUE;
                        files.upsertDiscovered(file.toAbsolutePath().normalize().toString(),attrs.size(),attrs.lastModifiedTime().toMillis(),classifier.classify(file),scanId);
                        long c=count.incrementAndGet(), b=bytes.addAndGet(attrs.size());
                        if(c%25==0) progress.current(file.toString(),c,b,-1);
                    } catch(Exception e){ log.warn("Skipping {}: {}",file,e.getMessage()); }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file,IOException exc){ log.debug("Cannot visit {}: {}",file,exc.toString()); return FileVisitResult.CONTINUE; }
            });
        }
        progress.current("",count.get(),bytes.get(),20);
    }

    private ResultCounts findExact(long scanId) throws Exception {
        checkCancel();
        progress.phase("EXACT_DUPLICATES","Finding byte-identical duplicates",20);
        List<IndexedFile> all=files.findByScan(scanId);
        Map<Long,List<IndexedFile>> bySize=all.stream().collect(Collectors.groupingBy(IndexedFile::size));
        List<List<IndexedFile>> candidates=bySize.values().stream().filter(g->g.size()>1).toList();
        int work=Math.max(1,candidates.stream().mapToInt(List::size).sum()), done=0, groupNo=0, redundant=0; long reclaim=0;
        for(List<IndexedFile> sameSize:candidates){
            checkCancel();
            Map<String,List<IndexedFile>> byPartial=new LinkedHashMap<>();
            for(IndexedFile f:sameSize){
                checkCancel();
                String p=f.partialHash();
                if(p==null){ try{p=hashes.partialHash(Path.of(f.path())); files.updatePartialHash(f.id(),p);}catch(Exception e){log.warn("Partial hash failed {}: {}: {}",f.path(),e.getClass().getSimpleName(),e.getMessage()); continue;} }
                byPartial.computeIfAbsent(p,k->new ArrayList<>()).add(f); done++; progress.current(f.path(),progress.get().filesSeen(),progress.get().bytesSeen(),20+(int)(12.0*done/work));
            }
            for(List<IndexedFile> partialGroup:byPartial.values()) if(partialGroup.size()>1){
                Map<String,List<IndexedFile>> byFull=new LinkedHashMap<>();
                for(IndexedFile f:partialGroup){
                    checkCancel();
                    String h=f.fullHash();
                    if(h==null){ try{h=hashes.fullHash(Path.of(f.path())); files.updateFullHash(f.id(),h);}catch(Exception e){log.warn("Full hash failed {}",f.path()); continue;} }
                    byFull.computeIfAbsent(h,k->new ArrayList<>()).add(f);
                }
                for(List<IndexedFile> g:byFull.values()) if(g.size()>1){
                    groupNo++; String id="D"+groupNo; files.setDuplicateGroup(g.stream().map(IndexedFile::id).toList(),id);
                    redundant+=g.size()-1; reclaim+=(long)(g.size()-1)*g.getFirst().size();
                }
            }
        }
        progress.phase("EXACT_DUPLICATES","Exact duplicate scan complete",45);
        return new ResultCounts(groupNo,redundant,reclaim);
    }

    private int findSimilarImages(long scanId,int threshold) {
        checkCancel();
        progress.phase("SIMILAR_IMAGES","Comparing image appearance",45);
        List<IndexedFile> imgs=files.findByScanAndType(scanId,MediaType.IMAGE);
        Map<Long,String> hashById=new HashMap<>(); int done=0;
        for(IndexedFile f:imgs){ checkCancel(); String h=f.perceptualHash();
            if(h==null){ try{h=perceptual.dHash(Path.of(f.path())); files.updatePerceptualHash(f.id(),h);}catch(Exception e){log.debug("Image hash failed {}: {}",f.path(),e.getMessage());} }
            if(h!=null) hashById.put(f.id(),h); done++; progress.current(f.path(),progress.get().filesSeen(),progress.get().bytesSeen(),45+(int)(10.0*done/Math.max(1,imgs.size())));
        }
        UnionFind uf=new UnionFind(); imgs.forEach(f->uf.add(f.id()));
        Map<String,List<Long>> bands=new HashMap<>(); Set<String> compared=new HashSet<>();
        Map<Long,IndexedFile> byId=imgs.stream().collect(Collectors.toMap(IndexedFile::id,Function.identity()));
        for(var e:hashById.entrySet()){
            checkCancel();
            long value=Long.parseUnsignedLong(e.getValue(),16);
            for(int band=0;band<16;band++){
                int b=(int)((value >>> (band*4)) & 0x0fL); String key=band+":"+b;
                for(Long other:bands.getOrDefault(key,List.of())){
                    long a=Math.min(e.getKey(),other), z=Math.max(e.getKey(),other); String pair=a+":"+z;
                    if(compared.add(pair) && perceptual.hamming(e.getValue(),hashById.get(other))<=threshold) uf.union(a,z);
                }
                bands.computeIfAbsent(key,k->new ArrayList<>()).add(e.getKey());
            }
        }
        Map<Long,List<Long>> clusters=uf.groups().values().stream().filter(g->g.size()>1).collect(Collectors.toMap(g->g.getFirst(),Function.identity()));
        int n=0;
        for(List<Long> g:clusters.values()){
            Set<String> identities=g.stream().map(byId::get).filter(Objects::nonNull).map(f->f.fullHash()!=null?f.fullHash():"id:"+f.id()).collect(Collectors.toSet());
            if(identities.size()<2) continue;
            files.setSimilarImageGroup(g,"I"+(++n));
        }
        progress.phase("SIMILAR_IMAGES","Similar image scan complete",65); return n;
    }

    private int findSimilarVideos(long scanId,int threshold,double durationTolerance) {
        checkCancel();
        progress.phase("SIMILAR_VIDEOS","Fingerprinting videos",65);
        if(!videos.available()){ progress.phase("SIMILAR_VIDEOS","FFmpeg/ffprobe not installed; video similarity skipped",80); return 0; }
        List<IndexedFile> vids=files.findByScanAndType(scanId,MediaType.VIDEO); Map<Long,String> fp=new HashMap<>(); int done=0;
        for(IndexedFile f:vids){ checkCancel(); String v=f.videoFingerprint();
            if(v==null){ try{v=videos.fingerprint(Path.of(f.path())); files.updateVideoFingerprint(f.id(),v);}catch(Exception e){log.warn("Video fingerprint failed {}: {}",f.path(),e.getMessage());} }
            if(v!=null) fp.put(f.id(),v); done++; progress.current(f.path(),progress.get().filesSeen(),progress.get().bytesSeen(),65+(int)(8.0*done/Math.max(1,vids.size())));
        }
        UnionFind uf=new UnionFind(); vids.forEach(f->uf.add(f.id())); Map<Long,List<Long>> buckets=new HashMap<>();
        for(var e:fp.entrySet()){ checkCancel(); var parsed=videos.parse(e.getValue()); if(parsed==null)continue; long bucket=Math.round(parsed.durationMs()/10000.0);
            for(long b=bucket-1;b<=bucket+1;b++) for(Long other:buckets.getOrDefault(b,List.of())) if(videos.similar(e.getValue(),fp.get(other),threshold,durationTolerance)) uf.union(e.getKey(),other);
            buckets.computeIfAbsent(bucket,k->new ArrayList<>()).add(e.getKey());
        }
        Map<Long,IndexedFile> byId=vids.stream().collect(Collectors.toMap(IndexedFile::id,Function.identity())); int n=0;
        for(List<Long> g:uf.groups().values()) if(g.size()>1){
            Set<String> identities=g.stream().map(byId::get).filter(Objects::nonNull).map(f->f.fullHash()!=null?f.fullHash():"id:"+f.id()).collect(Collectors.toSet());
            if(identities.size()<2) continue; files.setSimilarVideoGroup(g,"V"+(++n));
        }
        progress.phase("SIMILAR_VIDEOS","Similar video scan complete",80); return n;
    }

    private int runAi(long scanId) {
        checkCancel();
        progress.phase("AI_GROUPING","Checking local Ollama",80);
        if(!ai.available()){ progress.phase("AI_GROUPING","Ollama is unavailable; AI grouping skipped",98); return 0; }
        List<IndexedFile> images=files.findByScanAndType(scanId,MediaType.IMAGE);
        Map<String,IndexedFile> reps=new LinkedHashMap<>();
        for(IndexedFile f:images){ String key=f.fullHash()!=null?"hash:"+f.fullHash():"id:"+f.id(); reps.putIfAbsent(key,f); }
        int done=0;
        ExecutorService aiExecutor=Executors.newFixedThreadPool(AI_REQUEST_CONCURRENCY);
        try {
            ExecutorCompletionService<String> completedImages=new ExecutorCompletionService<>(aiExecutor);
            for(IndexedFile f:reps.values()) completedImages.submit(()->processAiImage(scanId,f));
            while(done<reps.size()){
                checkCancel();
                Future<String> completed;
                try {
                    completed=completedImages.poll(150,TimeUnit.MILLISECONDS);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancelledException();
                }
                if(completed==null) continue;
                String path;
                try {
                    path=completed.get();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancelledException();
                } catch(ExecutionException e) {
                    Throwable cause=e.getCause();
                    if(cause instanceof CancelledException) throw new CancelledException();
                    throw new IllegalStateException("AI image processing failed",cause);
                }
                done++;
                progress.current(path,progress.get().filesSeen(),progress.get().bytesSeen(),
                        80+(int)(17.0*done/Math.max(1,reps.size())));
            }
        } finally {
            aiExecutor.shutdownNow();
        }
        int n=timedAiGrouping(()->{
            List<IndexedFile> processedImages=files.findByScanAndType(scanId,MediaType.IMAGE);
            Map<String,List<Long>> groups=new TreeMap<>();
            for(IndexedFile f:processedImages) if(f.aiProcessed()){
                String key=nvl(f.aiCategory(),"other").trim().toLowerCase(Locale.ROOT)+"|"+nvl(f.aiScene(),"unknown").trim().toLowerCase(Locale.ROOT);
                groups.computeIfAbsent(key,k->new ArrayList<>()).add(f.id());
            }
            int groupCount=0;
            for(List<Long> ids:groups.values()) files.setAiGroup(ids,"A"+(++groupCount));
            return groupCount;
        });
        progress.phase("AI_GROUPING","AI grouping complete",98); return n;
    }

    private String processAiImage(long scanId,IndexedFile f) {
        checkCancel();
        if(f.aiProcessed()){
            aiCached.increment();
            AiAnalysis existing=new AiAnalysis(nvl(f.aiCategory(),"other"),nvl(f.aiScene(),"unknown"),
                    nvl(f.aiSubjects(),""),nvl(f.aiKeywords(),""),nvl(f.aiSummary(),""));
            if(f.fullHash()!=null) timeAiDatabase(()->files.updateAiByFullHash(scanId,f.fullHash(),existing));
        } else {
            try {
                AiAnalysis a=ai.analyse(Path.of(f.path()));
                timeAiDatabase(()->{
                    files.updateAi(f.id(),a);
                    if(f.fullHash()!=null)files.updateAiByFullHash(scanId,f.fullHash(),a);
                });
                aiAnalyzed.increment();
            } catch(Exception e) {
                if(cancel.get()) throw new CancelledException();
                aiFailed.increment();
                log.warn("AI analysis failed {}: {}",f.path(),e.getMessage());
            }
        }
        return f.path();
    }

    private void timeAiDatabase(Runnable operation) {
        Timer.Sample sample=Timer.start(meters);
        try { operation.run(); }
        finally { sample.stop(meters.timer("dupelens.ai.database")); }
    }

    private <T> T timedAiGrouping(java.util.function.Supplier<T> operation) {
        Timer.Sample sample=Timer.start(meters);
        try { return operation.get(); }
        finally { sample.stop(meters.timer("dupelens.ai.grouping")); }
    }

    private boolean excluded(Path p,Set<String> patterns){
        String name=p.getFileName()==null?p.toString():p.getFileName().toString();
        for(String pattern:patterns){
            if(name.equals(pattern))return true;
            if(pattern.contains("*")||pattern.contains("?")||pattern.contains("[")) try{if(FileSystems.getDefault().getPathMatcher("glob:"+pattern).matches(Path.of(name)))return true;}catch(Exception ignored){}
        }
        return false;
    }
    private boolean hidden(Path p){ try{return Files.isHidden(p);}catch(Exception e){return p.getFileName()!=null&&p.getFileName().toString().startsWith(".");} }
    private void checkCancel(){
        if(cancel.get())throw new CancelledException();
        while(paused.get()){
            if(cancel.get())throw new CancelledException();
            try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancelledException();}
        }
    }
    private String nvl(String s,String d){return s==null||s.isBlank()?d:s;}
    private record ResultCounts(int groups,int files,long reclaimable){}
    private static class CancelledException extends RuntimeException{}

    private static class UnionFind {
        private final Map<Long,Long> parent=new HashMap<>();
        void add(long x){parent.putIfAbsent(x,x);} long find(long x){long p=parent.getOrDefault(x,x); if(p!=x){p=find(p);parent.put(x,p);}return p;}
        void union(long a,long b){add(a);add(b);long x=find(a),y=find(b);if(x!=y)parent.put(y,x);}
        Map<Long,List<Long>> groups(){Map<Long,List<Long>> g=new LinkedHashMap<>();for(Long x:parent.keySet())g.computeIfAbsent(find(x),k->new ArrayList<>()).add(x);return g;}
    }
}
