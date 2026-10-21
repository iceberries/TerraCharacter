package org.confluence.terra_character.service;

import java.util.Locale;

/** Per-session accumulator survives player cloning, world changes and repeated saves. */
public final class CharacterPlayTime {
	private long ticks;

	public CharacterPlayTime(long savedTicks) {
		if (savedTicks < 0) throw new IllegalArgumentException("Negative play time");
		ticks = savedTicks;
	}

	public void tick(boolean playing) {
		if (playing && ticks < Long.MAX_VALUE) ticks++;
	}

	public static String format(long ticks) {
		if (ticks < 0) throw new IllegalArgumentException("Negative play time");
		long seconds = ticks / 20;
		return String.format(
				Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	public long getTicks() {
		return ticks;
	}
}
