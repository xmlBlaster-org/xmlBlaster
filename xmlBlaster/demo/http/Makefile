#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for SystemInfo Servlet demo
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/http
SRC_JAVA_DIR = $(XMLBLASTER_HOME)/demo/http
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
SUB_DIRS = dhtml/systemInfo
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = 
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
        $(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all: all_SUB_DIRS precond java

precond:
        $(MKDIR_RECURSIVE) $(CLASS_DIR)
        $(RM_RECURSIVE) javascript 
        $(CP_RECURSIVE) ../../src/java/org/xmlBlaster/protocol/http/javascript javascript
        $(RM_RECURSIVE) persistentWindow
        $(CP_RECURSIVE) ../../src/java/org/xmlBlaster/protocol/http/persistentWindow persistentWindow
        $(CP) ../../doc/howto/xmlBlaster.css .

java: $(LOCAL_FILES)
        @echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean: clean_SUB_DIRS
        @echo "Removing all in $(CLASS_DIR) ..."
        $(RM) $(CLASS_DIR)/*.*

