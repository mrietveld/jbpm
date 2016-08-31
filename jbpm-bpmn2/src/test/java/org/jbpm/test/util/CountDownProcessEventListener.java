package org.jbpm.test.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.drools.persistence.TransactionManager;
import org.drools.persistence.TransactionManagerFactory;
import org.drools.persistence.TransactionSynchronization;
import org.jbpm.ruleflow.instance.RuleFlowProcessInstance;
import org.kie.api.event.process.DefaultProcessEventListener;
import org.kie.api.event.process.ProcessCompletedEvent;
import org.kie.api.event.process.ProcessNodeLeftEvent;
import org.kie.api.event.process.ProcessNodeTriggeredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CountDownProcessEventListener extends DefaultProcessEventListener {

    private static final Logger logger = LoggerFactory.getLogger(CountDownProcessEventListener.class);

    private String nodeName;
    private CountDownLatch latch;
    
    private boolean reactOnBeforeNodeLeft = false;
    private boolean afterProcessCompletes = false;

    public CountDownProcessEventListener(String nodeName, int threads) {
        this.nodeName = nodeName;
        this.latch = new CountDownLatch(threads);
    }
    
    public CountDownProcessEventListener(String nodeName, int threads, boolean reactOnBeforeNodeLeft) {
        this.nodeName = nodeName;
        this.latch = new CountDownLatch(threads);
        this.reactOnBeforeNodeLeft = reactOnBeforeNodeLeft;
    }

    public CountDownProcessEventListener(int threads) {
        this.nodeName = null;
        this.afterProcessCompletes = true;
        this.latch = new CountDownLatch(threads);
    }

    public void useAfterProcessCompleted() {
        this.afterProcessCompletes = true;
    }

    @Override
    public void afterProcessCompleted( ProcessCompletedEvent event ) {
        if ( afterProcessCompletes ) {
            countDown();
        }
    }
    
    @Override
    public void beforeNodeLeft(ProcessNodeLeftEvent event) {
        if (reactOnBeforeNodeLeft && nodeName.equals(event.getNodeInstance().getNodeName())) {
            countDown();
        }
    }
    
    @Override
    public void afterNodeLeft( ProcessNodeLeftEvent event ) {
        if (! afterProcessCompletes && nodeName.equals(event.getNodeInstance().getNodeName())) {
            countDown();
        }
    }

    public void waitTillCompleted() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.debug("Interrputed thread while waiting for all triggers for node {}", nodeName);
        }
    }

    public void waitTillCompleted(long timeOut) {
        try {
            latch.await(timeOut, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.debug("Interrputed thread while waiting for all triggers for node {}", nodeName);
        }
    }
    
    public void reset(int threads) {
        this.latch = new CountDownLatch(threads);
    }
    
    public void reset(String nodeName, int threads) {
        this.nodeName = nodeName;
        this.latch = new CountDownLatch(threads);
    }
    
    protected void countDown() {
        try {
            TransactionManager tm = TransactionManagerFactory.get().newTransactionManager();
            if (tm != null && tm.getStatus() != TransactionManager.STATUS_NO_TRANSACTION
                    && tm.getStatus() != TransactionManager.STATUS_ROLLEDBACK
                    && tm.getStatus() != TransactionManager.STATUS_COMMITTED) {
                tm.registerTransactionSynchronization(new TransactionSynchronization() {

                    @Override
                    public void beforeCompletion() {
                    }

                    @Override
                    public void afterCompletion(int status) {
                        latch.countDown();
                    }
                });
            } else {
                latch.countDown();
            }
        } catch (Exception e) {
            latch.countDown();
        }
    }
}
