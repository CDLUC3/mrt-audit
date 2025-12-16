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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import java.net.URL;
import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.logging.LogAuditEntry;
import org.cdlib.mrt.audit.service.FixityServiceConfig;
import org.cdlib.mrt.s3.service.NodeIO;
import org.cdlib.mrt.s3.tools.CloudChecksum;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.core.MessageDigest;
import org.cdlib.mrt.s3.service.CloudStoreInf;
import org.cdlib.mrt.utility.DateUtil;
import org.cdlib.mrt.utility.FileUtil;
import org.cdlib.mrt.utility.FixityTests;
import org.cdlib.mrt.utility.HTTPUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * This interface defines the functional API for a Curational Storage Service
 * @author dloy
 */
public class FixityUtil
{

    protected static final String NAME = "FixityUtil";
    protected static final String MESSAGE = NAME + ": ";

    private static final Logger log4j = LogManager.getLogger();
    protected static final boolean DEBUG = false;
    public static final String DBDATEPATTERN = "yyyy-MM-dd HH:mm:ss";
    protected static final String NL = System.getProperty("line.separator");
    protected static final String SYSTEM_EXCEPTION = "***System Exception";
    protected static final String FIXITY_EXCEPTION = "***Fixity Exception";

    
    protected FixityUtil() {}

