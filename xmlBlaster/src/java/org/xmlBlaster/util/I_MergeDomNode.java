/*------------------------------------------------------------------------------
Name:      I_MergeDomNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to delegate the merging of a node into an existing DOM tree
Version:   $Id: I_MergeDomNode.java,v 1.2 1999/11/29 18:39:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.engine.RequestBroker;

import org.xml.sax.InputSource;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Attr;


/**
 * Interface to delegate / decouple the merging of a node into an existing DOM tree
 */
public interface I_MergeDomNode
{
   /**
    * @param the node to merge into the DOM tree
    */
   public org.w3c.dom.Node mergeNode(org.w3c.dom.Node newNode) throws XmlBlasterException;
}
