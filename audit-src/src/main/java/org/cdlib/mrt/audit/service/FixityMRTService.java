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



import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Properties;

import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.audit.action.ProcessFixityEntry;
import org.cdlib.mrt.audit.action.FixityCleanup;
import org.cdlib.mrt.audit.action.FixityEmailWrapper;
import org.cdlib.mrt.audit.action.FixityActionAbs;
import org.cdlib.mrt.audit.action.FixityReportEntries;
import org.cdlib.mrt.audit.action.FixityReportItem;
import org.cdlib.mrt.audit.action.FixityReportSQL;
import org.cdlib.mrt.audit.action.SearchFixityEntries;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.utility.TFrame;

/**
 * Fixity Service
 * @author  dloy
 */

public class FixityMRTService
        implements FixityMRTServiceInf
{
    private static final String NAME = "FixityService";
    private static final String MESSAGE = NAME + ": ";
    private static final String NL = System.getProperty("line.separator");
    private static final boolean DEBUG = true;
    private static final boolean THREADDEBUG = false;
    protected LoggerInf logger = null;
    protected Exception exception = null;
    protected FixityServiceConfig fixityServiceConfig = null;

    public static FixityMRTService getFixityService(FixityServiceConfig fixityServiceConfig)
            throws TException
    {
        return new FixityMRTService(fixityServiceConfig);
    }

    protected FixityMRTService(FixityServiceConfig fixityServiceConfig)
        throws TException
    {
        this.fixityServiceConfig = fixityServiceConfig;
        this.logger = fixityServiceConfig.getLogger();
    }

    @Override
    public FixityServiceState getFixityServiceState()
        throws TException
    {
        if (THREADDEBUG) FixityUtil.sysoutThreads("Begin getFixityServiceState");
        FixityServiceState fixityServiceState = fixityServiceConfig.getFixityServiceState();
        setProcessCount(fixityServiceState);
        if (THREADDEBUG) FixityUtil.sysoutThreads("End getFixityServiceState");
        return fixityServiceState;
    }

    @Override
    public FixityServiceState getFixityServiceStatus()
        throws TException
    {
        if (THREADDEBUG) FixityUtil.sysoutThreads("Begin getFixityServiceStatus");
        FixityServiceState fixityServiceState = fixityServiceConfig.getFixityServiceStatus();
        if (THREADDEBUG) FixityUtil.sysoutThreads("End getFixityServiceStatus");
        return fixityServiceState;
    }

    @Override
    public FixityEntriesState getFixityEntry(String urlS)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        Connection connection = fixityServiceConfig.getConnection(true);
        FixityMRTEntry[] entries = FixityDBUtil.getItemEntries(connection,urlS, logger);
        if (DEBUG) System.out.println("getFixityEntry.length=" + entries.length);
        if (entries == null) return null;
        return new FixityEntriesState(entries);
    }

    @Override
    public FixityMRTEntry[] getFixityEntries(InvAudit audit)
        throws TException
    {
        
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        Connection connection = fixityServiceConfig.getConnection(true);
        SearchFixityEntries searchEntries = FixityActionAbs.getSearchFixityEntries(audit, connection, logger);
        FixityMRTEntry[] entries = searchEntries.call();
        if (entries == null) {
            throw new TException.REQUESTED_ITEM_NOT_FOUND("No entries found for this search");
        }
        return entries;
    }

    @Override
    public FixityMRTEntry queue(InvAudit audit)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        Connection connection = fixityServiceConfig.getConnection(false);
        ProcessFixityEntry queue = ProcessFixityEntry.getProcessFixityEntry("queue", audit, connection, logger);
        FixityMRTEntry responseEntry = queue.call();
        if (queue.getException() != null) {
            throwException(queue.getException());
        }
        return responseEntry;
    }

    @Override
    public FixityMRTEntry test(Properties mrtProp)
        throws TException
    {
        ProcessFixityEntry test = ProcessFixityEntry.getTest(mrtProp, logger);
        FixityMRTEntry responseEntry = test.call();
        if (test.getException() != null) {
            throwException(test.getException());
        }
        return responseEntry;
    }

    @Override
    public FixityMRTEntry update(InvAudit audit)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        Connection connection = fixityServiceConfig.getConnection(false);
        ProcessFixityEntry update = ProcessFixityEntry.getProcessFixityEntry("update", audit, connection, logger);
        FixityMRTEntry responseEntry = update.call();
        if (update.getException() != null) {
            throwException(update.getException());
        }
        return responseEntry;
    }

    @Override
    public FixityMRTEntry update(long id)
        throws TException
    {
        
        LoggerInf localLog = fixityServiceConfig.getLogger();
        if (localLog == null) {
            throw new TException.GENERAL_EXCEPTION("update: null log");
        }
        Connection connection = fixityServiceConfig.getConnection(false);
        InvAudit audit = FixityDBUtil.getAudit(connection, id, localLog);
        if (audit == null) {
            throw new TException.REQUESTED_ITEM_NOT_FOUND(MESSAGE + "update item not found:" + id);
        }
        ProcessFixityEntry update = ProcessFixityEntry.getProcessFixityEntry("update", audit, connection, localLog);
        FixityMRTEntry responseEntry = update.call();
        if (update.getException() != null) {
            throwException(update.getException());
        }
        return responseEntry;
    }

    @Override
    public FixitySubmittedState getSelectReport(String select, String emailTo, String emailMsg, String formatType)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        try {

            FixityReportSQL report = FixityActionAbs.getFixityReportSQL(select, logger);
            FixityEmailWrapper sqlReportWrapper = new FixityEmailWrapper(
                report,
                true,
                emailMsg,
                emailTo,
                formatType,
                fixityServiceConfig.getDb(),
                fixityServiceConfig.getSetupProperties(),
                logger);
            ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
            threadExecutor.execute( sqlReportWrapper ); // start task1
            threadExecutor.shutdown();
            Thread.sleep(3000);
            FixitySubmittedState retState = new FixitySubmittedState(true);
            return retState;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }

    @Override
    public FixitySubmittedState doCleanup(String formatType)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        FixityItemDB db = null;
        try {
            FixityCleanup fix = FixityActionAbs.getFixityCleanup(fixityServiceConfig, logger);
            db = fixityServiceConfig.getDb();
            FixityEmailWrapper cleanupWrapper = new FixityEmailWrapper(
                fix,
                true,
                fix.getEmailTo(),
                fix.getEmailFrom(),
                fix.getEmailSubject(),
                fix.getEmailMsg(),
                "xml",
                db,
                fixityServiceConfig.getSetupProperties(),
                logger);
            ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
            threadExecutor.execute(cleanupWrapper ); // start task1
            threadExecutor.shutdown();
            Thread.sleep(3000);
            FixitySubmittedState retState = new FixitySubmittedState(true);
            return retState;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }

    @Override
    public FixitySubmittedState doPeriodicReport(String formatType)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        FixityItemDB db = null;
        try {
            FixityCleanup fix = FixityActionAbs.getFixityCleanup(fixityServiceConfig, logger);
            db = fixityServiceConfig.getDb();
            FixityEmailWrapper cleanupWrapper = new FixityEmailWrapper(
                fix,
                true,
                fix.getEmailTo(),
                fix.getEmailFrom(),
                fix.getEmailSubject(),
                fix.getEmailMsg(),
                "xml",
                db,
                fixityServiceConfig.getSetupProperties(),
                logger);
            ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
            threadExecutor.execute(cleanupWrapper ); // start task1
            threadExecutor.shutdown();
            Thread.sleep(3000);
            FixitySubmittedState retState = new FixitySubmittedState(true);
            return retState;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }

    @Override
    public FixitySubmittedState getEntryReport(InvAudit audit, String email, String formatType)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        try {

            FixityReportEntries report = FixityActionAbs.getFixityReportEntries(audit, logger);
            FixityEmailWrapper emailWrapper = new FixityEmailWrapper(
                report,
                true,
                "Fixity Entry Report",
                email,
                formatType,
                fixityServiceConfig.getDb(),
                fixityServiceConfig.getSetupProperties(),
                logger);
            ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
            threadExecutor.execute( emailWrapper ); // start task1
            threadExecutor.shutdown();
            Thread.sleep(3000);
            FixitySubmittedState retState = new FixitySubmittedState(true);
            return retState;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }

    @Override
    public FixitySubmittedState getItemReport(
            String typeS,
            String context,
            String emailTo,
            String formatTypeS)
        throws TException
    {
        if (fixityServiceConfig.isShutdown()) {
            throw new TException.EXTERNAL_SERVICE_UNAVAILABLE("Fixity service shutdown");
        }
        try {
            String contextDisp = context;
            if (context == null) contextDisp = "";
            FixityReportItem report = FixityActionAbs.getFixityReportItem(typeS, context, logger);
            String emailFrom = getServiceMailto("supportURI", "default@cdlib.org");
            DateState date = new DateState();
            String msg = "Message created: \"" + date.getIsoDate()+ "\"" + NL
                    + "type: \""  + typeS + "\""  + NL
                    + "context: \"" + contextDisp + "\""  + NL
                    + "format: \""  + formatTypeS + "\""  + NL;

            FixityEmailWrapper emailWrapper = new FixityEmailWrapper(
                report,
                true,
                emailTo,
                emailFrom,
                "Fixity Item Report",
                msg,
                formatTypeS,
                fixityServiceConfig.getDb(),
                fixityServiceConfig.getSetupProperties(),
                logger);

            ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
            threadExecutor.execute( emailWrapper ); // start task1
            threadExecutor.shutdown();
            Thread.sleep(3000);
            FixitySubmittedState retState = new FixitySubmittedState(true);
            return retState;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }

    @Override
    public FixityServiceState setFixityRun()
        throws TException
    {
        if (THREADDEBUG) FixityUtil.sysoutThreads("Begin setFixityRun");
        fixityServiceConfig.setShutdown(false);
        fixityServiceConfig.dbStartup();
        FixityState fixityState = null;
        try {
            fixityServiceConfig.refresh();
            fixityState = fixityServiceConfig.getFixityState();
            if (fixityState.isFixityProcessing()) {
                if (!fixityState.isRunFixity()) {
                    fixityState.setRunFixity(true);
                }
            }
            if (!fixityState.isFixityProcessing()) {
                RunFixity runFixity = new RunFixity(fixityState, fixityServiceConfig.getDb(), logger);
                ExecutorService threadExecutor = Executors.newFixedThreadPool( 1 );
                threadExecutor.execute( runFixity ); // start task1
                threadExecutor.shutdown();
                Thread.sleep(3000);
            }
            FixityServiceState fixityServiceState = fixityServiceConfig.getFixityServiceStatus();
            setProcessCount(fixityServiceState);
            //fixityServiceConfig.startPeriodicReport();
            if (THREADDEBUG) FixityUtil.sysoutThreads("End setFixityRun");
            return fixityServiceState;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }

    @Override
    public FixityServiceState setFixityStop()
        throws TException
    {
        if (THREADDEBUG) FixityUtil.sysoutThreads("Begin setFixityStop");
        FixityState fixityState = null;
        try {
            fixityState = fixityServiceConfig.getFixityState();
            if (fixityState.isFixityProcessing() && fixityState.isRunFixity()) {
                fixityState.setRunFixity(false);
            }
            FixityServiceState fixityServiceState = fixityServiceConfig.getFixityServiceStatus();
            setProcessCount(fixityServiceState);
            if (THREADDEBUG) FixityUtil.sysoutThreads("End setFixityStop");
            return fixityServiceState;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    @Override
    public FixityServiceState setShutdown()
        throws TException
    {
        if (THREADDEBUG) FixityUtil.sysoutThreads("Begin setShutdown");
        fixityServiceConfig.setShutdown(true);
        //fixityServiceConfig.shutdownPeriodicReport();
        fixityServiceConfig.dbShutDown();
        if (THREADDEBUG) FixityUtil.sysoutThreads("End setShutdown");
        return setFixityStop();
    }
    

    @Override
    public FixityServiceState setPause()
        throws TException
    {
        if (THREADDEBUG) FixityUtil.sysoutThreads("Begin setPause");
        fixityServiceConfig.setShutdown(true);
        if (THREADDEBUG) FixityUtil.sysoutThreads("End setPause");
        return setFixityStop();
    }

    @Override
    public void setStartup()
        throws TException
    {
        setFixityRun();
        fixityServiceConfig.setShutdown(false);
    }
    
    protected void setProcessCount(FixityServiceState fixityServiceState)
    {
        FixityState state = fixityServiceConfig.getFixityState();
        fixityServiceState.setProcessCount(state.getCnt());
    }
    protected void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(msg);
    }


    public static void main(String args[])
    {

        FixityItemDB db = null;
        FixityServiceConfig fixityServiceConfig = null;
        FixityEntriesState entries = null;
        try {
            fixityServiceConfig
                    = FixityServiceConfig.useYaml();
            FixityMRTService service = new FixityMRTService(fixityServiceConfig);
            StateInf state = service.getFixityServiceState();
            LoggerInf logger = fixityServiceConfig.getLogger();
            
            String format = formatIt(logger, state);
            System.out.println("Initial Service State:" + NL + format);


            System.out.println("****GETENTRY 1*****");
            String urlS = "http://localhost:28080/fixity/AndersonM.pdf";
            entries = service.getFixityEntry(urlS);
            FixityMRTEntry entry = null;
            if (entries.size() > 0) entry = entries.getContextEntry(0);
            if (entry == null) {
                System.out.println("NULL entry");
                return;
            }
            format = formatIt(logger, entry);
            System.out.println("getEntry 1:" + NL + format);

/*
            System.out.println("****TEST ADD 1*****");
            FixityMRTEntry addEntry = getTestEntry();
            FixityMRTEntry respEntry = service.add(addEntry);
            if (respEntry == null) {
                System.out.println("respEntry NULL");
                return;
            }
            format = formatIt(logger, respEntry);
            System.out.println("TEST ADD 1:" + NL + format);


            System.out.println("****TEST DELETE 2*****");
            FixityMRTEntry deleteEntry = service.delete(addEntry.getUrl(), true);
            if (deleteEntry == null) {
                System.out.println("deleteEntry NULL");
                return;
            }
            format = formatIt(logger, deleteEntry);
            System.out.println("TEST DELETE 2:" + NL + format);

if (true) return;
            System.out.println("****FIXITYRUN 1*****");
            state = service.setFixityRun();
            format = formatIt(logger, state);
            System.out.println("setFiityRun 1:" + NL + format);



            System.out.println("****GETENTRY 2*****");
            entries = service.getFixityEntry(urlS);
            entry = null;
            if (entries.size() > 0) entry = entries.getContextEntry(0);
            if (entry == null) {
                System.out.println("NULL entry");
                return;
            }
            format = formatIt(logger, entry);
            System.out.println("getEntry 2:" + NL + format);

            Thread.sleep(60000);
            System.out.println("****FIXITYSTOP 1*****");
            state = service.setFixityStop();
            format = formatIt(logger, state);
            System.out.println("setFixityStop 1:" + NL + format);

            System.out.println("****FIXITYRUN 2*****");
            state = service.setFixityRun();
            format = formatIt(logger, state);
            System.out.println("setFiityRun 2:" + NL + format);

            Thread.sleep(60000);
            System.out.println("****FIXITYSTOP 2*****");
            state = service.setFixityStop();
            format = formatIt(logger, state);
            System.out.println("setFixityStop 2:" + NL + format);
*/
        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            try {
                fixityServiceConfig.dbShutDown();
            } catch (Exception ex) { }
        }
    }

    public static String formatIt(
            LoggerInf logger,
            StateInf responseState)
    {
        try {
           FormatterInf anvl = FormatterAbs.getJSONFormatter(logger);
           ByteArrayOutputStream outStream = new ByteArrayOutputStream(5000);
           PrintStream  stream = new PrintStream(outStream, true, "utf-8");
           anvl.format(responseState, stream);
           stream.close();
           outStream.close();
           byte [] bytes = outStream.toByteArray();
           String retString = new String(bytes, "UTF-8");
           return retString;

        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            System.out.println("Trace:" + StringUtil.stackTrace(ex));
            return null;
        } finally {

        }
    }

    protected String getServiceMailto(String key, String defaultValue)
        throws TException
    {
        Properties serviceProperties = fixityServiceConfig.getSetupProperties();
        if (serviceProperties == null) return defaultValue;
        String value = serviceProperties.getProperty(key);
        if (StringUtil.isEmpty(value)) return defaultValue;
        if (!value.startsWith("mailto:")) return null;
        return value.substring(7);
    }

    protected void throwException(Exception ex)
        throws TException
    {
        if (ex instanceof TException) {
            throw (TException) ex;
        }
        throw new TException(ex);
    }

    public LoggerInf getLogger() {
        return logger;
    }
    
    

}
