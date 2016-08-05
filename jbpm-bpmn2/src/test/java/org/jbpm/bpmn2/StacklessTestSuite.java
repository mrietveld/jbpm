package org.jbpm.bpmn2;

import org.jbpm.bpmn2.structureref.StructureRefTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

//@RunWith(Suite.class)
@SuiteClasses({
    ActivityTest.class,
    DataTest.class,
    EndEventTest.class,
    ErrorEventTest.class,
    EscalationEventTest.class,
    IntermediateEventTest.class,
    MultiInstanceTest.class,
    ResourceTest.class,
    StartEventTest.class,
    StructureRefTest.class,

    FlowTest.class,
    StandaloneBPMNProcessTest.class,

    // Broken
    CompensationTest.class
})
public class StacklessTestSuite {

}
