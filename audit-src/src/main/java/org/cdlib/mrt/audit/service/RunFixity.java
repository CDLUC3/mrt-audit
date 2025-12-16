/******************************************************************************
Copyright (c) 2005-2012, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
 *
- Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
- Neither the name of the University of California nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
OF THE POSSIBILITY OF SUCH DAMAGE.
*******************************************************************************/

package org.cdlib.mrt.audit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.cdlib.mrt.audit.action.FixityValidationWrapper;
import java.util.LinkedList;
import java.util.Properties;
import java.util.HashMap;

import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import org.cdlib.mrt.log.utility.AddStateEntryGen;


import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.db.FixNames;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.logging.NodeStat;
import org.cdlib.mrt.audit.utility.FixityOwn;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.s3.service.NodeIO;
import org.cdlib.mrt.utility.DateUtil;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.utility.TFrame;
import org.json.JSONObject;

/**
 * This class performs the overall fixity functions.
 * Fixity runs as a background thread.
 * 
 * Fixity uses a relational database (here MySQL) to process the oldest entries 
 * first. These entries are pulled in as blocks. Each block is then processed and
 * the results are then collected before the next block is started.
 * 
 * The fixity tests whether either the extracted file has changed either size or digest.
 * Any change results in error information being saved to the db entry for that test.
 * 
 * Note that FixityState contains 2 flags used for controlling fixity handling:
 * 
 *  runFixity - this flag controls whether to start or stop fixity
 *            - true=fixity should be running or starting to run
 *            - false=stop fixity and exit routine
 *  fixityProcessing - this flag determines if fixity is running
 *            - true=fixity is now running
 *            - false=fixity has stopped
 * @author dloy
 */

public class RunFixity implements Runnable
{
    private static final String NAME = "RunFixity";
    private static final String MESSAGE = NAME + ": ";

    private static final boolean DEBUG = false;
    private static final boolean STOP = false;
    protected LoggerInf logger = null;
    protected FixityItemDB db = null;
    protected long maxout = 100000000;
    protected Exception exception = null;
    protected String auditQualify = "";
    protected boolean start=true;


    protected int capacity = 100;
    protected LinkedList<InvAudit> queue = null;
    FixityState fixityState = null;
    protected HashMap<Long,Long> idNodeMap = new HashMap<>();
    

    /**
     * Top lever routine controlling fixity handling
     * @param fixityState state controlling starting and stopping fixity plus process
     * features such as number of threads
     * @param rewriteEntry special class to dynamically modify URLs used for extracting
     * content to be fixity checked
     * @param db database handler
     * @param logger Merritt logger
     * @throws TException - Merritt process exception
     */
    public RunFixity(
            FixityState fixityState,
            FixityItemDB db,
            LoggerInf logger)
        throws TException
    {
        this.fixityState = fixityState;
        this.logger = logger;
        this.db = db;
        this.capacity = fixityState.getQueueCapacity();
        this.queue = new LinkedList<InvAudit>();
        setAuditQualify();
    }

    private void setAuditQualify()
        throws TException
    {
        auditQualify = fixityState.getAuditQualify();
        if (StringUtil.isAllBlank(auditQualify)) {
            auditQualify = "";
        }
    }

    
    /**
     * Extract the oldest block of untested entries and add entries to a queue for
     * testing
     * @return number of entries extracted
     * @throws TException 
     */
    protected int addSQLEntries()
        throws TException
    {
        Connection ownConnect = null;
        try {
            try {
                ownConnect = db.getConnection(false);
                if (!ownConnect.isValid(500)) {
                    throw new TException.GENERAL_EXCEPTION("Connection not valid");
                }
                if (start) {
                    logger.logMessage("Start runFixity queue", 1, true);
                }
                long startMs = System.currentTimeMillis();
                queue = FixityOwn.getOwnListAudit(ownConnect, auditQualify, capacity, logger);
                long stopMs = System.currentTimeMillis();
                if (start) {
                    logger.logMessage("Queue:" + queue.size() + " - Ms:" + (stopMs - startMs), 1, true);
                    start = false;
                }
                    
            } finally {
                ownConnect.close();
            }
            log("END ADD");
            return queue.size();

        } catch (TException fe) {
            throw fe;

        } catch(Exception e)  {
            if (logger != null)
            {
                logger.logError(
                    "Main: Encountered exception:" + e, 0);
                logger.logError(
                        StringUtil.stackTrace(e), 10);
            }
            throw new TException(e);

        } finally {
            try {
                ownConnect.close();
            } catch (Exception ex) {}
        }
    }
    
