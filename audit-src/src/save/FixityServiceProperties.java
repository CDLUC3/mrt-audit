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
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StringUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;


import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.s3.service.NodeIO;

/**
 * Base properties for Fixity
 * @author  dloy
 */

public class FixityServiceProperties
{
    private static final String NAME = "FixityServiceProperties";
    private static final String MESSAGE = NAME + ": ";
    private static final boolean DEBUG = true;

    protected Properties serviceProperties = null;
    protected Properties setupProperties = null;
    protected File fixityService = null;
    protected File fixityInfo = null;
    protected FixityItemDB db = null;
    protected LoggerInf logger = null;
    protected FixityServiceStateManager serviceStateManager = null;
    protected FixityState fixityState = null;
    protected FixityPeriodicReport periodicReport = null;
    protected RewriteEntry rewriteEntry = null;
    private static NodeIO nodeIO = null;
    protected boolean shutdown = true;

    public static FixityServiceProperties getFixityServiceProperties(Properties prop)
        throws TException
    {
        return new FixityServiceProperties(prop);
    }

    protected FixityServiceProperties(Properties setupProp)
        throws TException
    {
        try {
            this.setupProperties = setupProp;
            String fixityServiceS = setupProp.getProperty("AuditService");
            if (StringUtil.isEmpty(fixityServiceS)) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "missing property: FixityService");
            }
            fixityService = new File(fixityServiceS);
            if (!fixityService.exists()) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "fixity service directory does not exist:"
                        + fixityService.getCanonicalPath());
            }
            File logDir = new File(fixityService, "log");
            if (!logDir.exists()) {
                logDir.mkdir();
            }
            if (DEBUG) System.out.println("***logger set up at " + logDir.getCanonicalPath()
                    + " - " + PropertiesUtil.dumpProperties("setupProp", setupProp)
                    );
            fixityInfo = new File(fixityService, "audit-info.txt");
            if (!fixityInfo.exists()) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "audit-info.txt does not exist:"
                        + fixityService.getCanonicalPath());
            }
            
            InputStream fis = new FileInputStream(fixityInfo);
            serviceProperties = new Properties();
            serviceProperties.load(fis);
            setupProperties.putAll(serviceProperties);
            System.out.println(PropertiesUtil.dumpProperties(MESSAGE + "setupProperties", setupProp));
            logger = new TFileLogger("fixity", logDir.getCanonicalPath() + '/', setupProp);
            FixityServiceState state = new FixityServiceState(serviceProperties);
            fixityState = new FixityState(fixityInfo);
            db = new FixityItemDB(logger, setupProp);
            serviceStateManager
                    = FixityServiceStateManager.getFixityServiceStateManager(logger, fixityInfo);
            String nodeName = serviceProperties.getProperty("nodeName");
            if (!StringUtil.isAllBlank(nodeName)) {
                nodeIO = new NodeIO(nodeName, logger);
                nodeIO.printNodes("FixityServiceProperties");
            }

            File adminDir = new File(fixityService, "admin");
            if (!adminDir.exists()) {
                adminDir.mkdir();
            }
            setRewriteEntry();

            FixityScheme scheme = state.retrieveServiceScheme();
            if (scheme != null) {
                scheme.buildNamasteFile(fixityService);
            }

            //setPeriodicReport();

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }
    
    protected void setRewriteEntry()
        throws TException
    {
        if (rewriteEntry == null) {
            File rewriteEntryFile = new File(fixityService,"rewrite.txt");
            if (rewriteEntryFile.exists()) {
                rewriteEntry = new RewriteEntry(rewriteEntryFile, logger);
            }
        } else {
            rewriteEntry.setEntryMapper();
        }
    }
    
    public FixityItemDB getNewDb()
        throws TException
    {
        return new FixityItemDB(logger, setupProperties);
    }

    public void refresh()
        throws TException
    {
        try {
            fixityState.set();
            setRewriteEntry();
            //setPeriodicReport();

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    protected void setPeriodicReport()
        throws TException
    {
        periodicReport =  new FixityPeriodicReport(
                this,
                logger);
        if (DEBUG) {
            System.out.println("PERIODIC REPORT INITIAL:" + periodicReport.isRunning());
            System.out.println(periodicReport.dump("FixityServiceProperties: setPeriodicReport"));
        }
    }
    
    public FixityServiceState getFixityServiceState()
        throws TException
    {
        try {

            if (DEBUG) System.out.println("*********IS "
                    + " - SHUTDOWN:" + isShutdown()
                    + " - fixityState.isRunFixity():" + fixityState.isRunFixity()
                    + " - fixityState.isFixityProcessing():" + fixityState.isFixityProcessing()
                    ); //!!!!
            Connection connection = getConnection(true);
            FixityServiceState state = serviceStateManager.getFixityServiceState(connection);
            if (fixityState.isRunFixity()) {
                if (!fixityState.isFixityProcessing()) {
                    state.setStatus(FixityServiceState.StateStatus.unknown);
                } else {
                    state.setStatus(FixityServiceState.StateStatus.running);
                }
            } else {
                if (fixityState.isFixityProcessing()) {
                    state.setStatus(FixityServiceState.StateStatus.shuttingdown);
                } else {
                    state.setStatus(FixityServiceState.StateStatus.shutdown);
                }
            }
            return state;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }
    
    public FixityServiceState getFixityServiceStatus()
        throws TException
    {
        try {

            if (DEBUG) System.out.println("*********IS "
                    + " - SHUTDOWN:" + isShutdown()
                    + " - fixityState.isRunFixity():" + fixityState.isRunFixity()
                    + " - fixityState.isFixityProcessing():" + fixityState.isFixityProcessing()
                    ); //!!!!
            FixityServiceState state = serviceStateManager.getFixityServiceStatus();
            if (fixityState.isRunFixity()) {
                if (!fixityState.isFixityProcessing()) {
                    state.setStatus(FixityServiceState.StateStatus.unknown);
                } else {
                    state.setStatus(FixityServiceState.StateStatus.running);
                }
            } else {
                if (fixityState.isFixityProcessing()) {
                    state.setStatus(FixityServiceState.StateStatus.shuttingdown);
                } else {
                    state.setStatus(FixityServiceState.StateStatus.shutdown);
                }
            }
            return state;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public FixityItemDB getDb() {
        return db;
    }

    public File getFixityInfo() {
        return fixityInfo;
    }

    public File getFixityService() {
        return fixityService;
    }

    public FixityState getFixityState() {
        return fixityState;
    }

    public LoggerInf getLogger() {
        return logger;
    }

    public Properties getServiceProperties() {
        return serviceProperties;
    }

    public FixityServiceStateManager getServiceStateManager() {
        return serviceStateManager;
    }

    public Properties getSetupProperties() {
        return setupProperties;
    }

    public void dbShutDown()
        throws TException
    {
        if (db == null) return;
        db.shutDown();
        db = null;
    }

    public void dbStartup()
        throws TException
    {
        if (db != null) return;
        db = getNewDb();
    }

    public Connection getConnection(boolean autoCommit)
        throws TException
    {
        if (db == null) return null;
        return db.getConnection(autoCommit);
    }

    public RewriteEntry getRewriteEntry() {
        return rewriteEntry;
    }

    public static NodeIO getNodeIO() {
        return nodeIO;
    }

    public boolean isShutdown() {
        if (db == null) return true;
        return shutdown;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
        if (DEBUG) System.out.println("*********SET SHUTDOWN:" + isShutdown()); //!!!!
    }

    public void startPeriodicReport()
        throws TException
    {
        if (DEBUG) {
            System.out.println("startPeriodicReport:" + periodicReport.isRunning());
        }
        if (periodicReport.isRunning()) return;
        periodicReport.run();
        if (DEBUG) {
            System.out.println("PeriodicReport STARTED");
        }
    }

    public void shutdownPeriodicReport()
        throws TException
    {
        if (DEBUG) {
            System.out.println("shutdownPeriodicReport:" + periodicReport.isRunning());
        }
        if (!periodicReport.isRunning()) return;
        periodicReport.shutdown();
        if (DEBUG) {
            System.out.println("PeriodicReport STOPPED");
        }
    }
}
