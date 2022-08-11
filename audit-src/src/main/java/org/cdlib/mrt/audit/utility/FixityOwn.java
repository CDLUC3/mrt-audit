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

import org.cdlib.mrt.audit.service.*;
import org.cdlib.mrt.audit.action.FixityValidationWrapper;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.Connection;


import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.db.FixNames;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import static org.cdlib.mrt.audit.utility.FixityDBUtil.update;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.DateUtil;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.utility.TFrame;

/**
 * This class performs the overall fixity functions.
 * Fixity runs as a background thread.
 * 
 * Fixity uses a relational database (here MySQL) to process the oldest entries 
 * first. These entries are pulled in as blocks. Each block is then processed and
 * the results are then collected before the next block is started.
 * 
 * The fixity tests whether either the extracted file has changed either size or digest.
 * Any change results in error information being saved to the db entry for that test.
 * 
 * Note that FixityState contains 2 flags used for controlling fixity handling:
 * 
 *  runFixity - this flag controls whether to start or stop fixity
 *            - true=fixity should be running or starting to run
 *            - false=stop fixity and exit routine
 *  fixityProcessing - this flag determines if fixity is running
 *            - true=fixity is now running
 *            - false=fixity has stopped
 * @author dloy
 */

public class FixityOwn 
{
    private static final String NAME = "FixityOwn";
    private static final String MESSAGE = NAME + ": ";

    private static final boolean DEBUG = false;
    
    /**
     * Get items owned by this Audit server
     * @return number of entries extracted
     * @throws TException 
     */
    public static LinkedList<Long> getOwnListId(Connection ownConnect, 
            String auditQualify,
            int capacity,
            LoggerInf logger)
        throws TException
    {
        LinkedList<Long> idsList = new LinkedList<Long>();
        try {
            ownConnect.setAutoCommit(false);
            if (auditQualify == null) {
                auditQualify = "";
            }

            String sql = "select id "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where ((verified is NULL) OR (not DATE(verified)=DATE(NOW())))"
                    + " " + auditQualify + " "
                    + "order by verified "
                    + "limit " + capacity + " "
                    + "for update "
                    + ";";

            Properties [] ids = FixityDBUtil.cmd(ownConnect, sql, logger);

            if ((ids == null) || (ids.length==0)) return null;
            String concatid = "";
            for (Properties idP : ids) {
                String idS = idP.getProperty("id");
                long id = Long.parseLong(idS);
                if (concatid.length() > 0 ) {
                    concatid = concatid + ",";
                }
                concatid = concatid + idS;
                idsList.add(id);
            }

            DateState verified = new DateState();
            String verifiedDB= FixityUtil.getDBDate(verified);
            String sqlUpdate = "update inv_audits "
            + "set verified='" + verifiedDB + "',modified=NULL "
            + "where id in (" + concatid + "); ";

            int updateCnt = FixityDBUtil.update(ownConnect, sqlUpdate, logger);
            //System.out.println("updateCnt:" + updateCnt);
            ownConnect.commit();
            return idsList;

        } catch (Exception ex) {
            try {
                ownConnect.rollback();
            } catch (Exception exr) {
                System.out.println("WARNING rollback fails");
            }
            return null;

        }
    }
    
    public static LinkedList<InvAudit> getOwnListAudit(Connection ownConnect, 
            String auditQualify,
            int capacity,
            LoggerInf logger)
        throws TException
    {
        //System.out.println("getOwnListAudit entered capacity=" + capacity);
        LinkedList<InvAudit> auditList = new LinkedList<InvAudit>();
        try {
            ownConnect.setAutoCommit(false);
            if (auditQualify == null) {
                auditQualify = "";
            }

            String sql = "select * "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where ((verified is NULL) OR (not DATE(verified)=DATE(NOW())))"
                    + " " + auditQualify + " "
                    + "and not status='processing' "
                    + "order by verified "
                    + "limit " + capacity + " "
                    + "for update "
                    + ";";
            if (DEBUG) System.out.println("own sql:" + sql);

            Properties [] props = FixityDBUtil.cmd(ownConnect, sql, logger);
            if ((props == null) || (props.length==0)) return new LinkedList<InvAudit>();
            String concatid = "";
            for (Properties prop : props) {
                InvAudit audit = new InvAudit(prop, logger);
                if (concatid.length() > 0 ) {
                    concatid = concatid + ",";
                }
                concatid = concatid + audit.getId();
                auditList.add(audit);
            }

            DateState verified = new DateState();
            String verifiedDB= FixityUtil.getDBDate(verified);
            String sqlUpdate = "update inv_audits "
            + "set verified='" + verifiedDB + "',status='processing' "
            + "where id in (" + concatid + "); ";

            int updateCnt = FixityDBUtil.update(ownConnect, sqlUpdate, logger);
            //System.out.println("updateCnt:" + updateCnt);
            ownConnect.commit();
            return auditList;

        } catch (Exception ex) {
            try {
                ownConnect.rollback();
                System.out.println("Exception rollback:" + ex);
                
            } catch (Exception exr) {
                System.out.println("WARNING rollback fails:" + ex);
            }
            return new LinkedList<InvAudit>();

        }
    }
    
