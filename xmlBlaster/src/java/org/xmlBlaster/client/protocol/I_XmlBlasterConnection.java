/*------------------------------------------------------------------------------
Name:      I_XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;


/**
 * This is the client interface to xmlBlaster.
 * <p />
 * All protocol drivers are accessed through these methods.
 * We need it to decouple the protocol specific stuff
 * (like RemoteException from RMI or CORBA exceptions) from
 * our java client code.
 * <p />
 * Note that you don't need this code, you can access xmlBlaster
 * with your own lowlevel RMI or CORBA coding as well.
 * <p />
 * If you are interested in a fail save client connection, consider
 * using XmlBlasterConnection.java which implements some nice features.
 *
 * @see org.xmlBlaster.client.protocol.XmlBlasterConnection
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_XmlBlasterConnection
{
   /**
    * connect() is a login or authentication as well, the authentication schema
    * is transported in the qos.
    * It is more general then the login() method, since it allows
    * to transport any authentication info in the xml based qos.
    *
    * You can still use login() for simple name/password based authentication.
    *
    * @param qos The authentication and other informations
    * @param client A handle to your callback if desired or null
    * @return ConnectReturnQos
    */
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException;

   /**
    * Logout from xmlBlaster. 
    * @param qos The QoS or null
    */
   public boolean disconnect(DisconnectQos qos) throws XmlBlasterException;

   // Could make sense to the SOCKET driver, returns new SocketCallbackImpl
   //public I_CallbackServer getCbServerInstance() throws XmlBlasterException;

   /**
    * @return The connection protocol name "IOR" or "RMI" etc.
    */
   public String getProtocol();

   /**
    * Try to login to xmlBlaster. 
    */
   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException;

   /**
    * Is invoked when we poll for the server, for example after we have lost the connection. 
    */
   public ConnectReturnQos loginRaw() throws XmlBlasterException;

   /*
    * @deprecated Use disconnect() instead
    */
   //public boolean logout();

   public boolean shutdown();

   /** Reset the driver on problems */
   public void resetConnection();

   public String getLoginName();

   public boolean isLoggedIn();

   public String ping(String qos) throws XmlBlasterException;

   //public static void usage();

   public java.lang.String subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public org.xmlBlaster.util.MsgUnitRaw[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public String[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   /**
    * @param The msgUnit which is encrypted if a security plugin is activated
    */
   public String publish(org.xmlBlaster.util.MsgUnitRaw msgUnit) throws XmlBlasterException;

   public void publishOneway(org.xmlBlaster.util.MsgUnitRaw [] msgUnitArr) throws XmlBlasterException;

   public String[] publishArr(org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) throws XmlBlasterException;

   public java.lang.String[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
}

