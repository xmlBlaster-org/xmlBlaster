/*
        HtPasswd.java

        16/11/01 19:27 cyrille@ktaland.com

*/
package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;

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
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a> 16/11/01 09:06
 */
public class HtPasswd {

   private static final String ME = "HtPasswd";

   protected LogChannel log;

   protected final int ALLOW_PARTIAL_USERNAME = 1;
   protected final int FULL_USERNAME = 2;
   protected final int SWITCH_OFF = 3;

   protected int useFullUsername = ALLOW_PARTIAL_USERNAME;
   protected String htpasswdFilename = null ;
   protected Hashtable htpasswd = null ;

   private static boolean first = true;

   /**
    * Check password
    * 16/11/01 19:36 mad@ktaland.com
    */
   public HtPasswd(Global glob) throws XmlBlasterException {
      log = glob.getLog("auth");
      htpasswdFilename = glob.getProperty().get("Security.Server.Plugin.htpasswd.secretfile", "NONE" );
      boolean allowPartialUsername = glob.getProperty().get("Security.Server.Plugin.htpasswd.allowPartialUsername", false);
      if ( allowPartialUsername ) {
         useFullUsername = ALLOW_PARTIAL_USERNAME;
         if (log.TRACE) log.trace(ME, "Login names are searched with 'startswith' mode in '" + htpasswdFilename + "'.");
      }
      else {
          useFullUsername = FULL_USERNAME;
          if (log.TRACE) log.trace( ME, "contructor(" + htpasswdFilename + ") allowPartialUsername=false" );
      }
      if (htpasswdFilename != null && htpasswdFilename.equals("NONE")) {
          useFullUsername = SWITCH_OFF;
          if (first) {
             log.warn(ME, "Security risk, no access control: The passwd file is switched off with 'Security.Server.Plugin.htpasswd.secretfile=NONE'");
             first = false;
          }
          return;
      }


      if (log.TRACE) log.trace( ME, "contructor(" + htpasswdFilename + ") " );

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
     if (log.TRACE) log.trace(ME, "Comparing '" + userPassword + "' in " + fileEncodedPass.size() + " possibilities");
     String encoded = null,salt,userEncoded;
     for (Enumeration e = fileEncodedPass.elements();e.hasMoreElements();)
     { encoded = (String)e.nextElement();
       if ( encoded != null ) 
       {  salt = encoded.substring(0,2);
          userEncoded = jcrypt.crypt(salt,userPassword);
          if (log.TRACE) log.trace(ME, "Comparing '" + userEncoded + "' with passwd entry '" + encoded + "'");
          if ( userEncoded.equals(encoded) ) 
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
      if( htpasswd != null && userName!=null && userPassword!=null ){
         Vector pws = new Vector();

          //find user in Hashtable htpasswd
          String key;
          if ( useFullUsername == FULL_USERNAME ) {
            pws.addElement((String)htpasswd.get(userName));
          }
          else { 
            for (Enumeration e = htpasswd.keys();e.hasMoreElements() ; ) {
              key = (String)e.nextElement();
              if (log.TRACE) log.trace(ME, "Checking userName=" + userName + " with key='" + key + "'");
              if ( userName.startsWith(key) ) {
                pws.addElement((String)htpasswd.get(key));
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

      if (log.CALL) log.call( ME, "readHtpasswordFile : "+htpasswdFilename );
      File            htpasswdFile ;

      if( htpasswdFilename == null)
      throw new XmlBlasterException( ME, "missing property Security.Server.Plugin.htpasswd.secretfile" );

      htpasswdFile =new File(htpasswdFilename) ;

      if( ! htpasswdFile.exists() ) {
         log.error( ME, "Secret file doesn't exist : "+htpasswdFilename + ", please check your 'Security.Server.Plugin.htpasswd.secretfile' setting.");
         throw new XmlBlasterException( ME, "secret file doesn't exist : "+htpasswdFilename );
      }
      if( ! htpasswdFile.canRead() ) {
         log.error( ME, "Secret file '"+htpasswdFilename + "' has no read permission");
         throw new XmlBlasterException( ME, "no read access on file : "+htpasswdFilename );
      }

      FileInputStream fis = null ;

      try {
         fis = new FileInputStream( htpasswdFile );
         Reader r = new BufferedReader( new InputStreamReader( fis ) );
         StreamTokenizer st = new StreamTokenizer(r);
         st.slashSlashComments(true);
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
                  }else if ( ((char)st.ttype) == '*') { //This is the third case I mentioned above -> the password-file just contains a '*' -> all connection requests are authenticated
                    useFullUsername = SWITCH_OFF;
                    if (first) {
                      log.warn(ME, "Security risk, no access control: '" + htpasswdFile + "' contains '*'");
                      first = false;
                    }
                    end = true;                                  
                  }else{
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
               htpasswd.put( user.toString(), password.toString() );
               user.setLength(0);
               password.setLength(0);
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
         throw new XmlBlasterException( ME, "problem append when reading file : "+htpasswdFilename+". Ex="+ex );
      }

   }//readHtpasswordFile

   public String getPasswdFileName() {
      return htpasswdFilename; 
   }

}//class HtAccess
