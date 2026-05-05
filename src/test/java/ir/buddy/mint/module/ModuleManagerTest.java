package ir.buddy.mint.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleManagerTest {

    @Test
    void normalizeModuleNameRemovesSymbolsAndCase() {
        assertEquals("doubledoor", ModuleManager.normalizeModuleInput("Double Door"));
        assertEquals("verticalslab", ModuleManager.normalizeModuleInput("vertical-slab"));
    }
}
