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

import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.utility.TFrame;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.FixNames;
import org.cdlib.mrt.audit.service.FixityEntriesState;
import org.cdlib.mrt.audit.utility.FixityDBUtil;

import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class FixityReportItem
        extends FixityActionAbs
        implements Callable, Runnable, FixityActionInf
{

    protected static final String NAME = "FixityReportItem";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = true;

    protected enum Type {all, failed, adhoc, system, bad};
    protected FixityEntriesState fixityEntriesState = null;
    protected Type type = null;
    protected String context = null;

    public FixityReportItem(
            String typeS,
            String context,
            LoggerInf logger)
        throws TException
    {
        super(null, null, logger);
        setParms(typeS, context);
    }

    protected void setParms(
            String typeS,
            String context)
        throws TException
    {
        this.context = context;
        if (StringUtil.isNotEmpty(typeS)) {
            typeS = typeS.toLowerCase();
        }
        if (StringUtil.isEmpty(typeS)) {
            type = Type.failed;
        } else if (typeS.equals("all")) type = Type.all;
        else if (typeS.equals("failed")) type = Type.failed;
        else if (typeS.equals("system-unavailable")) type = Type.system;
        else if (typeS.equals("ad-hoc")) type = Type.adhoc;
        else if (typeS.equals("bad")) type = Type.bad;
        else {
            throw new TException.REQUEST_ELEMENT_UNSUPPORTED(MESSAGE + "type not supported:" + typeS);
        }
        System.out.println(MESSAGE + "type=" + type.toString());
    }

    @Override
    public void run()
    {
        try {
            log("run entered");
            connection.setAutoCommit(true);
            String sql = getSQL();
            FixityMRTEntry[] entries = FixityDBUtil.sqlToFixityMRT(connection, sql, logger);
            fixityEntriesState = new FixityEntriesState(entries);

        } catch (Exception ex) {
            Properties entryProp = mrtEntry.retrieveProperties();
            String msg = MESSAGE + "Exception for entry id=" + mrtEntry.getItemKey()
                    + " - " + PropertiesUtil.dumpProperties("entry", entryProp)
                    ;
            logger.logError(msg, 2);
            setException(ex);

        } finally {
            try {
                connection.close();
            } catch (Exception ex) { }
        }

    }

    public String getSQL()
    {
        String sql = "select * from " + FixNames.AUDIT_TABLE;
        log("SQL:" + sql);
        return sql;
    }
    
    protected String getStatus()
    {
        if (type == Type.failed) {
            return " and " + FixNames.AUDIT_TABLE
                    + ".status in ('size-mismatch','digest-mismatch')";
        }
        if (type == Type.system) {
            return " and " + FixNames.AUDIT_TABLE
                    + ".status='system-unavailable'";
        }
        if (type == Type.bad) {
            return " and " + FixNames.AUDIT_TABLE
                    + ".status in ('size-mismatch','digest-mismatch','system-unavailable')";
        }
        return "";
    }

    @Override
    public FixityEntriesState call()
    {
        run();
        return getFixityEntriesState();
    }

    public FixityEntriesState getFixityEntriesState() {
        return fixityEntriesState;
    }

    protected void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }


    /**
     * Main method
     */
    public static void main(String args[])
    {

        try {
            String propertyList[] = {
                "resources/FixityTest.properties"};
            TFrame tFrame = new TFrame(propertyList, "TestFixity");

            // Create an instance of this object
            LoggerInf logger = new TFileLogger(NAME, 50, 50);
            FixityReportItem fixityState = new FixityReportItem("all", null, logger);
            System.out.println("SQL1:" + fixityState.getSQL());
// all, failed, adhoc, system
            fixityState.setParms("failed", null);
            System.out.println("SQL2:" + fixityState.getSQL());
            fixityState.setParms("system-unavailable", null);
            System.out.println("SQL3:" + fixityState.getSQL());
            fixityState.setParms("all", "http://%");
            System.out.println("SQL4:" + fixityState.getSQL());
            fixityState.setParms("all", "http://abcde");
            System.out.println("SQL5:" + fixityState.getSQL());
            fixityState.setParms("system-unavailable", "http://%");
            System.out.println("SQL6:" + fixityState.getSQL());
        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        }
    }

}

