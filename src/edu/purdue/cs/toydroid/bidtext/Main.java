package edu.purdue.cs.toydroid.bidtext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.viz.DotUtil;

import edu.purdue.cs.toydroid.bidtext.analysis.AnalysisUtil;
import edu.purdue.cs.toydroid.bidtext.graph.TypingGraph;
import edu.purdue.cs.toydroid.bidtext.graph.TypingGraphUtil;
import edu.purdue.cs.toydroid.utils.AnalysisScopeUtil;
import edu.purdue.cs.toydroid.utils.EntrypointUtil;
import edu.purdue.cs.toydroid.utils.ResourceUtil;
import edu.purdue.cs.toydroid.utils.SimpleCounter;
import edu.purdue.cs.toydroid.utils.WalaUtil;

public class Main {

	private static Logger logger = LogManager.getLogger(Main.class);

	public static boolean DEBUG = false;

	// String apkFile = "E:\\com.weatheruniversalforecast-2.apk";
	// String apkFile = "E:\\com.gwhizmobile.barrons_essential_gre-3.apk";
	String apkFile = "E:\\Eclipse-Workspace\\TestAndroidAct\\bin\\TestAndroidAct.apk";
	// String apkFile = "E:\\com.algeo.cref-8.apk";
	// String apkFile = "E:\\com.weatheruniversalforecast-2.apk";
	AnalysisScope scope;
	ClassHierarchy cha;
	Map<Entrypoint, TypingGraph> ep2Graph;

	public Main() {
		ep2Graph = new HashMap<Entrypoint, TypingGraph>();
	}

