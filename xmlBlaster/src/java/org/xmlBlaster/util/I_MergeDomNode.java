/*------------------------------------------------------------------------------
Name:      I_MergeDomNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to delegate the merging of a node into an existing DOM tree
Version:   $Id: I_MergeDomNode.java,v 1.4 2000/06/13 13:04:02 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


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
