package com.jsql.model.injection.strategy.blind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;

import com.jsql.model.MediatorModel;
import com.jsql.model.exception.PreparationException;

/**
 * A time attack class using thread asynchronisation.
 */
public class ConcreteTimeInjection extends AbstractBlindInjection<CallableTime> {
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getLogger(ConcreteTimeInjection.class);

    /**
     * Waiting time in seconds, if response time is above
     * then the SQL query is false.
     */
    public static final long SLEEP_TIME = 5;

    /**
     *  Time based works by default, many tests will
     *  change it to false if it isn't confirmed.
     */
    private boolean isTimeInjectable = true;

    /**
     * Create time attack initialisation.
     * If every false requests are under 5 seconds and every true are below 5 seconds,
     * then time attack is confirmed. 
     */
    public ConcreteTimeInjection() {

        // Check if the user wants to stop the preparation
        if (MediatorModel.model().processIsStopped) {
            return;
        }

        /*
         *  Parallelize the call to the FALSE statements,
         *  it will use inject() from the model
         */
        ExecutorService executorFalseMark = Executors.newCachedThreadPool();
        Collection<CallableTime> listCallableFalse = new ArrayList<>();
        for (String urlTest: falseTest) {
            listCallableFalse.add(new CallableTime(urlTest));
        }
        
        // Begin the url requests
        List<Future<CallableTime>> listFalseMark = null;
        try {
            listFalseMark = executorFalseMark.invokeAll(listCallableFalse);
        } catch (InterruptedException e) {
            LOGGER.error(e, e);
        }
        executorFalseMark.shutdown();

        /*
         * If one FALSE query makes less than X seconds,
         * then the test is a failure => exit
         * Allow the user to stop the loop
         */
        try {
            for (Future<CallableTime> falseMark: listFalseMark) {
                if (MediatorModel.model().processIsStopped) {
                    return;
                }
                if (falseMark.get().isTrue()) {
                    isTimeInjectable = false;
                    return;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error(e, e);
        }

        /*
         *  Parallelize the call to the TRUE statements,
         *  it will use inject() from the model
         */
        ExecutorService executorTrueMark = Executors.newCachedThreadPool();
        Collection<CallableTime> listCallableTrue = new ArrayList<>();
        for (String urlTest: trueTest) {
            listCallableTrue.add(new CallableTime(urlTest));
        }
        
        // Begin the url requests
        List<Future<CallableTime>> listTrueMark;
        try {
            listTrueMark = executorTrueMark.invokeAll(listCallableTrue);
        } catch (InterruptedException e) {
            LOGGER.error(e, e);
            return;
        }
        executorTrueMark.shutdown();

        /*
         * If one TRUE query makes more than X seconds,
         * then the test is a failure => exit.
         * Allow the user to stop the loop
         */
        try {
            for (Future<CallableTime> falseMark: listTrueMark) {
                if (MediatorModel.model().processIsStopped) {
                    return;
                }
                if (!falseMark.get().isTrue()) {
                    isTimeInjectable = false;
                    return;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error(e, e);
        }
    }
    
    @Override
    public CallableTime getCallable(String string, int indexCharacter, boolean isTestingLength) {
        return new CallableTime(string, indexCharacter, isTestingLength);
    }

    @Override
    public CallableTime getCallable(String string, int indexCharacter, int bit) {
        return new CallableTime(string, indexCharacter, bit);
    }

    @Override
    public boolean isInjectable() throws PreparationException {
        if (MediatorModel.model().processIsStopped) {
            /**
             * TODO Stoppable Exception
             */
            throw new PreparationException();
        }

        if (MediatorModel.model().vendor.getValue().getSqlBlindFirstTest() == null) {
            return false;
        }
        
        CallableTime blindTest = new CallableTime(MediatorModel.model().vendor.getValue().getSqlBlindFirstTest());
        try {
            blindTest.call();
        } catch (Exception e) {
            LOGGER.error(e, e);
        }

        return this.isTimeInjectable && blindTest.isTrue();
    }

    @Override
    public String getInfoMessage() {
        return "Asking server \"Is this bit true?\", if delay does not exceed 5 seconds then response is true.\n";
    }
}