	public void startAnalysis() throws Exception {
		logger.info("Analysis starts for {}", apkFile);
		// URI[] androidLibs = new URI[0];
		// scope = AndroidAnalysisScope.setUpAndroidAnalysisScope(
		// new File(apkFile).toURI(), "AndroidRegressionExclusions.txt",
		// androidLibs);
		scope = AnalysisScopeUtil.makeAnalysisScope(apkFile);
		cha = ClassHierarchy.make(scope);

		WalaUtil.setClassHierarchy(cha);
		ResourceUtil.parse(apkFile, cha);
		Set<String> compClasses = ResourceUtil.getComponentClasses();

		AnalysisCache cache = new AnalysisCache(new DexIRFactory());
		ArrayList<Entrypoint> epList = new ArrayList<Entrypoint>(1);

		EntrypointUtil.initialEntrypoints(cha, compClasses);
		EntrypointUtil.discoverNewEntrypoints(scope);

		Entrypoint entrypoint;
		SSAPropagationCallGraphBuilder cgBuilder;
		CallGraph cg;
		SDG sdg;
		long start = System.currentTimeMillis();
		long end = 0;
		while (null != (entrypoint = EntrypointUtil.nextEntrypoint())) {
			String epSig = entrypoint.getMethod().getSignature();
			logger.info("Process entrypoint {}", epSig);
			// if (Main.DEBUG
			// // &&
			// //
			// !epSig.startsWith("com.weatheruniversalforecast.service.AlarmReciever$LoadData_CityNameWise.doInBackground"))
			// // {
			// &&
			// !epSig.startsWith("com.zwdmtmkbjgejgsi.AdTask.doInBackground([Ljava/lang/Object;)Ljava/lang/Object;"))
			// {
			// epList.clear();
			// continue;
			// }
			epList.add(entrypoint);
			AnalysisOptions options = new AnalysisOptions(scope, epList);
			options.setReflectionOptions(ReflectionOptions.NONE);
			cgBuilder = Util.makeVanillaNCFABuilder(1, options, cache, cha,
					scope);
			// Util.makeZeroCFABuilder(
			// options, cache, cha, scope);
			logger.info(" * Build CallGraph");
			cg = cgBuilder.makeCallGraph(options, null);
			// analyzeEntrypoint(entrypoint, cg);

			// System.out.println("++++++++++++++");
			logger.info(" * CG size: {}", cg.getNumberOfNodes());
			// dumpCG(cg);

			logger.info(" * Build SDG");
			sdg = new SDG(cg, cgBuilder.getPointerAnalysis(),
					DataDependenceOptions.NO_BASE_NO_EXCEPTIONS,
					ControlDependenceOptions.NONE);
			if (sdg.getNumberOfNodes() > 1000000) { // 1 million
				logger.warn(
						" * Too big SDG ({}). Use context-insensitive builder.",
						sdg.getNumberOfNodes());
				// dumpSDG(pruneSDG(sdg));
				cgBuilder = Util.makeVanillaZeroOneCFABuilder(options, cache,
						cha, scope);
				cg = cgBuilder.makeCallGraph(options, null);
				sdg = new SDG(cg, cgBuilder.getPointerAnalysis(),
						DataDependenceOptions.NO_BASE_NO_EXCEPTIONS,
						ControlDependenceOptions.NONE);
			}
			logger.info(" * SDG size before pruning: {}",
					sdg.getNumberOfNodes());
			Graph<Statement> g = pruneSDG(sdg);
			logger.info(" * SDG size after pruning: {}", g.getNumberOfNodes());
			// dumpSDG(g);
			// if (Main.DEBUG) {
			 DotUtil.dotify(g, WalaUtil.makeNodeDecorator(),
			 entrypoint.getMethod().getName().toString() + ".dot",
			 null, null);
			// }

			logger.info(" * Build TypingGraph");
			TypingGraphUtil.buildTypingGraph(entrypoint, cg, g, cha);

			epList.clear();
			if (System.currentTimeMillis() - start >= 60 * 60 * 1000) { // 1
																		// hour
				break;
			}
		}

		AnalysisUtil.dumpTextForSinks();
		logger.info("Analysis ends for {}", apkFile);

		end = System.currentTimeMillis();
		long duration = end - start;
		logger.info("Elapsed Time: {}.{} seconds", duration / 1000,
				duration % 1000);

		/*
		 * AnalysisOptions options = new AnalysisOptions(scope, es);
		 * options.setReflectionOptions(ReflectionOptions.NONE);
		 * SSAPropagationCallGraphBuilder cgBuilder = Util.makeZeroCFABuilder(
		 * options, cache, cha, scope); CallGraph callGraph =
		 * cgBuilder.makeCallGraph(options, new NullProgressMonitor());
		 * Collection<CGNode> cns = callGraph.getEntrypointNodes(); for (CGNode
		 * cn : cns) { System.out.println(cn.getMethod().toString()); }
		 * System.out.println(callGraph.getFakeRootNode().getIR());
		 */
	}

	private void dumpCG(CallGraph cg) {
		for (CGNode n : cg) {
			if (n.getMethod()
					.getDeclaringClass()
					.getClassLoader()
					.getReference()
					.equals(ClassLoaderReference.Primordial)
					&& !n.getMethod().isSynthetic()) {
				// continue;
				logger.debug("Primor: {} - {}", n.getMethod().getSignature(),
						cg.getSuccNodeCount(n));
			} else
				logger.debug("CGNode: {} - {}", n.getMethod().getSignature(),
						cg.getSuccNodeCount(n));
			if (n.getMethod()
					.getDeclaringClass()
					.getName()
					.toString()
					.equals("Ljava/util/Timer")) {
				logger.debug("{}", n.getIR());
			} else if (n.getMethod().getName().toString().equals("onCreate")) {
				logger.debug("{}", n.getIR());
				SSAInstruction i = n.getIR()
						.getControlFlowGraph()
						.getInstructions()[17];
				Set<CGNode> cs = cg.getPossibleTargets(n,
						((SSAAbstractInvokeInstruction) i).getCallSite());
				for (CGNode c : cs) {
					System.out.println(c);
				}
			}
		}
	}

