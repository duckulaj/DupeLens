package uk.co.dupelens.service;

import org.springframework.stereotype.Service;
import uk.co.dupelens.model.ScanSnapshot;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ScanProgressService {
    private final AtomicReference<ScanSnapshot> ref = new AtomicReference<>(ScanSnapshot.idle());
    public ScanSnapshot get(){ return ref.get(); }
    public void set(ScanSnapshot s){ ref.set(s); }
    public boolean isRunning(){ return ref.get().running(); }

    public void begin(long id){ set(new ScanSnapshot(true,id,"DISCOVERING","Discovering files","",0,0,-1,Instant.now(),0,0,0,0,0,0,false,null)); }

    public void phase(String phase,String message,int percent){ mutate(s->new ScanSnapshot(s.running(),s.scanId(),phase,message,s.currentFile(),s.filesSeen(),s.bytesSeen(),percent,s.startedAt(),s.duplicateGroups(),s.duplicateFiles(),s.reclaimableBytes(),s.similarImageGroups(),s.similarVideoGroups(),s.aiGroups(),s.cancelled(),s.error())); }
    public void current(String file,long files,long bytes,int percent){ mutate(s->new ScanSnapshot(s.running(),s.scanId(),s.phase(),s.message(),file,files,bytes,percent,s.startedAt(),s.duplicateGroups(),s.duplicateFiles(),s.reclaimableBytes(),s.similarImageGroups(),s.similarVideoGroups(),s.aiGroups(),s.cancelled(),s.error())); }
    public void counts(int dg,int df,long reclaim,int ig,int vg,int ag){ mutate(s->new ScanSnapshot(s.running(),s.scanId(),s.phase(),s.message(),s.currentFile(),s.filesSeen(),s.bytesSeen(),s.progressPercent(),s.startedAt(),dg,df,reclaim,ig,vg,ag,s.cancelled(),s.error())); }
    public void done(boolean cancelled,String error){ mutate(s->new ScanSnapshot(false,s.scanId(),cancelled?"CANCELLED":error==null?"COMPLETED":"FAILED",cancelled?"Scan cancelled":error==null?"Scan complete":"Scan failed",s.currentFile(),s.filesSeen(),s.bytesSeen(),100,s.startedAt(),s.duplicateGroups(),s.duplicateFiles(),s.reclaimableBytes(),s.similarImageGroups(),s.similarVideoGroups(),s.aiGroups(),cancelled,error)); }
    private synchronized void mutate(java.util.function.Function<ScanSnapshot,ScanSnapshot> f){ ref.set(f.apply(ref.get())); }
}
