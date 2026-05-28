package org.tsitle.rtsp.threads.rtcp;

import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.security.SrtcpContextInbound;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class SrtcpVarsInbound {

	@Nullable SrtcpContextInbound ctxObjCur;
	@Nullable SrtcpContextInbound ctxObjNext;
	final AtomicBoolean ctxUpdatePending = new AtomicBoolean(false);

	private final ReadWriteLock ctxLock = new ReentrantReadWriteLock();
	final Lock ctxReadLock = ctxLock.readLock();
	final Lock ctxWriteLock = ctxLock.writeLock();

}
