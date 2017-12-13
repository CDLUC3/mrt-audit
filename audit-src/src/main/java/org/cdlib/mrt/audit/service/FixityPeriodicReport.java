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

import java.util.Properties;

import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.action.PeriodicServiceReport;
import org.cdlib.mrt.audit.action.FixityEmailWrapper;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.utility.TFrame;

/**
 * Run scheduled reports.
 * @author  dloy
 */

public class FixityPeriodicReport implements Runnable
{
    private static final String NAME = "FixityPeriodicReport";
    private static final String MESSAGE = NAME + ": ";
    private static final String NL = System.getProperty("line.separator");

    private static final boolean DEBUG = false;
    protected LoggerInf logger = null;
    protected FixityItemDB db = null;
    protected Properties prop = null;
    protected Exception exception = null;
    //protected FixityServiceProperties serviceStateProperties = null;
    protected Properties setupProperties = null;
    //protected Properties serviceProperties = null;
    protected FixityServiceProperties fixityServiceProperties = null;
    protected FixityState fixityState = null;
    protected int delay = 0;
    protected long frequency = 0;

    protected String emailTo = null;
    protected String formatTypeS = null;
    protected String emailFrom = null;
    protected String subject = null;

    private volatile ScheduledExecutorService scheduler = null;
    
    public FixityPeriodicReport(
            FixityServiceProperties fixityServiceProperties,
            LoggerInf logger)
        throws TException
    {
        this.fixityServiceProperties = fixityServiceProperties;
        this.logger = logger;
        this.setupProperties = fixityServiceProperties.getSetupProperties();
        fixityState = fixityServiceProperties.getFixityState();
        this.emailTo = getMailto(fixityState.getNotification());
        this.emailFrom = getMailto(fixityState.getSupportURI());
        this.subject = "Fixity: periodic report";
        this.formatTypeS = fixityState.getPeriodicReportFormat();
        validate();
    }
    
    protected void validate()
        throws TException
    {
        if (fixityServiceProperties == null) {
            throw new TException.INVALID_OR_MISSING_PARM("FixityServiceProperties object required");
        }
        if (StringUtil.isEmpty(emailTo)) {
            throw new TException.INVALID_OR_MISSING_PARM("Report email location required");
        }
        if (StringUtil.isEmpty(emailFrom)) {
            throw new TException.INVALID_OR_MISSING_PARM("Report email From required");
        }
        if (StringUtil.isEmpty(formatTypeS)) {
            formatTypeS = "xml";
        }

        frequency = fixityState.getPeriodicReportFrequencyHours();
        frequency *= (60*60); // in hours

        String delayS = setupProperties.getProperty(NAME + ".delay", "60");
        delay = Integer.parseInt(delayS);
        log("delay=" + delay + " - frequency=" + frequency);
    }

    protected String getMailto(String value)
    {
        if (StringUtil.isEmpty(value)) return null;
        if (!value.startsWith("mailto:")) return value;
        return value.substring(7);
    }


    /**
     * Main method
     */
    public static void main(String args[])
    {

        TFrame tFrame = null;
        FixityServiceProperties fixityServiceProperties = null;
        try {
            String propertyList[] = {
                "resources/FixityTest.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");
            fixityServiceProperties =
                    FixityServiceProperties.getFixityServiceProperties(tFrame.getProperties());
            // Create an instance of this object
            LoggerInf logger = new TFileLogger(NAME, 50, 50);
            fixityServiceProperties.setShutdown(false);
            if (fixityServiceProperties.getDb() == null) 
                System.out.println("fixityServiceProperties: null");
            else System.out.println("fixityServiceProperties: Not null");
            FixityPeriodicReport report = new FixityPeriodicReport(
                    fixityServiceProperties,
                    logger
                    );
            System.out.println("AFTER constructor:"
                    + " - isRunning:" + report.isRunning()
                    );
            FixityEmailWrapper wrapper = report.setWrapper();
            wrapper.run();
            //report.run();
            //Thread.sleep(360000);

        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            try {
                fixityServiceProperties.getDb().shutDown();
            } catch (Exception ex) { }
        }
    }

    @Override
    public void run()
    {
        try {
            System.err.println("****RUN CALLED");
            log("****FixityPeriodicReport.run:" + fixityServiceProperties.isShutdown());
            if (fixityServiceProperties.isShutdown()) return;
            FixityEmailWrapper wrapper = setWrapper();
            if (setScheduler()) {
            System.err.println("****SCHEDULER EXISTS");
                scheduler.scheduleWithFixedDelay
                    (wrapper, delay, frequency, TimeUnit.SECONDS);
                log("scheduled:"
                        + " - delay=" + delay
                        + " - frequency=" + frequency
                        );
            }

        } catch (TException fe) {
            fe.printStackTrace();
            setEx(fe);

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

        } 
    }

    public FixityEmailWrapper setWrapper()
        throws Exception
    {
        try {
            DateState dateSubmitted = new DateState();
            String msg = "Audit Periodic Report" + NL + NL
                    + "Reports started: " + dateSubmitted.getIsoDate() + NL;
            log(PropertiesUtil.dumpProperties("setWrapper", fixityState.getServiceProperties()));
            PeriodicServiceReport report = PeriodicServiceReport.getPeriodicServiceReport(fixityServiceProperties, logger);

            FixityEmailWrapper wrapper = new FixityEmailWrapper(
                report,
                true,
                emailTo,
                emailFrom,
                subject,
                msg,
                formatTypeS,
                fixityServiceProperties.getDb(), //null,
                setupProperties,
                logger);
            return wrapper;

        } catch(Exception e)  {
            throw e;
        }
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public long getFrequency() {
        return frequency;
    }

    public void setFrequency(long frequency) {
        this.frequency = frequency;
    }


    protected void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }

    public Exception getEx() {
        return exception;
    }

    public void setEx(Exception ex) {
        this.exception = ex;
    }
    
    protected synchronized boolean isRunning()
        throws TException
    {
        if (scheduler == null) return false;
        return true;
    }
    
    protected synchronized boolean setScheduler()
        throws TException
    {
        System.out.println("****setScheduler called");
        try {
            if (scheduler != null) {
                log("***Scheduler set");
                return false;
            }
            scheduler =
                Executors.newSingleThreadScheduledExecutor();
            log("setScheduler invoked");
            return true;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public synchronized void shutdown()
    {
        try {
            scheduler.shutdown();
            log("shutdown invoked");
        } catch (Exception ex) {
            System.out.println("Warning scheduler shutdown fails:"  + ex);
        } finally {
            scheduler = null;
        }
    }
    
    public String dump(String header)
        throws TException
    {
        StringBuffer buf = new StringBuffer(100);
        buf.append(header + NL);
        String msg = ""
                + " - formatTypeS:" + formatTypeS
                + " - emailFrom:" + emailFrom
                + " - emailTo:" + emailTo
                ;
        buf.append(msg + NL);
        msg = PropertiesUtil.dumpProperties("setup", setupProperties); 
        buf.append(msg + NL);
        msg = PropertiesUtil.dumpProperties("service", fixityState.getServiceProperties()); 
        buf.append(msg + NL);
        return buf.toString();
    }
}
