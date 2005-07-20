/*------------------------------------------------------------------------------
Name:      ReferenceHolder.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Entry Holder to use with stl containers as containers of references
Version:   $Id$
------------------------------------------------------------------------------*/

#ifndef _UTIL_REFERENCEHOLDER_H
#define _UTIL_REFERENCEHOLDER_H

#include <util/XmlBCfg.h>
//

namespace org { namespace xmlBlaster { namespace util {

/**
 * Normally stl containers store values, i.e. if you pass an entry to a container, the entry is first
 * copied and then put into the container. When you retrieve the entry it is copied again. In cases you 
 * want to allocate the objects outside the container and store the references of such objects in the 
 * container (i.e. the container is not the owner of the object), you could use pointers. The drawback of
 * using pointers however is that you loose the comparison operator. This class allows you to store the 
 * objects as references and at the same time maintains the correct comparison between entries in the
 * container.
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

template <class T> class Dll_Export ReferenceHolder
{
private: 
   T* element_;

   void init() const
   {
      if (!element_) return;
//      if (!element_->isShareable()) {
//         element_ = new T(*element_);
         // throw an exception here since the class could have virtual methods.
//      }
      element_->addReference();
   }


public:

   ReferenceHolder(T* el)
   {
      element_ = el;
      init();
   }

   ReferenceHolder(T& el) : element_(&el)
   {
      init();
   }

   ReferenceHolder(const ReferenceHolder& refHolder)
      : element_(refHolder.element_)
   {
      init();
   }

   ReferenceHolder& operator =(const ReferenceHolder& refHolder)
   {
      if (element_ != refHolder.element_) {
         if (element_) element_->removeReference();
         element_ = refHolder.element_;
         init();
      }
      return *this;
   }

   
   ~ReferenceHolder()
   {
      if (element_) element_->removeReference();
   }


   T& operator *() const 
   {
      return *element_;
   }

   T* operator->() const 
   {
      return element_;
   }

   T* getElement() const
   {
      return element_;
   }

   bool isNull() const
   {
      return element_ == 0;
   }

   bool operator ==(const ReferenceHolder<T> other) const
   {
      return *element_ == *other.element_;
   }

   bool operator <(const ReferenceHolder<T> other) const
   {
      return *element_ < *other.element_;
   }

   /*
   bool operator >(const ReferenceHolder<T> other) const
   {
      return !(this->operator<(other)) && !(this->operator==(other));
   }
   */

   bool operator >(const ReferenceHolder<T> other) const
   {
      return !(*element_ < *other.element_) && !(*element_ == *other.element_);
   }

//   friend bool operator== (const ReferenceHolder<T>& lhs, const ReferenceHolder<T>& rhs);

//   friend bool operator< (const ReferenceHolder<T>& lhs, const ReferenceHolder<T>& rhs);

};

/*
template <class T> 
inline bool operator== (const ReferenceHolder<T>&  lhs, const ReferenceHolder<T>& rhs)
{
   return *lhs.element_ == *rhs.element_;
}

template <class T> 
inline bool operator< (const ReferenceHolder<T>&  lhs, const ReferenceHolder<T>& rhs)
{
   return *lhs.element_ < *rhs.element_;
}
*/

}}} // namespace

#endif
