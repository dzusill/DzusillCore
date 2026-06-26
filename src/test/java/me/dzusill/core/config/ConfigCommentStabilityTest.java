package me.dzusill.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import be.seeseemelk.mockbukkit.MockBukkit;

/**
 * Guards against the comment/blank-line duplication that appeared when this class's custom comment system ran on top of
 * Paper 1.18+'s native YAML comment preservation: every save re-emitted each comment, so headers and blank lines grew
 * on each reload. Repeated load/save cycles must leave the file byte-for-byte stable.
 */
class ConfigCommentStabilityTest {

    @TempDir
    File dir;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void repeatedReloadDoesNotDuplicateCommentsOrBlankLines() throws Exception {
        String original = """
                # Header comment line one
                # Header comment line two
                prefix: 'value'

                # Section comment
                section:
                  key: 1
                """;

        File file = new File(dir, "messages.yml");
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);

        String first = null;
        for (int i = 0; i < 10; i++) {
            Config cfg = Config.loadConfiguration(file);
            cfg.save(file);
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (first == null) {
                first = content;
            } else {
                assertEquals(first, content, "config content drifted after reload #" + i);
            }
        }
    }
}
