/*
        HtPasswd.java

        16/11/01 19:27 cyrille@ktaland.com

*/
package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.* ;
import java.util.Hashtable ;
import java.util.Enumeration ;
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
   /* Key is user name, values is the encrypted password */
   protected Hashtable htpasswd = null ;

   private static boolean first = true;
   private static boolean firstWild = true;

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
            String pw = (String)this.htpasswd.get(userName);
            pws.addElement(pw);
            found = true;
         }
         else { // ALLOW_PARTIAL_USERNAME
            for (Enumeration e = this.htpasswd.keys();e.hasMoreElements() ; ) {
               key = (String)e.nextElement();
               if (log.isLoggable(Level.FINE)) log.fine("Checking userName=" + userName + " with key='" + key + "'");
               if (userName.startsWith(key) || userName.endsWith(key)) {
                  String pw = (String)this.htpasswd.get(key);
                  pws.addElement(pw);
                  found = true;
               }
            }
         }

         if (!found) { // allow wildcard entry, for example "*:ad9dfjhf0"
            for (Enumeration e = this.htpasswd.keys();e.hasMoreElements() ; ) {
               key = (String)e.nextElement();
               if (key.equals("*")) {
                  pws.addElement(this.htpasswd.get(key));
               }
            }
         }
         
         return checkDetailed(userPassword,pws);
      }
      return false;
   }//checkPassword

   /**
    * Read passwords file
    * 16/11/01 20:42 mad@ktaland.com
    * @param the password filename
    * @return true if file all readed & well formated
    */
   boolean readHtpasswordFile( String htpasswdFilename )
        throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer( "readHtpasswordFile : "+htpasswdFilename );
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
            String passwd = (String)map.get(user);
            if (passwd == null) passwd = "";
            passwd = passwd.trim();
            if (this.htpasswd == null) this.htpasswd = new Hashtable();
            this.htpasswd.put(user, passwd);
            if (user.equals("*") && passwd.length() < 1) {
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
               java.util.Iterator i = this.htpasswd.keySet().iterator();
               System.out.println("========================================");
               while (i.hasNext()) {
                  String user = (String)i.next();
                  System.out.println("user='" + user + "' passwd='" + this.htpasswd.get(user) + "'");
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

      /*
      FileInputStream fis = null ;

      try {
         fis = new FileInputStream( htpasswdFile );
         Reader r = new BufferedReader( new InputStreamReader( fis ) );
         StreamTokenizer st = new StreamTokenizer(r);
         st.slashSlashComments(true); // Recognize C++ comments '//'
         //st.slashStarComments(true);
         st.ordinaryChars( 0,255 );
         st.eolIsSignificant(true);

         htpasswd = new Hashtable();
         StringBuffer    user = new StringBuffer() ;
         StringBuffer    password = new StringBuffer() ;

         boolean readUser = true ;
         boolean end = false ;
         while( ! end ){
            switch( st.nextToken() ){

            default:
               if( st.ttype>=0 ){
                  if( ((char)st.ttype) == ':' ){  // user:password
                     readUser = false ;
                  }
                  else{
                     if( readUser ){
                        user.append( (char)st.ttype );
                     }else{
                        password.append( (char)st.ttype );
                     }
                  }
               }
               break;

            case StreamTokenizer.TT_WORD:
               //println( st.ttype +": "+st.sval );
               if( readUser ){
                  user.append( st.sval );
               }else{
                  password.append( st.sval );
               }
               break;

            case StreamTokenizer.TT_EOL:
               readUser = true ;
               String u = user.toString().trim();
               String p = password.toString().trim();
               if (u.equals("*") && p.length()==0) {
                  //This is the third case I mentioned above -> the password-file just contains a '*' -> all connection requests are authenticated
                  useFullUsername = SWITCH_OFF;
                  if (first) {
                     log.warn(ME, "Security risk, no access control: '" + htpasswdFile + "' contains '*'");
                     first = false;
                  }
                  end = true;                                  
               }
               else {
                  htpasswd.put(u, p);
                  user.setLength(0);
                  password.setLength(0);
               }
               break;

            case StreamTokenizer.TT_EOF:
               end = true ;
               break ;

            }   // end Switch
         }  // end while

         fis.close();
         return true ;

      }catch( Exception ex ){
         try{
            fis.close();
         }catch( IOException ioex ){}
         htpasswd = null ;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "problem append when reading file : "+htpasswdFilename+". Ex="+ex );
      }
         */

   }//readHtpasswordFile

   public String getPasswdFileName() {
      return htpasswdFilename; 
   }

}//class HtAccess
