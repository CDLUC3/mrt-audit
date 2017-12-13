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

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.util.Vector;

import org.cdlib.mrt.audit.db.FixityMRTEntry;
import org.cdlib.mrt.audit.db.InvAudit;
import org.cdlib.mrt.utility.LoggerInf;
import org.cdlib.mrt.utility.StringUtil;
import org.cdlib.mrt.utility.TException;

/**
 * Run fixity
 * @author dloy
 */
public class RewriteEntry
{

    protected static final String NAME = "RewriteEntry";
    protected static final String MESSAGE = NAME + ": ";
    protected static final boolean DEBUG = false;

    protected LoggerInf logger = null;
    protected File entryFile = null;
    protected Vector<RMap> mapper = new Vector(20);

    public RewriteEntry(
            File entryFile,
            LoggerInf logger)
        throws TException
    {
        this.entryFile = entryFile;
        this.logger = logger;
        setEntryMapper();
    }

    public void setEntryMapper()
        throws TException
    {
        FileInputStream inStream = null;
        try {
            if (entryFile == null) return;
            if (!entryFile.exists()) return;
            mapper.clear();
            inStream = new FileInputStream(entryFile);
            DataInputStream in = new DataInputStream(inStream);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));

            while(true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                addLine(line);
            }

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException(ex);
        } finally {
            try {
                if (inStream != null) inStream.close();
            } catch (Exception ex) { }
        }
    }

    public InvAudit map(InvAudit entry)
        throws TException
    {
        if (entry == null) return null;
        if (mapper.size() == 0) return entry;
        String url = entry.getUrl();
        if (StringUtil.isEmpty(url)) return entry;
        for (RMap rmap : mapper) {
            if (rmap.match(url)) {
                setEntry(entry, rmap);
                return entry;
            }
        }
        return entry;
    }

    protected void setEntry(InvAudit entry, RMap rmap)
        throws TException
    {
        String url = entry.getUrl();
        String newurl = rmap.to + url.substring(rmap.from.length());
        if (DEBUG) System.out.println("***Map:\n"
                + " - from:" + url + "\n"
                + " -   to:" + newurl + "\n"
                );
        entry.setMapURL(newurl);
    }

    protected void addLine(String line)
        throws TException
    {
        try {
            if (line == null) {
                return;
            }
            RMap map = new RMap(line);
            System.out.println(MESSAGE + "Line:" + line);
            if (line.startsWith("#")) return;
            mapper.add(map);

        } catch (TException tex) {
            throw tex;

        } catch (Exception ex) {
            throw new TException(ex);
        }
    }

    public int getEntrySize()
    {
        return mapper.size();
    }

    protected void log(String msg)
    {
        if (!DEBUG) return;
        System.out.println(MESSAGE + msg);
    }

    public class RMap {
        public String from = null;
        public String to = null;
        public RMap(String line)
            throws TException
        {
            String [] parts = line.split("\\s+");
            System.out.println("RMap line:" + line);
            System.out.println("RMap length=" + parts.length);
            if ((parts.length < 2) || (parts.length > 3)) {
                throw new TException.INVALID_ARCHITECTURE("Rewrite line invalid:" + line);
            }
            from = parts[0];
            to = parts[1];
            System.out.println("RewriteEntry:\n"
                    + " - from:" + from + "\n"
                    + " -   to:" + to + "\n"
                    );
        }

        public boolean match(String test)
        {
            if (test.startsWith(from)) return true;
            else return false;
        }
    }
}

