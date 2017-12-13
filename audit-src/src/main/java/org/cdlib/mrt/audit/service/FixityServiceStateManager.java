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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.sql.Connection;

import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.audit.db.FixNames;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.utility.TFrame;

/**
 * Fixity build Service State
 * @author  dloy
 */

public class FixityServiceStateManager
{
    private static final String NAME = "FixityServiceStateManager";
    private static final String MESSAGE = NAME + ": ";
    private static final boolean DEBUG = false;

    
    protected File fixityInfo = null;
    protected LoggerInf logger = null;

    public static FixityServiceStateManager getFixityServiceStateManager(
            LoggerInf logger, File fixityInfo)
        throws TException
    {
        return new FixityServiceStateManager(logger, fixityInfo);
    }

    protected FixityServiceStateManager(LoggerInf logger, File fixityInfo)
        throws TException
    {
        try {
            this.logger = logger;
            this.fixityInfo = fixityInfo;
            if (!fixityInfo.exists()) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "fixity-info.txt does not exist:");
            }


        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public FixityServiceState getFixityServiceState(Connection connection)
        throws TException
    {
        try {
            InputStream fis = new FileInputStream(fixityInfo);
            Properties serviceProperties = new Properties();
            serviceProperties.load(fis);
            FixityServiceState state = new FixityServiceState(serviceProperties);
            if (DEBUG) {
                boolean dbRunning = false;
                if (connection != null) dbRunning = true;
                System.out.println(MESSAGE + "getFixityServiceState"
                    + " - dbRunning:" + dbRunning
                    );
            }
            if (connection != null) addDBContent(connection, state);
            return state;

        } catch (Exception ex) {
            throw new TException(ex);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ex) { }
            }
        }
    }

    public FixityServiceState getFixityServiceStatus()
        throws TException
    {
        try {
            InputStream fis = new FileInputStream(fixityInfo);
            Properties serviceProperties = new Properties();
            serviceProperties.load(fis);
            FixityServiceState state = new FixityServiceState(serviceProperties);
            if (DEBUG) {
                boolean dbRunning = false;
                System.out.println(MESSAGE + "getFixityServiceState"
                    + " - dbRunning:" + dbRunning
                    );
            }
            return state;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public void addDBContent(Connection connection, FixityServiceState state)
        throws TException
    {
        try {
            InputStream fis = new FileInputStream(fixityInfo);
            Properties serviceProperties = new Properties();
            serviceProperties.load(fis);
            addDates(connection, state);
            addCounts(connection, state);

            return;
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public void addDates(
            Connection connection,
            FixityServiceState state)
        throws TException
    {
        if (DEBUG) System.out.println(MESSAGE + "addDates");
        try {
            String sql = "select * "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where not status='processing' "
                    + "order by verified desc "
                    + "limit 1;";
            DateState recent = getDateState(sql,"verified",connection);
            state.setLastIteration(recent);
            sql = "select * "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where not status='processing' "
                    + "order by verified "
                    + "limit 1;";
            DateState oldest = getDateState(sql,"verified",connection);
            if ((recent != null) && (oldest != null)) {
                long recentL = recent.getTimeLong();
                long oldestL = oldest.getTimeLong();
                Long diffL =recentL - oldestL;
                Double diffF= diffL.doubleValue();
                diffF = diffF / (1000.0 * 60.0 * 60.0 * 24.0);
                state.setElapsedTimeDays(diffF); 
                if (DEBUG) System.out.println(MESSAGE + "addDates - elapsedTimeDays:" + state.getElapsedTimeDays());
            }

            return;

        } catch (Exception ex) {
            System.out.println("WARNING: addLastIteration exception:" + ex);
            return;
        }
    }



    public void addCounts(
            Connection connection,
            FixityServiceState state)
        throws TException
    {
        if (DEBUG) System.out.println(MESSAGE + "addCounts");
        try {
            String sql = "select count(id) "
                    + "from " + FixNames.AUDIT_TABLE + ";";
            state.setNumItems(getNum(sql, "count(id)", connection));

            sql = "select sum(full_size) from " + FixNames.FILE_TABLE + "";
            state.setTotalSize(getNum(sql, "sum(full_size)", connection));

            sql = "select count(id) "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where status='size-mismatch' or status='digest-mismatch'";

            state.setNumFailedItems(getNum(sql, "count(id)", connection));

            sql = "select count(id) "
                    + "from " + FixNames.AUDIT_TABLE + " "
                    + "where status='system-unavailable'";

            state.setNumUnavailable(getNum(sql, "count(id)", connection));
            if (DEBUG) System.out.println(MESSAGE + "addCounts - TotalSize:" + state.getTotalSize());
            return;

        } catch (Exception ex) {
            System.out.println("WARNING: addLastIteration exception:" + ex);
            return;
        }
    }

    protected long getNum(
            String sql,
            String key,
            Connection connection)
        throws TException
    {
        try {
            if (connection == null) return 0;
            Properties [] props = FixityDBUtil.cmd(connection, sql, logger);
            if ((props == null) || (props.length != 1)) {
                System.out.println("WARNING: getNum empty");
                return 0;
            }
            //System.out.println(PropertiesUtil.dumpProperties("addCount", props[0]));
            String countS = props[0].getProperty(key);
            if (StringUtil.isEmpty(countS)) {
                System.out.println("WARNING: " + key + " not found");
                return 0;
            }

            return Long.parseLong(countS);

        } catch (Exception ex) {
            System.out.println("WARNING: getNum exception:" + ex);
            return 0;
        }
    }

    protected DateState getDateState(
            String sql,
            String key,
            Connection connection)
        throws TException
    {
        try {
            if (connection == null) return null;
            Properties [] props = FixityDBUtil.cmd(connection, sql, logger);
            if ((props == null) || (props.length != 1)) {
                System.out.println("WARNING: getNum empty");
                return null;
            }
            //System.out.println(PropertiesUtil.dumpProperties("addCount", props[0]));
            String dateS = props[0].getProperty(key);
            if (StringUtil.isEmpty(dateS)) {
                System.out.println("WARNING: " + key + " not found");
                return  null;
            }

            return FixityUtil.setDBDate(dateS);

        } catch (Exception ex) {
            System.out.println("WARNING: getNum exception:" + ex);
            return  null;
        }
    }


    public static void main(String args[])
    {

        TFrame tFrame = null;
        FixityItemDB db = null;
        try {
            String propertyList[] = {
                "resources/FixityTest.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");
            Properties prop = tFrame.getProperties();
            // Create an instance of this object
            LoggerInf logger = new TFileLogger(NAME, 50, 50);
            String pathkey = NAME+ ".infoPath";
            String path  = prop.getProperty(NAME+ ".infoPath");
            if (StringUtil.isEmpty(path)) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "missing parm:" + pathkey);
            }
            File fixityInfo = new File(path);
            if (!fixityInfo.exists()) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "file does not exist:" + pathkey);
            }
            db = new FixityItemDB(logger, prop);
            FixityServiceStateManager manager = getFixityServiceStateManager
                    (logger, fixityInfo);
            Connection connect = db.getConnection(true);
            FixityServiceState state = manager.getFixityServiceState(connect);
            
            FormatterInf anvl = FormatterAbs.getANVLFormatter(logger);
            String format = formatIt(anvl, state);
            System.out.println("OUTPUT:" + format);

        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            try {
                db.shutDown();
            } catch (Exception ex) { }
        }
    }

    public static String formatIt(
            FormatterInf formatter,
            StateInf responseState)
    {
        try {
           ByteArrayOutputStream outStream = new ByteArrayOutputStream(5000);
           PrintStream  stream = new PrintStream(outStream, true, "utf-8");
           formatter.format(responseState, stream);
           stream.close();
           byte [] bytes = outStream.toByteArray();
           String retString = new String(bytes, "UTF-8");
           return retString;

        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            System.out.println("Trace:" + StringUtil.stackTrace(ex));
            return null;
        }
    }


}
