/*------------------------------------------------------------------------------
Name:      I_PersistenceDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: I_PersistenceDriver.java,v 1.1 2000/01/20 19:34:07 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.XmlKey;


/**
 * This interface hides the real driver to manage the persistence of messages.
 * <p />
 * Interface for xmlBlaster to access a persistent store.
 * <br />
 * Implement this interface if you want your own persistence handling
 * and add your driver to the $HOME/xmlBlaster.properties configuration file.
 * <br />
 * FileDriver.java is a very simple reference implementation,
 * storing the messages to files.
 *
 * @version $Revision: 1.1 $
 * @author $Author: ruff $
 */
public interface I_PersistenceDriver
{
   /**
    * This method allows a message to be stored.
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void store(MessageUnitWrapper messageWrapper) throws XmlBlasterException;


   /**
    * This method allows a stored message content to be updated.
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void store(XmlKey xmlKey, byte[] content) throws XmlBlasterException;


   /**
    * This method allows a message to be stored.
    * <p />
    * The protocol for storing is implemented in the derived class
    */
   public void recover(RequestBroker requestBroker) throws XmlBlasterException;
}

