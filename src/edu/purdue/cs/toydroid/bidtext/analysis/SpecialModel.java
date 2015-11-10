package edu.purdue.cs.toydroid.bidtext.analysis;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.types.MethodReference;

public class SpecialModel {

	public static boolean isSpecialModel(SSAAbstractInvokeInstruction inst) {
		MethodReference mRef = inst.getDeclaredTarget();
		String mName = mRef.getName().toString();
		if (isSpecialModelByName(mName)) {
			return true;
		}
		return false;
	}

	private static boolean isSpecialModelByName(String name) {
		if ("getInputStream".equals(name) || "getOutputStream".equals(name)) {
			return true;
		}
		return false;
	}
}
