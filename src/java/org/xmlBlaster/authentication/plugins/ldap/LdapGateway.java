package org.xmlBlaster.authentication.plugins.ldap;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.naming.*;
import javax.naming.directory.*;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * The constructor of this class connects to the specified LDAP server,
 * thereafter you can check the password of a user.
 * <p />
 * Access of all accessible attributes for the specified login name (user)
 * is possible as well (demo code).
 * <p />
 * Configuration of the LDAP plugin in xmlBlaster.properties:
 * <pre>
 *  ldap.serverUrl=ldap://localhost:389/o=xmlBlaster,c=ORG
 *  ldap.rootDN=cn=Manager,o=xmlBlaster,c=ORG
 *  ldap.rootPwd=secret
 *  ldap.loginFieldName=cn
 * </pre>
 *
 * You may set these settings on command line as well:
 * <pre>
 *  java -jar lib/xmlBlaster.jar \
 *        -ldap.serverUrl "ldap://localhost:389/o=xmlBlaster,c=ORG" \
 *        -ldap.rootDN "cn=Manager,o=xmlBlaster,c=ORG" \
 *        -ldap.rootPwd "secret" \
 *        -ldap.loginFieldName "cn"
 * </pre>
 *
 * NOTE: Authorization for actions is not supported with this plugin,
 *     xmlBlaster logs warnings to notify you about this.
 *     If you want to implement authorization, please subclass
 * <pre>
 *        org.xmlBlaster.authentication.plugins.ldap.Session
 * </pre>
 *     and implement the method:
 * <pre>
 *   public boolean isAuthorized(String actionKey, String key)
 *   {
 *      DirContext ctx = ldap.getRootContext();
 *      // ... your LDAP queries to authorize the user action ...
 *      // return true if user may do this.
 *   }
 * </pre>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class LdapGateway
{
   private static final String ME = "LdapGateway";
   private static Logger log = Logger.getLogger(LdapGateway.class.getName());

   private Global glob;
   
   /**
    * Specify the initial context implementation to use.
    * This could also be set by using the -D option to the java program.
    * For example,
    *   java -Djava.naming.factory.initial=com.ibm.jndi.LDAPCtxFactory LdapGateway
    */
   private final String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

   /**
    * The application xmlBlaster has sort of a super user, you may specify it
    * in xmlBlaster.properties or on command line.
    */
   private final String rootDN;
   private final String rootPwd;
   private DirContext rootCtx;

   /** The name in the LDAP server which represents the loginName, here we use 'cn' */
   private String loginFieldName;

   private final String serverUrl;


   /**
    * Connects to the LDAP server.
    * <p />
    * To test use your browser and try something like
    * <pre>
    *   ldap://localhost:389/o=xmlBlaster,c=ORG??sub
    * </pre>
    *
    * @param serverUrl For example "ldap://localhost:389/o=xmlBlaster,c=ORG"
    * @param rootDN The distinguishable name of the application super user e.g. "cn=Manager,o=xmlBlaster,c=ORG"
    * @param rootPwd The password e.g. "topsecret"
    * @param  loginFieldName The fieldname where the loginName in LDAP lies (here 'cn') (used for later login as a normal user)
    */
   public LdapGateway(Global glob, String serverUrl, String rootDN, String rootPwd,
                     String loginFieldName) throws XmlBlasterException
   {
      this.glob = glob;
      this.serverUrl = serverUrl;
      this.rootDN = rootDN;
      this.rootPwd = rootPwd;
      
      this.rootCtx = getRootContext();

      this.loginFieldName = loginFieldName;
   }

   /**
    * Clean up resources
    */
   public void close()
   {
      if (rootCtx != null) {
         try {
            rootCtx.close();
         }
         catch (javax.naming.NamingException e) {
            if (log.isLoggable(Level.FINE)) log.fine("Closing DirContext faild: " + e.toString());
         }
      }
      rootCtx = null;
   }

   /**
    * @param rootDN "cn=Manager,o=xmlBlaster,c=ORG"
    * @param rootPwd "secret"
    * @return The LDAP connection as master
    */
   public DirContext getRootContext() throws XmlBlasterException
   {
      try {
         Hashtable env = new Hashtable(7, 0.75f);
         if (log.isLoggable(Level.FINE)) log.fine("Using the factory " + CONTEXT_FACTORY);
         env.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);

         // Specify host and port to use for directory service
         if (log.isLoggable(Level.FINE)) log.fine("Using ldap server " + serverUrl + "??sub   (You can try this URL with your netscape browser)");
         env.put(Context.PROVIDER_URL, serverUrl);

         // specify authentication information
         env.put(Context.SECURITY_AUTHENTICATION, "simple");
         env.put(Context.SECURITY_PRINCIPAL, rootDN);
         env.put(Context.SECURITY_CREDENTIALS, rootPwd);

         if (log.isLoggable(Level.FINE)) log.fine("rootDN=" + rootDN + " rootPwd=" + rootPwd);
         if (log.isLoggable(Level.FINE)) log.fine("Getting master context handle with master password from xmlBlaster.org ...");
         DirContext ctx = new InitialDirContext(env);
         if (log.isLoggable(Level.FINE)) log.fine("Connected to ldap server '" + serverUrl + "' as '" + rootDN + "'");
         return ctx;
      }
      catch (NamingException e) {
         log.severe("Can't access root context, check your settings ldap.serverUrl='" + serverUrl + "', ldap.rootDN='" + rootDN + "' and ldap.rootPwd='***'");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
   }


   /**
    * Searches the loginName in LDAP and returns its distinguishable name DN,
    * e.g. cn=mrf -> returns "cn=mrf, ou=Employee, ou=096, o=xmlBlaster,c=ORG"
    *
    * @param The cn (user identifier == xmlBlaster login name) to look for
    * @param A valid DN for the given cn or an exception
    */
   private String getUserDN(String loginName) throws XmlBlasterException
   {
      try {
         String filter = loginFieldName + "=" + loginName;
         NamingEnumeration answer = search(rootCtx, filter);

         String baseName = getBaseName();
         if (log.isLoggable(Level.FINE)) log.fine("DN access for user=" + loginName + ". Trying basename = '" + baseName + "'");

         if (answer.hasMore()) {
            SearchResult sr = (SearchResult)answer.next();
            String userDN = sr.getName() + "," + baseName;
            if (log.isLoggable(Level.FINE)) log.fine("Successful accessed DN='" + userDN + "' for user " + loginName);
            return userDN;
         }
         else {
            log.severe("Can't access root context, check your setting of ldap.loginFieldName='" + loginFieldName + "'");
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, serverUrl + " is not valid");
         }
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }

   }


   /**
    * Please close the given Context after usage.
    *
    * @param loginName the user uid
    * @param userPassword The users password
    * @return The LDAP connection for this user
    */
   private DirContext getUserContext(String loginName, String userPassword) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINE)) log.fine("Getting user=" + loginName + " context handle");
      //if (log.isLoggable(Level.FINE)) log.trace(ME, "Getting user=" + loginName + " context handle with passwd=" + userPassword + " ...");
      try {
         Hashtable env = new Hashtable(7, 0.75f);
         if (log.isLoggable(Level.FINE)) log.fine("Using the factory " + CONTEXT_FACTORY);
         env.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);

         // Specify host and port to use for directory service
         if (log.isLoggable(Level.FINE)) log.fine("Using ldap server " + serverUrl + "??sub   (You can try this URL with your browser)");
         env.put(Context.PROVIDER_URL, serverUrl);

         String userDN = getUserDN(loginName);

         // specify authentication information
         env.put(Context.SECURITY_AUTHENTICATION, "simple");
         env.put(Context.SECURITY_PRINCIPAL, userDN);
         env.put(Context.SECURITY_CREDENTIALS, userPassword);

         if (log.isLoggable(Level.FINE)) log.fine("  Getting context handle ...");
         DirContext userCtx = new InitialDirContext(env);
         if (log.isLoggable(Level.FINE)) log.fine("  Connected to ldap server url='" + serverUrl + "' with DN='" + userDN + "'");
         return userCtx;
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
   }


   /**
    * Extract "o=xmlBlaster,c=ORG" from "ldap://localhost:389/o=xmlBlaster,c=ORG":
    */
   public String getBaseName()
   {
      return serverUrl.substring(serverUrl.indexOf("/", 8) + 1);
   }

   public String getServerUrl()
   {
      return serverUrl;
   }

   /**
    * Get all attributes of this 'Person' objectclass
    * @param loginName The user who does the query (his login name)<br />
    *        If loginName==null, we use the ldap.rootDN which was specified on startup
    * @param password His password<br />
    *        If loginName==null, we use the ldap.rootPwd which was specified on startup
    * @param password 
    * @param lookupUserId The user we want to examine (can be the same as userId)
    * @return A hashtable with all attributes for the given user (loginName )
    */
   public Hashtable getAllAttributes(String loginName, String password, String lookupUserId) throws XmlBlasterException
   {
      Hashtable attrHash = new Hashtable();

      DirContext userCtx = null;
      try {
         if (loginName == null)
            userCtx = rootCtx;
         else if (loginName.equals(lookupUserId)) // Query myself
            userCtx = getUserContext(loginName, password);
         else
            userCtx = getUserContext(loginName, password);  // Query from xmlBlaster Admin

         // Search attributes of a Person objectclass ...
         NamingEnumeration searchResults = search(userCtx, loginFieldName+"="+lookupUserId);

         /*
            * Print search results by iterating through
            *    1. All entries in search results
            *    2. All attributes in each entry
            *    3. All values in an attribute
            */
         while ( searchResults.hasMore() )
         {
            SearchResult nextEntry = ( SearchResult )searchResults.next();
            System.out.println("LdapGateway.getAllAttributes() name: " + nextEntry.getName());

            Attributes attributeSet = nextEntry.getAttributes();
            if (attributeSet.size() == 0)
            {
               log.severe("No attributes returned for cn=" + loginName + " in " + serverUrl);
            }
            else
            {
               NamingEnumeration allAttributes = attributeSet.getAll();

               while (allAttributes.hasMoreElements())
               {
                  Attribute attribute = ( Attribute ) allAttributes.next();
                  String attributeId = attribute.getID();
                  Enumeration values = attribute.getAll();
                  int ii=0;
                  while (values.hasMoreElements())
                  {
                     if (ii>0) if (log.isLoggable(Level.FINE)) log.fine("WARN: Ignoring multiple values for " + attributeId);
                     Object val = values.nextElement();
                     // userPassword:
                     // http://developer.netscape.com/tech/overview/index.html?content=/docs/technote/ldap/pass_sha.html
                     // http://www.openldap.org/lists/openldap-software/200002/msg00038.html
                     /*
                     if (log.isLoggable(Level.FINE)) log.trace(ME, attributeId + ": " + val + " <" + val.getClass().getName() + ">");
                     if (val instanceof Byte)
                        if (log.isLoggable(Level.FINE)) log.trace(ME, "Byte found");
                     */
                     attrHash.put(attributeId, val);
                     ii++;
                  }
               }
            }
         }
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
      finally {
         try {
            if (userCtx != null) userCtx.close();
         }
         catch (NamingException e) {
            log.warning("Problems closng the user context: " + e.toString());
         }
      }
      return attrHash;
   }


   /**
    * We assume that only one password is specified.
    * NOTE: The password is not clear text
    */
   public String getPassword(Attributes result) throws XmlBlasterException
   {
      try {
         if (result == null) {
            return null;
         } else {
            Attribute attr = result.get("userPassword");
            if (attr != null) {
               System.out.println("LdapGateway userPassword:");
               String password = null;
               for (NamingEnumeration vals = attr.getAll(); vals.hasMoreElements();)
                  password = (String)vals.nextElement();
               return password;
            }
         }
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
      return null;
   }


   /**
    * Check if given user exists
    * @param loginName The user which wants to know this. <br />
    *        If loginName==null, we use the ldap.rootDN which was specified on startup
    * @param password His password<br />
    *        If loginName==null, we use the ldap.rootPwd which was specified on startup
    * @param loginNameToCheck The user to check
    * @return true User is known
    */
   public boolean userExists(String loginName, String password, String loginNameToCheck) throws XmlBlasterException
   {
      String filter = "(" + loginFieldName + "=" + loginNameToCheck + ")";

      DirContext userCtx = null;
      try {
         
         if (loginName == null)
            userCtx = rootCtx;
         else
            userCtx = getUserContext(loginName, password);

         NamingEnumeration searchResults = null;
         try {
            searchResults = search(userCtx, filter);
         }
         catch(XmlBlasterException e) {
            log.severe("The cn=" + loginNameToCheck + " is unknown in " + serverUrl);
            return false;
         }

         if (searchResults.hasMore()) {
            if (log.isLoggable(Level.FINE)) log.fine("The cn=" + loginNameToCheck + " (dieser Pappenheimer) is well known in " + serverUrl);
            return true;
         }
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
      finally {
         try {
            if (userCtx != null) userCtx.close();
         }
         catch (NamingException e) {
            log.warning("Problems closng the user context: " + e.toString());
         }
      }

      return false;
   }


   /**
    * Check password
    * @param userPassword The clear text password
    * @return true The password is valid
    */
   public boolean checkPassword(String loginName, String userPassword) throws XmlBlasterException
   {
      try {
         DirContext userCtx = getUserContext(loginName, userPassword);
         if (userCtx != null) {
            userCtx.close();
            return true;
         }
         return false;
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
   }


   /**
    * Do a ldap query.
    * @param ctx The connection to ldap
    * @param filter Filter to use when searching: "(objectclass=*)" -> finds all
    * @return The results
    */
   private NamingEnumeration search(DirContext ctx, String filter) throws XmlBlasterException
   {
     /**
      * Initialize search constraint parameters and pass them to searchControl
      * constructor. Set the following values:
      *    1. Search scope to OBJECT_SCOPE (0), ONELEVEL_SCOPE (1), or
      *       SUBTREE_SCOPE (2).
      *    2. Number of milliseconds to wait before return: 0-> infinite.
      *    3. Maximum number of entries to return: 0 -> no limit.
      *    4. Attributes to return: null -> all; "" -> nothing
      *    5. Return object: true -> return the object bound to the name,
      *       false -> do not return object
      *    6. Deference: true -> deference the link during search
      int      scope                =  SearchControls.SUBTREE_SCOPE;
      int      timeLimit            =  1000;
      long     countLimit           =  1000;
      String   returnedAttributes[] =  { "cn", "sn", "userPassword" };
      boolean  returnObject         =  false;
      boolean  dereference          =  false;
      */

      try {
         if (log.isLoggable(Level.FINE)) log.fine("Calling SearchControl constructor to set search constraints...");
         SearchControls searchControls = new SearchControls(SearchControls.SUBTREE_SCOPE, 0,0,null,true,false);

         final String MY_SEARCHBASE = "";  // Subtree to search: "ou=Extern, ou=096"; -> finds "tim"
         
         if (log.isLoggable(Level.FINE)) log.fine("Searching " + filter);
         NamingEnumeration searchResults = ctx.search(MY_SEARCHBASE, filter, searchControls);
         if (log.isLoggable(Level.FINE)) log.fine("Searching successful done\n");

         return searchResults;
      }
      catch (NamingException e) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, e.toString());
      }
   }


   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.authentication.plugins.ldap.LdapGateway -loginName tim -userPassword tim -logging FINE
    */
   public static void main(String[] args)
   {
      System.out.println("\nUsage:\n\n\torg.xmlBlaster.authentication.plugins.ldap.LdapGateway -loginName <name> -userPassword <passwd>\n\torg.xmlBlaster.authentication.plugins.ldap.LdapGateway -loginName tim -userPassword tim");

      // ldap://localhost:389/o=xmlBlaster,c=ORG??sub
      try {
         org.xmlBlaster.util.Global glob = new org.xmlBlaster.util.Global(args);

         final String serverUrl = glob.getProperty().get("serverUrl", "ldap://localhost:389/o=xmlBlaster,c=ORG");
         final String rootDN = glob.getProperty().get("rootDN", "cn=Manager,o=xmlBlaster,c=ORG");
         final String rootPwd =  glob.getProperty().get("rootPwd", "secret");
         final String loginName = glob.getProperty().get("loginName", "tim");
         final String userPassword = glob.getProperty().get("userPassword", "tim");
         final String loginFieldName = glob.getProperty().get("loginFieldName", "cn");

         LdapGateway ldap = new LdapGateway(glob, serverUrl, rootDN, rootPwd, loginFieldName);

         System.out.println("\nTesting checkPassword() ...");
         boolean pwdOk = ldap.checkPassword(loginName, userPassword);
         System.out.println("The password=" + userPassword + " for cn=" + loginName + " is " + ((pwdOk)?"":" NOT ") + " valid.");

         System.out.println("\nTesting getAllAttributes() ...");
         Hashtable attrHash = ldap.getAllAttributes(loginName, userPassword, loginName);
         Enumeration keys = attrHash.keys();
         while( keys.hasMoreElements() ) {
            String key = (String)keys.nextElement();
            System.out.println(key + ": " + attrHash.get(key));
         }
      }
      catch(Exception e) {
         System.err.println("ERROR: " + e.toString());
         e.printStackTrace();
      }
   }
}

