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
package org.cdlib.mrt.audit.utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Properties;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.db.FixNames;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.SQLUtil;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;


/**
 * This interface defines the functional API for a Curational Storage Service
 * @author dloy
 */
public class FixityDBUtil
{

    protected static final String NAME = "FixityDBUtil";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;

    protected static final String NL = System.getProperty("line.separator");

    protected static final String REPLACE_ITEM =
            "replace into " + FixNames.AUDIT_TABLE + " set ";

    protected static final String UPDATE_ITEM =
            "update " + FixNames.AUDIT_TABLE + " set ";

    
    protected FixityDBUtil() {}

    public static InvAudit[] getAudits(
            Connection connection,
            String select,
            LoggerInf logger)
        throws TException
    {
        if (StringUtil.isAllBlank(select)) {
            log("NULL select in getAudits");
            return null;
        }
        Properties[] auditProps = cmd(connection, select, logger);
        if ((auditProps == null) || (auditProps.length <=0)) return null;
        InvAudit[] audits = new InvAudit[auditProps.length];
        int i = 0;
        for (Properties auditProp : auditProps) {
            InvAudit audit = new InvAudit(auditProp, logger);
            audits[i] = audit;
            i++;
        }
        return audits;
    }


    public static FixityMRTEntry[] getEntries(
            Connection connection,
            InvAudit[] auditEntries,
            LoggerInf logger)
        throws TException
    {
        if ((auditEntries == null) || (auditEntries.length == 0)) {
            log("NULL entries in getEntries");
            return null;
        }
        log("ENTRY length:" + auditEntries.length);
        FixityMRTEntry[] entries = new FixityMRTEntry[auditEntries.length];
        int ecnt = 0;
        for (InvAudit audit : auditEntries) {
            FixityMRTEntry entry = new FixityMRTEntry(audit, connection, logger);
            long key = entry.getItemKey();
            log("getEntries - entry(" + entry.getItemKey());
            ecnt++;
            entries[ecnt] = entry;
        }
        return entries;
    }

    public static InvAudit getAudit(
        Connection connection,
        long id,
        LoggerInf logger)
    throws TException
    {
        try {
            InvAudit[] audits = getAudits(connection, "select * from inv_audits where id=" + id, logger);
            if ((audits == null) || (audits.length == 0)) return null;
            return audits[0];

        } catch (Exception ex) {
            System.out.println("getEntry Exception:" + ex);
            ex.printStackTrace();
            return null;
        }
    }

