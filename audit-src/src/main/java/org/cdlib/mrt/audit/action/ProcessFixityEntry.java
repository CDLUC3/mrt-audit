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
import java.util.Properties;
import java.util.concurrent.Callable;

import org.cdlib.mrt.audit.handler.FixityHandler;
import org.cdlib.mrt.audit.handler.FixityHandlerAbs;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.core.MessageDigest;
import org.cdlib.mrt.audit.utility.FixityOwn;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.TimerUtil;

/**
 * Run fixity
 * @author dloy
 */
public class ProcessFixityEntry
        extends FixityActionAbs
        implements Callable, Runnable
{

    protected static final String NAME = "ProcessFixityEntry";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;

    public static enum FixityEntryType {add, queue, test, update};

    protected FixityHandler handler = null;
    protected FixityEntryType cmdType = null;
    
    protected ProcessFixityEntry(
            String cmd,
            InvAudit audit,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        super(audit, connection, logger);
        cmd = cmd.toLowerCase();
        try {
            cmdType = FixityEntryType.valueOf(cmd);
        } catch (Exception ex) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "Command type not supported:" + cmd);
        }
    }
    protected ProcessFixityEntry(
            Properties mrtProp,
            LoggerInf logger)
        throws TException
    {
        super(logger);
        try {
            cmdType = FixityEntryType.valueOf("test");
            FixityMRTEntry entry = new FixityMRTEntry(logger);
            entry.setFromProperties(mrtProp);
            this.mrtEntry = entry;
            
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    @Override
    public void run()
    {
        logger.logMessage(MESSAGE
                + " - cmdType=" + cmdType
                + " - url=" + mrtEntry.getUrl()
                , 10, true);
        if (DEBUG) System.out.println("***run:"
                + " - cmdType=" + cmdType
                + " - url=" + mrtEntry.getUrl());
        switch(cmdType) {
            case test:  
                TimerUtil.start(logger, "runTest");
                runTest();
                TimerUtil.end(logger, "runTest");
                return;
            case update:  
                TimerUtil.start(logger, "runUpdate");
                runUpdate2();
                TimerUtil.end(logger, "runUpdate");
                return;
            default: {
                TException tex = new TException.INVALID_ARCHITECTURE("Unsupported cmdType:" + cmdType.toString());
                setRunException(tex);
                return;
            }
        }
    }

    public void runTest()
    {
        try {
            log("runTest entered");
            handler = FixityHandlerAbs.getFixityHandler(mrtEntry, logger);
            handler.validate();

            log("before runFixity");
            handler.runFixity();
            if (mrtEntry.getStatus() != FixityStatusType.verified) {
                throw new TException.REQUEST_INVALID("Fixity Test fails:" + mrtEntry.getStatus().toString());
            }

        } catch (Exception ex) {
            setRunException(ex);

        }

    }

    public void runUpdate()
    {
        try {
            log("runUpdate2 entered");
            connection.setAutoCommit(false);
            long id = getItemID(mrtEntry);
            if(id < 1) {
                throw new TException.REQUESTED_ITEM_NOT_FOUND(
                        "No matching URL was found for this item:" + mrtEntry.getUrl());
            }
            if (DEBUG) mrtEntry.printEntry("IN UPDATE mrtEntry");
            TimerUtil.end(logger, "runUpdate getStatus");

            TimerUtil.start(logger, "runUpdate validate");
            handler = FixityHandlerAbs.getFixityHandler(mrtEntry, logger);
            handler.validate();
            TimerUtil.end(logger, "runUpdate validate");

            TimerUtil.start(logger, "runUpdate runFixity");
            if (DEBUG) System.out.println("***mrtEntry size:" + mrtEntry.getSize());
            handler.runFixity();
            if (DEBUG) mrtEntry.printEntry("AFTER UPDATE");
            TimerUtil.end(logger, "runUpdate runFixity");
            
            TimerUtil.start(logger, "runUpdate updateEntry");
            updateEntry();
            TimerUtil.end(logger, "runUpdate updateEntry");

            TimerUtil.start(logger, "runUpdate commit");
            connection.commit();
            TimerUtil.end(logger, "runUpdate commit");
            

        } catch (Exception ex) {
            setRunException(ex);

        } finally {
            try {
                connection.close();
            } catch (Exception ex) { }
        }

    }
    
    

    public void runUpdate2()
    {
        try {
            log("runUpdate2 entered");
            connection.setAutoCommit(true);
            long id = getItemID(mrtEntry);
            if(id < 1) {
                throw new TException.REQUESTED_ITEM_NOT_FOUND(
                        "No matching URL was found for this item:" + mrtEntry.getUrl());
            }
            int result = FixityOwn.ownInvAudit(id, connection, logger);
            if (result <= 0) {
                System.out.print("Warning audit update not owned:" + id);
                /*
                throw new TException.CONCURRENT_UPDATE(
                        "Item currently being processed:" + mrtEntry.getUrl());
                */
            }
            InvAudit audit = FixityDBUtil.getAudit(connection, id, logger);
            if (connection == null) return;
            
            connection.setAutoCommit(false);
            FixityValidationEntry validator = FixityActionAbs.getFixityValidationEntry(audit, connection, logger);
            validator.run();
            connection.commit();
            

        } catch (Exception ex) {
            try {
                connection.rollback();
            } catch(Exception rex) { 
                System.out.println(MESSAGE + "rollback exception");
            }
            setRunException(ex);

        }

    }

    /**
     * If either the digest or the file size don't match then a rerun is necessare
     * @param dbEntry - currently saved version of entry
     * @param mergeEntry - merge version of entry
     * @return true=rerun fixity test; false=no changes to primary fixity values
     * @throws TException
     */
    public static boolean isFixityDiff(FixityMRTEntry dbEntry, FixityMRTEntry mergeEntry)
        throws TException
    {
        System.out.println("***isFixityDiff\n"
                + " - " + dbEntry.dump("dbEntry") + "\n"
                + " - " + dbEntry.dump("mergeEntry") + "\n"
                );
        MessageDigest dbDigest = dbEntry.getDigest();
        MessageDigest mergeDigest = mergeEntry.getDigest();
        long dbSize = dbEntry.getSize();
        long mergeSize = mergeEntry.getSize();
        FixityMRTEntry.SourceType dbSourceType = dbEntry.getSource();
        FixityMRTEntry.SourceType mergeSourceType = mergeEntry.getSource();


        if ((dbDigest != null) && (mergeDigest != null)) {
            if (!dbDigest.toString().equals(mergeDigest.toString())) {
                return true;
            }

        } else if ((dbDigest == null) && (mergeDigest == null)) {
            return false;

        } else if ((dbDigest != null) || (mergeDigest != null)) {
            return true;
        }

        if ((dbSize <= 0) && (mergeSize <= 0)) {
            return false;

        }  else if (dbSize != mergeSize) {
            return true;
        }

        if ((dbSourceType != null) && (mergeSourceType != null)) {
            if (dbSourceType != mergeSourceType) {
                return true;
            }

        } else if ((dbSourceType == null) && (mergeSourceType == null)) {
            return false;

        } else if ((dbSourceType != null) || (mergeSourceType != null)) {
            return true;
        }

        return false;
    }
    protected void setRunException(Exception exception)
    {
        Properties entryProp = mrtEntry.retrieveProperties();
        String msg = MESSAGE + "Exception for entry id=" + mrtEntry.getItemKey()
                + " - " + PropertiesUtil.dumpProperties("entry", entryProp)
                ;
        if (DEBUG) exception.printStackTrace();
        logger.logError(msg, 2);
        logger.logError(StringUtil.stackTrace(exception), 5);
        try {
            if (connection != null) connection.rollback();
        } catch (Exception cex) {
            System.out.println("WARNING: rollback Exception:" + cex);
        }
        setException(exception);
    }


    @Override
    public FixityMRTEntry call()
    {
        run();
        return getEntry();
    }

    protected static void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }
}

