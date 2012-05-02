/*
        HtPasswd.java

        16/11/01 19:27 cyrille@ktaland.com

*/
package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.plugins.DataHolder;
import org.xmlBlaster.authentication.plugins.SessionHolder;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.* ;
import java.util.HashSet;
import java.util.Hashtable ;
import java.util.Enumeration ;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
//import org.xmlBlaster.authentication.plugins.htpasswd.jcrypt;

/**
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
 * Changes: mr@marcelruff.info<br />
 * Added simple authorization support.
 * <p />
 * NOTE: Currently the htpasswd file is reread every time a client logs in (see Session.java new HtPasswd())
 *       we should change this to check the timestamp of the file.
 *       On client - reconnect there is no reload forced.
 * <p />
 * Switch on logging with: -logging/org.xmlBlaster.authentication.plugins.htpasswd FINEST
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a> 16/11/01 09:06
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/security.htpasswd.html">The security.htpasswd requirement</a>
 */
public class HtPasswd {

   private static final String ME = "HtPasswd";
   
   private static boolean firstRead = true;

   protected Global glob;
   private static Logger log = Logger.getLogger(HtPasswd.class.getName());

   protected final int ALLOW_PARTIAL_USERNAME = 1;
   protected final int FULL_USERNAME = 2;
   protected final int SWITCH_OFF = 3;

   protected int useFullUsername = ALLOW_PARTIAL_USERNAME;
   protected String htpasswdFilename = null ;
   /* Key is user name, values is the Container with encrypted password */
   protected Hashtable htpasswdMap = null ;
   
