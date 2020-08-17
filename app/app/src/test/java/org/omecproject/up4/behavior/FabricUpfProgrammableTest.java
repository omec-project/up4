package org.omecproject.up4.behavior;

import org.junit.Before;
import org.junit.Test;
import org.onosproject.net.flow.FlowRuleServiceAdapter;

public class FabricUpfProgrammableTest {
    private final FabricUpfProgrammable upfProgrammable = new FabricUpfProgrammable();

    @Before
    public void setUp() throws Exception {
        upfProgrammable.flowRuleService = new FlowRuleServiceAdapter();
        upfProgrammable.up4Translator = new Up4TranslatorImpl();

    }

    @Test
    public void testAddPdr() {
    }

    @Test
    public void testAddFar() {
    }

    @Test
    public void testAddInterface() {
    }
}