/**
  SessionTableObserver adds itself to the integer bag as observer. 
  When SessionTableObserver receives an update, it .... 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

public class SessionTableObserver implements Observer {

      private SessionTableSubject sessionTableSubject;
      private AgentXSession session;
      private SessionEntryImpl sessionEntryImpl;
      private SessionTable sessionTable;
      private Hashtable sessionHashtable;
      private BitSet indexSet;
      private final static int MAXINDX = 100;

      public SessionTableObserver( SessionTableSubject sessionTableSubject,
                                AgentXSession session ) {
            this.sessionTableSubject = sessionTableSubject;               
            this.session = session;
            sessionTableSubject.addObserver( this );
            sessionTable = new SessionTable();
  	    session.addGroup(sessionTable);
            sessionHashtable = new Hashtable();
            indexSet = new BitSet();
            for (int i = 1; i <= MAXINDX; i++) {
		indexSet.set(i);
            }
      }

      public void update( Subject o ) {
          String clientName;
          String nodeName;
          String sessionName;
	  switch(sessionTableSubject.opCode) {
	  case SessionTableSubject.INSERT:   
            if( o == sessionTableSubject ) {
		  sessionName = sessionTableSubject.sessionEntryImplPeer.get_sessionName();
		  nodeName = sessionTableSubject.nodeName;
		  clientName = sessionTableSubject.clientName;
                  if (sessionHashtable.containsKey(nodeName + clientName + sessionName)) {
                     System.out.println("A session to " + nodeName + clientName + sessionName + " already exists.");
                  }
                  else {
                      System.out.println("Insert session = " + nodeName + clientName + sessionName);
		      // new sessionIndex
                      int sessionIndex = 1;
                      while (sessionIndex <= MAXINDX && !indexSet.get(sessionIndex)) {
			  sessionIndex++;
                      }

                      if (sessionIndex > MAXINDX) {
                         System.out.println("Error: sessionIndex > " + MAXINDX);
                      }
                      else {
			  // remove sessionIndex from indexSet
                          indexSet.clear(sessionIndex);

                          // insert nodeName + clientName + sessionName and sessionIndex in sessionHashtable
                          sessionHashtable.put(nodeName + clientName + sessionName, new Integer(sessionIndex));
  
                          // insert new sessionEntry in sessionTable
                          sessionEntryImpl = new SessionEntryImpl(sessionTableSubject.nodeIndex.intValue(),
								  sessionTableSubject.clientIndex.intValue(),
								  sessionIndex, 
                                                                  sessionTableSubject.sessionEntryImplPeer);
                          sessionTable.addEntry(sessionEntryImpl);

                          // increment client table reference count
                          sessionTableSubject.clientTableObserver.increment(sessionTableSubject.clientIndex);
                      }
                  }
            }
            break;
         
	  case SessionTableSubject.REMOVE:
            if( o == sessionTableSubject ) {
                  System.out.println("Remove a session table entry.");
		  sessionName = sessionTableSubject.sessionEntryImplPeer.get_sessionName();
		  nodeName = sessionTableSubject.nodeName;
		  clientName = sessionTableSubject.clientName;
                  if (!sessionHashtable.containsKey(nodeName + clientName + sessionName)) {
                     System.out.println("A session to " + nodeName + clientName + sessionName + " does not exist.");
                  }
                  else {
                      System.out.println("Remove session = " + nodeName + clientName + sessionName);
                      // remove node from sessionHashtable
                      int remInd = ((Integer)sessionHashtable.get(nodeName + clientName + sessionName)).intValue();
                      sessionHashtable.remove(nodeName + clientName + sessionName);

                      // remove session from sessionTable
                      for (Enumeration st=sessionTable.elements(); st.hasMoreElements();) {
                         sessionEntryImpl = (SessionEntryImpl)st.nextElement();
                         long nodeIndex = sessionEntryImpl.nodeIndex;
                         long clientIndex = sessionEntryImpl.clientIndex;
                         long sessionIndex = sessionEntryImpl.sessionIndex;
                         if ((sessionIndex == remInd) &&
                             (clientIndex == sessionTableSubject.nodeIndex.intValue()) && 
                             (nodeIndex == sessionTableSubject.nodeIndex.intValue())) {
                             sessionTable.removeEntry(sessionEntryImpl);
			     break;
                         }
                      }
                  } // end else
            }
	    break;

	  default:
            System.out.println("Unknown table operation code: " + sessionTableSubject.opCode);
            break;
          } // end switch   
      }

}











