/**
  NodeTableObserver adds itself to the integer bag as observer. 
  When NodeTableObserver receives an update, it .... 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

public class NodeTableObserver implements Observer {

      private NodeTableSubject nodeTableSubject;
      private AgentXSession session;
      private NodeEntryImpl nodeEntryImpl;
      private NodeTable nodeTable;
      private Hashtable nodeHashtable;
      private BitSet indexSet;
      private Hashtable refCounts;
      private final static int MAXINDX = 100;

      public NodeTableObserver( NodeTableSubject nodeTableSubject,
                                AgentXSession session ) {
            this.nodeTableSubject = nodeTableSubject;               
            this.session = session;
            nodeTableSubject.addObserver( this );
            nodeTable = new NodeTable();
  	    session.addGroup(nodeTable);
            nodeHashtable = new Hashtable();
	    refCounts = new Hashtable();
            indexSet = new BitSet();
            for (int i = 1; i <= MAXINDX; i++) {
		indexSet.set(i);
            }
      }

      public int increment(Integer nodeIndex) {
	 // find nodeName with nodeIndex
	 Enumeration e = nodeHashtable.keys();
         String nodeName = (String)e.nextElement();
         while ((nodeHashtable.get(nodeName) != nodeIndex) && e.hasMoreElements()) {
	     nodeName = (String)e.nextElement();
         }

         if (nodeHashtable.get(nodeName) != nodeIndex) {
	     return -1;
         }

	 // increment refsCount of nodeName  
         Integer rc = (Integer)refCounts.get(nodeName);
         if (rc != null) {
            refCounts.put(nodeName, new Integer(rc.intValue() + 1));
            System.out.println("increment, " + nodeName + ", " + ((Integer)refCounts.get(nodeName)).intValue());
            return rc.intValue() + 1;
         }
         else {
            refCounts.put(nodeName, new Integer(1));
            System.out.println("increment, " + nodeName + ", 1");
            return 1;
         }
      } 

      public int decrement(Integer nodeIndex) {
	 // find nodeName with nodeIndex
	 Enumeration e = nodeHashtable.keys();
         String nodeName = (String)e.nextElement();
         while ((nodeHashtable.get(nodeName) != nodeIndex) && e.hasMoreElements()) {
	     nodeName = (String)e.nextElement();
         }

         if (nodeHashtable.get(nodeName) != nodeIndex) {
	     return -1;
         }

         Integer rc = (Integer)refCounts.get(nodeName);
         if (rc == null) {
	     return -1;
         }

         if (rc.intValue() > 1) {
            refCounts.put(nodeName, new Integer(rc.intValue() - 1));
            return rc.intValue() - 1;
	 }
         else {
	    refCounts.remove(nodeName);
            return 0;
	 }
      } 

      public void sendTrap(AgentXSession session) {
         ClientTableThresholdOverflow clientTableNotify;
         long numClients;
         long maxClients;
         long clientThreshold;
         for (Enumeration nt=nodeTable.elements(); nt.hasMoreElements();) {
            System.out.println("nodeEntry +++ nodeEntry +++ nodeEntry");
            nodeEntryImpl = (NodeEntryImpl)nt.nextElement();
            numClients = nodeEntryImpl.get_numClients();
            maxClients = nodeEntryImpl.get_maxClients();
            clientThreshold = nodeEntryImpl.get_clientThreshold();
            System.out.println("maxClients: " + maxClients + 
                               ", clientThreshold: " + clientThreshold + 
                               ", numClients: " + numClients);
            if (maxClients * clientThreshold < numClients) {
               try {
                  clientTableNotify = new ClientTableThresholdOverflow(nodeEntryImpl, nodeEntryImpl, 
                                      nodeEntryImpl, nodeEntryImpl);
	          session.notify(clientTableNotify);
               } catch (Exception e) {
	          System.err.println(e);
	       }
            }
         }
      }

      public Integer getIndex(String key) {
          return (Integer)nodeHashtable.get(key);
      }

      public void update( Subject o ) {
	  switch(nodeTableSubject.opCode) {
	  case NodeTableSubject.INSERT:   
            if( o == nodeTableSubject ) {
		  String nodeName = nodeTableSubject.nodeEntryImplPeer.get_nodeName();
                  System.out.println("Insert a node with nodename = " + nodeName);
                  if (nodeHashtable.containsKey(nodeName)) {
                     System.out.println("A node with nodename = " + nodeName + " already exists.");
                  }
                  else {
                      System.out.println("Insert node = " + nodeName);
		      // new nodeIndex
                      int insInd = 1;
                      while (insInd <= MAXINDX && !indexSet.get(insInd)) {
			  insInd++;
                      }

                      if (insInd > MAXINDX) {
                         System.out.println("Error: insInd > " + MAXINDX);
                      }
                      else {
			  // remove insInd from indexSet
                          indexSet.clear(insInd);

                          // insert nodeName and insInd in nodeHashtable
                          nodeHashtable.put(nodeName, new Integer(insInd));
  
                          // insert new nodeEntry in nodeTable
                          nodeEntryImpl = new NodeEntryImpl(insInd, nodeTableSubject.nodeEntryImplPeer);
                          nodeTable.addEntry(nodeEntryImpl);
                      }
                  }
            }
            break;
         
	  case NodeTableSubject.REMOVE:
            if( o == nodeTableSubject ) {
		  String nodeName = nodeTableSubject.nodeEntryImplPeer.get_nodeName();
                  System.out.println("Remove a node with nodename = " + nodeName);
                  if (!nodeHashtable.containsKey(nodeName)) {
                     System.out.println("A node with nodename = " + nodeName + " does not exists.");
                  }
                  else {
                      System.out.println("Remove node = " + nodeName);

                      // is node referenced by other table entries
                      if (refCounts.get(nodeName) != null) {
                          System.out.println("node " + nodeName + " is referenced by other table entries.");
			  break;
                      }

                      // remove node from nodeHashtable
                      int remInd = ((Integer)nodeHashtable.get(nodeName)).intValue();
                      nodeHashtable.remove(nodeName);

                      // remove node from nodeTable
                      for (Enumeration nt=nodeTable.elements(); nt.hasMoreElements();) {
                         nodeEntryImpl = (NodeEntryImpl)nt.nextElement();
                         long ind = nodeEntryImpl.nodeIndex;
                         if (ind == remInd) {
                             nodeTable.removeEntry(nodeEntryImpl);
			     break;
                         }
                      }

                      // insert index in indexSet
                      indexSet.set(remInd);
                  }
            }
	    break;

	  default:
            System.out.println("Unknown table operation code: " + nodeTableSubject.opCode);
            break;
          } // end switch   
      }
}









