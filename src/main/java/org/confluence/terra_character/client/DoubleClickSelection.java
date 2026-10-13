package org.confluence.terra_character.client;

import java.util.Objects;

/** Only two clicks on the same entry within 250 ms confirm it. */
public final class DoubleClickSelection<T> {
	private T previous;
	private long time;

	public boolean click(T target, long now) {
		boolean confirm =
				previous != null
						&& Objects.equals(previous, target)
						&& now >= time
						&& now - time <= 250;
		previous = confirm ? null : target;
		time = now;
		return confirm;
	}

	public void reset() {
		previous = null;
	}
}
