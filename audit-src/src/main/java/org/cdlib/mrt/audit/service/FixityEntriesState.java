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

import java.util.Vector;

import org.cdlib.mrt.core.DateState;
import org.cdlib.mrt.utility.StateInf;
import org.cdlib.mrt.audit.db.FixityMRTEntry;

/**
 * State for multiple item entry results
 * @author  dloy
 */

public class FixityEntriesState
        implements StateInf
{
    private static final String NAME = "FixityEntriesState";
    private static final String MESSAGE = NAME + ": ";

    protected DateState reportDate = new DateState();
    protected Vector<FixityMRTEntry> entries = new Vector<FixityMRTEntry>();

    public FixityEntriesState(FixityMRTEntry [] entries)
    {
        setFixityEntries(entries);
    }

    public FixityEntriesState(Vector<FixityMRTEntry> entries)
    {
        replaceFixityEntries(entries);
    }


    public FixityMRTEntry getContextEntry(int i)
    {
        if (i < 0) return null;
        if (i >= entries.size()) return null;
        return entries.get(i);
    }

    public void setFixityEntries(FixityMRTEntry [] entries) {
        if ((entries == null) || (entries.length == 0)) return;
        clear();
        for (FixityMRTEntry entry : entries) {
            addFixityEntry(entry);
        }
    }

    public void addFixityEntries(FixityMRTEntry [] entries) {
        if ((entries == null) || (entries.length == 0)) return;
        for (FixityMRTEntry entry : entries) {
            addFixityEntry(entry);
        }
    }

    public void replaceFixityEntries(Vector<FixityMRTEntry> entries) {
        this.entries = entries;
    }

    public void addFixityEntry(FixityMRTEntry fixityEntry)
    {
        if (fixityEntry == null) return;
        entries.add(fixityEntry);
    }

    public Vector<FixityMRTEntry> getEntries() {
        return entries;
    }

    public void clear()
    {
        entries.clear();
    }

    public DateState getReportDate() {
        return reportDate;
    }
    
    public int size() 
    {
        return entries.size();
    }
 
}
