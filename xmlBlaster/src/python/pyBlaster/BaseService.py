# Based on BaseService module from LOGILAB
# just a little bit optimized / shortend for actual usage

# Copyright (c) 2000-2001 LOGILAB S.A. (Paris, FRANCE).
# http://www.logilab.fr/ -- mailto:contact@logilab.fr
#
# This program is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free Software
# Foundation; either version 2 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with
# this program; if not, write to the Free Software Foundation, Inc.,
# 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

import threading
import time

# Base Service #################################################################

class BaseService:
    """Base service"""
    
    def __init__(self,name=None) :
        self.thread = None
        self.threadName = name or 'BaseService'
        self.loop = 0

    def start(self) :
        self.thread = threading.Thread(name=self.threadName, target=self._run)
        self.loop = 1
        self.thread.start()
        print 'BaseService message:: ',self.threadName,'started'

    def stop(self) :
        self.loop = 0
        self.thread.join()
        print 'BaseService message:: ',self.threadName,'stopped'
        
    def _run(self) :
        while self.loop :
            time.sleep(1)
            print self.threadName, 'loop...'

