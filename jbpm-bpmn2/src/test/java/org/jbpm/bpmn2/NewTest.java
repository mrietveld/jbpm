package org.jbpm.bpmn2;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.bpmn2.objects.TestWorkItemHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class NewTest extends JbpmBpmn2TestCase {

    private Logger logger = LoggerFactory.getLogger(NewTest.class);
    
    @Parameters
    public static Collection<Object[]> persistence() {
        Object[][] data = new Object[][] { { false }, { true } };
        return Arrays.asList(data);
    };

    private KieSession ksession;

    public NewTest(boolean persistence) {
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
    
    private ProcessEventListener loggingListener = new DefaultProcessEventListener() {
            public void beforeNodeTriggered(ProcessNodeTriggeredEvent event) {
                logger.debug(">  {}" , event.getNodeInstance().getNodeName());
            }
            public void beforeNodeLeft(ProcessNodeLeftEvent event) {
                logger.debug(">> {}" , event.getNodeInstance().getNodeName());
            }
            public void afterNodeLeft(ProcessNodeLeftEvent event) {
                logger.debug("<< {}", event.getNodeInstance().getNodeName());
            }
            public void afterNodeTriggered(ProcessNodeTriggeredEvent event) {
                logger.debug("<  {}" , event.getNodeInstance().getNodeName());
            }
        };
        
    @Test
    public void signalSafePoint() throws Exception {
        KieSession ksession = createKnowledgeSession("BPMN2-SignalTask.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", handler);
        ksession.addEventListener(loggingListener);
        
        ProcessInstance processInstance = ksession.startProcess("signal-task");
        assertProcessInstanceActive(processInstance);
        
        // signalled by "one"
        List<WorkItem> workItems = handler.getWorkItems();
        
        WorkItem workItem = workItems.remove(0);
        logger.debug("COMPLETING TASK");
        
        Map<String, Object> res = new HashMap<String, Object>();
        res.put("output", "ooga");
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        assertProcessInstanceActive(processInstance);
        
        // signalled by "two"
        workItem = workItems.remove(0);
        logger.debug("COMPLETING TASK");
        ksession = restoreSession(ksession, false);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        if( ! isPersistence() ) { 
            assertEquals("Work item has not been completed.", WorkItem.COMPLETED, workItem.getState());
        }
        assertProcessInstanceActive(processInstance);

        logger.debug("SIGNALLING DONE");
        ksession.signalEvent("done", null);
        assertProcessInstanceCompleted(processInstance);
    }
        
    @Test
    public void twoParallelTasks() throws Exception {
        KieSession ksession = createKnowledgeSession("BPMN2-ForkTasks.bpmn2");
        TestWorkItemHandler handler = new TestWorkItemHandler();
        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", handler);
        ksession.addEventListener(loggingListener);
        
        ProcessInstance processInstance = ksession.startProcess("two-tasks");
        assertProcessInstanceActive(processInstance);
        
        // "one"
        logger.debug("COMPLETING TASK 1");
        List<WorkItem> workItems = handler.getWorkItems();
        WorkItem workItem = workItems.remove(0);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        if( ! isPersistence() ) { 
            assertEquals("Work item has not been completed.", WorkItem.COMPLETED, workItem.getState());
        }
        assertProcessInstanceActive(processInstance);
        
        // "two"
        logger.debug("COMPLETING TASK 2");
        workItem = workItems.remove(0);
        ksession.getWorkItemManager().completeWorkItem(workItem.getId(), null);
        if( ! isPersistence() ) { 
            assertEquals("Work item has not been completed.", WorkItem.COMPLETED, workItem.getState());
        }

        assertProcessInstanceCompleted(processInstance);
    }
}
