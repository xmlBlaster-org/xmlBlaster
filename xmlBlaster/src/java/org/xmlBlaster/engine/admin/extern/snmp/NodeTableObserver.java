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
      private NodeEntry nodeEntry;
      private NodeEntryImpl nodeEntryImpl;
      private NodeTable nodeTable;
      private Hashtable nodeHashtable;
      private BitSet indexSet;
      private final static int MAXINDX = 100;

      public NodeTableObserver( NodeTableSubject nodeTableSubject,
                                AgentXSession session ) {
            this.nodeTableSubject = nodeTableSubject;               
            this.session = session;
            nodeTableSubject.addObserver( this );
            nodeTable = new NodeTable();
  	    session.addGroup(nodeTable);
            nodeHashtable = new Hashtable();
            indexSet = new BitSet();
            for (int i = 1; i <= MAXINDX; i++) {
		indexSet.set(i);
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
            if( o == nodeTableSubject ) {
		  String nodeName = nodeTableSubject.nodeEntryImplPeer.get_nodeName();
                  if (nodeHashtable.containsKey(nodeName)) {
                     System.out.println("A node with nodename = " + nodeName + " already exists.");
                  }
                  else {
                      System.out.println("Insert node = " + nodeName);
		      // new nodeIndex
                      int nodeIndex = 1;
                      while (nodeIndex <= MAXINDX && !indexSet.get(nodeIndex)) {
			  nodeIndex++;
                      }

                      if (nodeIndex > MAXINDX) {
                         System.out.println("Error: nodeIndex > " + MAXINDX);
                      }
                      else {
			  // remove nodeIndex from indexSet
                          indexSet.clear(nodeIndex);

                          // insert nodeName and nodeIndex in nodeHashtable
                          nodeHashtable.put(nodeName, new Integer(nodeIndex));
  
                          // insert new nodeEntry in nodeTable
                          nodeEntry = new NodeEntryImpl(nodeIndex, nodeTableSubject.nodeEntryImplPeer);
                          nodeTable.addEntry(nodeEntry);
                      }
                  }
            }
      }
}








