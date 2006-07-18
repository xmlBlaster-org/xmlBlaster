/*
        HtPasswd.java

        16/11/01 19:27 cyrille@ktaland.com

*/
package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.SessionHolder;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.* ;
import java.util.HashSet;
import java.util.Hashtable ;
import java.util.Enumeration ;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
//import org.xmlBlaster.authentication.plugins.htpasswd.jcrypt;

/*
 * In xmlBlaster.properties add :<br>
 * Security.Server.Plugin.htpasswd.secretfile=${user.home}${file.separator}xmlBlaster.htpasswd
 * <p />
 * Changes: astelzl@avitech.de<br />
 * There can be three cases for authentication:
 * <ol> 
 *   <li>in xmlBlaster.properties the property Security.Server.Plugin.htpasswd.allowPartialUsername is true ->
 *       the user is authenticated with the right password and an username which begins with the specified username
 *   </li>
 *   <li>allowPartialUsername is false ->
 *        the user is just authenticated if the username and password in the password file
 *        exactly equals the specifications at connection to the xmlBlaster
 *   </li>it is possible that the password file just contains a * instead
 *        of (username,password) tuples -> any username and password combination is authenticated
 *        Same if Security.Server.Plugin.htpasswd.secretfile=NONE
 *   </li>
 *  </ol>
 * <p />
 * NOTE: Currently the htpasswd file is reread every time a client logs in (see Session.java new HtPasswd())
 *       we should change this to check the timestamp of the file.
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a> 16/11/01 09:06
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.htpasswd.html">The security.htpasswd requirement</a>
 */
public class HtPasswd {

   private static final String ME = "HtPasswd";

   protected Global glob;
   private static Logger log = Logger.getLogger(HtPasswd.class.getName());

   protected final int ALLOW_PARTIAL_USERNAME = 1;
   protected final int FULL_USERNAME = 2;
   protected final int SWITCH_OFF = 3;

   protected int useFullUsername = ALLOW_PARTIAL_USERNAME;
   protected String htpasswdFilename = null ;
   /* Key is user name, values is the Container with encrypted password */
   protected Hashtable htpasswd = null ;

   private static boolean first = true;
   private static boolean firstWild = true;
   
   private class Container {
      public String userName="";
      public String password="";
      public Map allowedMethodNames; // contains MethodNames for authorization as key, set of topic oids as value
      public Container(String user) {
         this.userName = user;
      }
      public String toString() {
         String ret = "user='" + userName + "' passwd='" + password + "'";
         if (this.allowedMethodNames != null)
            ret += " allowedMethods=" + MethodName.toString((MethodName[])this.allowedMethodNames.keySet().toArray(new MethodName[this.allowedMethodNames.size()]));
         return ret;
      }
      public boolean isAllowed(MethodName methodName, String topicOid) {
         if (this.allowedMethodNames == null) return true;
         Set oidSet = (Set)this.allowedMethodNames.get(methodName);
         if (oidSet == null) return false;
         if (topicOid == null) return true;
         if (oidSet.size() == 0) return true;
         return oidSet.contains(topicOid);
      }
   }

   /**
    * Check password
    * 16/11/01 19:36 mad@ktaland.com
    */
   public HtPasswd(Global glob) throws XmlBlasterException {
      this.glob = glob;

      htpasswdFilename = glob.getProperty().get("Security.Server.Plugin.htpasswd.secretfile", "NONE" );
      boolean allowPartialUsername = glob.getProperty().get("Security.Server.Plugin.htpasswd.allowPartialUsername", false);
      if ( allowPartialUsername ) {
         useFullUsername = ALLOW_PARTIAL_USERNAME;
         if (log.isLoggable(Level.FINE)) log.fine("Login names are searched with 'startswith' mode in '" + htpasswdFilename + "'.");
      }
      else {
          useFullUsername = FULL_USERNAME;
          if (log.isLoggable(Level.FINE)) log.fine( "contructor(" + htpasswdFilename + ") allowPartialUsername=false" );
      }
      if (htpasswdFilename != null && htpasswdFilename.equals("NONE")) {
          useFullUsername = SWITCH_OFF;
          if (first) {
             log.warning("Security risk, no access control: The passwd file is switched off with 'Security.Server.Plugin.htpasswd.secretfile=NONE'");
             first = false;
          }
          return;
      }


      if (log.isLoggable(Level.FINE)) log.fine( "contructor(" + htpasswdFilename + ") " );

      if( readHtpasswordFile( htpasswdFilename ) ){
      }

   }//HtPassWd