    /**
     * Main method - used only for testing
     */
    public static void main(String args[])
    {

        TFrame tFrame = null;
        FixityItemDB db = null;
        try {
            String propertyList[] = {
                "resources/FixityTest.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");

            // Create an instance of this object
            LoggerInf logger = new TFileLogger(NAME, 50, 50);
            FixityState fixityState = new FixityState();
            db = new FixityItemDB(logger, tFrame.getProperties());
            RunFixity test = new RunFixity(fixityState, db, logger);
            //test.run();
            ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
            threadExecutor.execute( test ); // start task1
            Thread.sleep(10000);
            fixityState.setRunFixity(false);
            Thread.sleep(10000);
            threadExecutor.shutdown();
            threadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            if (db != null) {
                try {
                    db.shutDown();
                } catch (Exception ex) { }
            }
        }
    }


    /**
     * Thread run method used for handling the background thread handling
     */
    @Override
    public void run()
    {
        long start = 0;
        int startCnt = 0;
        while (true) {
            try {
                startCnt++;
                start = DateUtil.getEpochUTCDate();
                runIt();
                if (!fixityState.isRunFixity()) {
                    log("SHUTDOWN detected");
                }
                break; //only sql exceptions allow restart

            } catch (TException.SQL_EXCEPTION sqlex) {
                long failTime = DateUtil.getEpochUTCDate() - start;
                sqlex.printStackTrace();
                setEx(sqlex);
                System.out.println("WARNING RunFixity restart(" + startCnt + "):" 
                        + " - Date:" + DateUtil.getCurrentIsoDate()
                        + " - failTime:" + failTime
                        + " - Exception:" + sqlex
                );
                if (failTime > 1800000) { // 30 minutes up before failure
                    startCnt = 0;
                    
                } else {
                    if (startCnt > 5) {
                        String msg = "Exception RunFixity restart exceeded(" + startCnt + "):" 
                            + "failTime:" + failTime
                            + "Exception:" + sqlex;
                        System.out.println(msg);
                        logger.logError(msg, 0);
                        logger.logError(
                            StringUtil.stackTrace(sqlex), 10);
                        break;
                    }
                    try {
                        Thread.sleep(180000 * startCnt);
                    } catch (Exception ex) { };
                }

            } catch (TException fe) {
                fe.printStackTrace();
                setEx(fe);
                break;

            } catch(Exception e)  {

                e.printStackTrace();
                if (logger != null)
                {
                    logger.logError(
                        "Main: Encountered exception:" + e, 0);
                    logger.logError(
                            StringUtil.stackTrace(e), 10);
                }
                setEx(e);
                break;

            }
        }
        fixityState.setFixityProcessing(false);
        System.out.println("************END RunFixity");
    }
    
    
    public void runIt()
        throws Exception
    {
        try {
            fixityState.setRunFixity(true);
            fixityState.setFixityProcessing(true);
            Thread.sleep(5);
            for (long outcnt=0; outcnt < maxout; outcnt += capacity) {
                log("PROCESS BLOCK:" + outcnt);
                processBlock();
                if (!fixityState.isRunFixity()) {
                    log("SHUTDOWN detected");
                    break;
                }
            }
            log("************leaving RunFixity");

        } catch (TException fe) {
            fe.printStackTrace();
            throw fe;

        } catch(Exception e)  {
            throw e;

        }
    }

