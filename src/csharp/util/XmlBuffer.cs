/*------------------------------------------------------------------------------
Name:      XmlBuffer.java
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

using System.Text;

namespace org.xmlBlaster.util
{


   /**
    * Same as StringBuffer but has the additional method AppendEscaped() which
    * escapes predefined XML identities.
    * @author mr@marcelruff.info
    */
   public class XmlBuffer
   {
      private StringBuilder buf;
      public XmlBuffer(int len)
      {
         this.buf = new StringBuilder(len);
      }

      /**
       * Escape predefined xml entities (&, <, >, ', ").
       * Additionally the '\0' is escaped.
       * @param text
       * @return The escaped text is appended to the StringBuffer.
       */
      public XmlBuffer AppendEscaped(string text)
      {
         Append(this.buf, text);
         return this;
      }

      /**
       * Escape predefined xml entities (', ", \r) for attributes.
       * Additionally the '\0' is escaped.
       * @param text
       * @return The escaped text is Appended to the StringBuffer.
       */
      public XmlBuffer AppendAttributeEscaped(string text)
      {
         AppendAttr(this.buf, text);
         return this;
      }

      /**
       * Appends a tag name (e.g. "bla" of a tag called <bla>).
       * Currently is a normal Append()
       * @param tagName Could in future escape invalid tokens  '<' and '&' in a tag name.
       * @return
       */
      public XmlBuffer AppendTag(string tagName)
      {
         this.buf.Append(tagName);
         return this;
      }

      /**
       * Aquivalent to a StringBuffer.Append().
       */
      public XmlBuffer Append(string str)
      {
         this.buf.Append(str);
         return this;
      }

      /**
       * Aquivalent to a StringBuffer.Append().
       */
      public XmlBuffer Append(long ln)
      {
         this.buf.Append(ln);
         return this;
      }

      /**
       * Aquivalent to a StringBuffer.Append().
       */
      public XmlBuffer Append(float ln)
      {
         this.buf.Append(ln);
         return this;
      }

      /**
       * Aquivalent to a StringBuffer.Append().
       */
      public XmlBuffer Append(double ln)
      {
         this.buf.Append(ln);
         return this;
      }

      /**
       * Aquivalent to a StringBuffer.Append().
       */
      public XmlBuffer Append(bool b)
      {
         this.buf.Append(b);
         return this;
      }

      public StringBuilder getRawBuffer()
      {
         return this.buf;
      }

      public int Length()
      {
         return this.buf.Length;
      }

      /**
       * Removes all buffer entries.
       * Calling Append fills new data to the beginning of the buffer. 
       */
      public void Reset()
      {
         this.buf.Remove(0, this.buf.Length);
      }

      override public string ToString()
      {
         return this.buf.ToString();
      }

      // 5 predefined XML entities plus some extra escapers
      private static readonly string S_AMP = "&amp;";
      private static readonly string S_LT = "&lt;";
      private static readonly string S_GT = "&gt;";
      private static readonly string S_QUOT = "&quot;";
      private static readonly string S_APOS = "&apos;";

      private static readonly string S_SLASH_R = "&#x0D;";
      private static readonly string S_NULL = "&#x0;";

      private static readonly char[] AMP = S_AMP.ToCharArray();
      private static readonly char[] LT = S_LT.ToCharArray();
      private static readonly char[] GT = S_GT.ToCharArray();
      private static readonly char[] QUOT = S_QUOT.ToCharArray();
      private static readonly char[] APOS = S_APOS.ToCharArray();

      private static readonly char[] SLASH_R = S_SLASH_R.ToCharArray();
      private static readonly char[] NULL = S_NULL.ToCharArray();

      /**
       * Escape predefined xml entities (&, <, >, ', ").
       * Additionally the '\0' is escaped.
       * @param text e.g. "Hello < and &"
       * @return "Hello &lt; and &amp;"
       */
      public static string escape(string text)
      {
         if (text == null || text.Length < 1)
            return text;
         StringBuilder buf = new StringBuilder((int)(text.Length * 1.2));
         Append(buf, text);
         return buf.ToString();
      }

      /**
       * Escape predefined xml entities (&, <, >, ', ").
       * Additionally the '\0' is escaped.
       * @param text
       * @return The escaped text is Appended to the given StringBuffer.
       */
      public static void Append(StringBuilder buf, string text)
      {
         if (text == null) return;
         int length = text.Length;
         for (int i = 0; i < length; i++)
         {
            char c = text[i];
            switch (c)
            {
               case '\0':
                  buf.Append(NULL);
                  break;
               case '&':
                  buf.Append(AMP);
                  break;
               case '<':
                  buf.Append(LT);
                  break;
               case '>':
                  buf.Append(GT);
                  break;
               case '"':
                  buf.Append(QUOT);
                  break;
               case '\'':
                  buf.Append(APOS);
                  break;
               case '\r':
                  buf.Append(SLASH_R);
                  break;
               default:
                  buf.Append(c);
                  break;
            }
         }
      }
      /**
       * Escape predefined xml entities (\0, ', ", \r). for attribute notation
       * Additionally the '\0' is escaped.
       * @param text
       * @return The escaped text is appended to the given StringBuffer.
       */
      public static void AppendAttr(StringBuilder buf, string text)
      {
         if (text == null) return;
         int length = text.Length;
         for (int i = 0; i < length; i++)
         {
            char c = text[i];
            switch (c)
            {
               case '\0':
                  buf.Append(NULL);
                  break;
               case '&':
                       buf.Append(AMP);
                   break;
               case '<':
                       buf.Append(LT);
                   break;
               /*
               case '>':
                       buf.Append(GT);
                   break;
               */
               case '"':
                  buf.Append(QUOT);
                  break;
               case '\'':
                  buf.Append(APOS);
                  break;
               case '\r':
                  buf.Append(SLASH_R);
                  break;
               default:
                  buf.Append(c);
                  break;
            }
         }
      }

      public static string UnEscape(string text)
      {
         if (text == null) return "";
         int length = text.Length;
         StringBuilder buf = new StringBuilder(length);
         for (int i = 0; i < length; i++)
         {
            if (text[i] == '&')
            {
               int len = 0;
               string sub = text.Substring(i);
               if (sub.StartsWith(S_NULL))
               {
                  buf.Append('\0');
                  len = S_NULL.Length;
               }
               else if (sub.StartsWith(S_AMP))
               {
                  buf.Append('&');
                  len = S_AMP.Length;
               }
               else if (sub.StartsWith(S_LT))
               {
                  buf.Append('<');
                  len = S_LT.Length;
               }
               else if (sub.StartsWith(S_GT))
               {
                  buf.Append('>');
                  len = S_GT.Length;
               }
               else if (sub.StartsWith(S_QUOT))
               {
                  buf.Append('"');
                  len = S_QUOT.Length;
               }
               else if (sub.StartsWith(S_APOS))
               {
                  buf.Append('\'');
                  len = S_APOS.Length;
               }
               else if (sub.StartsWith(S_SLASH_R))
               {
                  buf.Append('\r');
                  len = S_SLASH_R.Length;
               }

               if (len > 0)
               {
                  i += (len - 1);
                  continue;
               }
            }
            buf.Append(text[i]);
         }
         return buf.ToString();
      }

   } // class
} // namespace