	private void dumpSDG(Graph<Statement> sdg) {
		Map<CGNode, SimpleCounter> map = new HashMap<CGNode, SimpleCounter>();
		for (Statement stmt : sdg) {
			CGNode n = stmt.getNode();
			SimpleCounter counter = map.get(n);
			if (counter == null) {
				counter = SimpleCounter.increment(counter);
				map.put(n, counter);
			} else {
				SimpleCounter.increment(counter);
			}
		}
		logger.debug("******************************");
		for (Map.Entry<CGNode, SimpleCounter> e : map.entrySet()) {
			logger.debug(e.getValue().count + "    "
					+ e.getKey().getMethod().getSignature());
		}
		logger.debug("******************************");
	}

	private Graph<Statement> pruneSDG(final SDG sdg) {
		return GraphSlicer.prune(sdg, new Predicate<Statement>() {

			@Override
			public boolean test(Statement t) {
				Statement.Kind k = t.getKind();
				/*
				 * if (t.getNode().equals(sdg.getCallGraph().getFakeRootNode()))
				 * { logger.debug("FakeRootNode: {}", k ); return false; } else
				 */if (k == Statement.Kind.METHOD_ENTRY
						|| k == Statement.Kind.METHOD_EXIT) {
					return false;
				} else if (t.getNode()
						.getMethod()
						.getDeclaringClass()
						.getClassLoader()
						.getReference()
						.equals(ClassLoaderReference.Primordial)
						&& (!t.getNode().getMethod().isSynthetic() || t.getNode()
								.getMethod()
								.getDeclaringClass()
								.getReference()
								.getName()
								.toString()
								.startsWith("Ljava/lang/"))) {
					return false;

				} else if (t.getNode()
						.getMethod()
						.getDeclaringClass()
						.getName()
						.toString()
						.startsWith("Landroid/support/v")) {
					return false;
				} else if (k == Statement.Kind.NORMAL) {
					NormalStatement ns = (NormalStatement) t;
					SSAInstruction inst = ns.getInstruction();
					if (inst instanceof SSAAbstractInvokeInstruction) {
						return false;
					} else if (inst instanceof SSAGetInstruction) {
						SSAGetInstruction getInst = (SSAGetInstruction) inst;
						if (getInst.isStatic()
								&& getInst.getDeclaredField()
										.getDeclaringClass()
										.getName()
										.toString()
										.equals("Ljava/lang/System")) {
							return false;
						}
					}
				} else if (k == Statement.Kind.PARAM_CALLER) {
					ParamCaller pcr = (ParamCaller) t;
					SSAAbstractInvokeInstruction inst = pcr.getInstruction();
					if (inst.getUse(0) != pcr.getValueNumber()
							&& sdg.getSuccNodeCount(t) == 1) {
						Statement callee = sdg.getSuccNodes(t).next();
						if (callee.getNode()
								.getMethod()
								.getDeclaringClass()
								.getClassLoader()
								.getReference()
								.equals(ClassLoaderReference.Primordial)) {
							return false;
						}
					}
				} else if (t instanceof HeapStatement) {
					HeapStatement hs = (HeapStatement) t;
					PointerKey pk = hs.getLocation();
					if (pk instanceof StaticFieldKey) {
						StaticFieldKey sfk = (StaticFieldKey) pk;
						if (sfk.getField()
								.getDeclaringClass()
								.getClassLoader()
								.getReference()
								.equals(ClassLoaderReference.Primordial)
								&& sfk.getField()
										.getDeclaringClass()
										.getName()
										.toString()
										.equals("Ljava/lang/System")) {
							return false;
						}
					}
				}

				return true;
			}
		});
	}

	public static void main(String[] args) throws Exception {
		Main main = new Main();
		if (args.length > 0) {
			main.apkFile = args[0];
		}
		main.startAnalysis();
		System.exit(0);
	}

}
