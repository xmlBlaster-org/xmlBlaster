/*------------------------------------------------------------------------------
Name:      I_MergeDomNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to delegate the merging of a node into an existing DOM tree
Version:   $Id$
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
