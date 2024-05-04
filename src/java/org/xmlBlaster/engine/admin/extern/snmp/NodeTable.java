/*
 * This Java file has been generated by smidump 0.3.1. Do not edit!
 * It is intended to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */
package org.xmlBlaster.engine.admin.extern.snmp;

/**
    This class represents a Java AgentX (JAX) implementation of
    the table nodeTable defined in XMLBLASTER-MIB.

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

public class NodeTable extends AgentXTable
{

    // entry OID
    private final static long[] OID = {1, 3, 6, 1, 4, 1, 11662, 1, 2, 1};

    // constructors
    public NodeTable()
    {
        oid = new AgentXOID(OID);

        // register implemented columns
        columns.addElement(Long.valueOf(2));
        columns.addElement(Long.valueOf(3));
        columns.addElement(Long.valueOf(4));
        columns.addElement(Long.valueOf(5));
        columns.addElement(Long.valueOf(6));
        columns.addElement(Long.valueOf(7));
        columns.addElement(Long.valueOf(8));
        columns.addElement(Long.valueOf(9));
        columns.addElement(Long.valueOf(10));
        columns.addElement(Long.valueOf(11));
        columns.addElement(Long.valueOf(12));
        columns.addElement(Long.valueOf(13));
        columns.addElement(Long.valueOf(14));
    }

    public NodeTable(boolean shared)
    {
        super(shared);

        oid = new AgentXOID(OID);

        // register implemented columns
        columns.addElement(Long.valueOf(2));
        columns.addElement(Long.valueOf(3));
        columns.addElement(Long.valueOf(4));
        columns.addElement(Long.valueOf(5));
        columns.addElement(Long.valueOf(6));
        columns.addElement(Long.valueOf(7));
        columns.addElement(Long.valueOf(8));
        columns.addElement(Long.valueOf(9));
        columns.addElement(Long.valueOf(10));
        columns.addElement(Long.valueOf(11));
        columns.addElement(Long.valueOf(12));
        columns.addElement(Long.valueOf(13));
        columns.addElement(Long.valueOf(14));
    }

    public AgentXVarBind getVarBind(AgentXEntry entry, long column)
    {
        AgentXOID oid = new AgentXOID(getOID(), column, entry.getInstance());

        switch ((int)column) {
        case 2: // nodeName
        {
            byte[] value = ((NodeEntry)entry).get_nodeName();
            return new AgentXVarBind(oid, AgentXVarBind.OCTETSTRING, value);
        }
        case 3: // nodeUptime
        {
            long value = ((NodeEntry)entry).get_nodeUptime();
            return new AgentXVarBind(oid, AgentXVarBind.TIMETICKS, value);
        }
        case 4: // totalMem
        {
            long value = ((NodeEntry)entry).get_totalMem();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 5: // usedMem
        {
            long value = ((NodeEntry)entry).get_usedMem();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 6: // freeMem
        {
            long value = ((NodeEntry)entry).get_freeMem();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 7: // hostname
        {
            byte[] value = ((NodeEntry)entry).get_hostname();
            return new AgentXVarBind(oid, AgentXVarBind.OCTETSTRING, value);
        }
        case 8: // port
        {
            long value = ((NodeEntry)entry).get_port();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 9: // numClients
        {
            long value = ((NodeEntry)entry).get_numClients();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 10: // maxClients
        {
            long value = ((NodeEntry)entry).get_maxClients();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 11: // clientThreshold
        {
            long value = ((NodeEntry)entry).get_clientThreshold();
            return new AgentXVarBind(oid, AgentXVarBind.GAUGE32, value);
        }
        case 12: // errorLogfile
        {
            byte[] value = ((NodeEntry)entry).get_errorLogfile();
            return new AgentXVarBind(oid, AgentXVarBind.OCTETSTRING, value);
        }
        case 13: // logLevel
        {
            int value = ((NodeEntry)entry).get_logLevel();
            return new AgentXVarBind(oid, AgentXVarBind.INTEGER, value);
        }
        case 14: // runLevel
        {
            int value = ((NodeEntry)entry).get_runLevel();
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
        case 8: // port
        {
            if (vb.getType() != AgentXVarBind.GAUGE32)
                return AgentXResponsePDU.WRONG_TYPE;
            else
                return ((NodeEntry)entry).set_port(phase, vb.longValue());
        }
        case 10: // maxClients
        {
            if (vb.getType() != AgentXVarBind.GAUGE32)
                return AgentXResponsePDU.WRONG_TYPE;
            else
                return ((NodeEntry)entry).set_maxClients(phase, vb.longValue());
        }
        case 11: // clientThreshold
        {
            if (vb.getType() != AgentXVarBind.GAUGE32)
                return AgentXResponsePDU.WRONG_TYPE;
            else
                return ((NodeEntry)entry).set_clientThreshold(phase, vb.longValue());
        }
        case 12: // errorLogfile
        {
            if (vb.getType() != AgentXVarBind.OCTETSTRING)
                return AgentXResponsePDU.WRONG_TYPE;
            else
                return ((NodeEntry)entry).set_errorLogfile(phase, vb.bytesValue());
        }
        case 13: // logLevel
        {
            if (vb.getType() != AgentXVarBind.INTEGER)
                return AgentXResponsePDU.WRONG_TYPE;
            else
                return ((NodeEntry)entry).set_logLevel(phase, vb.intValue());
        }
        case 14: // runLevel
        {
            if (vb.getType() != AgentXVarBind.INTEGER)
                return AgentXResponsePDU.WRONG_TYPE;
            else
                return ((NodeEntry)entry).set_runLevel(phase, vb.intValue());
        }
        }

        return AgentXResponsePDU.NOT_WRITABLE;
    }

}










