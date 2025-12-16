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
package org.cdlib.mrt.audit.db;

import java.sql.Connection;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.core.FixityStatusType;
import org.cdlib.mrt.core.MessageDigest;
import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.audit.utility.FixityDBUtil;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Fixity Test entry
 * @author dloy
 */
public class FixityMRTEntry
    implements StateInf
{

    protected static final String NAME = "FixityEntry";
    protected static final String MESSAGE = NAME + ": ";
    public static final String DBDATEPATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final boolean DEBUG = false;
    public enum SourceType { merritt, file, web };
    protected long auditid = 0;
    protected long fileid = 0;
    protected String url = null;
    protected String mapURL = null;
    protected SourceType mapSource = null;
    protected SourceType source = null;
    protected MessageDigest digest = null;
    protected MessageDigest lastDigest = null;
    protected String lastDigestValue = null;
    protected long size = 0;
    protected long lastSize = 0;
    protected long streamMs = 0;
    protected String note = null;
    protected DateState created = null;
    protected DateState verified = null;
    protected DateState modified = null;
    protected FixityStatusType status = FixityStatusType.unverified;
    protected String contactId = null;
    protected LoggerInf logger = null;
    protected InvAudit audit = null;

    protected Vector<FixityContextEntry> contextEntries = new Vector<FixityContextEntry>();
    protected Hashtable<String, FixityContextEntry> hashContextEntries = new Hashtable<String, FixityContextEntry>();


    public FixityMRTEntry() {}
    
    public FixityMRTEntry(LoggerInf logger) {}
    {
        this.logger = logger;
    }
    
    public FixityMRTEntry(InvAudit invAudit, Connection connection, LoggerInf logger)
        throws TException
    {
        try {
            this.logger = logger;
            setFromAudit(invAudit, connection);
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
        }
    }

    public FixityMRTEntry(Properties prop)
        throws TException
    {
        try {
            setFromProperties(prop);
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
        }
    }
    
    public void setFromAudit(InvAudit invAudit, Connection connection)
    {
        try {
            setFromInvAudit(invAudit);
            long fileseq = invAudit.getFileid();
            setFromInvFiles(fileseq, connection);
            setItemKey(invAudit.getId());
            
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
        }
    }

    private void setFromInvAudit(InvAudit audit)
        throws TException
    {
        if (audit == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "setFromInvAudit - audit missing");
        }
        this.audit = audit;
        setAuditid(audit.getId());
        setUrl(audit.getUrl());
        setSource("web");
        setLastSize(audit.getFailedSize());
        setFileid(audit.getFileid());
        setCreated(audit.getCreated());
        setVerified(audit.getVerified());
        setModified(audit.getModified());
        setStatus(audit.getStatus());
        setNote(audit.getNote());
        setMapURL(audit.getMapURL());
    }
    
    public Properties setFromInvFiles(long fileseq, Connection connection)
        throws TException
    {
        try {
            String sql = "select * "
                    + "from inv_files "
                    + "where id=" + fileseq + ";";
            Properties [] props = FixityDBUtil.cmd(connection, sql, logger);
            if ((props == null) || (props.length==0)) return null;
            String digestType = props[0].getProperty("digest_type");
            String digestValue = props[0].getProperty("digest_value");
            setDigest(digestType, digestValue);
            setLastDigest(digestType, lastDigestValue);
            String sizeS = props[0].getProperty("full_size");
            if (StringUtil.isAllBlank(sizeS)) size = 0;
            else size = Long.parseLong(sizeS);
            if (DEBUG) System.out.println("***SIZE=" + size);
            return props[0];

        } catch(Exception e)  {
            if (logger != null)
            {
                logger.logError(
                    "Main: Encountered exception:" + e, 0);
                logger.logError(
                        StringUtil.stackTrace(e), 10);
            }
            throw new TException(e);

        }
    }
    
    public InvAudit getInvAudit()
        throws TException
    {
        InvAudit auditOut = new InvAudit(logger);
        auditOut.setId(getAuditid());
        auditOut.setUrl(getUrl());
        auditOut.setFailedSize(getLastSize());
        if (getLastDigest() != null) {
            String lastDigest = getLastDigest().getValue();
            auditOut.setFailedDigestValue(lastDigest);
        }
        auditOut.setFileid(getFileid());
        auditOut.setCreated(getCreated());
        auditOut.setVerified(getVerified());
        auditOut.setModified(getModified());
        auditOut.setStatus(getStatus());
        auditOut.setNote(getNote());
        this.audit = auditOut;
        return auditOut;
    }
    
    
    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public SourceType getSource() {
        return source;
    }

    public void setSource(SourceType method) {
        this.source = method;
    }

    public void setSource(String sourceS)
        throws TException
    {
        if (StringUtil.isEmpty(sourceS)) return;
        sourceS = sourceS.toLowerCase();
        this.source = SourceType.valueOf(sourceS);
    }

    public MessageDigest getDigest() {
        return digest;
    }

    public void setDigest(MessageDigest digest) {
        this.digest = digest;
    }

    public void setDigest(String digestTypeS, String digestValueS) 
        throws TException
    {
        this.digest = FixityUtil.getDigest(digestTypeS, digestValueS);
    }

    public String getDigestType()
    {
        if (digest == null) return null;
        else return digest.getJavaAlgorithm();
    }

    public String getDigestValue()
    {
        if (digest == null) return null;
        else return digest.getValue();
    }

    public MessageDigest getLastDigest() {
        return lastDigest;
    }

    public void setLastDigest(MessageDigest lastDigest) {
        this.lastDigest = lastDigest;
    }

    public void setLastDigest(String digestTypeS, String digestValueS)
        throws TException
    {
        if (StringUtil.isEmpty(digestTypeS) || StringUtil.isEmpty(digestValueS)) {
            return;
        }
        this.lastDigest = FixityUtil.getDigest(digestTypeS, digestValueS);
    }

    public DateState getVerified() {
        return verified;
    }

    public void setVerified(DateState verified) {
        this.verified = verified;
    }

    public void setVerified(String verified) {
        this.verified = FixityUtil.setDBDate(verified);
    }

    public void setVerified() {
        this.verified = new DateState();
    }

    public DateState getCreated() {
        return created;
    }

    public void setCreated(DateState created) {
        this.created = created;
    }

    public void setCreated(String createdS) {
        this.created = FixityUtil.setDBDate(createdS);
    }

    public void setCreated() {
        this.created = new DateState();
    }

    public long getItemKey() {
        return auditid;
    }

    public void setItemKey(long itemkey) {
        this.auditid = itemkey;
    }

    public void setItemKey(String itemKeyS)
        throws TException
    {
        if (StringUtil.isEmpty(itemKeyS)) return;
        try {
            this.auditid = Long.parseLong(itemKeyS);
        } catch (Exception ex) {
              throw new TException.INVALID_DATA_FORMAT(MESSAGE
                    + "setId - Exception: id is not numeric:" + itemKeyS);
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public DateState getModified() {
        return modified;
    }

    public void setModified(DateState modified) {
        this.modified = modified;
    }

    public void setModified(String modifiedS) {
        this.modified = FixityUtil.setDBDate(modifiedS);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long inSize) {
        this.size = inSize;
    }

    public void setSize(String inSize)
        throws TException
    {
        this.size = FixityUtil.setLong(inSize);
    }

    public long getLastSize() {
        return lastSize;
    }

    public void setLastSize(long lastSize) {
        this.lastSize = lastSize;
    }

    public void setLastSize(String inSize)
        throws TException
    {
        this.lastSize = FixityUtil.setLong(inSize);
    }

    public FixityStatusType getStatus() {
        return status;
    }

    public String retrieveDBStatus() {
        if (status == null) return null;
        return status.toString();
    }

    public void setStatus(FixityStatusType status) {
        this.status = status;
    }

    public void setStatus(String statusS)
        throws TException
    {
        if (StringUtil.isEmpty(statusS)) return;
        statusS = statusS.toLowerCase();
        this.status = FixityStatusType.getFixityStatusType(statusS);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Properties retrieveProperties()
    {
        Properties retProp = new Properties();
        if (auditid > 0) FixityUtil.setProp(retProp, "itemkey", "" + getItemKey());
        FixityUtil.setProp(retProp, "url", getUrl());
        if (size > 0)
            FixityUtil.setProp(retProp, "size", "" + getSize());
        if (source != null)
            FixityUtil.setProp(retProp, "source", "" + source);
        if (digest != null) {
            FixityUtil.setProp(retProp, "type",digest.getJavaAlgorithm());
            FixityUtil.setProp(retProp, "value", digest.getValue());
        }
        if (getLastSize() > 0) {
            FixityUtil.setProp(retProp, "lastsize", "" + getLastSize());
        }
        if (lastDigest != null) {
            FixityUtil.setProp(retProp, "lastvalue", lastDigest.getValue());
        }
        if (created != null) {
            FixityUtil.setProp(retProp, "created", FixityUtil.getDBDate(created));
        }
        if (verified != null) {
            FixityUtil.setProp(retProp, "verified", FixityUtil.getDBDate(verified));
        }
        if (modified != null) {
            FixityUtil.setProp(retProp, "modified", FixityUtil.getDBDate(modified));
        }
        FixityUtil.setProp(retProp, "status", retrieveDBStatus());
        FixityUtil.setProp(retProp, "note", note);
        FixityUtil.setProp(retProp, "contactid", contactId);
        return retProp;
    }

    public void setFromProperties(Properties prop)
        throws TException
    {
        if ((prop == null) || (prop.size() == 0)) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "setFromProperties - Properties missing");
        }
        setItemKey(prop.getProperty("itemkey"));
        setUrl(prop.getProperty("url"));
        setSource(prop.getProperty("source"));
        setSize(prop.getProperty("size"));
        setLastSize(prop.getProperty("lastsize"));
        setDigest(prop.getProperty("type"), prop.getProperty("value"));
        setLastDigest(prop.getProperty("type"), prop.getProperty("lastvalue"));
        setCreated(prop.getProperty("created"));
        setVerified(prop.getProperty("verified"));
        setModified(prop.getProperty("modified"));
        setStatus(prop.getProperty("status"));
        setContactId(prop.getProperty("contactid"));
        setNote(prop.getProperty("note"));
    }

    public String retrieveMapURL() {
        return mapURL;
    }


    public FixityMRTEntry mergeFromEntry(FixityMRTEntry inEntry)
        throws TException
    {
        if (inEntry == null) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "mergeFromEntry - inEntry missing");
        }
        Properties inProp = inEntry.retrieveProperties();
        if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("inProp", inProp));
        Properties thisProp = retrieveProperties();
        if (DEBUG) System.out.println(PropertiesUtil.dumpProperties("thisProp", thisProp));
        PropertiesUtil.mergeProperties(thisProp, inProp);
        FixityMRTEntry returnEntry = new FixityMRTEntry(thisProp);
        Properties inAuditProp = audit.retrieveProp();
        Properties thisAuditProp = audit.retrieveProp();
        PropertiesUtil.mergeProperties(thisAuditProp, inAuditProp);
        InvAudit thisAudit = new InvAudit(thisAuditProp, logger);
        setAudit(thisAudit);
        return returnEntry;
    }

    public void setMapURL(String runURL) {
        this.mapURL = runURL;
    }

    public SourceType retrieveMapSource() {
        return mapSource;
    }

    public void setMapSource(SourceType runSource) {
        this.mapSource = runSource;
    }


    public String dump(String header)
    {
        Properties prop = retrieveProperties();
        return PropertiesUtil.dumpProperties(header, prop);
    }

    public long getAuditid() {
        return auditid;
    }

    public void setAuditid(long auditid) {
        this.auditid = auditid;
    }

    public long getFileid() {
        return fileid;
    }

    public void setFileid(long fileid) {
        this.fileid = fileid;
    }

    public void printEntry(String header)
    {
        System.out.println("****" + header + "****");
        System.out.println(dump("--" + header + " Entry--"));
    }

    public void setAudit(InvAudit audit) {
        this.audit = audit;
    }

    public long getStreamMs() {
        return streamMs;
    }

    public void setStreamMs(long streamMs) {
        this.streamMs = streamMs;
    }
    
}