   /**
    * Helper class for checkPassword in the case of startWith(username) ->
    * here more usernames of the hashtable can be right
    * @param userPassword password in plaintext
    * @param fileEncodedPass vector of passwords where usernames match with the specified beginning of an username
    * @return true if any one matches
    */
   private boolean checkDetailed(String userPassword, Vector fileEncodedPass)
   { 
     if (log.isLoggable(Level.FINE)) log.fine("Comparing '" + userPassword + "' in " + fileEncodedPass.size() + " possibilities");
     String encoded = null,salt,userEncoded;
     for (Enumeration e = fileEncodedPass.elements();e.hasMoreElements();)
     { encoded = (String)e.nextElement();
       if (encoded != null && encoded.length() == 0) return true; // empty password "joe::"
       if (encoded != null && encoded.length() > 2) 
       {  salt = encoded.substring(0,2);
          userEncoded = jcrypt.crypt(salt,userPassword);
          if (log.isLoggable(Level.FINE)) log.fine("Comparing '" + userEncoded + "' with passwd entry '" + encoded + "'");
          if ( userEncoded.trim().equals(encoded.trim()) ) 
            return true;     
       }
     }
     return false;
   }
     

   /**
    * Check password
    * @param password The clear text password
    * @return true The password is valid
    */
   public boolean checkPassword( String userName, String userPassword )
                throws XmlBlasterException {

      //if (log.TRACE && htpasswd!=null) log.trace(ME, "Checking userName=" + userName + " passwd='" + userPassword + "' in " + htpasswd.size() + " entries, mode=" + useFullUsername + " ...");
      if ( useFullUsername == SWITCH_OFF ) {
        return true;
      }
      if( this.htpasswd != null && userName!=null && userPassword!=null ){
         Vector pws = new Vector();

         //find user in Hashtable htpasswd
         String key;
         boolean found = false;
         if ( useFullUsername == FULL_USERNAME ) {
            Container container = (Container)this.htpasswd.get(userName);
            if (container != null) {
               pws.addElement(container.password);
               found = true;
            }
         }
         else { // ALLOW_PARTIAL_USERNAME
            for (Enumeration e = this.htpasswd.keys();e.hasMoreElements() ; ) {
               key = (String)e.nextElement();
               if (log.isLoggable(Level.FINE)) log.fine("Checking userName=" + userName + " with key='" + key + "'");
               if (userName.startsWith(key) || userName.endsWith(key)) {
                  Container container = (Container)this.htpasswd.get(key);
                  pws.addElement(container.password);
                  found = true;
               }
            }
         }

         if (!found) { // allow wildcard entry, for example "*:ad9dfjhf0"
            for (Enumeration e = this.htpasswd.keys();e.hasMoreElements() ; ) {
               key = (String)e.nextElement();
               if (key.equals("*")) {
                  Container container = (Container)this.htpasswd.get(key);
                  pws.addElement(container.password);
               }
            }
         }
         
         return checkDetailed(userPassword,pws);
      }
      return false;
   }//checkPassword

   /**
    * Check of MethodName is allowed to be invoked by user. 
    * 
    * @param sessionHolder The user
    * @param dataHolder The method called
    * @return true if is authorized, false if no access
    */
   public boolean isAuthorized(SessionHolder sessionHolder, DataHolder dataHolder) {
      if (this.htpasswd == null) return true;
      Container container = (Container)this.htpasswd.get(sessionHolder.getSessionInfo().getSessionName().getLoginName());
      if (container.allowedMethodNames == null) return true;
      return container.isAllowed(dataHolder.getAction(), dataHolder.getKeyOid());
   }

   /**
    * Read passwords file
    * 16/11/01 20:42 mad@ktaland.com
    * @param the password filename
    * @return true if file all readed & well formated
    */
   boolean readHtpasswordFile( String htpasswdFilename )
        throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer(htpasswdFilename);
      File            htpasswdFile ;