    public static void runTest(
            FixityMRTEntry entry,
            int timeout,
            LoggerInf logger)
        throws TException
   {
        MessageDigest digest = entry.getDigest();
        if (digest == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "runTest - Exception: no digest provided");
        }
        String checksumType = digest.getJavaAlgorithm();
        String checksum = digest.getValue();
        long fileSize = entry.getSize();
        if (DEBUG) System.out.println("!!!FixityUtil size:" + fileSize);
        String location = entry.retrieveMapURL();
        if (location == null) location = entry.getUrl();
        if (location == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "runTest - Exception: no location provided");
        }
        //Some escape characters invalidly exist in URL remove them
        location = removeEsc(location);
        logger.logMessage(">>>Fixity test(" + entry.getAuditid() + "):" + location, 1, true);
        if (DEBUG) System.out.println("!!!FixityUtil location:" + location);
        InputStream inputStream = null;
        entry.setStatus(FixityStatusType.processing);
        try {
            NearLineResult nearLineResult = nearLineTest(location, entry, logger);
            if (nearLineResult != null) {
                setEntry(
                    nearLineResult.fileSizeMatch,nearLineResult.dataSize,
                    nearLineResult.checksumMatch, nearLineResult.dataChecksumType, nearLineResult.dataChecksum,
                    entry);
                logger.logMessage(entry.dump("nearLineTest"), 3, true);
                if (DEBUG) System.out.println("NearLineResult NOT null");
                return;
            } else {
                if (DEBUG) System.out.println("NearLineResult null");
            }
            try {
                if (DEBUG) System.out.println("runTest"
                        + " - Location:" + location
                        + " - timeout:" + timeout
                        );
                inputStream = getNodeStream(location, timeout);
                if (inputStream == null) {
                    if (DEBUG) System.out.println("!!!FixityUtil inputStream null:" + location);
                    throw new TException.REQUESTED_ITEM_NOT_FOUND(MESSAGE + "item not found:" + location);
                }

            } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE tex) {
                addSystemException(entry, tex);
                return;

            } catch (TException.REQUESTED_ITEM_NOT_FOUND rinf) {
                addStatusUnverified(entry, rinf);
                return;

            } catch (TException.REQUEST_ITEM_EXISTS rinf) {
                addStatusUnverified(entry, rinf);
                return;

            } catch (Exception ex) {
                throw ex;
            }
            FixityTests tests = new FixityTests(inputStream, checksumType, logger);
            if (DEBUG) System.out.println("!!!FixityUtil tests:" 
                    + " - tests getInputSize:" + tests.getInputSize()
                    + " - tests getChecksum:" + tests.getChecksum()
                    );
            FixityTests.FixityResult fixityResult
                    = tests.validateSizeChecksum(checksum, checksumType, fileSize);
            setEntry(
                    fixityResult.fileSizeMatch,tests.getInputSize(),
                    fixityResult.checksumMatch, checksumType, tests.getChecksum(),
                    entry);

            //entry.setDetail(out);
        } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE tex) {
            addSystemException(entry, tex);
            
        } catch (Exception ex) {
            logger.logError(MESSAGE + ex, 2);
            if (ex instanceof TException) {
                throw (TException) ex;
            }
            throw new TException.GENERAL_EXCEPTION(ex);

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) { }
            }
        }
    }

    public static void runCloudChecksum(
            FixityMRTEntry entry,
            int timeout,
            LoggerInf logger)
        throws TException
   {
        Long durationMs = null;
        MessageDigest digest = entry.getDigest();
        if (digest == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "runTest - Exception: no digest provided");
        }
        String checksumType = digest.getJavaAlgorithm();
        String checksum = digest.getValue();
        long fileSize = entry.getSize();
        if (DEBUG) System.out.println("!!!FixityUtil size:" + fileSize);
        String location = entry.getUrl();
        if (location == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "runTest - Exception: no location provided");
        }
        //Some escape characters invalidly exist in URL remove them
        location = removeEsc(location);
        //logger.logMessage(">>>Fixity test(" + entry.getAuditid() + "):" + location, 1, true);
        if (DEBUG) System.out.println("!!!FixityUtil location:" + location);
        entry.setStatus(FixityStatusType.processing);
        CloudChecksum cc = null;
        
        try {
            NearLineResult nearLineResult = nearLineTest(location, entry, logger);
            if (nearLineResult != null) {
                setEntry(
                    nearLineResult.fileSizeMatch,nearLineResult.dataSize,
                    nearLineResult.checksumMatch, nearLineResult.dataChecksumType, nearLineResult.dataChecksum,
                    entry);
                logger.logMessage(entry.dump("nearLineTest"), 3, true);
                if (DEBUG) System.out.println("NearLineResult NOT null");
                return;
            } else {
                if (DEBUG) System.out.println("NearLineResult null");
            }
            try {
                if (DEBUG) System.out.println("runTest"
                        + " - Location:" + location
                        + " - timeout:" + timeout
                        );
                cc = FixityServiceConfig.getCloudChecksum(location);
                
            } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE tex) {
                addSystemException(entry, tex);
                return;

            } catch (TException.REQUESTED_ITEM_NOT_FOUND rinf) {
                addStatusUnverified(entry, rinf);
                return;

            } catch (TException.REQUEST_ITEM_EXISTS rinf) {
                addStatusUnverified(entry, rinf);
                return;

            } catch (TException tex) {
                addStatusUnverified(entry, tex);
                return;

            } catch (Exception ex) {
                throw ex;
            }
            long startTime = System.currentTimeMillis();
            cc.process();
            long procTime = System.currentTimeMillis() - startTime;
            entry.setStreamMs(cc.getRunTime());
            durationMs = procTime;
            CloudChecksum.Digest test = cc.getDigest(checksumType);
            CloudChecksum.CloudChecksumResult fixityResult
                    = cc.validateSizeChecksum(checksum, checksumType, fileSize, logger);
            setEntry(
                    fixityResult.fileSizeMatch,test.inputSize,
                    fixityResult.checksumMatch, checksumType, test.checksum,
                    entry);

            double per = (double)fileSize/(double)procTime;
            logger.logMessage(">>>Test(" + entry.getAuditid() + "):"
                + " - size:" + fileSize
                + " - sT:" + fixityResult.fileSizeMatch
                + " - cT:" + fixityResult.checksumMatch
                + " - time:" + procTime
                + " - B/Ms:" + per
                    , 
                    2, true);
            
            LogAuditEntry.addLogAuditEntry(durationMs, entry);
            
        } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE tex) {
            addSystemException(entry, tex);
            
        } catch (Exception ex) {
            logger.logError(MESSAGE + ex, 2);
            if (ex instanceof TException) {
                throw (TException) ex;
            }
            throw new TException.GENERAL_EXCEPTION(ex);

        } finally {
            
        }
    }
    
    private static void setEntry(
            boolean fileSizeMatch,
            long lastSize,
            boolean checksumMatch,
            String checksumType,
            String checksum,
            FixityMRTEntry entry
            )
         throws TException
    {
        entry.setVerified();
        String out = "";
        if (checksumMatch && fileSizeMatch) {
            entry.setStatus(FixityStatusType.verified);
            entry.setLastSize(0);
            entry.setLastDigest(null);
            entry.setNote(null);

        } else if (!fileSizeMatch) {
            entry.setStatus(FixityStatusType.sizeMismatch);
            entry.setLastSize(lastSize);
            entry.setLastDigest(null);
            out = out + "File size mismatch" + NL
                    + " - entry size=" + entry.getSize() + NL
                    + " - data  size=" + entry.getLastSize() + NL
                    ;
            entry.setNote(out);

        } else if (!checksumMatch) {
            entry.setStatus(FixityStatusType.digestMismatch);
            entry.setLastDigest(checksumType, checksum);
            out = out + "Digest match Error" + NL
                    + " - digest type=" + entry.getDigest().getAlgorithm().toString() + NL
                    + " - entry checksum=" + entry.getDigest().getValue() + NL
                    + " - data  checksum=" + entry.getLastDigest().getValue() + NL
                    ;
            entry.setNote(out);
        }

    }

    public static NearLineResult nearLineTest(
            String storageURLS,
            FixityMRTEntry entry,
            LoggerInf logger)
        throws TException
    {
        try {
            if (DEBUG) System.out.println("+++entered nearLineTest");
            //String GLACIER = "GLACIER";
            String NEARLINE = "near-line"; //<<<USE
            //String NEARLINE = "on-line"; // <<< REMOVE TEST
            if (DEBUG) System.out.println("NearLineTest entered:" + storageURLS);
            NodeIO nodeIO = getNodeIO();
            if (nodeIO == null) {
                System.out.println("NodeIO not found");
                return null;
            }
            NodeIO.AccessKey accessKey = nodeIO.getAccessKey(storageURLS);
            if (accessKey == null) {
                System.out.println("accessKey == null:" + storageURLS);
                return null;
            }
            NodeIO.AccessNode accessNode = accessKey.accessNode;
            
            if (accessNode == null) {
                System.out.println("accessNode == null - return");
                return null;
            }
            String accessMode = accessNode.accessMode;
            if (accessMode == null) {
                return null;
            }
            if (DEBUG) System.out.println("accessMode=" + accessMode);
            if (!accessMode.equals(NEARLINE)) {
                return null;
            }
            NearLineResult nearLine = new NearLineResult();
            nearLine.dataChecksumType = "SHA-256";
            
            String serviceType = accessNode.serviceType;
            if (DEBUG) System.out.println("serviceType:" + serviceType);
            CloudStoreInf service = accessNode.service;
            String container = accessNode.container;
            String key = accessKey.key;
            Properties prop = service.getObjectMeta(container, key);
            //System.out.println(PropertiesUtil.dumpProperties("++++nearline+++", prop));
            if (prop == null)  {
                if (DEBUG) System.out.println("No Object Meta found");
                nearLine.error = "No Object Meta found";
                return nearLine;
            }
            if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("***KEY:" + key, prop));
            
            /*
            String storageClass = prop.getProperty("storageClass");
            if (storageClass == null)  {
                System.out.println("C");
                return null;
            }
            if (!storageClass.equals(GLACIER))  {
                System.out.println("D");
                return null;
            }
            */
            

            if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("***KEY2:" + key, prop));
            String sizeS = prop.getProperty("size");
            if (sizeS == null) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "runTest - Exception: cloud size required");
            }
            nearLine.dataSize = Long.parseLong(sizeS);
            
            MessageDigest digest = entry.getDigest();
            if (digest == null) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "runTest - Exception: no digest provided");
            }
            
            nearLine.dataChecksum = prop.getProperty("sha256");
            // this test defaults a true checksum if not provided
            if (nearLine.dataChecksum == null)  {
                nearLine.error = "No prop sha256";
                nearLine.checksumMatch = true;
                nearLine.dataChecksum = digest.getValue();
                if (nearLine.dataSize == entry.getSize()) {
                    nearLine.fileSizeMatch = true;
                }
                return nearLine;
            }
            if (DEBUG) System.out.println("Match:"
                    + " - nearLine.dataSize:" + nearLine.dataSize
                    + " - entry.getSize:" + entry.getSize()
            );
            String entrydataChecksumType = digest.getJavaAlgorithm();
            String entrydataChecksum = digest.getValue();
            if (DEBUG) System.out.println("Match:"
                    + " - entrydataChecksumType:" + entrydataChecksumType
                    + " - nearLine.dataChecksumType:" + nearLine.dataChecksumType
                    + " - entrydataChecksum:" + entrydataChecksum
                    + " - nearLine.dataChecksum:" + nearLine.dataChecksum
            );
            if (!nearLine.dataChecksumType.equals(entrydataChecksumType)) {
                return null;
            }
            if (nearLine.dataChecksum.equals(entrydataChecksum)) {
                nearLine.checksumMatch = true;
            } 

            if (nearLine.dataSize == entry.getSize()) {
                nearLine.fileSizeMatch = true;
            }
            if (DEBUG) System.out.println(nearLine.dump("***nearLineTest***"));
            return nearLine;
            
        } catch (TException tex) {
            System.out.println("TException:" + tex);
            tex.printStackTrace();
            throw tex;
            
        } catch (Exception ex) {
            System.out.println("Exception:" + ex);
            ex.printStackTrace();
            throw new TException(ex);
        }
    }
    
    private static InputStream getNodeStream(String location, int timeout)
        throws Exception
    {
        InputStream inputStream = null;
        Exception runex = null;
        NodeIO nodeIO = getNodeIO();
        if (nodeIO != null) {
            for (int retry=0; retry < 3; retry++) {
                try {
                    inputStream = nodeIO.getInputStream(location);
                    if (DEBUG) System.out.println(MESSAGE + "getNodeStream - NodeIO InputStream found");
                    return inputStream;
                    
                } catch (TException.INVALID_OR_MISSING_PARM iomp) {
                    runex = iomp;
                    Thread.sleep(3000);
                    continue;
                    
                } catch (TException.REQUESTED_ITEM_NOT_FOUND rinf) {
                    runex = rinf;
                    Thread.sleep(3000);
                    continue;
                    
                } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE esu) {
                    break;
                    
                } catch (Exception ex) {
                    System.out.println("FixityUtil Exception:" + ex);
                    runex = ex;
                    Thread.sleep(3000);
                    continue;
                }
            }
        }
        
        if ( (runex != null) && !(runex instanceof TException.EXTERNAL_SERVICE_UNAVAILABLE)) {
            throw runex;
        }
        
        // use storage server
        
        inputStream = getInputStream(location, timeout);
        if (DEBUG) System.out.println(MESSAGE + "getNodeStream - Storage InputStream found");
        return inputStream;
    }

    
    private static InputStream getNodeStreamOld(String location, int timeout)
        throws Exception
    {
        InputStream inputStream = null;
        NodeIO nodeIO = getNodeIO();
        if (nodeIO != null) {
            for (int retry=0; retry < 3; retry++) {
                try {
                    inputStream = nodeIO.getInputStream(location);
                    if (DEBUG) System.out.println(MESSAGE + "NodeIO used");
                    break;
                    
                } catch (TException.INVALID_OR_MISSING_PARM iomp) {
                    inputStream = null;
                    break;
                    
                } catch (TException.REQUESTED_ITEM_NOT_FOUND rinf) {
                    throw rinf;
                    
                } catch (Exception ex) {
                    System.out.println("FixityUtil Exception:" + ex);
                    Thread.sleep(3000);
                    inputStream = null;
                }
            }
        }
        if (inputStream == null) {
            System.out.println(MESSAGE + "using storage service:" + location);
            try {
                inputStream = getInputStream(location, timeout);
                if (DEBUG && (inputStream == null)) {
                    System.out.println("!!!FixityUtil inputStream null:" + location);
                }

            } catch (Exception ex) {
                throw ex;
            }
        }
        return inputStream;
    }
    
    public static NodeIO getNodeIO()
        throws TException
    {
        return FixityServiceConfig.getNodeIO();
    }

    public static String removeEsc(String urlS)
    {
        String capUrlS = urlS.toUpperCase();
        int pos = capUrlS.indexOf("%5C"); // backslash aka. esc
        if (pos < 0) return urlS;
        urlS = urlS.replace("%5c", "%5C");
        return urlS.replace("%5C", "");
    }

    public static void runStorageFixity(
            String location,
            FixityMRTEntry entry,
            int timeout,
            LoggerInf logger)
        throws TException
    {
        InputStream inputStream = null;
        try {

            logger.logMessage(">>>Fixity test:" + location, 1, true);
            try {
                inputStream = getNodeStream(location, timeout);

            } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE tex) {
                addSystemException(entry, tex);
                return;

            } catch (Exception ex) {
                throw ex;
            }
            Properties fixityProp = new Properties();
            fixityProp.load(inputStream);
            if (DEBUG) System.out.println(PropertiesUtil.dumpProperties(MESSAGE + "runStorageFixity", fixityProp));
            processStorageFixity(fixityProp, entry, timeout, logger);

        } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE tex) {
            addSystemException(entry, tex);

        } catch (Exception ex) {
            logger.logError(MESSAGE + ex, 2);
            if (ex instanceof TException) {
                throw (TException) ex;
            }
            throw new TException.GENERAL_EXCEPTION(ex);

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) { }
            }
        }
    }

    private static void addSystemException(FixityMRTEntry entry, Exception ex)
    {
        if (ex == null) return;
        entry.setNote(ex.toString());
        entry.setStatus(FixityStatusType.systemUnavailable);
        entry.setVerified();
    }

    private static void addStatusUnverified(FixityMRTEntry entry, Exception ex)
    {
        if (ex == null) return;
        System.out.println(entry.dump(MESSAGE + "status unverified"));
        entry.setNote(ex.toString());
        entry.setStatus(FixityStatusType.unverified);
        entry.setVerified();
    }

    private static void addFixityException(FixityMRTEntry entry, String out)
    {
        if (StringUtil.isEmpty(out)) return;
        entry.setNote(out);
    }

    public static void processStorageFixity(
            Properties fixityProp,
            FixityMRTEntry entry,
            int timeout,
            LoggerInf logger)
        throws TException
    {

        try {
            String sizeMatches = fixityProp.getProperty("sizeMatches");
            String digestMatches = fixityProp.getProperty("digestMatches");
            boolean checksumMatch = false;
            boolean fileSizeMatch = false;
            if (sizeMatches.equals("true")) fileSizeMatch = true;
            if (digestMatches.equals("true")) checksumMatch = true;

            entry.setVerified();
            String out = "";
            String manifestDigestS = fixityProp.getProperty("manifestDigest");
            MessageDigest manifestDigest = display2Digest(manifestDigestS);
            entry.setDigest(manifestDigest);
            String fileDigestS = fixityProp.getProperty("fileDigest");
            MessageDigest fileDigest = display2Digest(fileDigestS);
            String manifestFileSizeS = fixityProp.getProperty("manifestFileSize");
            entry.setSize(manifestFileSizeS);
            String fileSizeS = fixityProp.getProperty("fileSize");

            if (fileSizeMatch && checksumMatch) {
                entry.setStatus(FixityStatusType.verified);
                
            } else if (!fileSizeMatch) {
                entry.setStatus(FixityStatusType.sizeMismatch);
                entry.setLastSize(fileSizeS);
                entry.setLastDigest(null);
                out = out + "File size mismatch" + NL
                        + " - entry size=" + entry.getSize() + NL
                        + " - data  size=" + entry.getLastSize() + NL
                        ;

            } else if (!checksumMatch) {
                entry.setStatus(FixityStatusType.digestMismatch);
                entry.setLastDigest(fileDigest);
                out = out + "Digest match Error" + NL
                        + " - digest type=" + entry.getDigest().getAlgorithm().toString() + NL
                        + " - entry checksum=" + entry.getDigest().getValue() + NL
                        + " - data  checksum=" + entry.getLastDigest().getValue() + NL
                        ;
            }

            if (out.length() > 0) {
                addFixityException(entry, out);
            }

        } catch (Exception ex) {
            System.out.print("****>>>");
            ex.printStackTrace();
            if (DEBUG) {
                System.out.println("processStorageFixity exception:" + ex);
                ex.printStackTrace();
            }
            logger.logError(MESSAGE + ex, 2);
            if (ex instanceof TException) {
                throw (TException) ex;
            }
            throw new TException.GENERAL_EXCEPTION(ex);

        }
    }

    protected static MessageDigest display2Digest(String display)
        throws TException
    {
        try {
            if (StringUtil.isEmpty(display)) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE
                        + "Display value missing");
            }
            String [] parts = display.split("=");
            if (parts.length == 1) {
                throw new TException.INVALID_OR_MISSING_PARM(MESSAGE
                        + "fixity digest content invalid:" + display);
            }
            MessageDigest digest = new MessageDigest(parts[1], parts[0]);
            return digest;

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException.GENERAL_EXCEPTION(ex);
        }
    }

    public static InputStream getInputStream(String location, int timeout)
        throws TException
    {
        if (location == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "getInputStream - Exception: no location provided");
        }
        InputStream inputStream = null;
        String locationLower = location.toLowerCase();
        try {
            if (locationLower.startsWith("http://")) {
                inputStream = HTTPUtil.getObject404(location, timeout, 3);
            } else if (locationLower.startsWith("https://")) {
                inputStream = HTTPUtil.getObject404(location, timeout, 3);
            } else if (locationLower.startsWith("file://")) {
                URL fileURL = new URL(location);
                File file = FileUtil.fileFromURL(fileURL);
                inputStream = new FileInputStream(file);
            } else {
                inputStream = new FileInputStream(location);
            }
            return inputStream;

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception tryng to input:" + location);
        
        }
    }

    public static void setProp(Properties prop, String key, String value)
    {
        if (StringUtil.isEmpty(key) || StringUtil.isEmpty(value)) return;
        prop.setProperty(key, value);
    }

    public static DateState setDBDate(String dateS)
    {
        if (dateS == null) return null;
        return new DateState(DateUtil.getDateFromString(dateS, DBDATEPATTERN));
    }

    public static String getDBDate(DateState dateState)
    {
        if (dateState == null) return null;
        return DateUtil.getDateString(dateState.getDate(), DBDATEPATTERN);
    }

    public static MessageDigest getDigest(String digestTypeS, String digestValueS)
        throws TException
    {
        if (StringUtil.isEmpty(digestTypeS) && StringUtil.isEmpty(digestValueS)) {
            return null;
        }
        return new MessageDigest(digestValueS, digestTypeS);
    }

    public static long setLong(String inLong)
        throws TException
    {
        if (StringUtil.isEmpty(inLong)) return 0;
        try {
            return Long.parseLong(inLong);
        } catch (Exception ex) {
              throw new TException.INVALID_DATA_FORMAT(MESSAGE
                    + "setLong - Exception: size is not numeric:" + inLong);
        }
    }

    public static void sysoutThreads(String header) 
    {
        int activeCount = Thread.activeCount();
        
        Thread[] threads = new Thread[activeCount];
        Thread.enumerate(threads);
        System.out.println("sysoutThreads:" + header + " - count=" + threads.length);
        for (int j=0; j<threads.length; j++) {
            System.out.println(threads[j].toString());
        }
    }

    /**
     * Return true if a 404 http response
     * @param urlS test URL
     * @return true 404 response when attempting to access this URL, otherwise false
     * @throws TException 
     */
    public static boolean isMissing(String urlS)
        throws TException
    {
        try {
            InputStream fileStream = null;
            try {
                fileStream = HTTPUtil.getObject(urlS,  120000);
                
            } catch (TException.REQUESTED_ITEM_NOT_FOUND tex) {
                return true;
                
            } catch (Exception ex) {
                System.out.println("isMissing Exception:" + ex.toString());
                return false;
                
            } finally {
                if (fileStream != null) fileStream.close();
            }
            return false;
            
        } catch (Exception ex) {
            System.out.println("isMissing Exception:" + ex
                    + " - url=" + urlS);
            return false;
        }
    }
    
    public static class NearLineResult
    {
        public boolean fileSizeMatch = false;
        public boolean checksumMatch = false;
        public Long dataSize = null;
        public String dataChecksumType = null;
        public String dataChecksum = null;
        public String error = null;
    
        /**
         * Dump the content of this object to a string for logging
         * @param header header displayed in log entry
         */
        public String dump(String header)
        {
            StringBuffer buf = new StringBuffer(1000);
            buf.append(header + " [");
            buf.append(" - fileSizeMatch:" + fileSizeMatch);
            buf.append(" - checksumMatch:" + checksumMatch);
            buf.append(" - dataSize:" + dataSize);
            buf.append(" - dataChecksumType:" + dataChecksumType);
            buf.append(" - dataChecksum:" + dataChecksum);

            buf.append("]");
            return buf.toString();
        }
    }
}

