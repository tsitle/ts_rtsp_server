package org.tsitle.rtsp.threads.rtcp;

import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.kmd.SrtcpContextOutbound;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class SrtcpVarsOutbound {

	@Nullable SrtcpContextOutbound ctxObj;
	final AtomicBoolean ctxUpdatePending = new AtomicBoolean(false);

	private final ReadWriteLock ctxLock = new ReentrantReadWriteLock();
	final Lock ctxReadLock = ctxLock.readLock();
	final Lock ctxWriteLock = ctxLock.writeLock();

}
