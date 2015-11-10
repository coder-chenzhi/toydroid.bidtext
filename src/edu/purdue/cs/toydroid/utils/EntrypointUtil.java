package edu.purdue.cs.toydroid.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.classLoader.DexIRFactory;
import com.ibm.wala.dalvik.ipa.callgraph.impl.DexEntryPoint;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;

public class EntrypointUtil {
	private static Logger logger = LogManager.getLogger(EntrypointUtil.class);

	private static IClassHierarchy classHierarchy;
	private static Set<IMethod> uncalledMethods = new HashSet<IMethod>();
	private static Set<String> processedClasses = new HashSet<String>();
	private static Set<String> discoveredEntrypoints = new HashSet<String>();
	private static List<Entrypoint> allEntrypoints = new LinkedList<Entrypoint>();
	private static int nEntrypoints = 0;

	private static final String activityTypeString = "Landroid/app/Activity";
	private static final String serviceTypeString = "Landroid/app/Service";
	private static final String receiverTypeString = "Landroid/content/BroadcastReceiver";
	private static final String providerTypeString = "Landroid/content/ContentProvider";
	private static final String applicationTypeString = "Landroid/app/Application";
	private static final List<String> compomentTypeStrings = Arrays.asList(
			activityTypeString, serviceTypeString, receiverTypeString,
			providerTypeString, applicationTypeString);

	public static void addEntrypoint(Entrypoint ep) {
		allEntrypoints.add(ep);
		nEntrypoints++;
		logger.debug("Discovered entrypoint: {}::{}", ep.getMethod()
				.getDeclaringClass()
				.getName()
				.toString(), ep.getMethod().getName().toString());
	}

	/**
	 * Every time remove an entrypoint from records. After all are iterated, no
	 * more entries can be retrieved.
	 * 
	 * @return
	 */
	public static Entrypoint nextEntrypoint() {
		if (allEntrypoints.isEmpty())
			return null;
		return allEntrypoints.remove(0);
	}

	public static int allEntrypointCount() {
		return nEntrypoints;
	}

	public static void initialEntrypoints(IClassHierarchy ich,
			Set<String> compClasses) {
		classHierarchy = ich;
		for (IClass k : ich) {
			if (k.getClassLoader()
					.getReference()
					.equals(ClassLoaderReference.Application)) {
				String kName = k.getName().toString();
				if (compClasses.contains(kName)) {
					initialEntrypoints(ich, k);
				}
			}
		}
		logger.info("Initial entrypoints: {}", allEntrypoints.size());
	}

	private static void initialEntrypoints(IClassHierarchy ich, IClass clazz) {
		IClass comp = isAndroidComponent(clazz);
		if (comp != null) {
			Collection<IMethod> methods = clazz.getDeclaredMethods();
			for (IMethod method : methods) {
				String sig = method.getSignature();
				if (method.isPrivate() || discoveredEntrypoints.contains(sig)) {
					continue;
				}
				String mName = method.getName().toString();
				if (mName.startsWith("on") && overridingFramework(comp, method)) {
					discoveredEntrypoints.add(sig);
					addEntrypoint(new DexEntryPointWithInit(method, ich));
				}
			}
		}
	}