    /**
     * This method handles the overall processing of a queued set of entries.
     * The entries are processed by a dynamically allocated fixed set of threads.
     *
     * @throws TException 
     */
    protected void processBlock()
        throws TException
    {
        
        Connection auditConnection = null;
        
        ArrayList<FixityValidationWrapper> fixityWrapperList = new ArrayList<>();
        long getMySqlMs = 0L;
        long threadLatencyMs = 0L;
        Long startLatencyMs = null;
        try {
            auditConnection = db.getConnection(true);
            long startMySql = System.currentTimeMillis();
            if (addSQLEntries() == 0) {
                log("No ITEM content - sleep 30 seconds");
                Thread.sleep(30000);
                return;
            }
            getMySqlMs = System.currentTimeMillis() - startMySql;
            log("Thread pool count:" + fixityState.getThreadPool());
            if (!fixityState.isRunFixity()) return;
            int threadPoolCnt = fixityState.getThreadPool();
            ExecutorService threadPool 
                    = Executors.newFixedThreadPool(threadPoolCnt);
            
            long startMs = System.currentTimeMillis();
            long bytes = 0;
            for(int i = 0; i < capacity; i++){
                if (!fixityState.isRunFixity()) break;
                InvAudit audit = getEntry();
                if (audit == null) break;
                //sleepInterval(audit);
                log("PROCESS:" + audit.getId());
                FixityValidationWrapper wrapper
                        = new FixityValidationWrapper(audit, db, logger);
                
                threadPool.execute(wrapper);
                fixityWrapperList.add(wrapper);
                fixityState.bumpCnt();
            }
            if (threadPool instanceof ThreadPoolExecutor) {
                ThreadPoolExecutor tpe = (ThreadPoolExecutor) threadPool;
                int remainThreads = threadPoolCnt - tpe.getActiveCount();
                if (remainThreads > 0) {
                    startLatencyMs = System.currentTimeMillis();
                }
            }
            threadPool.shutdown();
            threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            if (startLatencyMs != null)
                threadLatencyMs = System.currentTimeMillis() - startLatencyMs;
            LinkedList<Long> verifiedList = new LinkedList<Long>();
            NodeStat nodeStat = new NodeStat();
            for (FixityValidationWrapper wrapper : fixityWrapperList) {
                long id = wrapper.getAudit().getId();
                long nodeID = wrapper.getAudit().getNodeid();
                try {
                    FixityMRTEntry mrtEntry = wrapper.getValidator().getEntry();
                    bytes += wrapper.getValidator().getEntry().getSize();
                    long entryBytes = mrtEntry.getSize();
                    long nodeNumber = getNodeNumber(nodeID);
                    long nodeTime = wrapper.getTimeMs();
                    long streamTime = mrtEntry.getStreamMs();
                    nodeStat.add(nodeNumber, entryBytes, nodeTime, streamTime);
                } catch (Exception bex) { 
                    System.out.println("BEX:" + bex);
                    bex.printStackTrace();
                }
                if (!wrapper.isUpdated()) {
                    verifiedList.add(id);
                } else {
                    //System.out.println("Updated(" + id + ") status=" + wrapper.getAudit().getStatus());
                }
            }
            
            long startCompleteMs = System.currentTimeMillis();
            FixityOwn.completeOwnList(auditConnection, verifiedList, logger);
            long stopMs = System.currentTimeMillis();
            long putMySqlMs = stopMs - startCompleteMs;
            if (STOP) stop();
            // long sleepTime = 700 - (delta * 50);
            long sleepTime = fixityState.getQueueSleepMs();
            if (sleepTime > 0) {
                try {
                Thread.sleep(sleepTime);
                } catch (Exception x) { }
            }
            
            String msg = MESSAGE
                    + " - capacity=" + capacity
                    + " - sleepTime=" + sleepTime
                    + " - verified=" + verifiedList.size()
                    + " - non-verified=" + (capacity - verifiedList.size())
                    + " - processMs=" + (stopMs - startMs)
                    + " - runVerifiedMs=" + (stopMs - startCompleteMs
                    + " - bytes=" + bytes
                    + " - getMySqlMs=" + getMySqlMs
                    + " - putMySqlMs=" + putMySqlMs
                    + " - threadLatencyMs=" + threadLatencyMs
                    );
            logger.logMessage(msg, 1, true);
            
            JSONObject batchJson = new JSONObject();
            JSONObject properties = new JSONObject();
            properties.put("capacity", capacity);
            properties.put("sleepTime", sleepTime);
            Integer awsVersion = FixityServiceConfig.getAwsVersion();
            if (awsVersion != null) {
                properties.put("awsVersion", "" + FixityServiceConfig.getAwsVersion());
            }
            if (auditQualify.length() > 0) {
                properties.put("auditQualify", auditQualify);
            }
            JSONObject runJson = new JSONObject();
            runJson.put("verified", verifiedList.size());
            runJson.put("non-verified", (capacity - verifiedList.size()));
            runJson.put("processMs", (stopMs - startMs));
            runJson.put("runVerifiedMs",(stopMs - startCompleteMs));
            runJson.put("bytes", bytes);
            runJson.put("getMySqlMs", getMySqlMs);
            runJson.put("putMySqlMs", putMySqlMs);
            runJson.put("threadLatencyMs", threadLatencyMs);
            batchJson.put("runInfo", runJson);
            
            batchJson.put("properties", properties);
            JSONObject nodeJson = nodeStat.getJson();
            batchJson.put("nodeInfo", nodeJson);
            if (false) {
                System.out.println("BATCH" +  batchJson.toString(2));
            }
            AddStateEntryGen.addLogStateEntry("info", "batchStat", batchJson);
            //AddStateEntryGen.addEntry("info", batchJson);
            //LogManager.getLogger().info("Fixity Batch Compelete");
            
            log("************Termination of threads");

        } catch (TException fe) {
            fe.printStackTrace();
            throw fe;

        } catch(Exception e)  {

            e.printStackTrace();
            if (logger != null)
            {
                logger.logError(
                    "Main: Encountered exception:" + e, 0);
                logger.logError(
                        StringUtil.stackTrace(e), 10);
            }
            throw new TException(e);
            
        } finally {
            try {
                auditConnection.close();
            } catch (Exception ex) {}
        }
    }
    
