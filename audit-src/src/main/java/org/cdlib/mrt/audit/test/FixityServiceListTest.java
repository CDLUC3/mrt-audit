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

package org.cdlib.mrt.audit.test;



import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Properties;

import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.audit.action.ProcessFixityEntry;
import org.cdlib.mrt.audit.action.FixityEmailWrapper;
import org.cdlib.mrt.audit.action.FixityActionAbs;
import org.cdlib.mrt.audit.action.FixityReportEntries;
import org.cdlib.mrt.audit.action.FixityReportItem;
import org.cdlib.mrt.audit.action.FixityReportSQL;
import org.cdlib.mrt.audit.action.SearchFixityEntries;
import org.cdlib.mrt.audit.service.*;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import static org.cdlib.mrt.audit.service.FixityMRTService.formatIt;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.utility.TFrame;

/**
 * Fixity Service
 * @author  dloy
 */

public class FixityServiceListTest
{
    private static final String NAME = "FixityService";
    private static final String MESSAGE = NAME + ": ";
    private static final String NL = System.getProperty("line.separator");
    private static final boolean DEBUG = true;
    private static final boolean THREADDEBUG = false;

 

    public static void main(String args[])
    {

        TFrame tFrame = null;
        FixityItemDB db = null;
        FixityServiceProperties fixityServiceProperties = null;
        FixityEntriesState entries = null;
        try {
            String propertyList[] = {
                "resources/Fixity.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");
            Properties prop = tFrame.getProperties();
            fixityServiceProperties
                    = FixityServiceProperties.getFixityServiceProperties(prop);
            FixityMRTService service = FixityMRTService.getFixityService(fixityServiceProperties);
            StateInf state = service.getFixityServiceState();
            LoggerInf logger = fixityServiceProperties.getLogger();
            
            String format = formatIt(logger, state);
            System.out.println("Initial Service State:" + NL + format);


            System.out.println("****FIXITYRUN 2*****");
            state = service.setFixityRun();
            state = service.setFixityStop();
            format = formatIt(logger, state);
            System.out.println("setFiityRun 2:" + NL + format);
            
            String prefix = "|objectid=ark:/28722/k22f7jr6r|%";
            File listFile = new File("C:/Documents and Settings/dloy/My Documents/Tasks/130806-cleanup/open-delete.txt");
            BufferedReader rd = setList(listFile);
            runList(service, rd);
            
            service.setShutdown();
            
        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            try {
                fixityServiceProperties.dbShutDown();
            } catch (Exception ex) { }
        }
    }

    public static String formatIt(
            LoggerInf logger,
            StateInf responseState)
    {
        try {
           FormatterInf anvl = FormatterAbs.getJSONFormatter(logger);
           ByteArrayOutputStream outStream = new ByteArrayOutputStream(5000);
           PrintStream  stream = new PrintStream(outStream, true, "utf-8");
           anvl.format(responseState, stream);
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

    protected static BufferedReader setList(File listFile)
        throws TException
    {
        try {
            FileInputStream inStream = new FileInputStream(listFile);
            DataInputStream in = new DataInputStream(inStream);
            return new BufferedReader(new InputStreamReader(in, "utf-8"));


        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    
    public static void runList(FixityMRTService service, BufferedReader reader)
        throws TException
    {
        LoggerInf logger = service.getLogger();
        try {/*
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.substring(0,1).equals("#")) continue;
                if (StringUtil.isEmpty(line)) continue;
                if (DEBUG) System.out.println("***Line:" + line);
                //"|objectid=ark:/28722/k22f7jr6r|%";
                try {
                    String prefix = "|objectid=" + line + "|%";
                    System.out.println("***Delete=" + prefix);
                    FixityEntriesState fes = service.deletePrefix(prefix, false);
                    String format = formatIt(logger, fes);
                    System.out.println("Delete:" + NL + format);
                    System.out.println("***Delete complete for " + prefix);
                    
                } catch (TException.REQUESTED_ITEM_NOT_FOUND rinf) {
                    System.out.println("NOT FOUND:" + line);
                    continue; 
                    
                } catch (TException tex) {
                    logger.logMessage("Error Manifest:" + line, 0, true);
                    continue;
                }
            }
            * */

        } catch(Exception e)  {
            if (logger != null)
            {
                logger.logError(
                    "Main: Encountered exception:" + e, 0);
                logger.logError(
                        StringUtil.stackTrace(e), 10);
            }
            throw new TException(e);

        } finally {
            try {
                reader.close();
            } catch (Exception ex) { }
        }
    }
    

}
