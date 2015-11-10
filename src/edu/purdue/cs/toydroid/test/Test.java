package edu.purdue.cs.toydroid.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.MethodEntryStatement;
import com.ibm.wala.ipa.slicer.MethodExitStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;

public class Test {

	public static void main(String[] args) throws Exception {
		Iterable<Entrypoint> entrypoints;
		AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(
				"bin/", null);
		ClassHierarchy cha = ClassHierarchy.make(scope);
		Iterator<IClass> iter = cha.iterator();
		int i = 0;
		while (iter.hasNext()) {
			IClass ik = iter.next();
			if (ik.getClassLoader()
					.getReference()
					.equals(ClassLoaderReference.Application)) {
				System.out.format("[%3d] %s\n", i++, ik.getName().toString());
				System.out.println(ik.getDeclaredMethods());
			}
		}
		entrypoints = Util.makeMainEntrypoints(scope, cha, "Ledu/purdue/cs/toydroid/test/TestCase");
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
		CallGraphBuilder builder = Util.makeRTABuilder(options, new AnalysisCache(), cha, scope);
		CallGraph cg = builder.makeCallGraph(options, null);
		System.out.println(CallGraphStats.getStats(cg));
		Iterator<CGNode> cgIter = cg.iterator();
		while (cgIter.hasNext()) {
			CGNode cn = cgIter.next();
			System.out.println(cn.getMethod());
		}
	}

}
