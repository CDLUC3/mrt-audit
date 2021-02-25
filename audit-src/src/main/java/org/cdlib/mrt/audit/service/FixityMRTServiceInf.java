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
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;

/**
 * Fixity Service Interface
 * @author  dloy
 */

public interface FixityMRTServiceInf
{
    public FixityServiceState getFixityServiceState()
        throws TException;
    
    public FixityServiceState getFixityServiceStatus()
        throws TException;

    public FixityEntriesState getFixityEntry(String urlS)
        throws TException;

    public FixityMRTEntry[] getFixityEntries(InvAudit audit)
        throws TException;
    
    public FixityMRTEntry queue(InvAudit audit)
        throws TException;

    public FixityMRTEntry test(Properties mrtProp)
        throws TException;
    
    public FixityMRTEntry update(InvAudit audit)
        throws TException;
    
    public FixityMRTEntry update(long id)
        throws TException;

    public FixitySubmittedState getSelectReport(String select, String emailTo, String emailMsg, String formatType)
        throws TException;
    
    public FixitySubmittedState doCleanup(String formatType)
        throws TException;
    
    public FixitySubmittedState doPeriodicReport(String formatType)
        throws TException;

    public FixitySubmittedState getEntryReport(InvAudit audit, String email, String formatType)
        throws TException;

    public FixitySubmittedState getItemReport(
            String typeS,
            String context,
            String emailTo,
            String formatTypeS)
        throws TException ;

    public FixityServiceState setFixityRun()
        throws TException;

    public FixityServiceState setFixityStop()
        throws TException;

    public FixityServiceState setShutdown()
        throws TException;

    public void setStartup()
        throws TException;

    public LoggerInf getLogger();
}
