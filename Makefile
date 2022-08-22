JAVA=$(HOME)/local/jdk-classfile/bin/java
# Note: currently need the impl package for
# j.cf.impl.LabelResolver.labelToBci().  tclj-in-tclj generates a
# faulty invoke with an owner of LabelResolver instead of CodeModel
JAVA_OPTS=--enable-preview \
  --add-exports java.base/jdk.classfile=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.constantpool=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.instruction=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.attribute=ALL-UNNAMED \
  --add-exports java.base/jdk.classfile.impl=ALL-UNNAMED
#JAVA_OPTS += -XX:+UseZGC -Xlog:gc
#-Djdk.tracePinnedThreads
TCLJ_MDIR=../jvm-stuff/bootstrap-tclj
#TCLJ_MDIR=../jvm-stuff/tclj-in-tclj/target/distrib/mods
#DET=--deterministic
TCLJ=$(JAVA) --module-path $(TCLJ_MDIR) $(JAVA_OPTS) -m tinyclj.compiler $(DET)

BOOTSTRAP_TCLJ=$(JAVA) --class-path $(TCLJ_MDIR)/tinyclj.rt:$(TCLJ_MDIR)/tinyclj.core:$(TCLJ_MDIR)/tinyclj.compiler $(JAVA_OPTS) tinyclj.build.main.__ns $(DET) --parent-loader :platform
BOOTSTRAP_RUN=$(JAVA) --class-path $(TCLJ_MDIR)/tinyclj.rt:$(DEST_DIR):resources

MAIN_NS=tcljc.core
RUN_TESTS_NS=tcljc.run-tests

# $(DEST_DIR) matches the compiler's default destination directory
PROJECT_DIR=$(notdir $(PWD))
DEST_DIR=/tmp/$(USER)/tinyclj/$(PROJECT_DIR)

compile:
	$(TCLJ) $(RUN_TESTS_NS)
watch-and-compile:
	$(TCLJ) --watch $(RUN_TESTS_NS)

run:
	$(TCLJ) -d :none $(RUN_TESTS_NS) $(MAIN_NS)/run
watch-and-run:
	$(TCLJ) --watch $(RUN_TESTS_NS) $(MAIN_NS)/run

# Call with "make test TEST=<scope>" (with <scope> being "ns-name" or
# "ns-name/var-name") to only run tests from the given namespace or
# var.  Only call this after compile, possibly while one of the
# watch-and-xxx targets is running.
test:
	$(JAVA) --module-path $(TCLJ_MDIR) $(JAVA_OPTS) --add-modules tinyclj.core -cp $(DEST_DIR):resources $(RUN_TESTS_NS).__ns
watch-and-test:
	$(TCLJ) --watch $(RUN_TESTS_NS)/run

run-main:
	$(JAVA) --module-path $(TCLJ_MDIR) $(JAVA_OPTS) --add-modules tinyclj.core -cp $(DEST_DIR):resources $(MAIN_NS).__ns

# Compilation of tinyclj.core needs the bootstrap setup: place modules
# in classpath so that they do not interfere with the compilation of
# this special namespace.
# test-core:
# 	$(JAVA) --class-path $(TCLJ_MDIR)/tinyclj.rt:$(TCLJ_MDIR)/tinyclj.core:$(DEST_DIR):resources $(JAVA_OPTS) tcljc.run-core.__ns
watch-and-test-core:
	$(BOOTSTRAP_TCLJ) --watch -s $(TCLJ_MDIR)/tinyclj.rt -s $(TCLJ_MDIR)/tinyclj.core -s src -s test tcljc.run-core/run

clean:
	rm -rf "$(DEST_DIR)"/* "$(DEST_DIR)".* *.class hs_err_pid*.log replay_pid*.log

print-line-count:
	find src/tcljc -name "*.cljt" | xargs wc -l | sort -n

.PHONY: compile watch-and-compile run watch-and-run test watch-and-test run-main clean


JAVAC=$(HOME)/local/jdk-classfile/bin/javac
JAVAP=$(HOME)/local/jdk-classfile/bin/javap
print-javap:
	rm -f *.class
	$(JAVAC) $(JAVA_OPTS) -source 20 Hello.java
	$(JAVAP) -v -p *.class

run-rtiow-nocore: IMAGE=$(DEST_DIR).rtiow-nocore/ray.ppm
run-rtiow-nocore:
	$(JAVA) --module-path $(TCLJ_MDIR) --add-modules tinyclj.rt -cp $(DEST_DIR).rtiow-nocore tcljc.rtiow-nocore-ref.__ns >$(IMAGE)
	md5sum $(IMAGE)
#	xdg-open $(IMAGE)

run-rtiow: IMAGE=$(DEST_DIR).rtiow/ray.ppm
run-rtiow:
	$(JAVA) --module-path $(TCLJ_MDIR) --add-modules tinyclj.rt -cp $(DEST_DIR).core tcljc.rtiow-ref.__ns >$(IMAGE)
	md5sum $(IMAGE)
#	xdg-open $(IMAGE)
