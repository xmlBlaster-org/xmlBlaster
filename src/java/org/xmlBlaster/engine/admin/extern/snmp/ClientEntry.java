/*
 * This Java file has been generated by smidump 0.3.1. Do not edit!
 * It is intended to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */
package org.xmlBlaster.engine.admin.extern.snmp;

/**
    This class represents a Java AgentX (JAX) implementation of
    the table row clientEntry defined in XMLBLASTER-MIB.

    @version 1
    @author  smidump 0.3.1
    @see     AgentXTable, AgentXEntry
 */

import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;
import jax.AgentXEntry;

public class ClientEntry extends AgentXEntry
{

    protected long clientIndex = 0;
    protected byte[] clientName = new byte[0];
    protected int peerType = 0;
    protected int connectionState = 0;
    protected long clientQueueNumEntries = 0;
    protected long clientQueueMaxEntries = 0;
    protected long undo_clientQueueMaxEntries = 0;
    protected long clientQueueThreshold = 0;
    protected long undo_clientQueueThreshold = 0;
    protected int clearClientQueue = 0;
    protected int undo_clearClientQueue = 0;
    protected long numSessions = 0;
    protected long maxSessions = 0;
    protected long undo_maxSessions = 0;
    protected long sessionThreshold = 0;
    protected long undo_sessionThreshold = 0;
    protected long clientUptime = 0;
    protected long clientDowntime = 0;
    // foreign indices
    protected long nodeIndex;

    public ClientEntry(long nodeIndex,
                       long clientIndex)
    {
        this.nodeIndex = nodeIndex;
        this.clientIndex = clientIndex;

        instance.append(nodeIndex);
        instance.append(clientIndex);
    }

    public long get_nodeIndex()
    {
        return nodeIndex;
    }

    public long get_clientIndex()
    {
        return clientIndex;
    }

    public byte[] get_clientName()
    {
        return clientName;
    }

    public int get_peerType()
    {
        return peerType;
    }

    public int get_connectionState()
    {
        return connectionState;
    }

    public long get_clientQueueNumEntries()
    {
        return clientQueueNumEntries;
    }

    public long get_clientQueueMaxEntries()
    {
        return clientQueueMaxEntries;
    }

    public int set_clientQueueMaxEntries(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_clientQueueMaxEntries = clientQueueMaxEntries;
            clientQueueMaxEntries = value;
            break;
        case AgentXSetPhase.UNDO:
            clientQueueMaxEntries = undo_clientQueueMaxEntries;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public long get_clientQueueThreshold()
    {
        return clientQueueThreshold;
    }

    public int set_clientQueueThreshold(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_clientQueueThreshold = clientQueueThreshold;
            clientQueueThreshold = value;
            break;
        case AgentXSetPhase.UNDO:
            clientQueueThreshold = undo_clientQueueThreshold;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public int get_clearClientQueue()
    {
        return clearClientQueue;
    }

    public int set_clearClientQueue(AgentXSetPhase phase, int value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_clearClientQueue = clearClientQueue;
            clearClientQueue = value;
            break;
        case AgentXSetPhase.UNDO:
            clearClientQueue = undo_clearClientQueue;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public long get_numSessions()
    {
        return numSessions;
    }

    public long get_maxSessions()
    {
        return maxSessions;
    }

    public int set_maxSessions(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_maxSessions = maxSessions;
            maxSessions = value;
            break;
        case AgentXSetPhase.UNDO:
            maxSessions = undo_maxSessions;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public long get_sessionThreshold()
    {
        return sessionThreshold;
    }

    public int set_sessionThreshold(AgentXSetPhase phase, long value)
    {
        switch (phase.getPhase()) {
        case AgentXSetPhase.TEST_SET:
            break;
        case AgentXSetPhase.COMMIT:
            undo_sessionThreshold = sessionThreshold;
            sessionThreshold = value;
            break;
        case AgentXSetPhase.UNDO:
            sessionThreshold = undo_sessionThreshold;
            break;
        case AgentXSetPhase.CLEANUP:
            break;
        default:
            return AgentXResponsePDU.PROCESSING_ERROR;
        }
        return AgentXResponsePDU.NO_ERROR;
    }
    public long get_clientUptime()
    {
        return clientUptime;
    }

    public long get_clientDowntime()
    {
        return clientDowntime;
    }

}
