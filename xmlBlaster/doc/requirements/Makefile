#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for html output of xml based requirements
#           See README how to do it manually
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/doc/requirements
SRC_JAVA_DIR = $(SRC_DOCS_DIR)/requirements
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = \
			$(CLASS_DIR)/CollectXml.class

XML_FILES = \
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
	$(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

requirement_files := $(patsubst %.xml,%.html,$(wildcard *.xml))


# all.html will be generated as well (how to suppress?)
$(requirement_files): %.html: %.xml
	java -Dcom.jclark.xsl.sax.parser=com.jclark.xsl.dom.SunXMLProcessorImpl \
	     com.jclark.xsl.sax.Driver \
		  $< detail.xsl $@

all: precond java collect overview $(requirement_files)
	$(RM) all.html

doc: all

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mCompiled $(SRC_JAVA_DIR)\033[0m";

clean:
	@echo "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*
	$(RM) all.xml
	$(RM) *.html

collect:
	java doc.requirements.CollectXml
	@echo -e "\033[1mDONE ............ \033[32;40mCreated all.xml\033[0m";

overview:
	java -Dcom.jclark.xsl.sax.parser=com.jclark.xsl.dom.SunXMLProcessorImpl com.jclark.xsl.sax.Driver all.xml overview.xsl requirement.html
	#java -Dcom.jclark.xsl.sax.parser=com.jclark.xsl.dom.SunXMLProcessorImpl com.jclark.xsl.sax.Driver all.xml html.xsl requirement.html
	@echo -e "\033[1mDONE ............ \033[32;40mCreated requirement.html\033[0m";

