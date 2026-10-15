package org.tsitle.rtsp_server.threads.rtcp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.SrtcpContextInbound;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class SrtcpVarsInbound {

	private static final int LIST_SIZE = 10;

	final LinkedList<@NonNull SrtcpContextInbound> ctxObjList = new LinkedList<>();
	final Map<@NonNull Long, @NonNull SrtcpContextInbound> ctxObjMap = new HashMap<>();

	private final ReadWriteLock ctxLock = new ReentrantReadWriteLock();
	final Lock ctxReadLock = ctxLock.readLock();
	final Lock ctxWriteLock = ctxLock.writeLock();

	void addSrtcpKmdInbound(@NonNull SrtxpKmd kmd) {
		final String FNC_NAME = getClass().getSimpleName() + ".addSrtcpKmdInbound()";

		ctxWriteLock.lock();
		try {
			if (kmd.mki().isEmpty()) {
				ctxObjList.clear();
				ctxObjMap.clear();
			} else if (ctxObjList.size() >= LIST_SIZE) {
				long firstMki = ctxObjList.getFirst().getMkiValue().orElse(0L);
				ctxObjList.removeFirst();
				ctxObjMap.remove(firstMki);
			}
			SrtcpContextInbound ctxObj = new SrtcpContextInbound(kmd);
			ctxObjList.add(ctxObj);
			ctxObjMap.put(kmd.mki().getValue().orElse(0L), ctxObj);
		} catch (SrtxpSecurityException e) {
			throw new IllegalArgumentException(FNC_NAME + ": SrtxpSecurityException caught: " + e.getMessage());
		} finally {
			ctxWriteLock.unlock();
		}
	}

	Optional<SrtcpContextInbound> getSrtcpContextInboundForPkt(@NonNull BufferExt srtpPacketBuf) {
		ctxReadLock.lock();
		try {
			if (ctxObjList.isEmpty()) {
				return Optional.empty();
			}
			SrtcpContextInbound tmpFirstCtx = ctxObjList.getFirst();
			SrtxpMki mki = tmpFirstCtx.extractMkiFromSrtcpPacket(srtpPacketBuf);
			return Optional.ofNullable(ctxObjMap.get(mki.getValue().orElse(0L)));
		} catch (SrtxpSecurityException e) {
			return Optional.empty();
		} finally {
			ctxReadLock.unlock();
		}
	}

}
