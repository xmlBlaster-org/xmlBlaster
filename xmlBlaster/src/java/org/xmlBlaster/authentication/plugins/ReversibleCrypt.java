package org.xmlBlaster.authentication.plugins;

import java.lang.*;
import java.util.*;

public class ReversibleCrypt
{
   private static ReversibleCrypt singleton = null;
   private String classname = null;
   private int classlen = 0;
   private byte charArr[] = new byte[100];
   private int count = 0;
   public final static ReversibleCrypt getSingleton()
   {
      if (singleton == null) {
         synchronized(ReversibleCrypt.class) {
            if (singleton == null)
               singleton = new ReversibleCrypt();
         }
      }
      return singleton;
   }

   public  ReversibleCrypt()
   {
      byte c;
      classname = getClass().getName();
      classlen = classname.length();
      for(c = (byte) 'A'; c <= (byte) 'Z'; c++)
      {
        charArr[count] = (byte)(c + 0x20);
        count ++;
        if(c < 'K')
        {
           charArr[count] = (byte)(c - 0x11);
           count ++;
        }
        charArr[count] = c;
        count ++;
      }

   }


    /**
     * Hack: transforms to string with local character encoding
     * Will break if encoding changes!!!
     */
    public byte[] crypt(byte[] dc2Value)
    {
       return crypt(new String(dc2Value)).getBytes();
    }


    public String crypt(String value)
    {
       int len = value.length() ;
       String dcValue = "";
       long longValue = 0;
       int i;
       byte b = 0;
       byte r = 0;
       for(i = 0; i < classlen && i < (len +1) ; i++)
       {

           byte x = (byte) classname.charAt(i);
           if(i < len)
           {
              b = (byte) value.charAt(i);
           }
           else
           {
              b = (byte) 0x7f;
           }

                r = (byte) (b ^ x);

           int pos = 8 * (3 - (i % 4));

           if(((i % 4) == 0) && (i != 0))
           {
               if(i > 4)
                  dcValue = dcValue + ":" + longValue;
               else
                  dcValue = "" + longValue;
           }
           if (i % 4 == 0)
           {
              longValue = 0;
           }
           longValue |= (r & 0xff) <<  pos;

       }

       if(i > 4)
          dcValue = dcValue + ":" + longValue;
       else
          dcValue = "" + longValue;
       StringTokenizer st = new StringTokenizer(dcValue, ":");
       String rval = "";
       String zval = "";
       while(st.hasMoreTokens())
       {
           String token = st.nextToken();
           if(token.equals("0"))
              zval += "90";
           else
              zval += token.length() + token;
       }
       len = zval.length();
       byte c1 = 0;
       byte c2 = 0;
       byte c = 0;
       for(i = 0 ;  i < len ; i++)
       {
           c1 = (byte) (zval.charAt(i) - '0');
            if(i < len - 1)
           {
              c2 = (byte) (zval.charAt(i+1) - '0');
              c = (byte) (c1 * 10 + c2);
           }
           else
              c = c1;
           if(c >= count || c1 == 0)
           {
             c = c1;
           }
           else
           {
              i++;
           }
           rval+=(char) charArr[c];
       }
       return rval;
    }

    /**
     * Hack: transforms to string with local character encoding
     * Will break if encoding changes!!!
     */
    public byte[] decrypt(byte[] dc2Value)
    {
      return decrypt(new String(dc2Value)).getBytes();
    }

    public String decrypt(String dc2Value)
    {
       int len = dc2Value.length();
       String dc1Value = "";

       for(int i = 0 ;  i < len   ; i++)
       {
           int j = 0;
           for(j = 0; j < count; j++)
               if(charArr[j] == (byte) dc2Value.charAt(i)) break;
           dc1Value+=j;
       }

       len = dc1Value.length();
       String value = "";
       String dcValue = "";
       for (int i = 0; i < len ; i++)
       {
           byte count = (byte) (dc1Value.charAt(i) - '0');
           if (count == 1)
           {
              i++;
              count = 10;
              count += (byte) (dc1Value.charAt(i) - '0');
           }
           if (count == 9)
           {
              if (dc1Value.charAt(i + 1 ) == '0')
                 count=1;
           }
           i++;
           String longValue = "";
           for ( int j = 0; j < count && i < len ; j++, i++)
           {
               longValue += dc1Value.charAt(i);
           }
           i--;
           if( i < len - 1)
             dcValue += longValue + ":";
           else
             dcValue += longValue;
       }
       len = dcValue.length();
       StringTokenizer st = new StringTokenizer(dcValue, ":");
       int j = 0;
       while(st.hasMoreTokens())
       {
          String token = st.nextToken();
          long intValue = new Long(token).longValue();
          for(int i = 0; i < 4; i++, j++)
          {
              int pos = 8 * (3 - i);
              byte b = (byte) ((intValue & (255 << pos))  >> pos);
              byte x = (byte) classname.charAt(j);
              if((byte) (b ^ x) == 127) break;

              value = value + (char) (b ^ x);

          }

       }

       return value;
    }



    public static void main(String[] args)
    {
         String value = args[0];
         String dcValue = ReversibleCrypt.getSingleton().crypt(value);

         System.out.println("crypt: " + value + " -> " + dcValue);
         System.out.println("decrypt: " + dcValue +  " -> " + ReversibleCrypt.getSingleton().decrypt(dcValue));
    }
}
