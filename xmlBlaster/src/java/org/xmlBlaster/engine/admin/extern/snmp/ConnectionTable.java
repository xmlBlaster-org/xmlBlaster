/*
 * This Java file has been generated by smidump 0.3.1. Do not edit!
 * It is intended to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */
package org.xmlBlaster.engine.admin.extern.snmp;

/**
    This class represents a Java AgentX (JAX) implementation of
    the table connectionTable defined in XMLBLASTER-MIB.

    @version 1
    @author  smidump 0.3.1
    @see     AgentXTable
 */

import java.util.Vector;

import jax.AgentXOID;
import jax.AgentXVarBind;
import jax.AgentXResponsePDU;
import jax.AgentXSetPhase;
import jax.AgentXTable;
import jax.AgentXEntry;

public class ConnectionTable extends AgentXTable
{

    // entry OID
    private final static long[] OID = {1, 3, 6, 1, 4, 1, 11662, 1, 3, 1};

    // constructors
    public ConnectionTable()
    {
        oid = new AgentXOID(OID);

        // register implemented columns
        columns.addElement(new Long(2));
        columns.addElement(new Long(3));
        columns.addElement(new Long(4));
        columns.addElement(new Long(5));
    }

    public ConnectionTable(boolean shared)
    {
        super(shared);

        oid = new AgentXOID(OID);

        // register implemented columns
        columns.addElement(new Long(2));
        columns.addElement(new Long(3));
        columns.addElement(new Long(4));
        columns.addElement(new Long(5));
    }

    public AgentXVarBind getVarBind(AgentXEntry entry, long column)
    {
        AgentXOID oid = new AgentXOID(getOID(), column, entry.getInstance());

        switch ((int)column) {
        case 2: // connectionHost
        {
            byte[] value = ((ConnectionEntry)entry).get_connectionHost();
            return new AgentXVarBind(oid, AgentXVarBind.OCTETSTRING, value);
        }
        case 3: // connectionPort
        {
            long value = ((ConnectionEntry)entry).get_connectionPort();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 4: // connectionAddress
        {
            byte[] value = ((ConnectionEntry)entry).get_connectionAddress();
            return new AgentXVarBind(oid, AgentXVarBind.OCTETSTRING, value);
        }
        case 5: // connectionProtocol
        {
            int value = ((ConnectionEntry)entry).get_connectionProtocol();
            return new AgentXVarBind(oid, AgentXVarBind.INTEGER, value);
        }
        }

        return null;
    }

    public int setEntry(AgentXSetPhase phase,
                        AgentXEntry entry,
                        long column,
                        AgentXVarBind vb)
    {

        switch ((int)column) {
        }

        return AgentXResponsePDU.NOT_WRITABLE;
    }

}

