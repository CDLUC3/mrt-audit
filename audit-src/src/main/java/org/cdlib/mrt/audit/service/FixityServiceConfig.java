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
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;

import org.cdlib.mrt.s3.service.CloudStoreInf;
import org.cdlib.mrt.core.ServiceStatus;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.TRuntimeException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;


import org.cdlib.mrt.s3.service.NodeIO;
import org.cdlib.mrt.security.SecurityUtil;
import org.cdlib.mrt.tools.SSMConfigResolver;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.TFileLogger;
import org.cdlib.mrt.tools.YamlParser;
import org.cdlib.mrt.utility.LoggerAbs;
import org.cdlib.mrt.utility.StateInf;
import org.json.JSONObject;

/**
 * Base properties for Inv
 * @author  dloy
 */

public class FixityServiceConfig
{
    private static final String NAME = "FixityServiceConfig";
    private static final String MESSAGE = NAME + ": ";
    private static final boolean DEBUG = false;
    
    protected JSONObject stateJSON = null;
    protected JSONObject serviceJSON = null;
    // protected JSONObject periodicReportJSON = null;
    protected JSONObject cleanupJSON = null;
    protected JSONObject dbJSON = null;
    protected JSONObject mailJSON = null;
    protected Properties setupProperties = new Properties();
    
//    protected DPRFileDB db = null;
    //protected FileManager fileManager = null;
    protected LoggerInf logger = null;
    protected boolean shutdown = true;
    private static NodeIO nodeIO = null;
    protected Properties cleanupEmailProp = null;
    
    protected FixityItemDB db = null;
    protected FixityServiceStateManager serviceStateManager = null;
    protected FixityState fixityState = null;
    protected RewriteEntry rewriteEntry = null;
    
    private static class Test{ };
    
