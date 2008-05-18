/*------------------------------------------------------------------------------
Name:      I_GprsManager.cs
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

using System.Text;
using System.Collections;

namespace org.xmlBlaster.util
{
   public interface I_GprsManager
   {
      bool Connect(bool callbackGui);
      /// <returns>true on success</returns>
      bool Disconnect(bool callbackGui);
   }
} // namespace