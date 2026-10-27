package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class RingBufferBase<T extends Cloneable> {

	private final @NonNull Function<Void, @NonNull T> factoryFnc;
	private final @NonNull Function<@NonNull T, @NonNull T> cloneFnc;
	private final int maxCacheSize;

	private final ReadWriteLock theLock = new ReentrantReadWriteLock();
	protected final Lock theReadLock = theLock.readLock();
	protected final Lock theWriteLock = theLock.writeLock();

	protected final List<@NonNull T> entries = new ArrayList<>();
	protected int indexRd = 0;
	protected int indexWr = 0;
	protected int used = 0;
	protected int capacity;

	protected RingBufferBase(
				int initialCacheSize,
				int maxCacheSize,
				@NonNull Function<Void, @NonNull T> factoryFnc,
				@NonNull Function<@NonNull T, @NonNull T> cloneFnc
			) {
		this.factoryFnc = factoryFnc;
		this.cloneFnc = cloneFnc;
		this.maxCacheSize = maxCacheSize;

		//
		if (initialCacheSize < 5) {
			initialCacheSize = 5;
		}
		this.capacity = initialCacheSize;
		for (int i = 0; i < initialCacheSize; i++) {
			this.entries.add(factoryFnc.apply(null));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		theReadLock.lock();
		try {
			return (used < 1);
		} finally {
			theReadLock.unlock();
		}
	}

	public boolean isFull() {
		theReadLock.lock();
		try {
			return (used >= maxCacheSize);
		} finally {
			theReadLock.unlock();
		}
	}

	@SuppressWarnings("unused")
	public int getUsed() {
		theReadLock.lock();
		try {
			return used;
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<T> getWritePointer() {
		theWriteLock.lock();
		try {
			if (used >= capacity && ! increaseCacheSize()) {
				return Optional.empty();
			}
			return Optional.of(entries.get(indexWr));
		} finally {
			theWriteLock.unlock();
		}
	}

	public void advanceWritePointer() {
		theWriteLock.lock();
		try {
			indexWr = ((indexWr + 1) % capacity);
			++used;
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<T> poll() {
		theWriteLock.lock();
		try {
			if (used < 1) {
				return Optional.empty();
			}
			T entry = entries.get(indexRd);
			indexRd = ((indexRd + 1) % capacity);
			--used;
			return Optional.of(entry);
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public void deleteAll(@NonNull Set<@NonNull T> entriesToDelete) {
		final String FNC_NAME = getClass().getSimpleName() + ".deleteAll()";

		theWriteLock.lock();
		try {
			boolean areConsecutive = true;
			int localIndexRd = indexRd;
			final int toDelCount = entriesToDelete.size();
			for (int x = 0; x < toDelCount; x++) {
				T tmpPtr = entries.get(localIndexRd);
				localIndexRd = ((localIndexRd + 1) % capacity);
				if (! entriesToDelete.contains(tmpPtr)) {
					areConsecutive = false;
					break;
				}
			}
			if (! areConsecutive) {
				throw new IllegalArgumentException(FNC_NAME + ": Entries to delete are not consecutive");
			}

			for (int x = 0; x < toDelCount; x++) {
				indexRd = ((indexRd + 1) % capacity);
				--used;
			}
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public void copyIntoList(@NonNull List<@NonNull T> output) {
		output.clear();

		theReadLock.lock();
		try {
			int locIndexRd = indexRd;
			for (int x = 0; x < used; x++) {
				T tmpPtr = entries.get(locIndexRd);
				output.add(cloneFnc.apply(tmpPtr));
				locIndexRd = ((locIndexRd + 1) % capacity);
			}
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	protected void clearCache() {
		theWriteLock.lock();
		try {
			indexRd = 0;
			indexWr = 0;
			used = 0;
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean increaseCacheSize() {
		if (capacity >= maxCacheSize) {
			return false;
		}
		// copy all pointers to the temporary array
		List<@NonNull T> tmpPackets = new ArrayList<>();
		for (int i = 0; i < used; i++) {
			tmpPackets.add(entries.get(indexRd));
			indexRd = ((indexRd + 1) % capacity);
		}
		// copy all pointers back into the actual array - but starting at offset 0
		for (int i = 0; i < used; i++) {
			entries.set(i, tmpPackets.get(i));
		}
		indexRd = 0;
		indexWr = used;
		// increase array size
		int nextSize = (int)((double)capacity * 1.5);
		nextSize += (nextSize % 2);
		nextSize = Math.min(nextSize, maxCacheSize);
		for (int i = 0; i < nextSize - capacity; i++) {
			entries.add(factoryFnc.apply(null));
		}
		capacity = nextSize;
		return true;
	}

}
