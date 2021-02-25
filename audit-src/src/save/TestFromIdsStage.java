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
import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.ArrayList;
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
import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.service.*;
import static org.cdlib.mrt.audit.service.FixityMRTService.formatIt;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.utility.LoggerAbs;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.TFrame;

/**
 * Fixity Service
 * @author  dloy
 */

public class TestFromIdsStage
{
    private static final String NAME = "TestFromIdsStage";
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
                "resources/TestFromIdsStage.properties"};
            tFrame = new TFrame(propertyList, "TestFixity");
            Properties prop = tFrame.getProperties();
            System.out.println(PropertiesUtil.dumpProperties("TestNearLine.properties", prop));
            fixityServiceProperties
                    = FixityServiceConfig.useYaml();
            LoggerInf logger = fixityServiceProperties.getLogger();
            //LoggerInf logger = fixityServiceProperties.getLogger();
            
int [] ids = 
{
    1538515,
1538516,
1538517,
1538518,
1538519,
1538520,
1538521,
1538522,
1538523,
1538524
};
            ArrayList<FixityMRTEntry> stats = new ArrayList<>();
            db = fixityServiceProperties.getDb();
            System.out.println("****TestUpdate*****");
            //state = service.setFixityRun();
            connection = db.getConnection(true);
            for (int id : ids) {
                System.out.println("\n\n***ID==" + id);
                FixityMRTEntry entry = doit(connection, id, logger);
                stats.add(entry);
            }
            connection.close();
            for (FixityMRTEntry entry : stats) {
                System.out.println(entry.dump("RESULT"));
            }
            //System.out.println(mrtEntry.dump(MESSAGE));
            //state = service.setFixityStop();
            //String format = formatIt(logger, state);
            //System.out.println("setFiityRun 2:" + NL + format);

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
    
    public static FixityMRTEntry  doit(Connection connection, long id, LoggerInf localLog)
        throws Exception
    {
        InvAudit audit = FixityDBUtil.getAudit(connection, id, localLog);
        System.out.println(PropertiesUtil.dumpProperties("audit>>>", audit.retrieveProp()));
        FixityMRTEntry mrtEntry = FixityDBUtil.getMRTEntry(audit, connection, localLog);
        
        try {
            InvAudit mapAudit = rewrite.map(audit);
            String mapUrl = mapAudit.getMapURL();
            mrtEntry.setMapURL(mapUrl);
            FixityUtil.runTest(mrtEntry, 30000, localLog);
            return mrtEntry;
        } catch (TException.EXTERNAL_SERVICE_UNAVAILABLE esu) {
            System.out.println("TestFromIds EXCEPTION(" + id + "):" + esu);
            return mrtEntry;
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

    
    

}