   protected Hashtable containerCache = new Hashtable();
   protected long lastModified = -1L;

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
      /**
       * 
       * @param methodName
       * @param topicOid "exact:weather.pacific"
       * @return
       */
      public boolean isAllowed(MethodName methodName, String topicOid) {
         if (this.allowedMethodNames == null) return true;
         Set oidSet = (Set)this.allowedMethodNames.get(methodName);
         if (oidSet == null) return false;
         if (topicOid == null) return true;
         if (oidSet.size() == 0) return true;
         boolean allowed = oidSet.contains(topicOid.trim());
         if (!allowed) { // && (methodName.equals(MethodName.PUBLISH) || methodName.equals(MethodName.PUBLISH_ONEWAY) || methodName.equals(MethodName.PUBLISH_ARR))) {
        	 Iterator<String> it = oidSet.iterator();
        	 while (it.hasNext()) {
				String token = it.next();
				if (token.startsWith("startsWith:")) {
					int len = "startsWith:".length();
					if (len < token.length()-1) {
						String oid = token.substring(len);
						if (topicOid.startsWith("exact:"+oid))
							return true;
					}
				}
				else if (token.startsWith("endsWith:")) {
					int len = "endsWith:".length();
					if (len < token.length()-1) {
						String oid = token.substring(len);
						if (topicOid.endsWith(oid))
							return true;
					}
				}
			}
         }
         return allowed;
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
     { 
       Container container = (Container)e.nextElement();
       encoded = container.password;
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
      if( this.htpasswdMap != null && userName!=null && userPassword!=null ){
         //find user in Hashtable htpasswd
         Vector pws = lookup(userName);
         return checkDetailed(userPassword,pws);
      }
      return false;
   }//checkPassword


   /**
    * Lookup userName in password file
    * @param userName
    * @return A list containing Container instances (matching the userName)
    * if a exact match is found, only this is returned
    */
   private Vector lookup(String userName) {
      Vector pws = new Vector();
      if (userName == null) return pws;
      //find user in Hashtable htpasswd
      String key;
      
	  // First check for exact match, if found no other matches are returned (since 2009-11-06)
	  Container container = (Container)this.htpasswdMap.get(userName);
	  if (container != null) {
	     pws.addElement(container);
	     return pws;
	  }

      boolean found = false;
	  if (useFullUsername != FULL_USERNAME ) { // ALLOW_PARTIAL_USERNAME or SWITCH_OFF: Return all matches because of authorization settings
         for (Enumeration e = this.htpasswdMap.keys();e.hasMoreElements() ; ) {
		   key = (String)e.nextElement();
		   if (log.isLoggable(Level.FINE)) log.fine("Checking userName=" + userName + " with key='" + key + "'");
		      if (userName.startsWith(key) || userName.endsWith(key)) {
		         container = (Container)this.htpasswdMap.get(key);
		         pws.addElement(container);
		         found = true;
		    }
		 }
	  }
	
      if (!found) { // allow wildcard entry, for example "*:ad9dfjhf0"
	    for (Enumeration e = this.htpasswdMap.keys();e.hasMoreElements() ; ) {
	      key = (String)e.nextElement();
	      if (key.equals("*")) {
	         container = (Container)this.htpasswdMap.get(key);
	         pws.addElement(container);
	      }
	    }
	  }
      return pws;
   }
   
   /**
    * Check of MethodName is allowed to be invoked by user. 
    * 
    * @param sessionHolder The user
    * @param dataHolder The method called
    * @return true if is authorized, false if no access
    */
   public boolean isAuthorized(SessionHolder sessionHolder, DataHolder dataHolder) {
      if (this.htpasswdMap == null) return true;
      SessionInfo sessionInfo = sessionHolder.getSessionInfo();
      if (sessionInfo == null) {
         log.warning("sessionInfo is null, will not authorize");
         return false;
      }
      SessionName sessionName = sessionInfo.getSessionName();
      if (sessionName == null) {
         log.warning("sessionName for '" + sessionInfo.toXml() + "' is null, will not authorize");
         return false;
      }
      String loginName = sessionName.getLoginName();
      if (loginName == null) {
         log.warning("loginName for '" + sessionName.toXml() + "' is null, will not authorize");
         return false;
      }
      
      Container container = (Container)this.containerCache.get(loginName);
      if (container == null) {
    	  Vector pws = lookup(loginName);
    	  if (pws.size() > 0) {
    		  container = (Container)pws.elementAt(0);
    		  this.containerCache.put(loginName, container);
    	  }
      }

      if (container == null) {
         StringBuffer buf = new StringBuffer(1024);
         Object[] keys = this.htpasswdMap.keySet().toArray();
         for (int i=0; i < keys.length; i++)
            buf.append("'").append(keys[i]).append("' ");
         log.severe("The login entry '" + loginName + "' has not been found in '" + this.htpasswdFilename + "'. Found entries are : " + buf.toString());
         return false;
      }
      if (container.allowedMethodNames == null) return true;
      if (dataHolder.getMsgUnit() == null || dataHolder.getMsgUnit().getKeyData() == null)
         return false;
      return container.isAllowed(dataHolder.getAction(),
             dataHolder.getKeyUrl());
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
      
      if(firstRead) {
    	  firstRead = false;
          log.info("Using secret file '"+htpasswdFilename + "' for authentication");
      }
      
      long curr = htpasswdFile.lastModified();
      if (this.lastModified == curr)
    	  return true;
      this.lastModified = curr;
      
      this.containerCache.clear();

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
            String[] tokens = StringPairTokenizer.parseLine(tail, ':', StringPairTokenizer.DEFAULT_QUOTE_CHARACTER, false, true);
            Container container = new Container(user);
            if (tokens.length > 0)
               container.password = tokens[0].trim();
            if (tokens.length > 1) {
               // parse "!SUBSCRIBE,ERASE" or 'CONNECT,DISCONNECT,PUBLISH("xpath://key"),subscribe("exact:hello")'
               // joe:079cv::  allows all methods
               String methodNames = tokens[1].trim();
               if (methodNames != null && methodNames.length() > 0) {
                  boolean positiveList = !methodNames.startsWith("!");
                  container.allowedMethodNames = new java.util.HashMap();
                  if (positiveList) {
                     String[] nameArr = org.xmlBlaster.util.StringPairTokenizer.parseLine(methodNames, ',', StringPairTokenizer.DEFAULT_QUOTE_CHARACTER, false);
                     for (int j=0; j<nameArr.length; j++) {
                        String name = nameArr[j].trim();
                        HashSet set = new HashSet();
                        int start = name.indexOf('(');
                        if (start != -1) {
                           int end = name.lastIndexOf(')');
                           if (end != -1) {
                              String topics = name.substring(start+1, end);
                              String[] topicArr = org.xmlBlaster.util.StringPairTokenizer.parseLine(topics, ';');
                              for (int n=0; n<topicArr.length; n++) {
                                 String url = topicArr[n].trim(); // expecting: "hello" or "exact:hello" or "xpath://key" or "domain:sport"
                                 if (url.length() == 0) continue;
                                 if (url.indexOf(":") == -1) {
                                    url = Constants.EXACT_URL_PREFIX+url;
                                 }
                                 set.add(url.trim());
                              }
                           }
                           name = name.substring(0, start);
                        }
                        try {
                           MethodName methodName = MethodName.toMethodName(name);
                           container.allowedMethodNames.put(methodName, set);
                        }
                        catch (IllegalArgumentException e) {
                           log.severe("Ignoring authorization method name, please check your configuration in '" + htpasswdFilename + "': " + e.toString());
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
                           log.severe("Ignoring authorization method name, please check your configuration in '" + htpasswdFilename + "': " + e.toString());
                        }
                     }
                     
                  }
               }
            }
            if (this.htpasswdMap == null) this.htpasswdMap = new Hashtable();
            this.htpasswdMap.put(user, container);
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
            if (this.htpasswdMap != null) {
               java.util.Iterator i = this.htpasswdMap.values().iterator();
               System.out.println("readHtpasswordFile() ========================================");
               while (i.hasNext()) {
                  Container container = (Container)i.next();
                  System.out.println(container.toString());
               }
               System.out.println("readHtpasswordFile() ========================================");
            }
            else {
               System.out.println("readHtpasswordFile() ======NO PASSWD ENTRY==================================");
            }
         }
         
         //log.info("Successfully read " + htpasswdFilename + " with " + this.htpasswdMap.size() + " entries");

         return true;
      }
      catch(Exception ex) {
         this.htpasswdMap = null ;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Problem when reading password file '"+htpasswdFilename+"'", ex);
      }
   }//readHtpasswordFile

   public String getPasswdFileName() {
      return htpasswdFilename; 
   }

   public void reset() {
	   this.containerCache.clear();
	   this.htpasswdMap.clear();
	   this.lastModified = -1L;
   }
   
   /**
    * Helper class for checkPassword in the case of startWith(username) ->
    * here more usernames of the hashtable can be right
    * @param userPassword password in plaintext
    * @param fileEncodedPass vector of passwords where usernames match with the specified beginning of an username
    * @return true if any one matches
    */
   private static boolean isSamePwd(String userPassword, String encoded) {
      if (encoded != null && encoded.length() == 0) return true; // empty password "joe::"
      if (encoded != null && encoded.length() > 2) {  
         String salt = encoded.substring(0,2);
         log.info("The Salt used is '" + salt + "'");
         String userEncoded = jcrypt.crypt(salt,userPassword);
         return userEncoded.trim().equals(encoded.trim());
      }
      return false;
   }
     

   public static void main(String[] args) {
      if (args.length < 2) {
         System.err.println("usage: " + HtPasswd.class.getName() + " userPwd encodedPwd");
         System.exit(-1);
      }
      String userPwd = args[0];
      String encoded = args[1];
      if (HtPasswd.isSamePwd(userPwd, encoded))
         log.info("The password was OK");
      else
         log.severe("The password was NOT OK");
   }
   
}//class HtAccess
