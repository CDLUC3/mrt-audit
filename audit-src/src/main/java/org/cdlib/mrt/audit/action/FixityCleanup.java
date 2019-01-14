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

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.service.FixitySelectState;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.service.FixityServiceProperties;
import org.cdlib.mrt.audit.service.RewriteEntry;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class FixityCleanup
        extends FixityActionAbs
        implements Callable, Runnable, FixityActionInf
{

    protected static final String NAME = "FixityCleanup";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;

    protected FixitySelectState fixitySelect = null;
    protected enum CleanupStatus {missing, exception, error, keyerror, tested, notset};
    protected FixityItemDB db = null;
    protected String selectKey =
            "select n.number as node, v.ark, v.number as version, f.pathname as filepath "
            + "from inv_nodes as n, "
            + "inv_audits as a, "
            + "inv_files as f, "
            + "inv_versions as v "
            + "where  a.inv_node_id = n.id "
            + "and a.inv_version_id=v.id "
            + "and a.inv_file_id=f.id "
            + "and a.id=";
 
    
    protected String selectRetry =
            "select a.* "
            + "from inv_audits as a "
            + "where (a.status='system-unavailable' "
            + "or a.status='unverified') "
            + "and a.inv_node_id=3 "
            //+ "limit 500;"; //!!! delete at future point
            ;

    protected Properties [] rows = null;
    protected Properties setupProperties = null;protected String msg = null;
    protected String emailFrom = null;
    protected String emailTo = null;
    protected String emailSubject = null;
    protected String emailMsg = null;
    protected String emailFormat = null;
    protected Integer testMax = null;
    protected RewriteEntry rewrite = null;
    protected int processCnt = 0;
    
    protected FixityCleanup(
            FixityItemDB db,
            LoggerInf logger)
        throws TException
    {
        super(null, null, logger);
        this.db = db;
    }
    
    protected FixityCleanup(
            FixityServiceProperties fixityServiceProperties,
            LoggerInf logger)
        throws TException
    {
        super(null, null, logger);
        this.db = fixityServiceProperties.getDb();
        this.setupProperties = fixityServiceProperties.getSetupProperties();
        File fixityService = fixityServiceProperties.getFixityService();
        File mapFile = new File(fixityService, "rewrite.txt");
        this.rewrite = new RewriteEntry(mapFile,logger);
        buildEmail();
    }


    @Override
    public void run()
    {
        Properties [] rows = null;
        try {
            log("run entered");
            connection = db.getConnection(true);
            rows = FixityDBUtil.cmd(connection, selectRetry, logger);
            if ((rows == null) || (rows.length == 0)) {
                System.out.println(MESSAGE + " null results");
                return;
            }
            ArrayList<Properties> testList = buildList(rows);
            if (DEBUG) System.out.println(MESSAGE + "rows cnt:" + rows.length);
            fixitySelect = new FixitySelectState(rows);
            fixitySelect.setSql(selectRetry);
            fixitySelect.setCount(processCnt);
            fixitySelect.replaceFixityEntries(testList);

        } catch (Exception ex) {
            ex.printStackTrace();
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

    protected ArrayList<Properties> buildList(Properties [] rows)
        throws TException
    {
        ArrayList<Properties> stateList = new ArrayList();
        try {
            for (Properties row: rows) {
                processCnt++;
        if (DEBUG) System.out.println("***rowlist:"
                + " - processCnt=" + processCnt
                + " - stateList.size()=" + stateList.size()
                + " - testMax=" + testMax + '\n'
                + PropertiesUtil.dumpProperties("ROW", row, 0)
        );
                if (testMax != null) {
                    if (stateList.size() >= testMax) break;
                }
                EntryTest test = processEntry(row);
                Properties testProp = test.getProp();
                if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("FixityCleanupOut", testProp));
                String status = testProp.getProperty("fixityStatus");
                if (!status.equals("verified")) {
                    stateList.add(testProp);
                }
            }
            return stateList;

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);

        } finally {
            try {
                connection.close();
            } catch (Exception ex) { }
        }

    }
    
    protected EntryTest processEntry(Properties row)
        throws TException
    {
        Connection upConnect = null;
        EntryTest entryTest = new EntryTest();
        try {
            log("run entered");
            
            upConnect = db.getConnection(true);
            InvAudit audit = new InvAudit(logger);
            //String mapUrl = mapAudit.getMapURL();
            //mrtEntry.setMapURL(mapUrl);
            audit.setProp(row);
            audit = rewrite.map(audit);
            
            entryTest.auditid = audit.getId();
            if (DEBUG) System.out.println("***AUDITID=" + audit.getId()
                    + " - mapURL=" + audit.getMapURL()
            );
            setNodeKey(entryTest);
            if (entryTest.ex != null){
                return entryTest;
            }
            /*
            if (audit.getNote() != null) {
                if (audit.getNote().contains("responseCode:404")) {
                    DateState verified = audit.getVerified();
                    entryTest.verifiedDate = verified.getIsoDate();
                    entryTest.fixityStatus = audit.getStatus();
                    //entryTest.
                    throw new TException.REQUESTED_ITEM_NOT_FOUND(audit.getNote());
                }
            }
*/
            ProcessFixityEntry pfe = getProcessFixityEntry("update", audit, upConnect,logger);
            FixityMRTEntry entry = pfe.call();
            if (DEBUG) System.out.println(entry.dump("+++processed+++"));
//            FixityDBUtil.replaceInvAudit(upConnect, entry, logger);
            Exception ex = getException();
            if (ex != null) {
                throw ex;
            }
            entryTest.fixityStatus = entry.getStatus();
            entryTest.cleanupStatus = CleanupStatus.tested;
            if (entry.getNote() != null) {
                if (entry.getNote().contains("REQUESTED_ITEM_NOT_FOUND")) {
                    entryTest.cleanupStatus = CleanupStatus.missing;
                }
            }
            DateState verified = entry.getVerified();
            entryTest.verifiedDate = verified.getIsoDate();
            return entryTest;
            
        } catch (Exception ex) {
                entryTest.ex = ex;
                if (ex.toString().contains("404") 
                        || ex.toString().toLowerCase().contains("missing")) {
                    entryTest.cleanupStatus = CleanupStatus.missing;
                    return entryTest;
                }
                entryTest.cleanupStatus = CleanupStatus.error;
                return entryTest;

        } finally {
            try {
                upConnect.close();
            } catch (Exception ex) { }
        }

    }
    
    protected void setNodeKey(EntryTest entryTest)
        throws TException
    {
        try {
        
            connection.setAutoCommit(true);
            String sql = selectKey + entryTest.auditid;
            rows = FixityDBUtil.cmd(connection, sql, logger);
            if ((rows == null) || (rows.length == 0)) {
                System.out.println(MESSAGE + " null results");
                entryTest.cleanupStatus=CleanupStatus.keyerror;
                if (entryTest.ex == null) {
                    entryTest.ex = new TException.REQUESTED_ITEM_NOT_FOUND("Key not found for auditid:" 
                        + entryTest.auditid);
                }
                return;
            }
            Properties prop = rows[0];
            
            long node = Long.parseLong(prop.getProperty("node"));
            String ark = prop.getProperty("ark");
            long version = Long.parseLong(prop.getProperty("version"));
            String filepath = prop.getProperty("filepath");
            entryTest.node = node;
            entryTest.key = ark + "|" + version  + "|" + filepath;
            
            
        } catch (Exception ex) {
                entryTest.ex = ex;
                entryTest.cleanupStatus=CleanupStatus.keyerror;
        }
    }


    @Override
    public FixitySelectState call()
    {
        run();
        return getFixitySelect();
    }

    public Properties[] getRows() {
        return rows;
    }

    public void setRows(Properties[] rows) {
        this.rows = rows;
    }

    public FixitySelectState getFixitySelect() {
        return fixitySelect;
    }

    public void setFixitySelect(FixitySelectState fixitySelect) {
        this.fixitySelect = fixitySelect;
    }

    protected void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }
    
    protected void buildEmail()
        throws TException
    {
        DateState dstate = new DateState();
        String ctime= dstate.getIsoDate();
        emailSubject = getMail(NAME + ".emailSubject", 
                "FixityCleanup Report");
        emailSubject += ": " + ctime;
        emailFrom = getMail(NAME + ".emailFrom","merritt@ucop.edu");
        emailFormat = getMail(NAME + ".emailFormat","xml");
        emailTo =  getMail(NAME + ".emailTo", null);
        if (emailTo == null) {
            throw new TException.INVALID_OR_MISSING_PARM(NAME + ".emailTo required");
        }
        emailMsg = getMail(NAME + ".emailMsg.1", null);
        if (emailMsg == null) {
            emailMsg = NAME + " Results:\n";
        } else {
            emailMsg += "\n";
            for (int i=2; true; i++) {
                String testMsg = getMail(NAME + ".emailMsg."+i, null);
                if (testMsg == null) break;
                emailMsg += testMsg + "\n";
            }
        }
    }
    
    protected String getMail(String key, String def)
    {
        if (setupProperties == null) return def;
        if (StringUtil.isAllBlank(key)) return null;
        String value = setupProperties.getProperty(key);
        if (value == null) value = def;
        return value;
    }

    public String getEmailFrom() {
        return emailFrom;
    }

    public void setEmailFrom(String emailFrom) {
        this.emailFrom = emailFrom;
    }

    public String getEmailTo() {
        return emailTo;
    }

    public void setEmailTo(String emailTo) {
        this.emailTo = emailTo;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    public String getEmailMsg() {
        return emailMsg;
    }

    public void setEmailMsg(String emailMsg) {
        this.emailMsg = emailMsg;
    }

    public String getEmailFormat() {
        return emailFormat;
    }

    public void setEmailFormat(String emailFormat) {
        this.emailFormat = emailFormat;
    }

    public Integer getTestMax() {
        return testMax;
    }

    public void setTestMax(Integer testMax) {
        this.testMax = testMax;
    }
    
    public static class EntryTest {
        public long auditid = 0;
        public CleanupStatus cleanupStatus = CleanupStatus.notset;
        public FixityStatusType fixityStatus = FixityStatusType.unknown;
        public Exception ex = null;
        public String key = null;
        public long node = 0;
        public String verifiedDate = null;
        public EntryTest() { }
        public EntryTest(long auditid) {
            this.auditid = auditid;
        }
        public Properties getProp()
        {
            Properties retProp = new Properties();
            retProp.setProperty("auditid", "" + auditid);
            retProp.setProperty("cleanupStatus", cleanupStatus.name());
            retProp.setProperty("fixityStatus", fixityStatus.name());
            retProp.setProperty("node", "" + node);
            retProp.setProperty("key", key);
            retProp.setProperty("verifiedDate", verifiedDate);
            if (ex != null) {
                retProp.setProperty("error", ex.toString());
            }
            return retProp;
        }
    }
}


