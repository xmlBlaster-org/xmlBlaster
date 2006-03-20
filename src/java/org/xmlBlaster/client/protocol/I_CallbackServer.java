/*------------------------------------------------------------------------------
Name:      I_CallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for clients, used by xmlBlaster to send messages back
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.qos.address.CallbackAddress;


/**
 * This is the client callback interface to xmlBlaster.
 * <p />
 * All callback protocol drivers are accessed through these methods.
 * We need it to decouple the protocol specific stuff
 * (like RemoteException from RMI or CORBA exceptions) from
 * our java client code.
 * <p />
 * Note that you don't need this code, you can access xmlBlaster
 * with your own lowlevel RMI or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_XmlBlasterConnection
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_CallbackServer extends I_Plugin
{
   /**
    * Initialize and start the callback server. 
    * <p />
    * This is guaranteed to be invoked after the default constructor.
    * <p>
    * NOTE: The plugin needs to set callbackAddress.setRawAddress(addr) on success
    * which is for example "et@mars.universe" or "http://myserver:8080/xmlrpc"
    * </p>
    * @param glob The global handle with your environment settings
    * @param name The login name of the client, for logging only
    * @param callbackAddress The address configuration of this callback server
    * @param client Your implementation to receive the callback messages from xmlBlaster
    */
   public void initialize(Global glob, String name, CallbackAddress callbackAddress, I_CallbackExtended client) throws XmlBlasterException;

   /**
    * Returns the 'well known' protocol type. 
    * @return E.g. "RMI", "SOCKET", "XMLRPC"
    */
   public String getCbProtocol();
   
   /**
    * Returns the current callback address. 
    * @return "rmi://develop.MarcelRuff.info:1099/xmlBlasterCB", "127.128.2.1:7607", "http://XMLRPC"
    *         or null if not known
    */
   public String getCbAddress() throws XmlBlasterException;
   
   /**
    * Stop the server
    */
   public void shutdown() throws XmlBlasterException;
}

