package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

/**
 * ClientTableObserver represents the observer side of an observer pattern. 
 * When ClientTableObserver receives an update notification from ClientTableSubject, 
 * it adds or removes an entry to/from clientTable. 
 *  
 * @version @VERSION@
 * @author Udo Thalmann
 */
public class ClientTableObserver implements Observer {

    private ClientTableSubject clientTableSubject;
    private AgentXSession session;
    private ClientEntryImpl clientEntryImpl;
    private ClientTable clientTable;
    private Hashtable clientHashtable;
    private BitSet indexSet;
    private Hashtable refCounts;
    private final static int MAXINDX = 100;

    /**
     * Adds itself to the clientTableSubject as observer.
     * Creates a new clientTable and adds it to the agentX session.
     * Creates a Hashtable for (client, index) entries.
     * Creates a BitSet for available indices.
     * Creates a Hashtable for (client, reference) entries,
     * where reference is a counter of referenced session entries.
     *
     * @param ClientTableSubject calls the update method.
     * @param AgentXSession between master agent and subagent.
     */
    public ClientTableObserver( ClientTableSubject clientTableSubject,
                                AgentXSession session ) {
        this.clientTableSubject = clientTableSubject;
        this.session = session;
        clientTableSubject.addObserver( this );
        clientTable = new ClientTable();
        session.addGroup(clientTable);
        clientHashtable = new Hashtable();
        refCounts = new Hashtable();
        indexSet = new BitSet();
        for (int i = 1; i <= MAXINDX; i++) {
            indexSet.set(i);
        }
    }

    /**
     * Increments the referenced session entries of this client.
     * @param ClientIndex identifies a client entry in the client table.
     * @return ReferenceCounter number of referenced session entries.
     * -1, if clientIndex identifies no client in the client table.
     */
    public int increment(Integer clientIndex) {
        // find clientName with clientIndex
        Enumeration e = clientHashtable.keys();
        String clientName = (String)e.nextElement();
        while ((clientHashtable.get(clientName) != clientIndex) && e.hasMoreElements()) {
            clientName = (String)e.nextElement();
        }

        if (clientHashtable.get(clientName) != clientIndex) {
            return -1;
        }

        // increment refsCount of clientName  
        Integer rc = (Integer)refCounts.get(clientName);
        if (rc != null) {
            refCounts.put(clientName, new Integer(rc.intValue() + 1));
            System.out.println("increment, " + clientName + ", " + ((Integer)refCounts.get(clientName)).intValue());
            return rc.intValue() + 1;
        }
        else {
            refCounts.put(clientName, new Integer(1));
            System.out.println("increment, " + clientName + ", 1");
            return 1;
        }
    }

    /**
     * Decrements the referenced session entries of this client.
     * 
     * @param Integer clientIndex: identifies a client entry in the client table.
     * @return ReferenceCounter number of referenced session entries.
     * -1, if clientIndex identifies no client in the client table.
     */
    public int decrement(Integer clientIndex) {
        // find clientName with clientIndex
        Enumeration e = clientHashtable.keys();
        String clientName = (String)e.nextElement();
        while ((clientHashtable.get(clientName) != clientIndex) && e.hasMoreElements()) {
            clientName = (String)e.nextElement();
        }

        if (clientHashtable.get(clientName) != clientIndex) {
            return -1;
        } 

        Integer rc = (Integer)refCounts.get(clientName);
        if (rc == null) {
            return -1;
        }

        if (rc.intValue() > 1) {
            refCounts.put(clientName, new Integer(rc.intValue() - 1));
            return rc.intValue() - 1;
        }
        else {
            refCounts.remove(clientName);
            return 0;
        }
    }

    /**
     * For each client table entry sendTrap
     * checks trap condition clientQueueMaxEntries * clientQueueThreshold < clientQueueNumEntries.
     * Sends a ClientQueueThresholdOverflow trap if the condition is fulfilled.
     * Checks trap condition maxSessions * sessionThreshold < numSessions.
     * Sends a SessionTableThresholdOverflow trap if the condition is fulfilled.
     * @param AgentXSession between master agent and subagent.
     */
    public void sendTrap(AgentXSession session) {
        ClientQueueThresholdOverflow clientQueueNotify;
        SessionTableThresholdOverflow sessionTableNotify;
        long clientQueueNumEntries;
        long clientQueueMaxEntries;
        long clientQueueThreshold;
        long numSessions;
        long maxSessions;
        long sessionThreshold;
        for (Enumeration ct=clientTable.elements(); ct.hasMoreElements();) {
            clientEntryImpl = (ClientEntryImpl)ct.nextElement();

            // clientQueueThresholdOverflow trap
            clientQueueNumEntries = clientEntryImpl.get_clientQueueNumEntries();
            clientQueueMaxEntries = clientEntryImpl.get_clientQueueMaxEntries();
            clientQueueThreshold = clientEntryImpl.get_clientQueueThreshold();
            System.out.println("clientQueueMaxEntries: " + clientQueueMaxEntries + 
                               ", clientQueueThreshold: " + clientQueueThreshold + 
                               ", clientQueueNumEntries: " + clientQueueNumEntries);
            if (clientQueueMaxEntries * clientQueueThreshold < clientQueueNumEntries) {
                try {
                    clientQueueNotify = new ClientQueueThresholdOverflow(clientEntryImpl, clientEntryImpl,
                                                                         clientEntryImpl, clientEntryImpl);
                    session.notify(clientQueueNotify);
                } catch (Exception e) {
                    System.err.println(e);
                }
            }

            // sessionTableThresholdOverflow trap
            numSessions = clientEntryImpl.get_numSessions();
            maxSessions = clientEntryImpl.get_maxSessions();
            sessionThreshold = clientEntryImpl.get_sessionThreshold();
            System.out.println("maxSessions: " + maxSessions + 
                               ", sessionThreshold: " + sessionThreshold +
                               ", numSessions: " + numSessions);
            if (maxSessions * sessionThreshold < numSessions) {
                try {
                    sessionTableNotify = new SessionTableThresholdOverflow(clientEntryImpl, clientEntryImpl,
                                                                           clientEntryImpl, clientEntryImpl);
                    session.notify(sessionTableNotify);
                } catch (Exception e) {
                    System.err.println(e);
                }
            }
         } // end for
    }

