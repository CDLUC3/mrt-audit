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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Primary Fixity container used by RunFixity. This class contains the primary control
 * properties used for fixity process handling.
 * 
 * 
 *  runFixity - this flag controls whether to start or stop fixity
 *            - true=fixity should be running or starting to run
 *            - false=stop fixity and exit routine
 *  fixityProcessing - this flag determines if fixity is running
 *            - true=fixity is now running
 *            - false=fixity has stopped
 * capacity - size of fixity block size
 * threadPool - number of concurrent threads to run fixity
 * interval - interval between the last and next execution of fixity for a particular entry
 * queueSleep - sleep interval between each fixity test to be performed
 * 
 * @author dloy
 */

public class FixityState
{
    private static final String NAME = "FixityState";
    private static final String MESSAGE = NAME + ": ";
    private static final String NL = System.getProperty("line.separator");

    private static final boolean DEBUG = true;
    protected volatile boolean runFixity = false;
    protected volatile boolean fixityProcessing = false;
    protected volatile int queueCapacity = 100;
    protected volatile int threadPool = 4;
    protected volatile long interval = 0;
    protected volatile long queueSleep = 0;
    protected AtomicLong cnt = new AtomicLong();
    protected Properties serviceProperties = null;
    protected long periodicReportFrequency = -1;
    protected String periodicReportFormat = null;
    protected String periodicReportTo = null;
    protected String notification = null;
    protected String supportURI = null;
    protected String auditQualify = null;

    public FixityState() { }

    public FixityState(Properties serviceProperties)
        throws TException
    {
        this.serviceProperties = serviceProperties;
        set();
    }

    public synchronized void set()
        throws TException
    {
        try {
            FixityServiceState state = new FixityServiceState(serviceProperties);
            setIntervalDays(state.getIntervalDays());
            setThreadPool(state.getThreadPool());
            setQueueSleepMs(state.getQueueSleepMs());
            setPeriodicReportFormat(state.getPeriodicReportFormat());
            setPeriodicReportFrequencyHours(state.getPeriodicReportFrequencyHours());
            setPeriodicReportTo(state.getPeriodicReportTo());
            setAuditQualify(state.getAuditQualify());
            setQueueCapacity(state.getQueueCapacity());
    
    
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public boolean isRunFixity() {
        return runFixity;
    }

    public synchronized void setRunFixity(boolean runFixity) {
        this.runFixity = runFixity;
    }

    public boolean isFixityProcessing() {
        return fixityProcessing;
    }

    public synchronized void setFixityProcessing(boolean fixityProcessing) {
        this.fixityProcessing = fixityProcessing;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public synchronized void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public long getIntervalDays() {
        return interval;
    }

    public synchronized void setIntervalDays(long interval) {
        this.interval = interval;
    }

    public int getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(int threadPool) {
        this.threadPool = threadPool;
    }

    public long getCnt()
    {
        return cnt.get();
    }

    public long getQueueSleepMs() {
        return queueSleep;
    }

    /**
     * Service state queueSleep is in increments of seconds - convert to milliseconds before storing
     * @param queueSleep
     */
    public synchronized void setQueueSleepMs(long queueSleep) {
        this.queueSleep = queueSleep;
    }

    public long bumpCnt()
    {
        return cnt.getAndAdd(1);
    }

    public Properties getServiceProperties() {
        return serviceProperties;
    }

    public long getPeriodicReportFrequency() {
        return periodicReportFrequency;
    }

    public void setPeriodicReportFrequency(long periodicReportFrequency) {
        this.periodicReportFrequency = periodicReportFrequency;
    }

    public String getPeriodicReportTo() {
        return periodicReportTo;
    }

    public void setPeriodicReportTo(String periodicReportTo) {
        this.periodicReportTo = periodicReportTo;
    }

    public String getPeriodicReportFormat() {
        return periodicReportFormat;
    }

    public void setPeriodicReportFormat(String periodicReportFormat) {
        this.periodicReportFormat = periodicReportFormat;
    }

    public long getPeriodicReportFrequencyHours() {
        return periodicReportFrequency;
    }

    public void setPeriodicReportFrequencyHours(long periodicReportFrequency) {
        this.periodicReportFrequency = periodicReportFrequency;
    }

    public String getSupportURI() {
        return supportURI;
    }

    public void setSupportURI(String supportURI) {
        this.supportURI = supportURI;
    }

    public String getAuditQualify() {
        return auditQualify;
    }

    public void setAuditQualify(String auditQualify) {
        this.auditQualify = auditQualify;
    }

    public String dump(String header)
        throws TException
    {
        StringBuffer buf = new StringBuffer(100);
        buf.append("FixityState:" + header + NL);
        String msg = ""
                + " - runFixity=" + runFixity + NL
                + " - fixityProcessing=" + fixityProcessing + NL
                + " - threadPool=" + threadPool + NL
                + " - interval=" + interval + NL
                + " - queueSleep=" + queueSleep + NL
                + " - cnt=" + cnt + NL
                + " - periodicReportFrequency=" + periodicReportFrequency + NL
                + " - periodicReportFormat=" + periodicReportFormat + NL
                + " - notification=" + notification + NL
                + " - queueSleep=" + queueSleep + NL
                + " - supportURI=" + supportURI + NL
                + " - serviceProperties=" + PropertiesUtil.dumpProperties("dump", serviceProperties) + NL
                ;
        buf.append(msg);
        return buf.toString();
               
    }
    
}
