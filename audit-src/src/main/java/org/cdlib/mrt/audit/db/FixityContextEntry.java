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

import java.util.Properties;

import org.cdlib.mrt.audit.utility.FixityUtil;
import org.cdlib.mrt.utility.PropertiesUtil;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Fixity Test entry
 * @author dloy
 */
public class FixityContextEntry
    implements StateInf
{

    protected static final String NAME = "FixityContextEntry";
    protected static final String MESSAGE = NAME + ": ";
    public static final String DBDATEPATTERN = "yyyy-MM-dd HH:mm:ss";

    protected long contextKey = 0;
    protected long itemKey = 0;
    protected String context = null;
    protected String note = null;

    public FixityContextEntry() {}

    public FixityContextEntry(Properties prop)
        throws TException
    {
        try {
            setFromProperties(prop);
        } catch (Exception ex) {
            System.out.println(MESSAGE + "Exception:" + ex);
            ex.printStackTrace();
        }
    }

    public String getNote() {
        return note;
    }

    public void setNote(String detail) {
        this.note = detail;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public long getContextKey() {
        return contextKey;
    }

    public void setContextKey(long key) {
        this.contextKey = key;
    }

    public void setContextKey(String keyS)
        throws TException
    {
        if (StringUtil.isEmpty(keyS)) return;
        try {
            this.contextKey = Long.parseLong(keyS);
        } catch (Exception ex) {
              throw new TException.INVALID_DATA_FORMAT(MESSAGE
                    + "setId - Exception: id is not numeric:" + keyS);
        }
    }

    public long getItemKey() {
        return itemKey;
    }

    public void setItemKey(long itemkey) {
        this.itemKey = itemkey;
    }

    public void setItemKey(String itemKeyS)
        throws TException
    {
        if (StringUtil.isEmpty(itemKeyS)) return;
        try {
            this.itemKey = Long.parseLong(itemKeyS);
        } catch (Exception ex) {
              throw new TException.INVALID_DATA_FORMAT(MESSAGE
                    + "setId - Exception: id is not numeric:" + itemKeyS);
        }
    }

    public Properties retrieveProperties()
    {
        Properties retProp = new Properties();
        if (itemKey > 0) FixityUtil.setProp(retProp, "itemkey", "" + getItemKey());
        if (contextKey > 0) FixityUtil.setProp(retProp, "contextkey", "" + getContextKey());
        FixityUtil.setProp(retProp, "context",getContext());
        FixityUtil.setProp(retProp, "note",getNote());
        return retProp;
    }

    public void setFromProperties(Properties prop)
        throws TException
    {
        if ((prop == null) || (prop.size() == 0)) {
            throw new TException.INVALID_OR_MISSING_PARM(MESSAGE + "setFromProperties - Properties missing");
        }
        setItemKey(prop.getProperty("itemkey"));
        setContextKey(prop.getProperty("contextkey"));
        setContext(prop.getProperty("context"));
        setNote(prop.getProperty("note"));
    }

    public String dump(String header)
    {
        Properties prop = retrieveProperties();
        return PropertiesUtil.dumpProperties(header, prop);
    }
}

