/*
 * This Java file has been generated by smidump 0.3.1. It
 * is intended to be edited by the application programmer and
 * to be used within a Java AgentX sub-agent environment.
 *
 * $Id: SessionEntryImpl.java,v 1.6 2003/03/25 07:48:19 ruff Exp $
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;
import jax.AgentXEntry;

/**
 *  This class extends the Java AgentX (JAX) implementation of
 *  the table row sessionEntry defined in XMLBLASTER-MIB.
 *  SessionEntryImpl is the interface side of a bridge pattern.
 *  Contains a reference to the implementation side of the bridge pattern (= SessionEntryImplPeer).
 *  Implements its methods by forwarding its calls to SessionEntryImplPeer.
 *  
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class SessionEntryImpl extends SessionEntry
{

    public SessionEntryImplPeer sessionEntryImplPeer;

    /**
     * SessionEntryImpl initializes mib variables.
     * Builds a reference to SessionEntryImplPeer, which implements SessionEntryImpl methods.
     * @param NodeIndex identifies a node in node table.
     * @param ClientIndex identifies a client in client table together with nodeIndex.
     * @param SessionIndex identfies a session in session table together with nodeIndex and clientIndex.
     * @param SessionEntryImplPeer implements SessionEntryImpl methods.
     */
    public SessionEntryImpl(long nodeIndex,
                            long clientIndex,
                            long sessionIndex,
                            SessionEntryImplPeer sessionEntryImplPeer)
    {
        super(nodeIndex, clientIndex, sessionIndex);

        sessionName = sessionEntryImplPeer.get_sessionName().getBytes();
        cbQueueMaxEntries = sessionEntryImplPeer.get_cbQueueMaxEntries();
        cbQueueThreshold = sessionEntryImplPeer.get_cbQueueThreshold();
        clearCbQueue = get_clearCbQueue();
        closeSession = sessionEntryImplPeer.get_closeSession();
        this.sessionEntryImplPeer = sessionEntryImplPeer;
    }

    /**
     * Forwards the call to sessionEntryImplPeer.get_sessionName().
     * @return SessionName name of a client session.
     */
    public byte[] get_sessionName()
    {
        // sessionName = sessionEntryImplPeer.get_sessionName();
        return sessionName;
    }

    /**
     * Forwards the call to sessionEntryImplPeer.get_cbQueueNumEntries().
     * @return CbQueueNumEntries actual number of messages in the callback queue.
     */
    public long get_cbQueueNumEntries()
    {
        // cbQueueNumEntries = sessionEntryImplPeer.get_cbQueueNumEntries();
        return cbQueueNumEntries;
    }

    /**
     * Forwards the call to sessionEntryImplPeer.get_cbQueueMaxEntries().
     * @return CbQueueMaxEntries maximum number of messages in the callback queue.
     */
    public long get_cbQueueMaxEntries()
    {
        // cbQueueMaxEntries = sessionEntryImplPeer.get_cbQueueMaxEntries();
        return cbQueueMaxEntries;
    }

    /**
     * Implements the snmp set command for the mib object cbQueueMaxEntries.
     * @param AgentXSetPhase
     * @param Value is the new value of cbQueueMaxEntries.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to sessionEntryImplPeer.get_cbQueueThreshold().
     * @return CbQueueThreshold threshold (%) number of messages in the callback queue.
     */
    public long get_cbQueueThreshold()
    {
        // cbQueueThreshold = sessionEntryImplPeer.get_cbQueueThreshold();
        return cbQueueThreshold;
    }

    /**
     * Implements the snmp set command for the mib object cbQueueThreshold.
      * @param AgentXSetPhase phase:
     * @param Value is the new value for cbQueueThreshold.
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Indicates the callback queue status.
     * = 0: the callback queue is not empty.
     * > 0: the callback queue is empty.
     * @return ClearCbQueue indicates whether the callback is empty (> 0) or not (= 0).
     */
    public int get_clearCbQueue()
    {
        if (get_cbQueueNumEntries() > 0) {
            clearCbQueue = 0;
        }
        else {
            clearCbQueue = 1;
        }
        return clearCbQueue;
    }

    /**
     * Implements the snmp set command for the mib object clearCbQueue.
     * @param AgentXSetPhase
     * @param Value indicates whether the callback queue has to emptied (> 0) or not (= 0).
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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

    /**
     * Forwards the call to sessionEntryImplPeer.get_closeSession().
     * Indicates the session status.
     * = 0: the session is open.
     * > 0: the session is closed.
     * @return CloseSession indicates whether the session is open (= 0) or closed (> 1).
     */
    public int get_closeSession()
    {
        // closeSession = sessionEntryImplPeer.get_closeSession();
        return closeSession;
    }

    /**
     * Implements the snmp set command for the mib object closeSession.
     * @param AgentXSetPhase
     * @param Value indicates whether the session is open (= 0) or closed (> 1).
     * @return AgentXResponsePDU.PROCESSING_ERROR
     */
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










