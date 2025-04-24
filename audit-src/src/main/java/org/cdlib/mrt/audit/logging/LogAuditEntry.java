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
package org.cdlib.mrt.audit.logging;

import java.util.Properties;
import org.apache.logging.log4j.Level;
import org.cdlib.mrt.audit.handler.FixityHandlerStandard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.service.FixityServiceConfig;
import static org.cdlib.mrt.audit.utility.FixityUtil.removeEsc;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.log.utility.AddStateEntryGen;
import org.cdlib.mrt.s3.service.NodeIO;

import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.StringUtil;
/**
 * Run fixity
 * @author dloy
 */
public class LogAuditEntry
{

    protected static final String NAME = "LogAuditEntry";
    protected static final String MESSAGE = NAME + ": ";
    private static final Logger log4j = LogManager.getLogger();
    
    
    protected static final Level addLevel = Level.DEBUG;
    protected String serviceProcess = null;
    protected Long durationMs = null;
    protected FixityMRTEntry fixityEntry = null;
    protected Long addBytes = null;
    protected Long addFiles = null;
    protected AddStateEntryGen stateEntry = AddStateEntryGen.getAddStateEntryGen("audit", "audit", "auditRun");
    protected String keyPrefix = null;
    
    
    public static LogAuditEntry getLogAuditEntry(
            long durationMs,
            FixityMRTEntry fixityEntry)
        throws TException
    {
        return new LogAuditEntry(durationMs,fixityEntry);
    }
    
    public static void addLogAuditEntry(
            long durationMs,
            FixityMRTEntry fixityEntry)
        throws TException
    {
        if (log4j.getLevel() != addLevel) {
            log4j.trace("***skip addLogAuditEntry");
            return;
        }
        LogAuditEntry logEntry = getLogAuditEntry(durationMs, fixityEntry);
        logEntry.addEntry(addLevel);
    }
    
    public LogAuditEntry(
            Long durationMs,
            FixityMRTEntry fixityEntry)
        throws TException
    {
        this.keyPrefix = "autest";
        this.serviceProcess = "AuditTest";
        this.durationMs = durationMs;
        if (fixityEntry == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "fixityEntry missing");
        }
        this.fixityEntry = fixityEntry;
        if (durationMs == null) {
            log4j.trace("Nearline: url:" + fixityEntry.getUrl());
            return;
        } else {
            stateEntry.setDurationMs(durationMs);
        }
        log4j.trace("LogAuditEntry constructor");
        setEntry();
    }
    
    private void setEntry()
        throws TException
    {
        FixityStatusType fixityStatus = fixityEntry.getStatus();
        if (fixityStatus != null) {
            stateEntry.setStatus(fixityStatus.toString());
        }
        
        String locationUrl = fixityEntry.getUrl();
        locationUrl = removeEsc(locationUrl);
        NodeIO.AccessKey accessKey = FixityServiceConfig.getCloudChecksumAccessKey(locationUrl);
        NodeIO.AccessNode accessNode = accessKey.accessNode;
        stateEntry.setProcessNode(accessNode.nodeNumber);
        stateEntry.setKey(accessKey.key);

        String [] parts = accessKey.key.split("\\|",3);
        if (parts.length == 3) {
            stateEntry.setArk(parts[0]);
            try {
                int version = Integer.parseInt(parts[1]);
                stateEntry.setVersion(version);
            } catch (Exception tmpEx) { }
            stateEntry.setFileID(parts[2]);
        }
        stateEntry.setBytes(fixityEntry.getSize());
        stateEntry.setFiles(1L);
        stateEntry.setAwsVersion(FixityServiceConfig.getAwsVersion());
        Properties stateProp = new Properties();
        if (durationMs != null) {
            stateProp.setProperty("nearline", "true");
        }
    }
    
    
    public void addEntry(Level addLevel)
        throws TException
    {
        stateEntry.addLogStateEntry(addLevel.toString(), "auditJSON");
    }
}

