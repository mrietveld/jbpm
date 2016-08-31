/**
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.bpmn2;

import java.util.Arrays;
import java.util.Collection;

import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.persistence.session.objects.TestWorkItemHandler;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.test.util.CountDownProcessEventListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.KieBase;
import org.kie.api.io.Resource;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.runtime.StatefulKnowledgeSession;

@RunWith(Parameterized.class)
public class ProcessFactoryTest extends JbpmBpmn2TestCase {

    @Parameters
    public static Collection<Object[]> persistence() {
        Object[][] data = new Object[][] {
            { false },
            { true }
            };
        return Arrays.asList(data);
    };

    public ProcessFactoryTest(boolean stackless) {
        super(false, false, stackless);
    }

    @Test
	public void testProcessFactory() throws Exception {
		RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
		factory
			// header
			.name("My process").packageName("org.jbpm")
			// nodes
			.startNode(1).name("Start").done()
			.actionNode(2).name("Action")
				.action("java", "System.out.println(\"Action\");").done()
			.endNode(3).name("End").done()
			// connections
			.connection(1, 2)
			.connection(2, 3);
		RuleFlowProcess process = factory.validate().getProcess();
		Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
		res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
		KieBase kbase = createKnowledgeBaseFromResources(res);
		StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
		ksession.startProcess("org.jbpm.process");
		ksession.dispose();
	}

    @Test
    public void testCompositeNode() throws Exception {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
            // header
            .name("My process").packageName("org.jbpm")
            // nodes
            .startNode(1).name("Start").done()
            .compositeNode(2)
                .name("SubProcess")
                .startNode(1).name("SubProcess Start").done()
                .actionNode(2).name("SubProcess Action").action("java", "System.out.println(\"SubProcess Action\");").done()
                .endNode(3).name("SubProcess End").terminate(true).done()
                .connection(1, 2)
                .connection(2, 3)
                .done()
            .endNode(3).name("End").done()
            // connections
            .connection(1, 2)
            .connection(2, 3);
        RuleFlowProcess process = factory.validate().getProcess();

        assertEquals("SubProcess", process.getNode(2).getName());

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        KieBase kbase = createKnowledgeBaseFromResources(res);
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        ProcessInstance pi = ksession.startProcess("org.jbpm.process");

        assertEquals(ProcessInstance.STATE_COMPLETED, pi.getState());

        ksession.dispose();
    }

    @Test(timeout=10000)
    public void testBoundaryTimerTimeCycle() throws Exception {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
            // header
            .name("My process").packageName("org.jbpm")
            // nodes
            .startNode(1).name("Start").done()
            .humanTaskNode(2).name("Task").actorId("john").taskName("MyTask").done()
            .endNode(3).name("End1").terminate(false).done()
            .boundaryEventNode(4).name("BoundaryTimerEvent").attachedTo(2).timeCycle("1s###5s").cancelActivity(false).done()
            .endNode(5).name("End2").terminate(false).done()
            // connections
            .connection(1, 2)
            .connection(2, 3)
            .connection(4, 5);
        RuleFlowProcess process = factory.validate().getProcess();

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        KieBase kbase = createKnowledgeBaseFromResources(res);
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", testHandler);
        CountDownProcessEventListener countDownListener = new CountDownProcessEventListener("BoundaryTimerEvent", 1);
        ksession.addEventListener(countDownListener);

        ProcessInstance pi = ksession.startProcess("org.jbpm.process");
        assertProcessInstanceActive(pi);

        countDownListener.waitTillCompleted(); // wait for boundary timer firing

        Thread.currentThread().sleep(100);

        assertNodeTriggered(pi.getId(), "End2");
        assertProcessInstanceActive(pi); // still active because CancelActivity = false

        ksession.getWorkItemManager().completeWorkItem(testHandler.getWorkItem().getId(), null);
        assertProcessInstanceCompleted(pi);

        ksession.dispose();
    }

    @Test(timeout=10000)
    public void testBoundaryTimerTimeDuration() throws Exception {
        CountDownProcessEventListener countDownListener = new CountDownProcessEventListener("BoundaryTimerEvent", 1);
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.jbpm.process");
        factory
            // header
            .name("My process").packageName("org.jbpm")
            // nodes
            .startNode(1).name("Start").done()
            .humanTaskNode(2).name("Task").actorId("john").taskName("MyTask").done()
            .endNode(3).name("End1").terminate(false).done()
            .boundaryEventNode(4).name("BoundaryTimerEvent").attachedTo(2).timeDuration("1s").cancelActivity(false).done()
            .endNode(5).name("End2").terminate(false).done()
            // connections
            .connection(1, 2)
            .connection(2, 3)
            .connection(4, 5);
        RuleFlowProcess process = factory.validate().getProcess();

        Resource res = ResourceFactory.newByteArrayResource(XmlBPMNProcessDumper.INSTANCE.dump(process).getBytes());
        res.setSourcePath("/tmp/processFactory.bpmn2"); // source path or target path must be set to be added into kbase
        KieBase kbase = createKnowledgeBaseFromResources(res);
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        TestWorkItemHandler testHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", testHandler);
        ksession.addEventListener(countDownListener);

        ProcessInstance pi = ksession.startProcess("org.jbpm.process");
        assertProcessInstanceActive(pi);

        countDownListener.waitTillCompleted(); // wait for boundary timer firing

        assertNodeTriggered(pi.getId(), "End2");
        assertProcessInstanceActive(pi); // still active because CancelActivity = false

        ksession.getWorkItemManager().completeWorkItem(testHandler.getWorkItem().getId(), null);
        assertProcessInstanceCompleted(pi);

        ksession.dispose();
    }
}