    public static void doSleep(int cnt)
    {
        try {
        int sleepTime = (cnt*60000);
        System.out.println("Lock wait sleep:" + sleepTime);
        Thread.sleep(sleepTime);
        } catch (Exception ex) { }
    }
    
    public static LinkedList<InvAudit> getOwnListAuditProducer(Connection ownConnect, 
            String auditQualify,
            int capacity,
            boolean producerOnly,
            LoggerInf logger)
        throws TException
    {
        //System.out.println("getOwnListAudit entered capacity=" + capacity);
        LinkedList<InvAudit> auditList = new LinkedList<InvAudit>();
        try {
            ownConnect.setAutoCommit(false);
            if (auditQualify == null) {
                auditQualify = "";
            }

            String sql = "select * "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where ((verified is NULL) OR (not DATE(verified)=DATE(NOW()))) "
                    + "and not status='processing' "
                    + " " + auditQualify + " "
                    + "order by verified "
                    + "limit " + capacity + " "
                    + "for update "
                    + ";";

            //System.out.print("BEFORE sql:" + sql);
            Properties [] props = FixityDBUtil.cmd(ownConnect, sql, logger);
            //System.out.print("sql:" + props.length);
            if ((props == null) || (props.length==0)) return null;
            String concatid = "";
            for (Properties prop : props) {
                InvAudit audit = new InvAudit(prop, logger);
                if (concatid.length() > 0 ) {
                    concatid = concatid + ",";
                }
                concatid = concatid + audit.getId();
                //System.out.println("url:" + audit.getUrl());
                if (producerOnly && audit.getUrl().contains("/producer")) {
                    auditList.add(audit);
                } else {
                    auditList.add(audit);
                }
            }

            DateState verified = new DateState();
            String verifiedDB= FixityUtil.getDBDate(verified);
            String sqlUpdate = "update inv_audits "
            + "set verified='" + verifiedDB + "',status='processing' "
            + "where id in (" + concatid + "); ";

            int updateCnt = FixityDBUtil.update(ownConnect, sqlUpdate, logger);
            //System.out.println("updateCnt:" + updateCnt);
            ownConnect.commit();
            return auditList;

        } catch (Exception ex) {
            try {
                ownConnect.rollback();
            } catch (Exception exr) {
                System.out.println("WARNING rollback fails");
            }
            return null;

        }
    }
    
    
    public static LinkedList<InvAudit> getOwnListAuditLength(Connection ownConnect, 
            String auditQualify,
            int capacity,
            LoggerInf logger)
        throws TException
    {
        //System.out.println("getOwnListAudit entered capacity=" + capacity);
        LinkedList<InvAudit> auditList = new LinkedList<InvAudit>();
        try {
            ownConnect.setAutoCommit(false);
            if (auditQualify == null) {
                auditQualify = "";
            }
            String sql = "select f.full_size,a.* "
                    + "from inv_audits as a, "
                    + "inv_files as f "
                    + "where ((a.verified is NULL) OR (not DATE(a.verified)=DATE(NOW()))) "
                    + "and f.id = a.inv_file_id "
                    + " " + auditQualify + " "
                    + "order by a.verified "
                    + "limit " + capacity + " "
                    + "for update "
                    + ";";

System.out.println("auditQualify:" + auditQualify + "\nsql:" + sql + "\n"

);
            Properties [] props = FixityDBUtil.cmd(ownConnect, sql, logger);
            if ((props == null) || (props.length==0)) return null;
            String concatid = "";
            for (Properties prop : props) {
                InvAudit audit = new InvAudit(prop, logger);
                if (concatid.length() > 0 ) {
                    concatid = concatid + ",";
                }
                concatid = concatid + audit.getId();
                auditList.add(audit);
            }

            DateState verified = new DateState();
            String verifiedDB= FixityUtil.getDBDate(verified);
            String sqlUpdate = "update inv_audits "
            + "set verified='" + verifiedDB + "',status='processing' "
            + "where id in (" + concatid + "); ";

            int updateCnt = FixityDBUtil.update(ownConnect, sqlUpdate, logger);
            //System.out.println("updateCnt:" + updateCnt);
            ownConnect.commit();
            return auditList;

        } catch (Exception ex) {
            try {
                ownConnect.rollback();
            } catch (Exception exr) {
                System.out.println("WARNING rollback fails");
            }
            return null;

        }
    }
    
