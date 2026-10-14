package uk.co.dupelens.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsServiceTest {
    @Test
    void persistsScanRootsAndRemovesBlankAndDuplicateEntries() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE app_setting (setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL)");
            SettingsService settings = new SettingsService(jdbc);

            settings.saveScanRoots(List.of("/photos", "", "/videos", "/photos"));

            assertEquals(List.of("/photos", "/videos"), settings.getScanRoots());
        } finally {
            dataSource.destroy();
        }
    }
}
