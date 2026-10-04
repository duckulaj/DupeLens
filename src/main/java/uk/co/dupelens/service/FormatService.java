package uk.co.dupelens.service;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component("fmt")
public class FormatService {
    public String bytes(long n){if(n<1024)return n+" B";double v=n;String[]u={"KB","MB","GB","TB"};int i=-1;do{v/=1024;i++;}while(v>=1024&&i<u.length-1);return String.format("%.1f %s",v,u[i]);}
    public String date(long millis){return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));}
}
