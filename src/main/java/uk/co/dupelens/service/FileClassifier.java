package uk.co.dupelens.service;

import org.springframework.stereotype.Component;
import uk.co.dupelens.model.MediaType;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Component
public class FileClassifier {
    private static final Set<String> IMAGES = Set.of("jpg","jpeg","png","gif","bmp","webp","tif","tiff");
    private static final Set<String> VIDEOS = Set.of("mp4","m4v","mov","mkv","avi","webm","mpg","mpeg","mts","m2ts","3gp","wmv");

    public MediaType classify(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot+1) : "";
        if (IMAGES.contains(ext)) return MediaType.IMAGE;
        if (VIDEOS.contains(ext)) return MediaType.VIDEO;
        return MediaType.OTHER;
    }
}
