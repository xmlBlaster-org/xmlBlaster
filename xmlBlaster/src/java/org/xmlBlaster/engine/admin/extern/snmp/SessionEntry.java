/*
 * This Java file has been generated by smidump 0.3.1. Do not edit!
 * It is intended to be used within a Java AgentX sub-agent environment.
 *
 * $Id: SessionEntry.java,v 1.2 2003/03/25 07:48:19 ruff Exp $
 */
package org.xmlBlaster.engine.admin.extern.snmp;

/**
    This class represents a Java AgentX (JAX) implementation of
    the table row sessionEntry defined in XMLBLASTER-MIB.

    @version 1
    @author  smidump 0.3.1
    @see     AgentXTable, AgentXEntry
 */

import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;
import jax.AgentXEntry;

public class SessionEntry extends AgentXEntry
{

    protected long sessionIndex = 0;
    protected byte[] sessionName = new byte[0];
    protected long cbQueueNumEntries = 0;
    protected long cbQueueMaxEntries = 0;
    protected long undo_cbQueueMaxEntries = 0;
    protected long cbQueueThreshold = 0;
    protected long undo_cbQueueThreshold = 0;
    protected int clearCbQueue = 0;
    protected int undo_clearCbQueue = 0;
    protected int closeSession = 0;
    protected int undo_closeSession = 0;
    // foreign indices
    protected long nodeIndex;
    protected long clientIndex;

    public SessionEntry(long nodeIndex,
                        long clientIndex,
                        long sessionIndex)
    {
        this.nodeIndex = nodeIndex;
        this.clientIndex = clientIndex;
        this.sessionIndex = sessionIndex;

        instance.append(nodeIndex);
        instance.append(clientIndex);
        instance.append(sessionIndex);
    }

    public long get_nodeIndex()
    {
        return nodeIndex;
    }

    public long get_clientIndex()
    {
        return clientIndex;
    }

    public long get_sessionIndex()
    {
        return sessionIndex;
    }

    public byte[] get_sessionName()
    {
        return sessionName;
    }

    public long get_cbQueueNumEntries()
    {
        return cbQueueNumEntries;
    }

    public long get_cbQueueMaxEntries()
    {
        return cbQueueMaxEntries;
    }

    public int set_cbQueueMaxEntries(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_cbQueueMaxEntries = cbQueueMaxEntries;
            cbQueueMaxEntries = value;
            break;
        case AgentXSetPhase.UNDO:
            cbQueueMaxEntries = undo_cbQueueMaxEntries;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public long get_cbQueueThreshold()
    {
        return cbQueueThreshold;
    }

    public int set_cbQueueThreshold(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_cbQueueThreshold = cbQueueThreshold;
            cbQueueThreshold = value;
            break;
        case AgentXSetPhase.UNDO:
            cbQueueThreshold = undo_cbQueueThreshold;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public int get_clearCbQueue()
    {
        return clearCbQueue;
    }

    public int set_clearCbQueue(AgentXSetPhase phase, int value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_clearCbQueue = clearCbQueue;
            clearCbQueue = value;
            break;
        case AgentXSetPhase.UNDO:
            clearCbQueue = undo_clearCbQueue;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public int get_closeSession()
    {
        return closeSession;
    }

    public int set_closeSession(AgentXSetPhase phase, int value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_closeSession = closeSession;
            closeSession = value;
            break;
        case AgentXSetPhase.UNDO:
            closeSession = undo_closeSession;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
}

