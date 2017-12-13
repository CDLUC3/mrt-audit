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
package org.cdlib.mrt.audit.action;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.Properties;

import org.cdlib.mrt.audit.db.FixityItemDB;
import org.cdlib.mrt.audit.email.FixityEmail;
import org.cdlib.mrt.formatter.FormatterAbs;
import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.audit.service.FixitySelectState;
import org.cdlib.mrt.audit.service.FixityServiceState;
import org.cdlib.mrt.utility.DateUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class FixityEmailWrapper
        implements Runnable
{

    protected static final String NAME = "FixityEmailWrapper";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;

    protected boolean autoCommit = true;
    //protected String select = null;
    protected String msg = null;
    protected String emailFrom = null;
    protected String emailTo = null;
    protected String subject = "Fixity Report";
    protected FixityItemDB db = null;
    protected LoggerInf logger = null;
    protected FixitySelectState selectState = null;
    protected StateInf state = null;
    protected String formatTypeS = null;
    protected FormatterInf.Format formatType = null;
    protected Properties setupProperties = null;
    protected FixityActionInf action = null;

    public FixityEmailWrapper(
            FixityActionInf action,
            boolean autoCommit,
            String msg,
            String emailTo,
            String formatTypeS,
            FixityItemDB db,
            Properties setupProperties,
            LoggerInf logger)
        throws TException
    {
        this.autoCommit = autoCommit;
        this.msg = msg;
        this.action = action;
        this.emailTo = emailTo;
        this.formatTypeS = formatTypeS;
        this.logger = logger;
        this.db = db;
        this.setupProperties = setupProperties;
        emailFrom = setupProperties.getProperty("mail.from");
        validate();
    }


    public FixityEmailWrapper(
            FixityActionInf action,
            boolean autoCommit,
            String emailTo,
            String emailFrom,
            String subject,
            String msg,
            String formatTypeS,
            FixityItemDB db,
            Properties setupProperties,
            LoggerInf logger)
        throws TException
    {
        this.autoCommit = autoCommit;
        this.msg = msg;
        this.action = action;
        this.emailTo = emailTo;
        this.formatTypeS = formatTypeS;
        this.logger = logger;
        this.db = db;
        this.setupProperties = setupProperties;
        this.emailFrom = emailFrom;
        this.subject = subject;
        validate();
    }

    protected void validate()
        throws TException
    {
        if (action == null) {
            throw new TException.INVALID_OR_MISSING_PARM("Action object required");
        }
        if (StringUtil.isEmpty(emailTo)) {
            throw new TException.INVALID_OR_MISSING_PARM("Report email To required");
        }
        if (StringUtil.isEmpty(emailFrom)) {
            throw new TException.INVALID_OR_MISSING_PARM("Report email From required");
        }
        if (StringUtil.isEmpty(msg)) {
            throw new TException.INVALID_OR_MISSING_PARM("Report email Message required");
        }
        if (StringUtil.isEmpty(formatTypeS)) {
            formatTypeS = "xml";
        }
        try {
            formatTypeS = formatTypeS.toLowerCase();
            formatType = FormatterInf.Format.valueOf(formatTypeS);
        } catch (Exception ex) {
            throw new TException.REQUEST_INVALID(MESSAGE + "Format type not supported:" + formatTypeS);
        }
    }

    @Override
    public void run()
    {
        if (DEBUG) System.out.println("FixityEmailWrapper: run entered");
        if (db == null) return;
        if (DEBUG) System.out.println("FixityEmailWrapper: start processing");
        Connection connection = null;
        String dispSubject = subject;
        try {
            if (db != null) {
                connection = db.getConnection(autoCommit);
                action.setConnection(connection);
            }
            Thread t = Thread.currentThread();
            String name = t.getName();
            if (DEBUG) System.out.println('[' + name + "]: START");
            state = action.call();
            if (action.getException() != null) {
                Exception ex = action.getException();
                if (ex instanceof TException) {
                    state = (TException)ex;
                } else {
                    state = new TException(ex);
                }
                dispSubject += ": Exception";
            }
            
            if (state instanceof FixityServiceState) {
                FixityServiceState fixState = (FixityServiceState)state;
                String subjectMessage = "";
                if (fixState.getNumItems() == null) {
                    subjectMessage = "Database shutdown";
                } else if ((fixState.getNumUnavailable() > 0) || (fixState.getNumFailedItems() > 0)) {
                    subjectMessage = "ERROR(S) detected"
                            + " - failed:" + fixState.getNumFailedItems()
                            + " - unavailable:" + fixState.getNumUnavailable();
                } else subjectMessage = "Status OK";
                dispSubject += ": " + subjectMessage;
            }
            
            String disp = formatIt(state);
            if (DEBUG) System.out.println("DISP:" + disp);
            emailServ(emailTo, dispSubject, disp);

        } catch(Exception e)  {
            e.printStackTrace();

        } finally {
            try {
                connection.close();
            } catch (Exception ex) { }
        }
    }

    protected void emailServ(String email, String dispSubject, String disp)
        throws TException
    {
        if (DEBUG) System.out.println("DUMP:"
                + " - autoCommit=" + autoCommit
                + " - msg=" + msg
                + " - formatTypeS=" + formatTypeS
                + " - emailFrom=" + emailFrom
                + " - emailTo=" + emailTo
                + " - subject=" + dispSubject
                + " - disp=" + disp
                );
            FixityEmail emailer = new FixityEmail(setupProperties);
            String[] to = getTo(email);
            emailer.sendEmail(
                "report",
                formatTypeS,
                to,
                emailFrom,
                dispSubject,
                msg,
                disp);
    }

    public String[] getTo(String to)
        throws TException
    {
        if (StringUtil.isEmpty(to)) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "TO not supplied");
        }
        String [] addrs = to.split("\\s*\\;\\s*");
        if (addrs.length == 0) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "TO invalid");
        }
        String [] retAddrs = new String [addrs.length];
        for (int i=0; i < addrs.length; i++) {
            String addr = addrs[i];
            retAddrs[i] = stripMailto(addr);
            if (DEBUG) {
                String date = DateUtil.getCurrentIsoDate();
                System.out.println("MAILTO[" + date + "]:" + retAddrs[i]);
            }
        }
        return retAddrs;
    }

    protected String stripMailto(String value)
    {
        value = value.trim();
        if (!value.startsWith("mailto:")) return value;
        return value.substring(7);
    }

    public String formatIt(
            StateInf responseState)
    {
        try {
           if (responseState == null) {
               return "NULL RESPONSE";
           }

           FormatterInf formatter = FormatterAbs.getFormatter(formatType, logger);
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

