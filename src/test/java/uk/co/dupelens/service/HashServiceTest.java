package uk.co.dupelens.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class HashServiceTest {
    @TempDir Path temp;
    @Test void identicalFilesHaveSameHashes() throws Exception {
        HashService s=new HashService(); Path a=temp.resolve("a.bin"),b=temp.resolve("b.bin"); byte[] data="same-content".repeat(1000).getBytes(); Files.write(a,data);Files.write(b,data);
        assertThat(s.partialHash(a)).isEqualTo(s.partialHash(b));assertThat(s.fullHash(a)).isEqualTo(s.fullHash(b));
    }
    @Test void differentFilesHaveDifferentFullHashes() throws Exception {
        HashService s=new HashService(); Path a=temp.resolve("a"),b=temp.resolve("b"); Files.writeString(a,"one");Files.writeString(b,"two");
        assertThat(s.fullHash(a)).isNotEqualTo(s.fullHash(b));
    }
}
