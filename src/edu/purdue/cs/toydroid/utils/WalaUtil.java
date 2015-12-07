package edu.purdue.cs.toydroid.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapParamCallee;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapParamCaller;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapReturnCallee;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapReturnCaller;
import com.ibm.wala.ipa.slicer.NormalReturnCallee;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.NodeDecorator;

public class WalaUtil {
	private static Logger logger = LogManager.getLogger(WalaUtil.class);

	private static ClassHierarchy cha;

	public static void setClassHierarchy(ClassHierarchy ch) {
		cha = ch;
	}

	public static String getSignature(SSAAbstractInvokeInstruction instr) {
		MethodReference mRef = instr.getDeclaredTarget();
		return getSignature(mRef);
	}

	public static String getSignature(MethodReference mRef) {
		if (cha == null) {
			logger.warn("Without proper ClassHierarchy set, the signature might be incorrect.");
			return mRef.getSignature();
		}
		IMethod m = cha.resolveMethod(mRef);
		if (m != null) {
			return m.getSignature();
		}
		return mRef.getSignature();
	}

	public static String stringForStmt(Statement n) {
		Kind k = n.getKind();
		StringBuilder builder = new StringBuilder(k.toString());
		builder.append("\n");
		builder.append(n.getNode());
		builder.append("\n");
		switch (k) {
			case NORMAL:
				NormalStatement ns = (NormalStatement) n;
				builder.append(ns.getInstruction().toString());
				break;
			case PARAM_CALLER:
				ParamCaller pcr = (ParamCaller) n;
				builder.append(pcr.getInstruction().toString());
				builder.append("\n v" + pcr.getValueNumber());
				break;
			case PARAM_CALLEE:
				ParamCallee pce = (ParamCallee) n;
				builder.append("v" + pce.getValueNumber());
				break;
			case NORMAL_RET_CALLER:
				NormalReturnCaller nrcr = (NormalReturnCaller) n;
				builder.append(nrcr.getInstruction().toString());
				break;
			case NORMAL_RET_CALLEE:
				break;
			case HEAP_PARAM_CALLER:
				HeapParamCaller hpcr = (HeapParamCaller) n;
				builder.append(hpcr.getCall().toString());
				builder.append("\n");
				builder.append(hpcr.getLocation().hashCode());
				break;
			case HEAP_RET_CALLER:
				HeapReturnCaller hrcr = (HeapReturnCaller) n;
				builder.append(hrcr.getCall().toString());
				builder.append("\n");
				builder.append(hrcr.getLocation().hashCode());
				break;
			case HEAP_PARAM_CALLEE:
			case HEAP_RET_CALLEE:
				HeapStatement hse = (HeapStatement) n;
				builder.append(hse.getLocation().hashCode());
				break;
			case PHI:
				PhiStatement phiStmt = (PhiStatement) n;
				builder.append(phiStmt.getPhi().toString());
				break;
			default:
				builder.append("\n Unsupported");
				break;
		}
		return builder.toString();
	}

	public static NodeDecorator<Statement> makeNodeDecorator() {
		return new NodeDecorator<Statement>() {

			@Override
			public String getLabel(Statement n) throws WalaException {
				return stringForStmt(n);
			}

		};
	}

	public static boolean isAPI(ParamCaller stmt) {
		return isAPI(stmt.getInstruction().getDeclaredTarget());
	}

	public static boolean isAPI(NormalReturnCaller stmt) {
		return isAPI(stmt.getInstruction().getDeclaredTarget());
	}

	public static boolean isAPI(MethodReference mRef) {
		try {
			IMethod m = cha.resolveMethod(mRef);
			if (m.getDeclaringClass()
					.getClassLoader()
					.getReference()
					.equals(ClassLoaderReference.Primordial)) {
				return true;
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}
}
