 module tinyclj.compiler {
  requires transitive tinyclj.core;
  
  exports tcljc.main;
  
  exports tcljc.classfile;
  exports tcljc.compiler;
  exports tcljc.compiler.adapt;
  exports tcljc.compiler.invoke;
  exports tcljc.compiler.resolve;
  exports tcljc.compiler.sigclass;
  exports tcljc.compiler.sigfn;
  exports tcljc.config;
  exports tcljc.context;
  exports tcljc.emitter;
  exports tcljc.emitter.bytecode;
  exports tcljc.emitter.classes;
  exports tcljc.emitter.emitfn;
  exports tcljc.emitter.exprcode;
  exports tcljc.emitter.namespace;
  exports tcljc.emitter.prepare;
  exports tcljc.expr;
  exports tcljc.exprfold;
  exports tcljc.grammar;
  exports tcljc.io;
  exports tcljc.javabase;
  exports tcljc.macro;
  exports tcljc.main.beachhead;
  exports tcljc.main.builder;
  exports tcljc.main.efmt;
  exports tcljc.main.invoke;
  exports tcljc.main.options;
  exports tcljc.main.publics;
  exports tcljc.main.task;
  exports tcljc.main.tclj0;
  exports tcljc.main.tclj1;
  exports tcljc.main.watcher;
  exports tcljc.parser;
  exports tcljc.predefined;
  exports tcljc.reader;
  exports tcljc.runtime;
  exports tcljc.synquote;
  exports tcljc.type;
  exports tcljc.util;
  exports tcljc.wrong;
}