    public static Properties[] cmd(
            Connection connection,
            String cmd,
            LoggerInf logger)
        throws TException
    {
        if (StringUtil.isEmpty(cmd)) {
            throw new TException.INVALID_OR_MISSING_PARM("cmd not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        if (logger == null) {
            throw new TException.INVALID_OR_MISSING_PARM("logger not supplied");
        }
        try {
            PreparedStatement pstmt = connection.prepareStatement (cmd);
            pstmt.setQueryTimeout(1800);
            ResultSet resultSet = pstmt.executeQuery();
            Properties [] results = SQLUtil.getResult(resultSet,logger);
            if (logger.getMessageMaxLevel() >= 10) {
                for (Properties result : results) {
                    logger.logMessage(PropertiesUtil.dumpProperties(MESSAGE + "getOperation", result), 10);
                }
            }
            return results;

        } catch(Exception e) {
            String msg = "Exception"
                + " - cmd=" + cmd
                + " - exception:" + e;

            logger.logError(MESSAGE + "getOperation - " + msg, 0);
            e.printStackTrace();
            throw new TException.SQL_EXCEPTION(msg, e);
        }
     }


    public static boolean exec(
            Connection connection,
            String replaceCmd,
            LoggerInf logger)
        throws TException
    {
        if (StringUtil.isEmpty(replaceCmd)) {
            throw new TException.INVALID_OR_MISSING_PARM("replaceCmd not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        try {

            Statement statement = connection.createStatement();
            boolean works = statement.execute(replaceCmd);
            log("replaceCmd=" + replaceCmd
                    + " - works=" + works);
            return works;

        } catch(Exception e) {
            String msg = "Exception"
                + " - sql=" + replaceCmd
                + " - exception:" + e;

            logger.logError(MESSAGE + "exec - " + msg, 0);
            System.out.println(msg);
            throw new TException.SQL_EXCEPTION(msg, e);
        }
    }

    public static int update(
            Connection connection,
            String replaceCmd,
            LoggerInf logger)
        throws TException
    {
        if (StringUtil.isEmpty(replaceCmd)) {
            throw new TException.INVALID_OR_MISSING_PARM("replaceCmd not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        Statement statement = null;
        try {

            statement = connection.createStatement();
            ResultSet resultSet = null;
            int rowCnt = statement.executeUpdate(replaceCmd);
            return rowCnt;

        } catch(Exception e) {
            String msg = "Exception"
                + " - sql=" + replaceCmd
                + " - exception:" + e;

            logger.logError(MESSAGE + "exec - " + msg, 0);
            System.out.println(msg);
            throw new TException.SQL_EXCEPTION(msg, e);
            
        } finally {
	    try {
	       statement.close();
	    } catch (Exception e) {}
	}
    }
    
    public static void replaceInvAudit(
            Connection connection,
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        if (entry == null) {
            throw new TException.INVALID_OR_MISSING_PARM("entry not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        long auditId = entry.getAuditid();
        if (auditId > 0) {
            FixityStatusType status = entry.getStatus();
            logger.logMessage(">>>Fixity status(" +  auditId + "):" + status.toString(), 1, true);
        }
        if (entry.getItemKey() > 0) {
            updateAudit(connection, entry, logger);
            return;
        }
        InvAudit audit = entry.getInvAudit();
        Properties prop = audit.retrieveProp();
        if (!replaceAudit(connection, prop, logger)) {
            //throw new TException.GENERAL_EXCEPTION("SQL fails");
        }
     }

    public static boolean replaceAudit(
            Connection connection,
            Properties prop,
            LoggerInf logger)
        throws TException
    {
        if (prop == null) {
            throw new TException.INVALID_OR_MISSING_PARM("entry not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        String replaceCmd = REPLACE_ITEM + buildModify(prop) + ";";
        return exec(connection, replaceCmd, logger);
     }
    
    public static void updateAudit(
            Connection connection,
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        if (entry == null) {
            throw new TException.INVALID_OR_MISSING_PARM("entry not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        InvAudit audit = entry.getInvAudit();
        Properties prop = audit.retrieveProp();
        if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("!!!updateAudit", prop));
        long id = audit.getId();
        prop.remove("id");
        updateInvAudit(id, connection, prop, logger);
     }

    public static boolean updateInvAudit(
            long id,
            Connection connection,
            Properties prop,
            LoggerInf logger)
        throws TException
    {
        if (prop == null) {
            throw new TException.INVALID_OR_MISSING_PARM("entry not supplied");
        }
        if (connection == null) {
            throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
        }
        String updateCmd = UPDATE_ITEM + buildModify(prop) + " where id=" + id + ";";
        return exec(connection, updateCmd, logger);
    }
    
    public static int ownInvAudit(
            long id,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        String sql = "update inv_audits "
            + "set status='processing' "
            + "where id=" + id + " "
            + "and not status='processing'"
            + "and ((verified is NULL) OR (not DATE(verified)=DATE(NOW())));";
        
        try {
            if (connection == null) {
                throw new TException.INVALID_OR_MISSING_PARM("connection not supplied");
            }
            connection.setAutoCommit(true);
            int updateCnt = update(connection, sql, logger);
            logger.logMessage("audit updates(" + id + ")=" + updateCnt, 5, true);
            return updateCnt;
        
        } catch (Exception ex) {
            return 0;
        }
    }
    
    public static String buildModify(Properties prop)
    {
        Enumeration e = prop.propertyNames();
        String key = null;
        String value = null;
        StringBuffer buf = new StringBuffer();
        while( e.hasMoreElements() )
        {
           key = (String)e.nextElement();
           value = prop.getProperty(key);
           if (buf.length() > 0) buf.append(",");
           buf.append(key + "='"  + SQLUtil.sqlEsc(value) + "'");
        }
        return buf.toString();
    }

    /**
     * Build a select query from a set of properties
     * Individual search elements are adjusted based on presents of % for triggering a like relation.
     * If the element is a url, then a trailing * is used to trigger the like.
     * @param prop
     * @return 
     */
    public static String buildSelect(Properties prop)
    {
        Enumeration e = prop.propertyNames();
        String key = null;
        String value = null;
        StringBuffer buf = new StringBuffer();
        while( e.hasMoreElements() )
        {
            key = (String)e.nextElement();
            value = prop.getProperty(key);
            if (StringUtil.isEmpty(value)) continue;
            if (buf.length() > 0) {
                buf.append(" and ");
            }
            
            String eq = "=";
            if (isURL(value)) {
                if (value.contains("*")) {
                    eq = " like ";
                    value = value.replace("%", "\\%");
                    value = value.replace("*", "%");
                }
            } else {
                if (value.contains("%")) eq = " like ";
            }
                
           buf.append(key + eq + "'"  + SQLUtil.sqlEsc(value) + "'");
        }
        return buf.toString();
    }

    protected static boolean isURL(String value) 
    {
        String test = "";
        if (value.length() <= 8) return false;
        test = value.substring(0,8).toLowerCase();
        if (test.contains("://")) return true;
        return false;
    }
    
    public static long matchItemKey(
            Connection connection,
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        long localKey = entry.getItemKey();
        if (localKey > 0) return localKey;
        return matchItemKey(connection, entry.getUrl(), logger);
    }

    public static long matchItemKey(
            Connection connection,
            String url,
            LoggerInf logger)
        throws TException
    {
        if (StringUtil.isEmpty(url)) return 0;
        String sql = "select id,url from " + FixNames.AUDIT_TABLE + " where url='"
                    + url
                    + "';";
        Properties [] props = FixityDBUtil.cmd(connection, sql, logger);
        if ((props != null) && (props.length > 0)) {
            for (Properties prop : props) {
                if (DEBUG) System.out.println(MESSAGE + " - url=" + url + "\n" + PropertiesUtil.dumpProperties("matchItemKey", prop));
                String urlS = prop.getProperty("url");
                if (StringUtil.isEmpty(urlS)) continue;
                if (urlS.equals(url)) {
                    String itemKeyS = prop.getProperty("id");
                    if (StringUtil.isEmpty(itemKeyS)) return 0;
                    return Long.parseLong(itemKeyS);
                }
            }
        }
        return 0;
    }
    
    public static FixityMRTEntry getMRTEntry(Properties auditProp, Connection connection, LoggerInf logger)
        throws TException
    {
        if ((auditProp == null) || (auditProp.size() == 0)) return null;
     
        InvAudit audit = new InvAudit(auditProp, logger);
        FixityMRTEntry entry = new FixityMRTEntry(audit, connection, logger);
        return entry;
    }
    
    public static FixityMRTEntry getMRTEntry(InvAudit audit, Connection connection, LoggerInf logger)
        throws TException
    {
        if (audit == null) return null;
        FixityMRTEntry entry = new FixityMRTEntry(audit, connection, logger);
        return entry;
    }

    /**
     * Get ItemEntry using URL
     * @param connection db connection
     * @param urlS fixity URL
     * @param logger system logger
     * @return Fixity entry
     * @throws TException
     */
    public static FixityMRTEntry[] getItemEntries(
            Connection connection,
            String urlS,
            LoggerInf logger)
        throws TException
    {
        try {
            String sql =
                "select * from " + FixNames.AUDIT_TABLE + " where url='" + urlS + "';";
            Properties[] auditProps = cmd(connection, sql, logger);
            FixityMRTEntry[] mrtEntries = auditPropToFixityMRT( auditProps, connection, logger);
            if (mrtEntries == null) return null;
            return mrtEntries;

        } catch (Exception ex) {
            System.out.println("getEntry Exception:" + ex);
            ex.printStackTrace();
            return null;
        }
     }
    
    public static FixityMRTEntry[] auditPropToFixityMRT(
            Properties[] auditProps,
            Connection connection,
            LoggerInf logger)
    {
        try {
            if ((auditProps == null) || (auditProps.length == 0)) return null;
            FixityMRTEntry [] mrtEntries = new FixityMRTEntry[auditProps.length];
            for (int i=0; i<mrtEntries.length; i++){
                InvAudit audit = new InvAudit(auditProps[0], logger);
                mrtEntries[i] = new FixityMRTEntry(audit, connection, logger);
            }
            return mrtEntries;
            
        } catch (Exception ex) {
            System.out.println("getEntry Exception:" + ex);
            ex.printStackTrace();
            return null;
        }
        
    }

    public static FixityMRTEntry[] sqlToFixityMRT(
            Connection connection,
            String sql,
            LoggerInf logger)
        throws TException
    {
        try {
            Properties[] auditProps = cmd(connection, sql, logger);
            return auditPropToFixityMRT(auditProps, connection, logger) ;
        } catch (Exception ex) {
            System.out.println("getEntry Exception:" + ex);
            ex.printStackTrace();
            return null;
        }
     }


    public static FixityMRTEntry[] getFromAuditEntries(
            Connection connection,
            Properties auditProp,
            LoggerInf logger)
        throws TException
    {
        String sql = "select * from " + FixNames.AUDIT_TABLE + " where ";
        String build = buildSelect(auditProp);
        sql += build;
        System.out.println("SQL:" + sql);
        return sqlToFixityMRT(connection,sql,logger);
     }


    protected static void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }
}