      if( htpasswdFilename == null)
      throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "missing property Security.Server.Plugin.htpasswd.secretfile" );

      htpasswdFile =new File(htpasswdFilename) ;

      if( ! htpasswdFile.exists() ) {
         log.severe( "Secret file doesn't exist : "+htpasswdFilename + ", please check your 'Security.Server.Plugin.htpasswd.secretfile' setting.");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "secret file doesn't exist : "+htpasswdFilename );
      }
      if( ! htpasswdFile.canRead() ) {
         log.severe( "Secret file '"+htpasswdFilename + "' has no read permission");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "no read access on file : "+htpasswdFilename );
      }

      try {
         String rawString = FileLocator.readAsciiFile(htpasswdFilename);
         java.util.Map map = org.xmlBlaster.util.StringPairTokenizer.parseToStringStringPairs(rawString, "\n", ":");
         java.util.Iterator it = map.keySet().iterator();
         while (it.hasNext()) {
            String user = (String)it.next();
            user = user.trim();
            if (user.startsWith("#" ) || user.length() < 1) {
               continue;
            }
            String tail = (String)map.get(user); // joe:secret:CONNECT,PUBLISH,ERASE:other stuff in future
            String[] tokens = StringPairTokenizer.parseLine(tail, ':', StringPairTokenizer.DEFAULT_QUOTE_CHARACTER, false);
            Container container = new Container(user);
            if (tokens.length > 0)
               container.password = tokens[0].trim();
            if (tokens.length > 1) {
               // parse "!SUBSCRIBE,ERASE" or "CONNECT,DISCONNECT,PUBLISH"
               // joe:079cv::  allows all methods
               String methodNames = tokens[1].trim();
               if (methodNames != null && methodNames.length() > 0) {
                  boolean positiveList = !methodNames.startsWith("!");
                  container.allowedMethodNames = new java.util.HashMap();
                  if (positiveList) {
                     String[] nameArr = org.xmlBlaster.util.StringPairTokenizer.parseLine(methodNames, ',');
                     for (int j=0; j<nameArr.length; j++) {
                        String name = nameArr[j].trim();
                        HashSet set = new HashSet();
                        int start = name.indexOf('(');
                        if (start != -1) {
                           int end = name.indexOf(')');
                           if (end != -1) {
                              String topics = name.substring(start+1, end);
                              String[] topicArr = org.xmlBlaster.util.StringPairTokenizer.parseLine(topics, ';');
                              for (int n=0; n<topicArr.length; n++)
                                 set.add(topicArr[n]);
                           }
                           name = name.substring(0, start);
                        }
                        try {
                           MethodName methodName = MethodName.toMethodName(name);
                           container.allowedMethodNames.put(methodName, set);
                        }
                        catch (IllegalArgumentException e) {
                           log.severe("Ignoring allowed method name, please check your configuration in '" + htpasswdFilename + "': " + e.toString());
                        }
                     }
                  }
                  else {
                     MethodName[] all = MethodName.getAll();
                     HashSet set = new HashSet();
                     for (int k=0; k<all.length; k++) container.allowedMethodNames.put(all[k], set);
                     String[] nameArr = org.xmlBlaster.util.StringPairTokenizer.parseLine(methodNames.substring(1), ',');
                     for (int j=0; j<nameArr.length; j++) {
                        try {
                           MethodName methodName = MethodName.toMethodName(nameArr[j].trim());
                           container.allowedMethodNames.remove(methodName);
                        }
                        catch (IllegalArgumentException e) {
                           log.severe("Ignoring allowed method name, please check your configuration in '" + htpasswdFilename + "': " + e.toString());
                        }
                     }
                     
                  }
               }
            }
            if (this.htpasswd == null) this.htpasswd = new Hashtable();
            this.htpasswd.put(user, container);
            if (user.equals("*") && container.password.length() < 1) {
               //This is the third case I mentioned above -> the password-file just contains a '*' -> all connection requests are authenticated
               useFullUsername = SWITCH_OFF;
               if (firstWild) {
                  log.warning("Security risk, no access control: '" + htpasswdFile + "' contains '*'");
                  firstWild = false;
               }
            }
         }


         // Dump it:
         if (log.isLoggable(Level.FINEST)) {
            if (this.htpasswd != null) {
               java.util.Iterator i = this.htpasswd.values().iterator();
               System.out.println("========================================");
               while (i.hasNext()) {
                  Container container = (Container)i.next();
                  System.out.println(container.toString());
               }
               System.out.println("========================================");
            }
            else {
               System.out.println("======NO PASSWD ENTRY==================================");
            }
         }

         return true;
      }
      catch(Exception ex) {
         this.htpasswd = null ;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problem when reading password file '"+htpasswdFilename+"'", ex);
      }
   }//readHtpasswordFile

   public String getPasswdFileName() {
      return htpasswdFilename; 
   }

}//class HtAccess
