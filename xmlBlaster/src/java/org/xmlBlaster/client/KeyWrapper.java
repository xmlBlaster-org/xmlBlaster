/*------------------------------------------------------------------------------
Name:      KeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: KeyWrapper.java,v 1.1 1999/12/14 23:18:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;


/**
 * This base class encapsulates XmlKey which you send to xmlBlaster. 
 * <p />
 * A typical minimal key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711'>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * see xmlBlaster/src/dtd/XmlKey.xml
 */
public class KeyWrapper
{
   private String ME = "KeyWrapper";
   protected String oid = "";


   /**
    * Constructor with unknown oid
    */
   public KeyWrapper()
   {
   }


   /**
    * Constructor with given oid
    */
   public KeyWrapper(String oid)
   {
      if (oid != null)
         this.oid = oid;
   }
}
