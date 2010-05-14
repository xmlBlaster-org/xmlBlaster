/*------------------------------------------------------------------------------
Name:      SessionQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.property.PropInt;
import org.xmlBlaster.util.property.PropBoolean;


/**
 * This class encapsulates the qos of session attributes of a login() or connect(). 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
 * @see org.xmlBlaster.util.qos.ConnectQosSaxFactory
 * @see org.xmlBlaster.test.classtest.ConnectQosTest
 */
public final class SessionQos implements java.io.Serializable, Cloneable
{
   private static final long serialVersionUID = 1L;
   private final transient Global glob;
   private static Logger log = Logger.getLogger(SessionQos.class.getName());

   /** Default session span of life is one day, given in millis "-session.timeout 86400000" */
   private PropLong sessionTimeout = new PropLong(Constants.DAY_IN_MILLIS);

   /** Maximum of ten parallel logins for the same client "session.maxSessions 10" */
   public static final int DEFAULT_maxSessions = 10;
   private PropInt maxSessions = new PropInt(DEFAULT_maxSessions);

   /** Clear on login all other sessions of this user (for recovery reasons) "session.clearSessions false" */
   private PropBoolean clearSessions = new PropBoolean(false);

   /**
    * If reconnectSameClientOnly=true a client with a given publicSessionId
    * can only reconnect to xmlBlaster if it is the the client instance which
    * created the login session (other clients can't capture the session).
    * This option is useful if you want to assure a singleton client (set additionally
    * maxSessions=1).
    * Defaults to false.
    */
   protected PropBoolean reconnectSameClientOnly = new PropBoolean(false);

   /** Passing own secret sessionId */
   private String sessionId = null;

   /** The unified session name which is a clusterwide unique identifier
   */
   private SessionName sessionName;
   private boolean sessionNameModified = false;

   /** The node id to which we want to connect */
   private NodeId nodeId = null;

   /**
    * Constructor for client side. 
    */
   public SessionQos(Global glob) {
      this(glob, null);
   }
   
   /**
    * Constructor for cluster server. 
    * @param nodeId The the unique cluster node id, supports configuration per node
    */
   public SessionQos(Global glob, NodeId nodeId) {
      this.glob = (glob == null) ? Global.instance() : glob;

      this.nodeId = nodeId;
      initialize();
   }

   private final void initialize() {

      // login name: As default use the JVM System.property ${user.name} which is usually the login name of the OS
      String defaultLoginName = glob.getProperty().get("user.name", "guest");

      String sessionNameStr = glob.getProperty().get("session.name", (String)null);
      if (sessionNameStr == null) {
          String loginName = glob.getProperty().get("loginName", (String)null);
          if (loginName != null) {
        	  sessionNameStr = loginName;
          }
          else {
        	  sessionNameStr = defaultLoginName;
          }
      }
      setSessionTimeout(glob.getProperty().get("session.timeout", Constants.DAY_IN_MILLIS)); // One day
      setMaxSessions(glob.getProperty().get("session.maxSessions", DEFAULT_maxSessions));
      clearSessions(glob.getProperty().get("session.clearSessions", false));
      setReconnectSameClientOnly(glob.getProperty().get("session.reconnectSameClientOnly", false));
      setSecretSessionId(glob.getProperty().get("session.secretSessionId", (String)null));
      if (nodeId != null) {
         sessionNameStr = glob.getProperty().get("session.name["+nodeId+"]", sessionNameStr);
         setSessionTimeout(glob.getProperty().get("session.timeout["+nodeId+"]", getSessionTimeout()));
         setMaxSessions(glob.getProperty().get("session.maxSessions["+nodeId+"]", getMaxSessions()));
         clearSessions(glob.getProperty().get("session.clearSessions["+nodeId+"]", clearSessions()));
         setReconnectSameClientOnly(glob.getProperty().get("session.reconnectSameClientOnly["+nodeId+"]", reconnectSameClientOnly()));
         setSecretSessionId(glob.getProperty().get("session.secretSessionId["+nodeId+"]", getSecretSessionId()));
      }

      this.sessionName = new SessionName(glob, nodeId, sessionNameStr);
      //if (log.isLoggable(Level.FINE)) log.trace(ME, "sessionName =" + sessionName.getRelativeName() + " absolute=" + sessionName.getAbsoluteName());

      {
         // user warning for the old style loginName
         String loginName = glob.getProperty().get("loginName", (String)null);
         if (loginName != null && !loginName.equals(sessionNameStr))
            log.warning("session.name=" + this.sessionName + " is stronger than loginName=" + loginName + ", we proceed with " + this.sessionName);
      }

      if (log.isLoggable(Level.FINE)) log.fine("initialize session.name=" + this.sessionName + " nodeId=" + nodeId);
   }

   /**
    * Timeout until session expires if no communication happens
    */
   public final long getSessionTimeout() {
      return this.sessionTimeout.getValue();
   }

   /**
    * Timeout until session expires if no communication happens
    * @param timeout The login session will be destroyed after given milliseconds.<br />
    *                Session lasts forever if set to 0L
    */
   public final void setSessionTimeout(long timeout) {
      if (timeout < 0L)
         this.sessionTimeout.setValue(0L);
      else
         this.sessionTimeout.setValue(timeout);
   }

   /**
    * If maxSession == 1, only a single login is possible
    */
   public final int getMaxSessions() {
      return this.maxSessions.getValue();
   }

