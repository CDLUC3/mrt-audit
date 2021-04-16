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
package org.cdlib.mrt.audit.action;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.cdlib.mrt.audit.db.FixityItemDB;

import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.service.FixityServiceConfig;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import static org.cdlib.mrt.audit.utility.FixityDBUtil.getMRTEntry;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Abstract for performing a fixity test
 * @author dloy
 */
public class FixityActionAbs
{

    protected static final String NAME = "FixityActionAbs";
    protected static final String MESSAGE = NAME + ": ";
    protected static final String  STATUS_PROCESSING = "processing";
    protected static final boolean DEBUG = false;

    protected FixityMRTEntry mrtEntry = null;
    protected InvAudit audit = null;
    protected LoggerInf logger = null;
    protected Connection connection = null;
    protected Exception exception = null;
    protected boolean updated = false;

    public static ProcessFixityEntry getProcessFixityEntry(
            String cmdType,
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        return new ProcessFixityEntry(cmdType, audit, connection, logger);

    }

    public static ProcessFixityEntry getTest(
            Properties mrtProp,
            LoggerInf logger)
        throws TException
    {
        return new ProcessFixityEntry(mrtProp, logger);

    }
    
    public static FixityValidation getFixityValidation(
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        return new FixityValidation(audit, connection, logger);

    }
    
    public static FixityValidationEntry getFixityValidationEntry(
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        return new FixityValidationEntry(audit, connection, logger);

    }

    public static FixityReportEntries getFixityReportEntries(
            InvAudit audit,
            LoggerInf logger)
        throws TException
    {
        return new FixityReportEntries(audit, logger);

    }

    public static FixityReportItem getFixityReportItem(
            String typeS,
            String context,
            LoggerInf logger)
        throws TException
    {
        return new FixityReportItem(typeS, context,logger);
    }

    public static FixityReportSQL getFixityReportSQL(
            String select,
            LoggerInf logger)
        throws TException
    {
        return new FixityReportSQL(select, logger);

    }

    public static FixityCleanup getFixityCleanup(
            FixityServiceConfig fixityServiceConfig,
            LoggerInf logger)
        throws TException
    {
        return new FixityCleanup(fixityServiceConfig, logger);

    }

    public static PeriodicServiceReport getPeriodicServiceReport(
            FixityServiceConfig fixityServiceConfig,
            LoggerInf logger)
        throws TException
    {
        return new PeriodicServiceReport(fixityServiceConfig, logger);

    }
    
    public static SearchFixityEntries getSearchFixityEntries(
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        return new SearchFixityEntries(audit, connection, logger);

    }

    protected FixityActionAbs(
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        this.audit = audit;
        this.logger = logger;
        this.connection = connection;
        mrtEntry = FixityDBUtil.getMRTEntry(audit, connection, logger);
    }

    protected FixityActionAbs(
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        this.logger = logger;
        this.connection = connection;
    }

    protected FixityActionAbs(
            LoggerInf logger)
        throws TException
    {
        this.logger = logger;
    }

    protected boolean itemExists(FixityMRTEntry item)
        throws TException
    {
        long itemKey = FixityDBUtil.matchItemKey(connection, item, logger);
        if (itemKey > 0) return true;
        return false;
    }

    protected long getItemID(FixityMRTEntry item)
        throws TException
    {
        return FixityDBUtil.matchItemKey(connection, item, logger);
    }

    protected void setProcessingOld(long id)
        throws TException
    {
        Properties prop = new Properties();
        prop.setProperty("status", "" + STATUS_PROCESSING);
        FixityDBUtil.updateInvAudit(id, connection, prop, logger);
    }

    protected boolean ownProcessing(long id)
        throws TException
    {
        int updates = FixityDBUtil.ownInvAudit(id, connection, logger);
        if (updates == 1) return true;
        return false;
    }
    
    protected void insertEntry()
        throws TException
    {
        if (FixityActionAbs.DEBUG) System.out.println("insertEntry entered");
        FixityDBUtil.replaceInvAudit(connection, mrtEntry, logger);
        long itemKey = FixityDBUtil.matchItemKey(connection, mrtEntry, logger);
        if (itemKey == 0) {
            throw new TException.GENERAL_EXCEPTION(MESSAGE
                        + "insertEntry fails:" + mrtEntry.getUrl());

        }
        mrtEntry.setItemKey(itemKey);
    }

    protected boolean updateEntry()
        throws TException
    {
        if (FixityActionAbs.DEBUG) System.out.println(PropertiesUtil.dumpProperties("!!!updateEntry", mrtEntry.retrieveProperties()));
        updated = FixityDBUtil.updateAudit(connection, mrtEntry, logger);
        return updated;
    }

    protected boolean updateEntryVerified()
        throws TException
    {
        if (FixityActionAbs.DEBUG) System.out.println(PropertiesUtil.dumpProperties("!!!updateEntry", mrtEntry.retrieveProperties()));
        updated = FixityDBUtil.updateAuditVerified(connection, mrtEntry, logger);
        return updated;
    }

    public FixityMRTEntry getEntry() {
        return mrtEntry;
    }

    public void setEntry(FixityMRTEntry entry) {
        this.mrtEntry = entry;
    }

    public Exception getException() {
        return exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public InvAudit getAudit() {
        return audit;
    }

    public void setAudit(InvAudit audit) {
        this.audit = audit;
    }

    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }
    
}

