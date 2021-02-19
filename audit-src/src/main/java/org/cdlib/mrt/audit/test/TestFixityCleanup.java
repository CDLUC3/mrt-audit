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



import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Properties;
import java.util.List;

import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.audit.action.ProcessFixityEntry;
import org.cdlib.mrt.audit.action.FixityEmailWrapper;
import org.cdlib.mrt.audit.action.FixityActionAbs;
import org.cdlib.mrt.audit.action.FixityCleanup;
import org.cdlib.mrt.audit.action.FixityReportEntries;
import org.cdlib.mrt.audit.action.FixityReportItem;
import org.cdlib.mrt.audit.action.FixityReportSQL;
import org.cdlib.mrt.audit.action.SearchFixityEntries;
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.service.*;
//import static org.cdlib.mrt.audit.service.FixityMRTService.formatIt;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.utility.LoggerAbs;
import org.cdlib.mrt.utility.TFrame;

/**
 * Fixity Service
 * @author  dloy
 */

public class TestFixityCleanup
{
    private static final String NAME = "TestFixityCleanup";
    private static final String MESSAGE = NAME + ": ";
    private static final String NL = System.getProperty("line.separator");
    private static final boolean DEBUG = true;
    private static final boolean THREADDEBUG = false;

 

    public static void main(String args[])
    {

        TFrame tFrame = null;
        FixityItemDB db = null;
        FixityServiceConfig fixityServiceProperties = null;
        FixityEntriesState entries = null;
        
        Connection connection = null;
        try {
            String propertyList[] = {
                //"resources/TestFixityCleanup.properties"};
                "resources/TestFixityCleanupStage.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");
            Properties prop = tFrame.getProperties();
            fixityServiceProperties
                    = FixityServiceConfig.useYaml();
            LoggerInf logger = LoggerAbs.getTFileLogger("testFormatter", 1, 10);
            FixityCleanup fix = FixityActionAbs.getFixityCleanup(fixityServiceProperties, logger);
         
            System.out.println("from:" + fix.getEmailFrom());
            System.out.println("to:" + fix.getEmailTo());
            System.out.println("subject:" + fix.getEmailSubject());
            System.out.println("msg:" + fix.getEmailMsg());
            if (false) return;
            FixitySelectState state = fix.call();
            List<Properties> list = state.retrieveRows();
            for (Properties outProp : list) {
                System.out.println(PropertiesUtil.dumpProperties("FixityCleanup", outProp));
            }
            String report = formatIt(logger, state);
            System.out.println("report\n" + report);
        } catch(Exception e) {
                System.out.println(
                    "Main: Encountered exception:" + e);
                System.out.println(
                        StringUtil.stackTrace(e));
        } finally {
            try {
                connection.close();
            } catch (Exception ex) { }
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
           //FormatterInf anvl = FormatterAbs.getJSONFormatter(logger);
           FormatterInf anvl = FormatterAbs.getXMLFormatter(logger);
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

    
    

}
