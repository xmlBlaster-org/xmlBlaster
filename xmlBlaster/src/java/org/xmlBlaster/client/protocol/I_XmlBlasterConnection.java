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
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.qos.address.Address;


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
 * If you are interested in a failsafe client connection, consider
 * using XmlBlasterAccess.java which implements some nice features.
 * <p>
 * The plugins which implement this interface do NOT need to be thread safe.
 * </p>
 *
 * @see org.xmlBlaster.client.XmlBlasterAccess
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_XmlBlasterConnection extends I_Plugin
{
   /**
    * Intialize the driver and verify if the remote side is reachable on the low-level protocol layer. 
    * Calling this method multiple times will do noting if a low level connection is available.
    * @param  address Contains the remote address,
    *         e.g. the host and port where the remote server listens
    * @exception XmlBlasterException ErrorCode.COMMUNICATION* if the server is not reachable,
    *            in this case we can poll for the server.<br />
    *            Other errors if for example a malformed address is passed, in this case we stop
    *            and give up.
    */
   public void connectLowlevel(Address address) throws XmlBlasterException;

   /**
    * connect() is a login or authentication as well, the authentication schema
    * is transported in the qos.
    * It is more general then the login() method, since it allows
    * to transport any authentication info in the xml based qos.
    *
    * You can still use login() for simple name/password based authentication.
    *
    * @param qos The authentication and other informations (ConnectQos encrypted)
    * @param client A handle to your callback if desired or null
    * @return ConnectReturnQos string
    */
   public String connect(String connectQos) throws XmlBlasterException;

   /**
    * Pass the driver the decrypted and parsed ConnectReturnQos directly after a connect. 
    * Some driver take the secretSessionId from it or a returned remote address
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) throws XmlBlasterException;

   /**
    * Logout from xmlBlaster. 
    * @param disconnectQos The QoS or null
    */
   public boolean disconnect(String disconnectQos) throws XmlBlasterException;

   // Could make sense to the SOCKET driver, returns new SocketCallbackImpl
   //public I_CallbackServer getCbServerInstance() throws XmlBlasterException;

   /**
    * @return The connection protocol name "IOR" or "RMI" etc.
    */
   public String getProtocol();

   /*
    * Is invoked when we poll for the server, for example after we have lost the connection. 
    * @return ConnectReturnQos string
    */
//   public String loginRaw() throws XmlBlasterException;

   /*
    * @deprecated Use disconnect() instead
    */
   //public boolean logout();

   public void shutdown() throws XmlBlasterException;

   /** 
    * Reset the driver on problems. 
    * This method is called by the dispatcher framework on transition to POLLING
    */
   public void resetConnection();

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