    /**
     * Get items owned by this Audit server
     * @return number of entries extracted
     * @throws TException 
     */
    public static Integer completeOwnList(Connection ownConnect, 
            LinkedList<Long> ids,
            LoggerInf logger)
        throws TException
    {
        
        try {
            if (ownConnect == null) return 0;
            if (!ownConnect.isValid(1)) {
                logger.logMessage(MESSAGE + "updateAudit reset", 1, true);
                ownConnect = FixityServiceConfig.getConnection(true);
                if (ownConnect == null) return 0;
            }
            //System.out.println("completeOwnList:" + ids.size());
            if ((ids == null) || (ids.size()==0)) return null;
            ownConnect.setAutoCommit(true);
            String concatid = "";
            for (Long id : ids) {
                String idS = "" + id;
                if (concatid.length() > 0 ) {
                    concatid = concatid + ",";
                }
                concatid = concatid + idS;
            }
            if (DEBUG) System.out.println("CompleteOwnList concatid=" + concatid);
            
            String sqlUpdate = "update inv_audits "
            + "set status='verified' "
            + "where id in (" + concatid + "); ";

            int updateCnt = FixityDBUtil.update(ownConnect, sqlUpdate, logger);
            return updateCnt;

        } catch (Exception ex) {
            return null;

        }
    }
    
    
    /**
     * Get items owned by this Audit server
     * @return number of entries extracted
     * @throws TException 
     */
    public static LinkedList<Long> getOwnList2(Connection ownConnect, 
            String auditQualify,
            int capacity,
            LoggerInf logger)
        throws TException
    {
        LinkedList<Long> idsList = new LinkedList<Long>();
        try {
            ownConnect.setAutoCommit(true);
            if (auditQualify == null) {
                auditQualify = "";
            }

            String sql = "select id "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where ((verified is NULL) OR (not DATE(verified)=DATE(NOW())))"
                    + " " + auditQualify + " "
                    + "order by verified "
                    + "limit " + capacity + " "
                    + ";";

            Properties [] ids = FixityDBUtil.cmd(ownConnect, sql, logger);

            if ((ids == null) || (ids.length==0)) return null;
            String concatid = "";
            for (Properties idP : ids) {
                String idS = idP.getProperty("id");
                long id = Long.parseLong(idS);
                int cnt = ownInvAudit(id, ownConnect, logger);
                if (cnt > 0) {
                    idsList.add(id);
                }
            }
            return idsList;

        } catch (Exception ex) {
            return null;
        }
    }
    
    public static InvAudit getAudit(long id, Connection connection, LoggerInf logger)
        throws TException
    {
        
        try {
            connection.setAutoCommit(true);
            String sql = "select * "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where id=" + id +";";
            Properties [] props = FixityDBUtil.cmd(connection, sql, logger);
            if ((props == null) || (props.length==0)) {
                throw new TException.INVALID_ARCHITECTURE(MESSAGE + "Multiple items or none returned:" + sql);
            }
            InvAudit audit = new InvAudit(props[0], logger);
            return audit;

        } catch (TException fe) {
            throw fe;

        } catch(Exception e)  {
            throw new TException(e);
        }
    }
    
    public static int ownInvAudit(
            long id,
            Connection connection,
            LoggerInf logger)
        throws TException
    {
        DateState verified = new DateState();
        String verifiedDB= FixityUtil.getDBDate(verified);
        String sql = "update inv_audits "
            + "set verified='" + verifiedDB + "',status='processing' "
            + "where id=" + id + " "
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
}
