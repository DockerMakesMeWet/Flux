package org.owl.flux.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MastersServiceTest {

    @Test
    void parseContentIgnoresCommentsAndBlankLines() {
        String content = """
                # Flux Masters
                Heavencide

                burgersarefatx
                # another comment
                notquitecloudy
                """;

        Set<String> parsed = MastersService.parseContent(content);
        assertEquals(Set.of("heavencide", "burgersarefatx", "notquitecloudy"), parsed);
    }

    @Test
    void parseContentHandlesEmptyInput() {
        assertTrue(MastersService.parseContent("   ").isEmpty());
    }
}
