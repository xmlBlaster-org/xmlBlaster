/*
 * This Java file has been generated by smidump 0.3.1. It
 * is intended to be edited by the application programmer and
 * to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;
import jax.AgentXEntry;

/**
 *  This class extends the Java AgentX (JAX) implementation of
 *  the table row clientEntry defined in XMLBLASTER-MIB.
 *  ClientEntryImpl 
 *  - is the interface side of a bridge pattern.
 *  - contains a reference to the implementation side of the bridge pattern (= ClientEntryImplPeer).
 *  - implements its methods by forwarding its calls to ClientEntryImplPeer.
 *  
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class ClientEntryImpl extends ClientEntry
{

    public ClientEntryImplPeer clientEntryImplPeer;

    /**
     * Initializes ClientEntry mib variables.
     * Builds a reference to ClientEntryImplPeer, which implements ClientEntryImpl methods.
     * @param NodeIndex identifies a node in nodeTable.
     * @param ClientIndex identifies a client in clientTable together with nodeIndex.
     * @param ClientEntryImplPeer implements ClientEntryImpl methods.    
     */
    public ClientEntryImpl(long nodeIndex,
                           long clientIndex,
                           ClientEntryImplPeer clientEntryImplPeer)
    {
        super(nodeIndex, clientIndex);
        clientName = clientEntryImplPeer.get_clientName().getBytes();
        peerType = clientEntryImplPeer.get_peerType();
        connectionState = clientEntryImplPeer.get_connectionState();
        clientQueueMaxEntries = clientEntryImplPeer.get_clientQueueMaxEntries();
        clientQueueThreshold = clientEntryImplPeer.get_clientQueueThreshold();
        clearClientQueue = clientEntryImplPeer.get_clearClientQueue();
        maxSessions = clientEntryImplPeer.get_maxSessions();
        sessionThreshold = clientEntryImplPeer.get_sessionThreshold();
        this.clientEntryImplPeer = clientEntryImplPeer;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_clientName().
     * @return ClientName name of an xmlBlaster client.
     */
    public byte[] get_clientName()
    {
        // clientName = clientEntryImplPeer.get_clientName();
        return clientName;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_peerType().
     * @return PeerType type of peer entity (0 = client, 1 = mom).
     */
    public int get_peerType()
    {
        // peerType = clientEntryImplPeer.get_peerType();
        return peerType;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_connectionState().
     * @return ConnectionState state of the client connection (0 = down, 1 = up).
     */
    public int get_connectionState()
    {
        // connectionState = clientEntryImplPeer.get_connectionState();
        return connectionState;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_clientQueueNumEntries().
     * @return ClientQueueNumEntries actual number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueNumEntries()
    {
        // clientQueueNumEntries = clientEntryImplPeer.get_clientQueueNumEntries();
        return clientQueueNumEntries;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_clientQueueMaxEntries().
     * 
     * @return ClientQueueMaxEntries maximum number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueMaxEntries()
    {
        // clientQueueMaxEntries = clientEntryImplPeer.get_clientQueueMaxEntries();
        return clientQueueMaxEntries;
    }

    /**
     * Implements the snmp set command for the mib object clientQueueMaxEntries.
     *
     * @param AgentXSetPhase 
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to clientEntryImplPeer.get_clientQueueThreshold().
     * 
     * @return ClientQueueThreshold threshold (%) number of messages in the
     * point to point client queue.
     */
    public long get_clientQueueThreshold()
    {
        // clientQueueThreshold = clientEntryImplPeer.get_clientQueueThreshold();
        return clientQueueThreshold;
    }

    /**
     * Implements the snmp set command for the mib object clientQueueThreshold.
     *
     * @param AgentXSetPhase
     * @param Value to be set
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to clientEntryImplPeer.get_clearClientQueue().
     * 
     * @return ClearClientQueue for values > 0, the point to point client queue is emptied.
     */
    public int get_clearClientQueue()
    {
        // clearClientQueue = clientEntryImplPeer.get_clearClientQueue();
        return clearClientQueue;
    }

    /**
     * Implements the snmp set command for the mib object clearClientQueue.
     *
     * @param AgentXSetPhase
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to clientEntryImplPeer.get_numSessions().
     * @return NumSessions actual number of client sessions in the session table.
     */
    public long get_numSessions()
    {
        // numSessions = clientEntryImplPeer.get_numSessions();
        return numSessions;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_maxSessions().
     * 
     * @return MaxSessions maximum number of client sessions in the session table.
     */
    public long get_maxSessions()
    {
        // maxSessions = clientEntryImplPeer.get_maxSessions();
        return maxSessions;
    }

    /**
     * Implements the snmp set command for the mib object maxSessions.
     *
     * @param AgentXSetPhase
     * @param Value to be set
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to clientEntryImplPeer.get_sessionThreshold().
     * @return SessionThreshold threshold (%) number of client sessions in the session table.
     */
    public long get_sessionThreshold()
    {
        // sessionThreshold = clientEntryImplPeer.get_sessionThreshold();
        return sessionThreshold;
    }

    /**
     * Implements the snmp set command for the mib object sessionThreshold.
     *
     * @param AgentXSetPhase
     * @param Value to be set.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to clientEntryImplPeer.get_clientUptime().
     * @return ClientUptime client connection uptime.
     */
    public long get_clientUptime()
    {
        // clientUptime = clientEntryImplPeer.get_clientUptime();
        return clientUptime;
    }

    /**
     * Forwards the call to clientEntryImplPeer.get_clientDowntime().
     * @return ClientDowntime client connection downtime.
     */
    public long get_clientDowntime()
    {
        // clientDowntime = clientEntryImplPeer.get_clientDowntime();
        return clientDowntime;
    }

}














