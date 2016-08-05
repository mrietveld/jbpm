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

import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.KieBase;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class ResourceTest extends JbpmBpmn2TestCase {

    private static final Logger logger = LoggerFactory.getLogger(ResourceTest.class);

    @Parameters(name="{3}")
    public static Collection<Object[]> persistence() {
        return getTestOptions(TestOption.NO_PERSISTENCE);
    };

    private StatefulKnowledgeSession ksession;

    public ResourceTest(boolean persistence, boolean locking, boolean queueBased, String name) {
        super(persistence, locking, queueBased, name);
    }

    @Test
    public void testResourceType() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-MinimalProcess.bpmn2");
        ksession = createKnowledgeSession(kbase);
        ksession.startProcess("Minimal");
    }

    @Test
    public void testMultipleProcessInOneFile() throws Exception {
        KieBase kbase = createKnowledgeBaseWithoutDumper("BPMN2-MultipleProcessInOneFile.bpmn2");
        ksession = createKnowledgeSession(kbase);
        ProcessInstance processInstance = ksession.startProcess("Evaluation");
        assertNotNull(processInstance);
        ProcessInstance processInstance2 = ksession.startProcess("Simple");
        assertNotNull(processInstance2);
    }

}
