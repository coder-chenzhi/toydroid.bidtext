package edu.purdue.cs.toydroid.utils;

public class SimpleCounter {
	public int count;

	SimpleCounter(int c) {
		count = c;
	}

	public static SimpleCounter increment(SimpleCounter counter) {
		if (counter == null) {
			counter = new SimpleCounter(1);
		} else {
			counter.count++;
		}
		return counter;
	}
}