    public static FixityServiceConfig useYaml()
        throws TException
    {
        try {

            JSONObject auditInfoJSON = getYamlJson();
            FixityServiceConfig auditationConfig = new FixityServiceConfig(auditInfoJSON);
            
            return auditationConfig;
            
        } catch (TException tex) {
            tex.printStackTrace();
            throw tex;
            
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
        
    }
    
    protected FixityServiceConfig(JSONObject auditInfoJSON) 
        throws TException
    {
        try {
            System.out.println("***getYamlJson:\n" + auditInfoJSON.toString(3));
            
            stateJSON = auditInfoJSON.getJSONObject("state");
            serviceJSON = auditInfoJSON.getJSONObject("service");
            cleanupJSON = auditInfoJSON.getJSONObject("cleanup");
            dbJSON = auditInfoJSON.getJSONObject("db");
            mailJSON = auditInfoJSON.getJSONObject("mail");
            //periodicReportJSON = auditInfoJSON.getJSONObject("periodicReport");
            
            putSetupProperties();
            JSONObject jInvLogger = auditInfoJSON.getJSONObject("fileLogger");
            logger = setLogger(jInvLogger);
            setFixityItemDB();
            serviceStateManager
                    = FixityServiceStateManager.getFixityServiceStateManager(logger, setupProperties);
            setNodeIO();
            
            FixityServiceState state = new FixityServiceState(setupProperties);
            fixityState = new FixityState(setupProperties);
            
            
        } catch (TException tex) {
            tex.printStackTrace();
            throw tex;
            
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }
    
    protected static JSONObject getYamlJson()
       throws TException
    {
        try {
            String yamlName = "resources/audit"
                    + "Config.yml";
            System.out.println("propName:" + yamlName);
            Test test=new Test();
            InputStream propStream =  test.getClass().getClassLoader().
                    getResourceAsStream(yamlName);
            String auditYaml = StringUtil.streamToString(propStream, "utf8");
            System.out.println("auditYaml:\n" + auditYaml);
            String auditInfoConfig = getYamlInfo();
            System.out.println("\n\n***table:\n" + auditInfoConfig);
            String rootPath = System.getenv("SSM_ROOT_PATH");
            System.out.append("\n\n***root:\n" + rootPath + "\n");
            SSMConfigResolver ssmResolver = new SSMConfigResolver();
            YamlParser yamlParser = new YamlParser(ssmResolver);
            System.out.println("\n\n***InventoryYaml:\n" + auditYaml);
            LinkedHashMap<String, Object> map = yamlParser.parseString(auditYaml);
            LinkedHashMap<String, Object> lmap = (LinkedHashMap<String, Object>)map.get(auditInfoConfig);
            if (lmap == null) {
                throw new TException.INVALID_CONFIGURATION(MESSAGE + "Unable to locate configuration");
            }
            //System.out.println("lmap not null");
            yamlParser.loadConfigMap(lmap);

            yamlParser.resolveValues();
            return yamlParser.getJson();
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new TException(ex);
        }
    }
    
    protected static String getYamlInfo()
       throws TException
    { 
        String invInfoConfig = System.getenv("which-audit-info");
        if (invInfoConfig == null) {
            invInfoConfig = System.getenv("MERRITT_AUDIT_INFO");
        }
        if (invInfoConfig == null) {
            invInfoConfig = "audit-info";
        }
        return invInfoConfig;
    }
    
    public void setNodeIO()
        throws TException
    {
        try {
            String nodeIOPath = serviceJSON.getString("nodePath");
            nodeIO = NodeIO.getNodeIOConfig(nodeIOPath, logger);
            setNodeIO(nodeIO);
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }
    
    public void setFixityItemDB()
       throws TException
    {
        try {
            
            String  password = dbJSON.getString("password");
            String  user = dbJSON.getString("user");
            
            String server = dbJSON.getString("host");
            String encoding = dbJSON.getString("encoding");
            if (encoding.equals("OPTIONAL")) {
                encoding = "";
            } else {
                encoding = "?" + encoding;
            }
            String name = dbJSON.getString("name");
            String url = "jdbc:mysql://" + server + ":3306/" + name + encoding;
            
            Properties dbProp = new Properties();
            dbProp.setProperty("db.url", url);
            dbProp.setProperty("db.user", user);
            dbProp.setProperty("db.password", password);
            db = new FixityItemDB(logger, dbProp);
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            throw new TException(ex);
        }
    }
    
     public Properties getSetupProperties() 
        throws TException
    {
        return setupProperties;
    }
    
    
    protected boolean setCleanupProp(String jsonKey, String propKey, Properties prop)
       throws TException
    {
        
        String base = "ReplicCleanup";
        try {
            String jValue = cleanupJSON.getString(jsonKey);
            if (jValue.equals("NONE")) return false;
            prop.setProperty(base + "." + propKey, jValue);
            return true;
            
        } catch (Exception ex) {
            return false;
        }
    }
    
    /**
     * set local logger to node/log/...
     * @param path String path to node
     * @return Node logger
     * @throws Exception process exception
     */
    protected LoggerInf setLogger(JSONObject fileLogger)
        throws Exception
    {
        String qualifier = fileLogger.getString("qualifier");
        String path = fileLogger.getString("path");
        String name = fileLogger.getString("name");
        Properties logprop = new Properties();
        logprop.setProperty("fileLogger.message.maximumLevel", "" + fileLogger.getInt("messageMaximumLevel"));
        logprop.setProperty("fileLogger.error.maximumLevel", "" + fileLogger.getInt("messageMaximumError"));
        logprop.setProperty("fileLogger.name", name);
        logprop.setProperty("fileLogger.trace", "" + fileLogger.getInt("trace"));
        logprop.setProperty("fileLogger.qualifier", fileLogger.getString("qualifier"));
        if (StringUtil.isEmpty(path)) {
            throw new TException.INVALID_OR_MISSING_PARM(
                    MESSAGE + "setCANLog: path not supplied");
        }

        File canFile = new File(path);
        File log = new File(canFile, "logs");
        if (!log.exists()) log.mkdir();
        String logPath = log.getCanonicalPath() + '/';
        
        if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("LOG", logprop)
            + "\npath:" + path
            + "\nlogpath:" + logPath
        );
        LoggerInf logger = LoggerAbs.getTFileLogger(name, log.getCanonicalPath() + '/', logprop);
        return logger;
    }
    
    
    public Properties getCleanupEmailProp()
       throws TException
    {
        Properties cleanupEmailProp = new Properties();
        try {
            String msg = cleanupJSON.getString("msg");
            if ((msg.length() == 0) || msg.equals("NONE")) {
                return null;
            }
            setProp(cleanupJSON, "subject","FixityCleanup.emailSubject", cleanupEmailProp, true);
            setProp(cleanupJSON, "to","FixityCleanup.emailTo", cleanupEmailProp, true);
            setProp(mailJSON, "from","FixityCleanup.emailFrom", cleanupEmailProp, true);
            setProp(mailJSON, "smtp","mail.smtp.host", cleanupEmailProp, true);
            ArrayList<String> arr = new ArrayList<String>();
            int p = 0;
            int s = 0;
            System.out.println("len:" + msg.length());
            while(true) {
                if (p == msg.length()) break;
                int pos = msg.indexOf("\\",p);
                if (pos < 0) {
                    arr.add(msg.substring(s));
                    break;
                }
                if (msg.charAt(pos+1) == 'n') {
                    arr.add(msg.substring(s,pos));
                    s=pos+2;
                    p=pos+2;
                    continue;
                }
                p=pos+1;
            }
            int i = 1;
            for (String phrase: arr) {
                cleanupEmailProp.setProperty("FixityCleanup.emailMsg." + i, phrase);
                i++;
            }
            
            return cleanupEmailProp;
  
        } catch (Exception ex) {
            return null;
        }
    }
    
    protected void putSetupProperties()
        throws TException
    {
        setProp(stateJSON, "id","id", setupProperties, true);
        setProp(stateJSON, "name","name", setupProperties, true);
        setProp(stateJSON, "description","description", setupProperties, true);
        setProp(stateJSON, "version","version", setupProperties, true);
        setProp(stateJSON, "serviceScheme","serviceScheme", setupProperties, true);
        
        setProp(serviceJSON, "intervalDays","intervalDays", setupProperties, true);
        setProp(serviceJSON, "threadPool","threadPool", setupProperties, true);
        setProp(serviceJSON, "auditQualify","auditQualify", setupProperties, false);
        setProp(serviceJSON, "queueCapacity","queueCapacity", setupProperties, true);
        setProp(serviceJSON, "queueSleepMs","queueSleepMs", setupProperties, true);
        
        
        setProp(mailJSON , "smtp","mail.smtp.host", setupProperties, false);
        setProp(mailJSON , "from","mailFrom", setupProperties, false);
        
        //setProp(periodicReportJSON , "to","periodicReportTo", setupProperties, false);
        //setProp(periodicReportJSON , "frequencyHours","periodicReportFrequencyHours", setupProperties, false);
        //setProp(periodicReportJSON , "format","periodicReportFormat", setupProperties, false);
        
        System.out.println(PropertiesUtil.dumpProperties("putSetupProperties", setupProperties));
    }
    
    public static void setProp(JSONObject json, String jsonKey, String propKey, Properties prop, boolean required)
       throws TException
    {
        
        String jsonString = null;
        try {
            jsonString = json.toString(2);
            String jValue = json.getString(jsonKey);
            System.out.println("jValue:" + jValue);
            if (jValue.equals("NONE")) {
                if (required) {
                    throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "setProp required property missing:" 
                            + " - json=" + json.toString(2)
                            + " - jsonKey=" + jsonKey
                            + " - propKey=" + propKey
                    );
                } else return;
            }
            prop.setProperty(propKey, jValue);
            
        } catch (TException tex) {
            throw tex;
            
        } catch (Exception ex) {
            System.out.println("*>>Exception:" + ex);
            ex.printStackTrace();
            if (required) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "setProp missing json exception:" 
                        + " - json=" + jsonString
                        + " - jsonKey=" + jsonKey
                        + " - propKey=" + propKey
                );
            }
        }
    }
    
    protected void setFixityServiceStateManager()
        throws TException
    {
        serviceStateManager = FixityServiceStateManager.getFixityServiceStateManager(logger, setupProperties);
    }
    

    public LoggerInf getLogger() {
        return logger;
    }

    public JSONObject getStateJSON() {
        return stateJSON;
    }

    public JSONObject getServiceJSON() {
        return serviceJSON;
    }
    
    public void setLogger(LoggerInf logger) {
        this.logger = logger;
    }

    public static NodeIO getNodeIO() {
        if (nodeIO == null) {
            throw new TRuntimeException.INVALID_ARCHITECTURE(MESSAGE + "nodeIO not allocated");
        }
        return nodeIO;
    }

    public void setNodeIO(NodeIO nodeIO) {
        this.nodeIO = nodeIO;
    }
    
    
    public static void main(String[] argv) {
    	
    	try {
            
            LoggerInf logger = new TFileLogger("test", 50, 50);
            FixityServiceConfig fixityServiceConfig = FixityServiceConfig.useYaml();
            Properties setupProperties = fixityServiceConfig.getCleanupEmailProp();
            System.out.println(PropertiesUtil.dumpProperties("***Cleanup", setupProperties));
            //FileManager.printNodes("MAIN NODEIO");
            ServiceStatus serviceStatus = null;
            FixityItemDB db = fixityServiceConfig.getDb();
            if (db == null) serviceStatus = ServiceStatus.shutdown;
            else  serviceStatus = ServiceStatus.running;
            FixityServiceState fixityServiceStatus = fixityServiceConfig.getFixityServiceStatus();
            String statusXML = fixityServiceConfig.formatItXML(logger, fixityServiceStatus);
            System.out.println("statusXML:\n" + statusXML + "\n\n");
            System.out.println("setDB dbStatus:" + serviceStatus);
            if (serviceStatus == ServiceStatus.running) {
                db.shutDown();
            }
            NodeIO nodeIO = fixityServiceConfig.getNodeIO();
            nodeIO.printNodes("test");
            validateNodeIO(nodeIO);
            
        } catch (Exception ex) {
                // TODO Auto-generated catch block
                System.out.println("Exception:" + ex);
                ex.printStackTrace();
        }
    }
    
    public static void validateNodeIO(NodeIO nodeIO) 
    {        
        try {
            
            nodeIO.printNodes("test");
            List<NodeIO.AccessNode> accessNodes = nodeIO.getAccessNodesList();
            System.out.println("\n###ValidateNodeIO - Number nodes:" + accessNodes.size());
            for (NodeIO.AccessNode accessNode : accessNodes) {
                CloudStoreInf service = accessNode.service;
                long node = accessNode.nodeNumber;
                String bucket = accessNode.container;
                System.out.println(">>>Start:"
                        + " - node=" + node
                        + " - bucket=" + bucket
                );
                try {
                    service.getState(bucket);
                    System.out.println(">>>End:"
                        + " - node=" + node
                        + " - bucket=" + bucket
                    );
                    
                } catch (Exception ex) {
                    System.out.println("Exception:" + ex);
                }
            }
            
        } catch (Exception ex) {
                // TODO Auto-generated catch block
                System.out.println("Exception:" + ex);
                ex.printStackTrace();
        }
    }
    
    //#################
    
    public FixityItemDB getNewDb()
        throws TException
    {
        setFixityItemDB();
        return db;
    }

    public void refresh()
        throws TException
    {
        try {
            fixityState.set();

        } catch (Exception ex) {
            throw new TException(ex);
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

    public FixityState getFixityState() {
        return fixityState;
    }

    public FixityServiceStateManager getServiceStateManager() {
        return serviceStateManager;
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
    

    public boolean isShutdown() {
        if (db == null) return true;
        return shutdown;
    }

    public void setShutdown(boolean shutdown) {
        this.shutdown = shutdown;
        if (DEBUG) System.out.println("*********SET SHUTDOWN:" + isShutdown()); //!!!!
    }
    
    

    public static String formatItXML(
            LoggerInf logger,
            StateInf responseState)
    {
        try {
           FormatterInf xml = FormatterAbs.getXMLFormatter(logger);
           ByteArrayOutputStream outStream = new ByteArrayOutputStream(5000);
           PrintStream  stream = new PrintStream(outStream, true, "utf-8");
           xml.format(responseState, stream);
           stream.close();
           outStream.close();
           byte [] bytes = outStream.toByteArray();
           String retString = new String(bytes, "UTF-8");
           return retString;

        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            System.out.println("Trace:" + StringUtil.stackTrace(ex));
            return null;
        } finally {

        }
    }

    
}
