/*------------------------------------------------------------------------------
Name:      NodeScalarProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   xmlBlaster to SNMP proxy class
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;

/*
 * This Java file has been generated by smidump 0.3.1. It
 * is intended to be edited by the application programmer and
 * to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */

/**
 *  This class extends the Java AgentX (JAX) implementation of
 *  the scalar group nodeScalar defined in XMLBLASTER-MIB.
 *  NodeScalarProxy 
 *  - is the interface side of a bridge pattern.
 *  - contains a reference to the implementation side of the bridge pattern (= NodeScalarProxyPeer).
 *  - implements its methods by forwarding its calls to NodeScalarProxyPeer.
 *  
 * @version @VERSION@
 * @author Udo Thalmann
 * @since 0.79g
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.snmp.html">admin.snmp requirement</a>
 */
public class NodeScalarProxy extends NodeScalar
{
   private final String ME = "NodeScalarProxy";
   private static Logger log = Logger.getLogger(NodeScalarProxy.class.getName());

   /**
   * NodeScalarProxy
   * - builds a reference to NodeScalarProxyPeer, which implements NodeScalarProxy methods.
   */
   public NodeScalarProxy(Global glob)
   {
      super();

   }

   /**
   * get_numNodes
   * - forwards the call to nodeScalarImplPeer.get_numNodes().
   * 
   * @return long numNodes: actual number of nodes in nodeTable.
   */
   public long get_numNodes() {
      
      log.severe("DEBUG only: Entering get_numNodes(), returning ");
      return numNodes;
   }
}