    /**
     * Returns an index to clientTable given a (nodeName + clientName)-key.
     * @param Key nodeName/clientName-key to clientHashtable.
     * @return Index to clientTable.
     */
    public Integer getIndex(String key) {
        return (Integer)clientHashtable.get(key);
    }

    /**
     * Adds or removes a client entry to/from the client table.
     * Updates client indexSet.
     * Updates clientHashtable.
     * Updates reference counter in nodeTableObserver.
     * @param ClientTableSubject which calls update.
     */
    public void update( Subject o ) {
        String clientName;
        String nodeName;
        switch(clientTableSubject.opCode) {
            case ClientTableSubject.INSERT:   
                if( o == clientTableSubject ) {
                    clientName = clientTableSubject.clientEntryImplPeer.get_clientName();
                    nodeName = clientTableSubject.nodeName;
                    if (clientHashtable.containsKey(nodeName + clientName)) {
                        System.out.println("A node/client  " + nodeName + clientName + " already exists.");
                    }
                    else {
                        System.out.println("Insert node/client " + nodeName + clientName);
                        // new clientIndex
                        int clientIndex = 1;
                        while (clientIndex <= MAXINDX && !indexSet.get(clientIndex)) {
                            clientIndex++;
                        }

                        if (clientIndex > MAXINDX) {
                            System.out.println("Error: clientIndex > " + MAXINDX);
                        }
                        else {
                            // remove clientIndex from indexSet
                            indexSet.clear(clientIndex);

                            // insert nodeName + clientName in clientHashtable
                            clientHashtable.put(nodeName + clientName, new Integer(clientIndex));
  
                            // insert new clientEntry in clientTable
                            clientEntryImpl = new ClientEntryImpl(clientTableSubject.nodeIndex.intValue(),
                                                                  clientIndex,
                                                                  clientTableSubject.clientEntryImplPeer);
                            clientTable.addEntry(clientEntryImpl);

                            // increment node table reference count
                            clientTableSubject.nodeTableObserver.increment(clientTableSubject.nodeIndex);
                        } // end else
                    } // end else
                } // end if
                break;
         
            case ClientTableSubject.REMOVE:
                if( o == clientTableSubject ) {
                    System.out.println("Remove a client table entry.");
                    clientName = clientTableSubject.clientEntryImplPeer.get_clientName();
                    nodeName = clientTableSubject.nodeName;
                    if (!clientHashtable.containsKey(nodeName + clientName)) {
                        System.out.println("A node/client " + nodeName + clientName + " does not exist.");
                    }
                    else {
                        System.out.println("Remove node/client " + nodeName + clientName);

                        // is client referenced by other session table entries
                        if (refCounts.get(nodeName + clientName) != null) {
                            System.out.println("node/client " + nodeName + clientName + " is referenced by ...");
                            break;
                        }

                        // remove node from clientHashtable
                        int remInd = ((Integer)clientHashtable.get(nodeName + clientName)).intValue();
                        clientHashtable.remove(nodeName + clientName);

                        // remove client from clientTable
                        for (Enumeration ct=clientTable.elements(); ct.hasMoreElements();) {
                            clientEntryImpl = (ClientEntryImpl)ct.nextElement();
                            long nodeIndex = clientEntryImpl.nodeIndex;
                            long clientIndex = clientEntryImpl.clientIndex;
                            if ((clientIndex == remInd) && (nodeIndex == clientTableSubject.nodeIndex.intValue())) {
                                clientTable.removeEntry(clientEntryImpl);
                          
                                // decrement node table reference count
                                clientTableSubject.nodeTableObserver.decrement(clientTableSubject.nodeIndex);
                                break;
                            } // end if
                        } // end for
                    } // end else
                } // end if
                break;

            default:
                System.out.println("Unknown table operation code: " + clientTableSubject.opCode);
                break;
        } // end switch   
    }
}















