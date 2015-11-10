package edu.purdue.cs.toydroid.bidtext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
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
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
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
import edu.purdue.cs.toydroid.utils.Stat;
import edu.purdue.cs.toydroid.utils.WalaUtil;

public class TextLeak implements Callable<TextLeak> {
	private static Logger logger = LogManager.getLogger(TextLeak.class);

	public static boolean taskTimeout = false;
	private static long timeout = 20;
	private static long taskStart;

	//static String ApkFile = "E:\\Eclipse-Workspace\\TestAndroidAct\\bin\\TestAndroidAct.apk";
	static String ApkFile = "E:\\x\\y\\AM-com.nitrogen.android-221000000.apk";

	public static void main(String[] args) {
		taskStart = System.currentTimeMillis();
		if (args.length == 1) {
			ApkFile = args[0];
		}
		try {
			doAnalysis(ApkFile);
		} catch (Throwable e) {
			logger.error("Crashed: {}", e.getMessage());
			e.printStackTrace();
		}
		long end = System.currentTimeMillis();
		String time = String.format("%d.%03d", (end - taskStart) / 1000,
				(end - taskStart) % 1000);
		logger.info("Total Time: {} seconds.", time);
		System.exit(0);
	}

	public static void doAnalysis(String apk) throws Throwable {
		logger.info("Start Analysis...");
		TextLeak analysis = new TextLeak(apk);
		Future<TextLeak> future = Executors.newSingleThreadExecutor().submit(
				analysis);
		try {
			try {
				future.get(timeout, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				logger.error("InterruptedException: {}", e.getMessage());
				e.printStackTrace();
			} catch (ExecutionException e) {
				logger.error("ExecutionException: {}", e.getMessage());
				e.printStackTrace();
			} catch (TimeoutException e) {
				taskTimeout = true;
				logger.warn("Analysis Timeout after {} {}!", timeout,
						TimeUnit.MINUTES);
			}
		} catch (Throwable t) {
			logger.error("Crashed: {}", t.getMessage());
			t.printStackTrace();
		}
		if (!future.isDone()) {
			future.cancel(true);
		}
		//Stat.dumpStat(ApkFile);
		long analysisEnd = System.currentTimeMillis();
		String time = String.format("%d.%03d",
				(analysisEnd - taskStart) / 1000,
				(analysisEnd - taskStart) % 1000);
		logger.info("Total Analysis Time: {} seconds.", time);
		// if (analysis.hasResult()) {
		// analysis.dumpResult();
		// }
		// analysis.dumpCgStat();
	}

	private String apkFile;
	private AnalysisScope scope;
	private ClassHierarchy cha;
	private Map<Entrypoint, TypingGraph> ep2Graph;

	public TextLeak(String apk) {
		apkFile = apk;
		ep2Graph = new HashMap<Entrypoint, TypingGraph>();
	}

	@Override
	public TextLeak call() throws Exception {
		initialize();
		analyze();
		return this;
	}

	private void initialize() throws Exception {
		scope = AnalysisScopeUtil.makeAnalysisScope(apkFile);
		cha = ClassHierarchy.make(scope);

		WalaUtil.setClassHierarchy(cha);
		ResourceUtil.parse(apkFile, cha);
		Set<String> compClasses = ResourceUtil.getComponentClasses();

		EntrypointUtil.initialEntrypoints(cha, compClasses);
		EntrypointUtil.discoverNewEntrypoints(scope);
	}

	private void analyze() throws Exception {
		Entrypoint entrypoint;
		SSAPropagationCallGraphBuilder cgBuilder;
		CallGraph cg;

		AnalysisCache cache = new AnalysisCache(new DexIRFactory());
		ArrayList<Entrypoint> epList = new ArrayList<Entrypoint>(1);
		int nEntrypoints = EntrypointUtil.allEntrypointCount();
		int idxEntrypoint = 1;

		while (null != (entrypoint = EntrypointUtil.nextEntrypoint())) {
			if (taskTimeout) {
				break;
			}
			String epSig = entrypoint.getMethod().getSignature();
			
			logger.info("Process entrypoint ({}/{}) {}", idxEntrypoint++,
					nEntrypoints, epSig);
			epList.add(entrypoint);
			AnalysisOptions options = new AnalysisOptions(scope, epList);
			options.setReflectionOptions(ReflectionOptions.NONE);
			cgBuilder = Util.makeVanillaNCFABuilder(1, options, cache, cha,
					scope); // Util.makeZeroCFABuilder(options, cache, cha,
							// scope);
			logger.info(" * Build CallGraph");
			cg = cgBuilder.makeCallGraph(options, null);
			// analyzeEntrypoint(entrypoint, cg);
//			if (Stat.statCG(cg)) {
//				continue;
//			}
			if (taskTimeout) {
				break;
			}

			// System.out.println("++++++++++++++");
			logger.info(" * CG size: {}", cg.getNumberOfNodes());
			// dumpCG(cg);

			logger.info(" * Build SDG");
			SDG sdg = new SDG(cg, cgBuilder.getPointerAnalysis(),
					DataDependenceOptions.NO_BASE_NO_EXCEPTIONS,
					ControlDependenceOptions.NONE);
			int nNodesInSDG = sdg.getNumberOfNodes();
			if (nNodesInSDG > 10000000) {// 10 million
				logger.warn(" * Too big SDG ({}). Ignore it.", nNodesInSDG);
				continue;
			} else if (nNodesInSDG > 1000000) { // 1 million
				logger.warn(
						" * Too big SDG ({}). Use context-insensitive builder.",
						nNodesInSDG);
				// dumpSDG(pruneSDG(sdg));
				cgBuilder = Util.makeVanillaZeroOneCFABuilder(options, cache,
						cha, scope);
				cg = cgBuilder.makeCallGraph(options, null);
				sdg = new SDG(cg, cgBuilder.getPointerAnalysis(),
						DataDependenceOptions.NO_BASE_NO_EXCEPTIONS,
						ControlDependenceOptions.NONE);
			}
			if (taskTimeout) {
				break;
			}
			logger.info(" * SDG size before pruning: {}",
					sdg.getNumberOfNodes());
			Graph<Statement> g = pruneSDG(sdg);
			logger.info(" * SDG size after pruning: {}", g.getNumberOfNodes());
			// dumpSDG(g);
			// if (Main.DEBUG) {
			// DotUtil.dotify(g, WalaUtil.makeNodeDecorator(),
			// entrypoint.getMethod().getName().toString() + ".dot", null,
			// null);
			// }

			logger.info(" * Build TypingGraph");
			TypingGraphUtil.buildTypingGraph(entrypoint, cg, g, cha);

			epList.clear();
		}

		AnalysisUtil.dumpTextForSinks();

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
					if (sdg.getPredNodeCount(t) == 0) {
						return false;
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

}