    private static void stop() 
        throws TException
    {
        try {
            throw new TException.GENERAL_EXCEPTION("stop");
        } catch (TException tex) {
            tex.printStackTrace();
            throw tex;
        }
    }
            
    /**
     * This routine throttles fixity by using two type of intervals. 
     * The queue sleep interval issues a thread sleep between the process 
     * of each queued entry. This can be used to spread out the verification dates if
     * they are too clumped.
     * 
     * The standard interval is the minimum number of days between fixity tests being
     * performed on a single entry. This can be used to spread out the verification dates if
     * they are too clumped.
     * 
     * @param entry to be processed
     * @throws TException 
     */
    protected void sleepInterval(InvAudit audit)
        throws TException
    {
        try {
            if (!fixityState.isRunFixity()) return;

            long queueSleep = fixityState.getQueueSleepMs();
            if (queueSleep > 0) {
                long queueSleepMilleseconds = queueSleep;
                log("sleepInterval - "
                    + " - queueSleep=" + queueSleep
                    + " - queueSleepMilleseconds=" + queueSleepMilleseconds
                    );
                Thread.sleep(queueSleepMilleseconds);
            }
            long intervalDay = fixityState.getIntervalDays();
            if (intervalDay == 0) return;
            long intervalSeconds = intervalDay * (24*60*60);
            long entryTime = audit.getVerified().getTimeLong();
            log("sleepInterval - "
                    + " - intervalDay=" + intervalDay
                    + " - intervalSeconds=" + intervalSeconds
                    + " - entryTime=" + entryTime
                    + " - currentTime=" + new DateState().getTimeLong()
                    );
            while (true) {
                if (!fixityState.isRunFixity()) return;
                long currentTime = new DateState().getTimeLong();
                if ((entryTime + intervalSeconds) > currentTime) return;
                Thread.sleep(30000);
            }
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    /**
     * Return entry from queue and modify the entry if a rewrite rule is applied.
     * @return entry to be processed - rewrite applied if necessary
     * @throws TException 
     */
    protected InvAudit getEntry()
        throws TException
    {
        try {
            if (queue.size() < 1) {
                return null;
            }
            InvAudit audit = queue.pop();
            return audit;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }
    
    protected long getNodeNumber(long nodeID)
        throws TException
    {
        Connection localConnect = null;
        Long nodeNumber = idNodeMap.get(nodeID);
        if (nodeNumber != null) {
            return nodeNumber;
        }
        try {
            localConnect = db.getConnection(true);
            Long localNode = FixityDBUtil.getNodeNumber(nodeID, localConnect, logger);
            if (localNode == null) {
                throw new TException.INVALID_OR_MISSING_PARM("NodeID does not exist");
            }
            long nodeNumberL = localNode;
            idNodeMap.put(nodeID, nodeNumberL);
            
            return nodeNumberL;
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            throw new TException(ex);
            
        } finally {
            try {
                if (localConnect != null) localConnect.close();
            } catch (Exception x) { }
        }
    }

    protected void log(String msg)
    {
        logger.logMessage(msg, 15, true);
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }

    public Exception getEx() {
        return exception;
    }

    public void setEx(Exception ex) {
        this.exception = ex;
    }


}
