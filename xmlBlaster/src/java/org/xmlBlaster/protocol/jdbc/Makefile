#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/protocol/jdbc
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/protocol/jdbc
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES   := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
        $(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all:    precond java

precond:
        $(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
        @echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean:
        @echo "Removing all in $(CLASS_DIR) ..."
        $(RM) $(CLASS_DIR)/*.*

#doc:
#       $(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
