package edu.purdue.cs.toydroid.utils;

import com.ibm.wala.analysis.typeInference.ConeType;
import com.ibm.wala.analysis.typeInference.PrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dalvik.ipa.callgraph.impl.DexEntryPoint;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

public class DexEntryPointWithInit extends DexEntryPoint {

	public DexEntryPointWithInit(IMethod method, IClassHierarchy cha) {
		super(method, cha);
	}

	public DexEntryPointWithInit(MethodReference method, IClassHierarchy cha) {
		super(method, cha);
	}

	protected int makeArgument(AbstractRootMethod m, int i) {
		if (i == 0) {
			Entrypoint initEp = EntrypointUtil.findInitWithMostParams(this, getCha());
			if (initEp != null) {
				SSAAbstractInvokeInstruction call = initEp.addCall(m);
				if (call != null) {
					return call.getUse(0);
				}
			}
		}
		TypeReference[] p = getParameterTypes(i);
		if (p.length == 0) {
			return -1;
		} else if (p.length == 1) {
			if (p[0].isPrimitiveType()) {
				return m.addLocal();
			} else {
				SSANewInstruction n = m.addAllocation(p[0]);
				return (n == null) ? -1 : n.getDef();
			}
		} else {
			int[] values = new int[p.length];
			int countErrors = 0;
			for (int j = 0; j < p.length; j++) {
				SSANewInstruction n = m.addAllocation(p[j]);
				int value = (n == null) ? -1 : n.getDef();
				if (value == -1) {
					countErrors++;
				} else {
					values[j - countErrors] = value;
				}
			}
			if (countErrors > 0) {
				int[] oldValues = values;
				values = new int[oldValues.length - countErrors];
				System.arraycopy(oldValues, 0, values, 0, values.length);
			}

			TypeAbstraction a;
			if (p[0].isPrimitiveType()) {
				a = PrimitiveType.getPrimitive(p[0]);
				for (i = 1; i < p.length; i++) {
					a = a.meet(PrimitiveType.getPrimitive(p[i]));
				}
			} else {
				IClassHierarchy cha = m.getClassHierarchy();
				IClass p0 = cha.lookupClass(p[0]);
				a = new ConeType(p0);
				for (i = 1; i < p.length; i++) {
					IClass pi = cha.lookupClass(p[i]);
					a = a.meet(new ConeType(pi));
				}
			}

			return m.addPhi(values);
		}
	}

}
