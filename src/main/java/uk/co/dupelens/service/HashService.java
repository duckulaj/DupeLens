package uk.co.dupelens.service;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class HashService {
    private static final int CHUNK = 1024 * 1024;

    public String partialHash(Path path) throws Exception {
        long size = Files.size(path);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            byte[] buf = new byte[(int)Math.min(CHUNK, size)];
            if (buf.length > 0) {
                raf.readFully(buf); md.update(buf);
                if (size > CHUNK) {
                    int tail = (int)Math.min(CHUNK, size);
                    byte[] end = new byte[tail];
                    raf.seek(size - tail); raf.readFully(end); md.update(end);
                }
            }
        }
        md.update(longBytes(size));
        return HexFormat.of().formatHex(md.digest());
    }

    public String fullHash(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) if (n > 0) md.update(buf,0,n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private byte[] longBytes(long v) {
        return new byte[]{(byte)(v>>>56),(byte)(v>>>48),(byte)(v>>>40),(byte)(v>>>32),(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};
    }
}
