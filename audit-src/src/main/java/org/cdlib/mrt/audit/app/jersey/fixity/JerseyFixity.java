/*
Copyright (c) 2005-2012, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

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
*********************************************************************/
package org.cdlib.mrt.audit.app.jersey.fixity;

import org.cdlib.mrt.audit.app.FixityServiceInit;


import javax.servlet.ServletConfig;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.server.CloseableService;

import org.cdlib.mrt.formatter.FormatterInf;
import org.cdlib.mrt.audit.app.jersey.KeyNameHttpInf;
import org.cdlib.mrt.audit.app.jersey.JerseyBase;
import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.audit.service.FixityEntriesState;
import org.cdlib.mrt.audit.service.FixityMRTServiceInf;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.TException;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;

/**
 * Thin Jersey layer for fixity handling
 * @author  David Loy
 */
@Path ("/")
public class JerseyFixity
        extends JerseyBase
        implements KeyNameHttpInf
{

    protected static final String NAME = "JerseyFixity";
    protected static final String MESSAGE = NAME + ": ";
    protected static final FormatterInf.Format DEFAULT_OUTPUT_FORMAT
            = FormatterInf.Format.xml;
    protected static final boolean DEBUG = false;
    protected static final String NL = System.getProperty("line.separator");

    /**
     * Get state information about a specific node
     * @param nodeID node identifier
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted service information
     * @throws TException
     */
    @GET
    @Path("/state")
    public Response callGetServiceState(
            @DefaultValue("xhtml") @QueryParam(KeyNameHttpInf.RESPONSEFORM) String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        // return getServiceState(formatType, cs, sc); get State is depricated
        return getServiceStatus(formatType, cs, sc);
    }
    
    /**
     * Status is used for monitor information
     * @param formatType return format
     * @param cs
     * @param sc
     * @return
     * @throws TException 
     */
    @GET
    @Path("/status")
    public Response callGetServiceStatus(
            @DefaultValue("xhtml") @QueryParam(KeyNameHttpInf.RESPONSEFORM) String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        return getServiceStatus(formatType, cs, sc);
    }
    
    /**
     * Get entry content for item matching this url
     * @param url URL of fixity item entry
     * @param formatType response format
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return
     * @throws TException 
     */
    @GET
    @Deprecated
    @Path("url/{url}")
    public Response callGetUrlEntry(
            @PathParam("url") String url,
            @DefaultValue("xhtml") @QueryParam(KeyNameHttpInf.RESPONSEFORM) String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        return getFixityEntry(url, formatType, cs, sc);
    }

    @POST
    @Path("service/{setType}")
    public Response callService(
            @PathParam("setType") String setType,
            @DefaultValue("xhtml") @QueryParam(KeyNameHttpInf.RESPONSEFORM) String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        if (StringUtil.isEmpty(setType)) {
            throw new TException.REQUEST_INVALID("Set fixity status requires 'S' query element");
        }
        setType = setType.toLowerCase();
        if (setType.equals("start")) {
            return runFixity(formatType, cs, sc);

        } else if (setType.equals("stop")) {
            return shutdownFixity(formatType, cs, sc);

        } else if (setType.equals("pause")) {
            return pauseFixity(formatType, cs, sc);

        } else  {
            throw new TException.REQUEST_ELEMENT_UNSUPPORTED("Set fixity state value not recognized:" + setType);
        }
    }

    @POST
    @Path("update/{auditid}")
    public Response callTest(
            @PathParam("auditid") String auditIDS,
            @DefaultValue("xhtml") @QueryParam(KeyNameHttpInf.RESPONSEFORM) String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        if (StringUtil.isEmpty(auditIDS)) {
            throw new TException.REQUEST_INVALID("Test fixity status requires fileID");
        }
        long auditID = Long.parseLong(auditIDS);
        return updateAuditEntry(auditID, formatType, cs, sc);
    }
  

    @POST
    @Deprecated
    @Path("cleanup")
    public Response callCleanup(
            @DefaultValue("xhtml") @QueryParam(KeyNameHttpInf.RESPONSEFORM) String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        return cleanupAudit(formatType, cs, sc);
    }  
    
    @POST
    @Deprecated
    @Path("report")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response callGetReport(
            @DefaultValue("") @FormDataParam("select") String select,
            @DefaultValue("") @FormDataParam("emailTo") String emailTo,
            @DefaultValue("") @FormDataParam("emailMsg") String emailMsg,
            @DefaultValue("xhtml") @FormDataParam("response-form") String formatType,
            @Context CloseableService cs,
            @Context ServletConfig sc)
        throws TException
    {
        if (DEBUG) System.out.println(MESSAGE + "callAdd entered"
                    + " - select=" + select + NL
                    + " - emailTo=" + emailTo + NL
                    + " - emailMsg=" + emailMsg + NL
                    );
        return getSelectReport(select, emailTo, emailMsg, formatType, cs, sc);
    }


    /**
     * Get state information about a specific node
     * @param nodeID node identifier
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted service information
     * @throws TException
     */
    public Response getServiceState(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("getServiceState entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.getFixityServiceState();
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Get state information about a specific node
     * @param nodeID node identifier
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted service information
     * @throws TException
     */
    public Response getServiceStatus(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("getServiceState entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.getFixityServiceStatus();
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Start fixity service
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity service information
     * @throws TException
     */
    public Response runFixity(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("runFixity entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.setFixityRun();
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Update individual entry using audit entry id
     * @param auditID inv_audit entry
     * @param formatType
     * @param cs
     * @param sc
     * @return
     * @throws TException 
     */
    public Response updateAuditEntry(
            long auditID,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("updateAuditEntry entered:"
                    + " - auditID=" + auditID
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            FixityMRTEntry entry = fixityService.update(auditID);
            FixityMRTEntry [] entries = new FixityMRTEntry[1];
            entries[0] = entry;
            FixityEntriesState responseState = new FixityEntriesState(entries);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Update individual entry using audit entry id
     * @param auditID inv_audit entry
     * @param formatType
     * @param cs
     * @param sc
     * @return
     * @throws TException 
     */
    public Response cleanupAudit(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("cleanupAudit entered:"
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            fixityService.doCleanup(formatType);
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.getFixityServiceStatus();
            return getStateResponse(responseState, formatType, logger, cs, sc);


        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }
    
    /**
     * Stop fixity service
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity service information
     * @throws TException
     */
    public Response stopFixity(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("stopFixity entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.setFixityStop();
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Stop fixity service
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity service information
     * @throws TException
     */
    public Response shutdownFixity(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("shutdownFixity entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.setShutdown();
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }


    public Response pauseFixity(
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("pauseFixity entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.setPause();
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Return fixity entry item(s) - return multiple entries if truncation is used
     * @param url fixity entry URL
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity entry information
     * @throws TException
     */
    public Response getFixityEntry(
            String url,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("getFixityEntry entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.getFixityEntry(url);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * Return fixity entry item
     * @param url fixity entry URL
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity entry information
     * @throws TException
     */
    public Response getFixityEntries(
            InvAudit audit,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("getFixityEntries entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();
            FixityMRTEntry[] entries  = fixityService.getFixityEntries(audit);
            StateInf responseState = null;
            if (entries != null) {
                responseState = new FixityEntriesState(entries);
            }
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }
    /**
     * queue fixity entry
     * @param entry fixity entry
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity entry information
     * @throws TException
     */
    public Response queue(
            InvAudit audit,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("queue entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.queue(audit);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    /**
     * test fixity entry
     * @param entry fixity entry
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity entry information
     * @throws TException
     */
/*
    public Response test(
            InvAudit audit,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("test entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.test(audit);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }
    */

    /**
     * update fixity entry
     * @param entry fixity entry
     * @param formatType user provided format type
     * @param cs on close actions
     * @param sc ServletConfig used to get system configuration
     * @return formatted fixity entry information
     * @throws TException
     */
    public Response update(
            InvAudit audit,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("test entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.update(audit);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    protected Response getSelectReport(
            String select,
            String emailTo,
            String emailMsg,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("add entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.getSelectReport(select, emailTo, emailMsg, formatType);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

    protected Response getItemReport(
            String typeS,
            String context,
            String email,
            String formatType,
            CloseableService cs,
            ServletConfig sc)
        throws TException
    {
        LoggerInf logger = defaultLogger;
        try {
            log("add entered:"
                    + " - formatType=" + formatType
                    );
            FixityServiceInit fixityServiceInit = FixityServiceInit.getFixityServiceInit(sc);
            FixityMRTServiceInf fixityService = fixityServiceInit.getFixityService();
            logger = fixityService.getLogger();

            StateInf responseState = fixityService.getItemReport(typeS, context, email, formatType);
            return getStateResponse(responseState, formatType, logger, cs, sc);

        } catch (TException tex) {
            return getExceptionResponse(tex, formatType, logger);

        } catch (Exception ex) {
            System.out.println("TRACE:" + StringUtil.stackTrace(ex));
            throw new TException.GENERAL_EXCEPTION(MESSAGE + "Exception:" + ex);
        }
    }

}