	public static void discoverNewEntrypoints(AnalysisScope scope) {
		AnalysisCache cache = new AnalysisCache(new DexIRFactory());
		List<Entrypoint> epList = new LinkedList<Entrypoint>();
		IProgressMonitor pm = new NullProgressMonitor();
		epList.addAll(allEntrypoints);
		Set<IMethod> cachedUncalled = new HashSet<IMethod>();
		Set<IMethod> newUncalled = new HashSet<IMethod>();
		int round = 1;

		while (!epList.isEmpty()) {
			logger.info("Entrypoint discovery round-{}: {} entrypoints.",
					round++, epList.size());
			int origNUncalled = uncalledMethods.size();
			AnalysisOptions options = new AnalysisOptions(scope, epList);
			options.setReflectionOptions(ReflectionOptions.NONE);
			SSAPropagationCallGraphBuilder cgBuilder = Util.makeZeroCFABuilder(
					options, cache, classHierarchy, scope);
			try {
				CallGraph cg = cgBuilder.makeCallGraph(options, pm);
				for (CGNode n : cg) {
					IMethod m = n.getMethod();
					String mName = m.getName().toString();
					if (m.isPrivate() || m.isSynthetic()) {
						continue;
					} else if ("<init>".equals(mName)) {
						processClass(m.getDeclaringClass());
					}
				}

				for (CGNode n : cg) {
					IMethod m = n.getMethod();
					String mName = m.getName().toString();
					if (m.isPrivate() || m.isSynthetic()
							|| cg.getEntrypointNodes().contains(n)
							|| "<init>".equals(mName)) {
						continue;
					}
					if (uncalledMethods.contains(m)) {
						uncalledMethods.remove(m);
					}
				}
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (CallGraphBuilderCancelException e) {
				e.printStackTrace();
			}

			if (origNUncalled == uncalledMethods.size()) {
				break;
			}
			newUncalled.addAll(uncalledMethods);
			newUncalled.removeAll(cachedUncalled);
			cachedUncalled.addAll(uncalledMethods);

			// only build a big CG for newly discovered entries. this way may
			// discover fewer new entries than building CG with all discovered
			// entries as a whole
			epList.clear();
			for (IMethod m : newUncalled) {
				epList.add(new DexEntryPointWithInit(m, classHierarchy));
			}
			newUncalled.clear();
		}
		cachedUncalled.clear();
		cachedUncalled = null;
		epList = null;
		newUncalled = null;
		for (IMethod m : uncalledMethods) {
			discoveredEntrypoints.add(m.getSignature());
			addEntrypoint(new DexEntryPointWithInit(m, classHierarchy));
		}
		logger.info("Newly discovered entrypoints: {}", uncalledMethods.size());
		uncalledMethods.clear();
	}

	private static void processClass(IClass clazz) {
		if (!clazz.getClassLoader()
				.getReference()
				.equals(ClassLoaderReference.Application)) {
			return;
		}
		String cName = clazz.getName().toString();
		if (cName.startsWith("Ljava/util/") || cName.startsWith("Ljava/lang/")) {
			return;
		}
		if (processedClasses.contains(cName)) {
			return;
		}
		processedClasses.add(cName);

		Set<IClass> primordialClasses = new HashSet<IClass>();
		allPrimodialParent(clazz, primordialClasses);
		for (IMethod m : clazz.getDeclaredMethods()) {
			String sig = m.getSignature();
			if (m.isPrivate() || m.isSynthetic()
					|| m.getName().toString().equals("<init>")
					|| discoveredEntrypoints.contains(sig)) {
				continue;
			}
			if (overridingFramework(primordialClasses, m)) {
				uncalledMethods.add(m);
			}
		}
	}

	private static IClass isAndroidComponent(IClass clazz) {
		IClass comp = null;
		boolean found = false;
		IClass s = clazz.getSuperclass();
		while (s != null) {
			if (comp == null
					&& s.getClassLoader()
							.getReference()
							.equals(ClassLoaderReference.Primordial)) {
				comp = s;
			}
			String sName = s.getName().toString();
			if (compomentTypeStrings.contains(sName)) {
				found = true;
				break;
			}
			s = s.getSuperclass();
		}
		if (!found)
			comp = null;
		return comp;
	}

	/**
	 * 
	 * @param clazz
	 * @param pps
	 *            - return values
	 */
	private static void allPrimodialParent(IClass clazz, Set<IClass> pps) {
		IClass s = clazz.getSuperclass();
		while (s != null) {
			if (s.getName().toString().equals("Ljava/lang/Object")) {
				break;
			}
			if (s.getClassLoader()
					.getReference()
					.equals(ClassLoaderReference.Primordial)) {
				pps.add(s);
				break;
			} else {
				s = s.getSuperclass();
			}
		}
		Collection<IClass> ifs = clazz.getAllImplementedInterfaces();
		for (IClass k : ifs) {
			if (k.getClassLoader()
					.getReference()
					.equals(ClassLoaderReference.Primordial)) {
				if (!k.getName().toString().equals("Ljava/lang/Object")) {
					pps.add(k);
				}
			} else {
				allPrimodialParent(k, pps);
			}
		}
	}

	private static boolean overridingFramework(Set<IClass> primordialList,
			IMethod method) {
		for (IClass k : primordialList) {
			if (overridingFramework(k, method)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overridingFramework(IClass compClass, IMethod method) {
		Selector s = method.getSelector();
		IMethod m = compClass.getMethod(s);
		boolean overriding = false;
		if (m != null
				&& !m.getDeclaringClass()
						.getName()
						.toString()
						.equals("Ljava/lang/Object")) {
			overriding = true;
		}
		return overriding;
	}

	private static Collection<IMethod> getAllInitWithParam(IClass klass) {
		ArrayList<IMethod> result = new ArrayList<IMethod>();
		for (IMethod m : klass.getDeclaredMethods()) {
			if (!m.getName().toString().equals("<init>")
					|| m.getNumberOfParameters() <= 1)
				continue;
			// now we got an <init> with parameter(s)
			result.add(m);
		}
		return result;
	}

	public static Entrypoint findInitWithMostParams(Entrypoint ep,
			IClassHierarchy cha) {
		IMethod bestInit = null;
		int longestLength = 0;
		IClass dxc = ep.getMethod().getDeclaringClass();
		Collection<IMethod> inits = getAllInitWithParam(dxc);
		for (IMethod m : inits) {
			if (m.getNumberOfParameters() > longestLength)
				bestInit = m;
		}
		if (bestInit != null)
			return new DefaultEntrypoint(bestInit, cha);
		else
			return null;
	}

}
