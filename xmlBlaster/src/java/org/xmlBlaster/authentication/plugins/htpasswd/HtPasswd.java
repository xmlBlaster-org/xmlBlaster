/*
        HtPasswd.java

        16/11/01 19:27 cyrille@ktaland.com

*/
package org.xmlBlaster.authentication.plugins.htpasswd;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Log;

import java.io.* ;
import java.util.Hashtable ;
import java.util.Enumeration ;
//import org.xmlBlaster.authentication.plugins.htpasswd.jcrypt;

/*
 * In xmlBlaster.properties add :<br>
 * Security.Server.Plugin.htpasswd.secretfile=${user.home}${file.separator}xmlBlaster.htpasswd
 *
 * @author <a href="mailto:cyrille@ktaland.com">Cyrille Giquello</a> 16/11/01 09:06
 */
public class HtPasswd {

   private static final String ME = "HtAccess";

   protected String htpasswdFilename = null ;
   protected Hashtable htpasswd = null ;

   /**
    * Check password
    * 16/11/01 19:36 mad@ktaland.com
    * @param password The clear text password
    * @return true The password is valid
    */

   public HtPasswd(Global glob) throws XmlBlasterException {

      htpasswdFilename = glob.getProperty().get("Security.Server.Plugin.htpasswd.secretfile", (String) null );

      Log.trace( ME, "contructor()" );

      if( readHtpasswordFile( htpasswdFilename ) ){
      }

   }//HtPasswd

   /**
    * Check password
    * @param password The clear text password
    * @return true The password is valid
    */
   public boolean checkPassword( String userName, String userPassword )
                throws XmlBlasterException {
      if( htpasswd != null ){
         if( userName!=null && userPassword!=null ){

            //find user in Hashtable htpasswd
            String fileEncodedPass = (String) htpasswd.get( userName );
            if( fileEncodedPass != null ){
               // Get salt to encode user password
               String salt = fileEncodedPass.substring(0,2);
               // encode user password for futher comparaison
               String userEncodedPW = jcrypt.crypt( salt, userPassword );

               if( userEncodedPW.equals( fileEncodedPass ) ){
                  return true ;
               }
            }
         }
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

      Log.trace( ME, "readHtpasswordFile : "+htpasswdFilename );
      File            htpasswdFile ;

      if( htpasswdFilename == null)
      throw new XmlBlasterException( ME, "missing property Security.Server.Plugin.htpasswd.secretfile" );

      htpasswdFile =new File(htpasswdFilename) ;

      if( ! htpasswdFile.exists() )
         throw new XmlBlasterException( ME, "secret file doesn't exist : "+htpasswdFilename );
      if( ! htpasswdFile.canRead() )
         throw new XmlBlasterException( ME, "no read access on file : "+htpasswdFilename );

      FileInputStream fis = null ;

      try {
         fis = new FileInputStream( htpasswdFile );
         Reader r = new BufferedReader( new InputStreamReader( fis ) );
         StreamTokenizer st = new StreamTokenizer(r);

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

}//class HtAccess
