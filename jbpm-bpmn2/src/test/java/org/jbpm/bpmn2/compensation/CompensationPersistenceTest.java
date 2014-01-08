package org.jbpm.bpmn2.compensation;

import org.drools.core.impl.EnvironmentFactory;
import org.jbpm.bpmn2.JbpmBpmn2TestCase;
import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;

public class CompensationPersistenceTest  extends JbpmBpmn2TestCase {

    public CompensationPersistenceTest() { 
        super(true);
    }

    @BeforeClass
    public static void setup() throws Exception {
        setUpDataSource();
        
        // clear logs
        Environment env = EnvironmentFactory.newEnvironment();
        env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
    }
    

    @Test
    public void testSubProcessPersistence() throws Exception {
        KieBase kbase = createKnowledgeBase("BPMN2-SubProcessUserTask.bpmn2");
        KieSession ksession = createKnowledgeSession(kbase);
        
        TestWorkItemHandler workItemHandler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", workItemHandler);
        
        ProcessInstance processInstance = ksession.startProcess("SubProcess");
        assertProcessInstanceActive(processInstance);

        WorkItem workItem = workItemHandler.getWorkItem();
        assertNotNull(workItem);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceFinished(processInstance, ksession);
    }

}
