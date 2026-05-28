package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.packets.rtp.RtpEncryptedPacket;
import org.tsitle.rtsp.security.SrtpContextOutbound;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class SrtpVarsOutbound {

	@Nullable SrtpContextOutbound ctxObj;
	final AtomicBoolean ctxUpdatePending = new AtomicBoolean(false);

	private final ReadWriteLock ctxLock = new ReentrantReadWriteLock();
	final Lock ctxReadLock = ctxLock.readLock();
	final Lock ctxWriteLock = ctxLock.writeLock();

	@Nullable RtpEncryptedPacket cacheRtpEncrPacket = null;

}
