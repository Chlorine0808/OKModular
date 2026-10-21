package ruiseki.okmodular.common.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * The example writer must be handed to StructureManager before it initialises.
 *
 * StructureManager runs its generator from initialize(), and MachineryModule.preInit also scans
 * recipes/ for NEI right after. Setting the generator any later, or not at all, leaves a fresh
 * install with no machines on its first launch - the A-1 gap this writer exists to close.
 */
class DefaultConfigWiringTest {

    @Test
    void preInitSetsTheWriterBeforeInitialising() {
        String preInit = JavaSource.methodBody(
            JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve("MachineryModule.java"))),
            "public void preInit(FMLPreInitializationEvent event)");
        int set = preInit.indexOf("setDefaultStructureGenerator(DefaultConfigWriter::writeMissing)");
        int init = preInit.indexOf(".initialize(configDir)");

        assertTrue(set >= 0, "preInit never sets DefaultConfigWriter as the generator");
        assertTrue(set < init, "the generator is set after StructureManager has already initialised");
    }
}
