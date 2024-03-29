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

import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.log.utility.Log4j2Util;

/**
 * Format container class for Fixity Service
 * @author dloy
 */
public class FixityServiceState
        implements StateInf
{
    private static final String NAME = "FixityServiceState";
    private static final String MESSAGE = NAME + ": ";
    public enum StateStatus  { unknown, paused, running, shuttingdown, shutdown, pause; }

    protected String name = null;
    protected String identifier = null;
    protected String description = null;
    protected String version = null;
    protected long interval = 0;
    protected int threadPool = 1;
    protected int queueCapacity = 100;
    protected DateState lastIteration = null;
    protected Double elapsedTime = null;
    protected Long totalSize = null;
    protected Long numItems = null;
    protected Long numFailedItems = null;
    protected Long numUnavailable = null;
    protected Long numUnverified = null;
    protected StateStatus status = StateStatus.unknown;
    protected FixityStatusType type = null;
    protected DateState created = null;
    protected DateState lastModified = null;
    protected FixityScheme serviceScheme = null;
    //protected String baseURI = null;
    protected String supportURI = null;
    protected Long processCount = null;
    protected String periodicReportTo = null;
    protected long periodicReportFrequency = -1;
    protected String periodicReportFormat = null;
    protected long queueSleep = 0;
    protected String auditQualify = null;

    public FixityServiceState() { }

    public FixityServiceState(Properties prop)
    {
        setValues(prop);
    }

    /**
     * @return Creation date for entry
     */
    public DateState getCreated() {
        return created;
    }

    public void setCreated(DateState created) {
        this.created = created;
    }

    /**
     * 
     * @return non required description of entry
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 
     * @return number of days between oldest verification entry 
     * and most recent verification entry
     */
    public Double getElapsedTimeDays() {
        return elapsedTime;
    }

    public void setElapsedTimeDays(Double elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * 
     * @return minimum number of days between the fixity testing for a specific entry
     */
    public Long getIntervalDays() {
        return interval;
    }

    public void setIntervalDays(long interval) {
        this.interval = interval;
    }

    public void setIntervalDays(String intervalS) {
        if (intervalS == null) return;
        this.interval = Long.parseLong(intervalS);
    }

    public DateState getLastIteration() {
        return lastIteration;
    }

    /**
     * 
     * @return Date of last entry processed
     */
    public void setLastIteration(DateState lastIteration) {
        this.lastIteration = lastIteration;
    }

    public DateState getLastModified() {
        return lastModified;
    }

    public void setLastModified(DateState lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * 
     * @return Name of fixity service
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPeriodicReportTo() {
        return periodicReportTo;
    }

    public void setPeriodicReportTo(String periodicReportTo) {
        this.periodicReportTo = periodicReportTo;
    }

    /**
     * 
     * @return Number of entries that failed fixity tests
     */
    public Long getNumFailedItems() {
        return numFailedItems;
    }

    public void setNumFailedItems(Long numFailedItems) {
        this.numFailedItems = numFailedItems;
    }

    /**
     * 
     * @return Number of entries
     */
    public Long getNumItems() {
        return numItems;
    }

    public void setNumItems(Long numItems) {
        this.numItems = numItems;
    }

    public void setNumItems(String numItemsS) {
        if (StringUtil.isEmpty(numItemsS)) return;
        this.numItems = Long.parseLong(numItemsS);
    }

    public void setServiceScheme(FixityScheme serviceScheme) {
        this.serviceScheme = serviceScheme;
    }

    /**
     * 
     * @return Fixity service scheme
     */
    public String getServiceScheme() {
        if (serviceScheme == null) return null;
        return serviceScheme.toString();
    }

    public void setServiceScheme(String schemeLine)
    {
        try {
            this.serviceScheme = FixityScheme.buildSpecScheme("fixity", schemeLine);
        } catch (Exception ex) {
            System.out.println("WARNING: setServiceScheme fails:" + ex);
        }
    }

    public FixityScheme retrieveServiceScheme()
    {
        return serviceScheme;
    }

    /**
     * 
     * @return fixity status: running, shuttingdown, shutdown
     */
    public String getStatus() {
        return status.toString();
    }

    public void setStatus(StateStatus status) {
        this.status = status;
    }

    /**
     * 
     * @return Number of concurrent executing threads
     */
    public int getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(int threadPool) {
        this.threadPool = threadPool;
    }

    public void setThreadPool(String threadPoolS) {
        this.threadPool = Integer.parseInt(threadPoolS);
    }

    /**
     * 
     * @return total of all entry sizes
     */
    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public FixityStatusType getType() {
        return type;
    }

    public void setType(FixityStatusType type) {
        this.type = type;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Set all entry values based on Properties
     * @param prop 
     */
    public void setValues(Properties prop)
    {
        setName(prop.getProperty("name"));
        setDescription(prop.getProperty("description"));
        //setIntervalDays(prop.getProperty("interval"));
        setIntervalDays(prop.getProperty("intervalDays"));
        setThreadPool(prop.getProperty("threadPool"));
        setServiceScheme(prop.getProperty("serviceScheme"));
        //setBaseURI(prop.getProperty("baseURI"));
        setPeriodicReportTo(prop.getProperty("periodicReportTo"));
        //setSupportURI(prop.getProperty("supportURI"));
        setPeriodicReportFrequencyHours(prop.getProperty("periodicReportFrequency"));
        setPeriodicReportFrequencyHours(prop.getProperty("periodicReportFrequencyHours"));
        setPeriodicReportFormat(prop.getProperty("periodicReportFormat"));
        setQueueSleepMs(prop.getProperty("queueSleepMs"));
        setAuditQualify(prop.getProperty("auditQualify"));
        setQueueCapacity(prop.getProperty("queueCapacity"));
    }

    /**
     * 
     * @return number of entries getting a 5xx error on request
     */
    public Long getNumUnavailable() {
        return numUnavailable;
    }

    public void setNumUnavailable(Long numUnavailable) {
        this.numUnavailable = numUnavailable;
    }

    public Long getNumUnverified() {
        return numUnverified;
    }

    public void setNumUnverified(Long numUnverified) {
        this.numUnverified = numUnverified;
    }

    /**
     * 
     * @return number of entries processed since last startup
     */
    public Long getProcessCount() {
        return processCount;
    }

    public void setProcessCount(Long processCount) {
        this.processCount = processCount;
    }

    public DateState getCurrentReportDate()
    {
        return new DateState();
    }

    /**
     * 
     * @return Format of periodic report
     */
    public String getPeriodicReportFormat() {
        return periodicReportFormat;
    }

    public void setPeriodicReportFormat(String periodicReportFormat) {
        this.periodicReportFormat = periodicReportFormat;
    }

    /**
     * 
     * @return Number of hours between the automatic submission of a periodic report
     */
    public long getPeriodicReportFrequencyHours() {
        return periodicReportFrequency;
    }

    public void setPeriodicReportFrequencyHours(long periodicReportFrequency) {
        this.periodicReportFrequency = periodicReportFrequency;
    }

    public void setPeriodicReportFrequencyHours(String periodicReportFrequencyS) {
        if (StringUtil.isEmpty(periodicReportFrequencyS)) return;
        this.periodicReportFrequency = Long.parseLong(periodicReportFrequencyS);
    }

    /**
     * 
     * @return Number of seconds between the submission of each entry for processing
     */
    public long getQueueSleepMs() {
        return queueSleep;
    }

    public void setQueueSleepMs(long queueSleep) {
        this.queueSleep = queueSleep;
    }


    public void setQueueSleepMs(String queueSleepS) {
        if (StringUtil.isEmpty(queueSleepS)) return;
        this.queueSleep = Long.parseLong(queueSleepS);
    }


    // seconds
    public void setQueueSleep(String queueSleepS) {
        if (StringUtil.isEmpty(queueSleepS)) return;
        this.queueSleep = (Long.parseLong(queueSleepS) * 1000);
    }

    // auditQualify - MySQL qualification to audit select
    public String getAuditQualify() {
        return auditQualify;
    }

    public void setAuditQualify(String auditQualify) {
        this.auditQualify = auditQualify;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    

    public void setQueueCapacity(String capacityS) {
        if (StringUtil.isAllBlank(capacityS)) return;
        this.queueCapacity = Integer.parseInt(capacityS);
    }

    public static DateState getServiceStartTime() {
        return FixityServiceConfig.getServiceStartTime();
    }
    
    public static String getLogRootLevel()
    {
        try {
            return Log4j2Util.getRootLevel();
        } catch (Exception  ex) {
            return "Not found";
        }
    }
}
