/**
 * Copyright 2010 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.bpmn2.compensation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.bpmn2.JbpmBpmn2TestCase;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.jbpm.process.core.context.exception.CompensationScope;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;

@RunWith(Parameterized.class)
public class CompensationExtendedTest extends JbpmBpmn2TestCase {

    @Parameters(name="{0}")
    public static Collection<Object[]> persistence() {
        Object[][] data = new Object[][] { { false }, { true } };
        return Arrays.asList(data);
    };

    private KieSession ksession;

    public CompensationExtendedTest(boolean persistence) {
        super(persistence);
    }

    @BeforeClass
    public static void setup() throws Exception {
        setUpDataSource();
    }

    @Before
    public void prepare() {
        clearHistory();
    }

    @After
    public void dispose() {
        if (ksession != null) {
            ksession.dispose();
            ksession = null;
        }
    }

    /**
     * TESTS
     */

    @Test
    @Ignore
    public void compensationViaCancellation() throws Exception {
        KieSession ksession = createKnowledgeSession("compensation/BPMN2-Compensation-IntermediateThrowEvent.bpmn2");
        TestTrackWorkItemHandler workItemHandler = new TestTrackWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "0");
        ProcessInstance processInstance = ksession.startProcess("CompensateIntermediateThrowEvent", params);

        ksession.signalEvent("Cancel", null, processInstance.getId());
        ksession.getWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getId(), null);

        // compensation activity (assoc. with script task) signaled *after* script task
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertProcessVarValue(processInstance, "x", "1");
    }
    
    @Test
    public void compensationViaIntermediateThrowEventProcessWithPersistence() throws Exception {
        KieSession ksession = createKnowledgeSession("compensation/BPMN2-Compensation-IntermediateThrowEvent-Persistence.bpmn2");
        TestTrackWorkItemHandler workItemHandler = new TestTrackWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "0");
        ProcessInstance processInstance = ksession.startProcess("CompensateIntermediateThrowEvent", params);

        // compensation activity (assoc. with script task) signaled *after* user task
        ksession.getWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getId(), null);

        // compensation activity is a user task
        assertProcessInstanceActive(processInstance.getId(), ksession);
        
        // complete user task and let process complete
        params.clear();
        params.put("x", "1");
        ksession.getWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getId(), params);
        assertProcessVarValue(processInstance, "x", "1" );
    }
    
    @Test
    public void compensationViaEventSubProcessWithPersistence() throws Exception {
        KieSession ksession = createKnowledgeSession("compensation/BPMN2-Compensation-EventSubProcess-Persistence.bpmn2");
        TestTrackWorkItemHandler workItemHandler = new TestTrackWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
 
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("x", "0");
        ProcessInstance processInstance = ksession.startProcess("CompensationEventSubProcess", params);

        assertProcessInstanceActive(processInstance.getId(), ksession);
        ksession.getWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getId(), null);

        // compensation starts, but stops at compensation user task
        assertProcessInstanceActive(processInstance.getId(), ksession);
        
        // continue compensation
        ksession.getWorkItemManager().completeWorkItem(workItemHandler.getWorkItem().getId(), null);
        
        assertProcessInstanceCompleted(processInstance.getId(), ksession);
        assertProcessVarValue(processInstance, "x", "1");
    }
    
    public class TestTrackWorkItemHandler implements WorkItemHandler {

        private List<WorkItem> workItems = new ArrayList<WorkItem>();

        public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
            workItems.add(workItem);
        }

        public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        }

        public WorkItem getWorkItem() {
            if (workItems.size() == 0) {
                return null;
            }
            return workItems.remove(0);
        }

    }
}