   /**
    * If maxSession == 1, only a single login is possible
    * @param max How often the same client may login
    */
   public final void setMaxSessions(int max) {
      if (max < 0)
         this.maxSessions.setValue(0);
      else
         this.maxSessions.setValue(max);
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    */
   public final boolean clearSessions() {
      return this.clearSessions.getValue();
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    * @param clear Defaults to false
    */
   public void clearSessions(boolean clear) {
      this.clearSessions.setValue(clear);
   }

   /**
    * @param Set if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public void setReconnectSameClientOnly(boolean reconnectSameClientOnly) {
      this.reconnectSameClientOnly.setValue(reconnectSameClientOnly);
   }

   /**
    * @return true if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public boolean reconnectSameClientOnly() {
      return this.reconnectSameClientOnly.getValue();
   }

   /**
    */
   public PropBoolean reconnectSameClientOnlyProp() {
      return this.reconnectSameClientOnly;
   }

   /**
    * Set our session identifier which authenticates us for xmlBlaster. 
    * <p />
    * This is used server side only.
    * @param id The unique and secret sessionId
    */
   public void setSecretSessionId(String id) {
      if(id==null || id.equals("")) id = null;
      this.sessionId = id;
   }

   /**
    * Get our secret session identifier which authenticates us for xmlBlaster. 
    * <p />
    * @return The unique, secret sessionId
    */
   public final String getSecretSessionId() {
      return this.sessionId;
   }

   /**
    * The public session ID to support reconnect to an existing session. 
    * <p>
    * This is extracted from the sessionName.getPublicSessionId()
    * </p>
    * @return 0 if no session but a login name<br />
    *        <0 if session ID is generated by xmlBlaster<br />
    *        >0 if session ID is given by user
    */
   public final long getPublicSessionId() {
      if (this.sessionName != null) {
         return this.sessionName.getPublicSessionId();
      }
      return 0L;
   }

   public final boolean hasPublicSessionId() {
      if (this.sessionName != null) {
         return this.sessionName.isSession();
      }
      return false;
   }

   /**
    * Set our unique SessionName. 
    * @param sessionName
    */
   public void setSessionName(SessionName sessionName) {
      this.sessionName = sessionName;
      this.sessionNameModified = true;
   }

   /**
    * Set our unique SessionName. 
    * @param sessionName
    * @param markAsModified false if you are setting a default sessionName, true if the user set the sessionName
    */
   void setSessionName(SessionName sessionName, boolean markAsModified) {
      this.sessionName = sessionName;
      if (markAsModified) {
         this.sessionNameModified = markAsModified;
      }
   }

   public boolean isSessionNameModified() {
      return sessionNameModified;
   }

   /**
    * Get our unique SessionName. 
    * <p />
    * @return The unique SessionName (null if not known)
    */
   public final SessionName getSessionName() {
      return this.sessionName;
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return toXml();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the SessionQos as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(250);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<session");
      if (getSessionName() != null) {
         sb.append(" name='").append(getSessionName().getAbsoluteName()).append("'");
      }
      if (this.sessionTimeout.isModified()) {
         sb.append(" timeout='").append(getSessionTimeout()).append("'");
      }
      if (this.maxSessions.isModified()) {
         sb.append(" maxSessions='").append(getMaxSessions()).append("'");
      }
      if (this.clearSessions.isModified()) {
         sb.append(" clearSessions='").append(clearSessions()).append("'");
      }
      if (this.reconnectSameClientOnly.isModified()) {
         sb.append(" reconnectSameClientOnly='").append(reconnectSameClientOnly()).append("'");
      }
      if (this.sessionId!=null)
         sb.append(" sessionId='").append(this.sessionId).append("'");
      sb.append("/>");

      return sb.toString();
   }

   /**
    * Returns a shallow clone, you can change safely all basic or immutable types
    * like boolean, String, int.
    */
   public Object clone() {
      try {
         log.severe("clone() is not tested");
         SessionQos newOne = null;
         newOne = (SessionQos)super.clone();
         synchronized(this) {
            newOne.sessionTimeout = (PropLong)this.sessionTimeout.clone();
            newOne.maxSessions = (PropInt)this.maxSessions.clone();
            newOne.clearSessions = (PropBoolean)this.clearSessions.clone();
            newOne.reconnectSameClientOnly = (PropBoolean)this.reconnectSameClientOnly.clone();
            //newOne.sessionName = (SessionName)this.sessionName.clone();
            //newOne.nodeId = (NodeId)this.nodeId.clone();
         }
         return newOne;
      }
      catch (java.lang.CloneNotSupportedException e) {
         log.severe(e.toString());
         return null;
      }
   }

   /**
    * Get a usage string for the connection parameters
    */
   public String usage() {
      String text = "\n";
      text += "Control my session settings\n";
      text += "   -session.name       The name for login, e.g. 'joe' or with public session ID 'joe/2' []\n";
      text += "   -session.timeout    How long lasts our login session in milliseconds, 0 is forever,\n";
      text += "                       defaults to one day [" + Constants.DAY_IN_MILLIS + "].\n";
      text += "   -session.maxSessions\n";
      text += "                       Maximum number of simultanous logins per client [" + DEFAULT_maxSessions + "].\n";
      text += "   -session.clearSessions\n";
      text += "                       Kill other sessions running under my login name [false]\n";
      text += "   -session.reconnectSameClientOnly\n";
      text += "                       Only creator client may reconnect to session [false]\n";
      text += "   -session.secretSessionId\n";
      text += "                       The secret sessionId []\n";
      text += "\n";
      return text;
   }
}
