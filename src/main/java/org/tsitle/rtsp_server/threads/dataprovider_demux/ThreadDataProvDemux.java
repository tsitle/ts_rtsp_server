package org.tsitle.rtsp_server.threads.dataprovider_demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.demux.FfmpegDemuxer;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;
import org.tsitle.rtsp_server.threads.ThreadBase;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;

public final class ThreadDataProvDemux extends ThreadBase implements TdpDemuxReadNextAvPacketInterface {

	private static final int CACHE_SIZE_DEFAULT = 10;
	private static final int CACHE_SIZE_MAX = 1_000;

	private static class FfPktCacheEntry {
		final @NonNull FfmpegAvPktBasics ffPktObj = new FfmpegAvPktBasics();
		final @NonNull TimestampEpochNs ffPktTimestamp = TimestampEpochNs.ofEmpty();
	}

	private static class FfPktCacheCont {
		final List<@NonNull FfPktCacheEntry> cacheList = new ArrayList<>(CACHE_SIZE_MAX);
		int count = 0;
		int ixRead = 0;
		int ixWrite = 0;
		final ReentrantLock lock = new ReentrantLock();
		/** Condition to signal that the queue is not empty */
		final Condition cacheNotEmpty = lock.newCondition();
	}

	private final @NonNull URI inputSourceDemuxMsUri;

	private final ReadWriteLock demuxerLock = new ReentrantReadWriteLock();
	private final Lock demuxerReadLock = demuxerLock.readLock();
	private final Lock demuxerWriteLock = demuxerLock.writeLock();

	private final AtomicBoolean eosReached = new AtomicBoolean(false);

	private boolean haveInputSi = false;
	private boolean haveInputVideo = false;
	private boolean haveInputAudio = false;
	private final FfPktCacheCont cacheVid = new FfPktCacheCont();
	private final FfPktCacheCont cacheAud = new FfPktCacheCont();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param inputSourceDemuxMsUri URI of the Input Source
	 */
	public ThreadDataProvDemux(@NonNull LogMsgInterface logMsgInterface, @NonNull URI inputSourceDemuxMsUri) {
		super(logMsgInterface);

		//
		if (! ("file".equals(inputSourceDemuxMsUri.getScheme()) ||
				"http".equals(inputSourceDemuxMsUri.getScheme()) || "https".equals(inputSourceDemuxMsUri.getScheme()))) {
			throw new IllegalArgumentException("Input URI scheme must be 'file|http|https'");
		}
		this.inputSourceDemuxMsUri = URI.create(inputSourceDemuxMsUri.toString());

		//
		for (int i = 0; i < CACHE_SIZE_MAX; i++) {
			cacheVid.cacheList.add(new FfPktCacheEntry());
			cacheAud.cacheList.add(new FfPktCacheEntry());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		String inputFilePath = (inputSourceDemuxMsUri.getPath() == null ? "" : inputSourceDemuxMsUri.getPath());

		//
		try (FfmpegDemuxer ffmpegDemuxer = FfmpegDemuxer.createForDemuxingOnly(
					logMsgInterface,
					inputFilePath,
					-1L,
					true,
					true
				)) {
			boolean readNextPkt;
			while (! doStop.get()) {
				demuxerReadLock.lock();
				try {
					if (eosReached.get() && cacheVid.count == 0 && cacheAud.count == 0) {
						break;
					}
					readNextPkt = (
							! eosReached.get() &&
							(
								(! haveInputVideo || cacheVid.count < CACHE_SIZE_DEFAULT) ||
								(! haveInputAudio || cacheAud.count < CACHE_SIZE_DEFAULT)
							)
						);
				} finally {
					demuxerReadLock.unlock();
				}
				//
				if (! haveInputSi || readNextPkt) {
					//logDebug(FNC_NAME, "read next cv=" + cacheVid.count + ", ca=" + cacheAud.count);  // @TODO
					internalReadNextAvPacket(ffmpegDemuxer);
				} else {
					boolean tmpDoSignV;
					demuxerReadLock.lock(); try { tmpDoSignV = (cacheVid.count > 0); } finally { demuxerReadLock.unlock(); }
					if (tmpDoSignV) {
						signalCacheNotEmpty(cacheVid);
					}
					boolean tmpDoSignA;
					demuxerReadLock.lock(); try { tmpDoSignA = (cacheAud.count > 0); } finally { demuxerReadLock.unlock(); }
					if (tmpDoSignA) {
						signalCacheNotEmpty(cacheAud);
					}
					//noinspection BusyWait
					Thread.sleep(1);
				}
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException caught");
			Thread.currentThread().interrupt();  // restore flag
		} catch (InputStreamEosException e) {
			logDebug(FNC_NAME, "InputStreamEosException caught");
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			signalCacheNotEmpty(cacheVid);
			signalCacheNotEmpty(cacheAud);

			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void readNextPacketVideo(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException {
		internalPollNextPacketFromCache(cacheVid, buf, stTimestamp);
	}

	@Override
	public void readNextPacketAudio(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException {
		internalPollNextPacketFromCache(cacheAud, buf, stTimestamp);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
		signalCacheNotEmpty(cacheVid);
		signalCacheNotEmpty(cacheAud);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void signalCacheNotEmpty(@NonNull FfPktCacheCont cacheCont) {
		cacheCont.lock.lock();
		try {
			cacheCont.cacheNotEmpty.signalAll();
		} finally {
			cacheCont.lock.unlock();
		}
	}

	private void addToCache(@NonNull FfPktCacheCont cacheCont, @NonNull FfPktCacheEntry cacheEntry) {
		FfPktCacheEntry tmpEntry = cacheCont.cacheList.get(cacheCont.ixWrite);
		tmpEntry.ffPktObj.pktBe.copyOf(cacheEntry.ffPktObj.pktBe);
		tmpEntry.ffPktTimestamp.copyFrom(cacheEntry.ffPktTimestamp);

		cacheCont.ixWrite = (cacheCont.ixWrite + 1) % cacheCont.cacheList.size();
		++cacheCont.count;
	}

	private static void copyFromCache(
				@NonNull FfPktCacheCont cacheCont,
				@NonNull BufferExt buf,
				@NonNull TimestampEpochNs stTimestamp
			) {
		FfPktCacheEntry cacheEntry = cacheCont.cacheList.get(cacheCont.ixRead);
		buf.copyOf(cacheEntry.ffPktObj.pktBe);
		stTimestamp.copyFrom(cacheEntry.ffPktTimestamp);

		cacheCont.ixRead = (cacheCont.ixRead + 1) % cacheCont.cacheList.size();
		--cacheCont.count;
	}

	private void internalPollNextPacketFromCache(
				@NonNull FfPktCacheCont cacheCont,
				@NonNull BufferExt buf,
				@NonNull TimestampEpochNs stTimestamp
			) throws InputStreamEosException, InputStreamThreadEndedException {
		if (doStop.get() || ! isRunning.get()) {
			throw new InputStreamThreadEndedException();
		}
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}

		cacheCont.lock.lock();
		try {
			cacheCont.cacheNotEmpty.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		} finally {
			cacheCont.lock.unlock();
		}

		if (doStop.get() || ! isRunning.get()) {
			throw new InputStreamThreadEndedException();
		}
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}

		demuxerWriteLock.lock();
		try {
			copyFromCache(cacheCont, buf, stTimestamp);
		} finally {
			demuxerWriteLock.unlock();
		}
	}

	private void internalReadNextAvPacket(@NonNull FfmpegDemuxer ffDemuxer) throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadNextAvPacket()";

		demuxerWriteLock.lock();
		try {
			if ((haveInputVideo && cacheVid.count == CACHE_SIZE_MAX) ||
					(haveInputAudio && cacheAud.count == CACHE_SIZE_MAX)) {
				logError(FNC_NAME, "cache is full");
				throw new InputStreamEosException();  // something went wrong, so we abort here
			}

			//
			if (eosReached.get()) {
				throw new InputStreamEosException();
			}

			FfPktCacheEntry tmpCacheEntry = new FfPktCacheEntry();

			FfmpegDemuxer.ReadResult tmpRr = ffDemuxer.readNextAvPacket(tmpCacheEntry.ffPktObj);
			if (tmpRr == FfmpegDemuxer.ReadResult.RR_EOF) {
				eosReached.set(true);
				return;
			}

			//
			if (! haveInputSi) {
				haveInputVideo = ffDemuxer.getFfAvStreamIxVideo().isPresent();
				haveInputAudio = ffDemuxer.getFfAvStreamIxAudio().isPresent();
				haveInputSi = true;
			}

			//
			TimestampEpochNs tmpTs = TimestampEpochNs.ofEpochNsUnsigned64bit(
					(long)(tmpCacheEntry.ffPktObj.ptsUnitsToSeconds() * 1_000_000_000.0)
				);
			tmpCacheEntry.ffPktTimestamp.copyFrom(tmpTs);

			FfPktCacheCont tmpCacheCont = (tmpRr == FfmpegDemuxer.ReadResult.RR_OK_VID ? cacheVid : cacheAud);
			addToCache(tmpCacheCont, tmpCacheEntry);
			signalCacheNotEmpty(tmpCacheCont);
		} catch (FfmpegGenericException e) {
			eosReached.set(true);
			logError(FNC_NAME, "FfmpegGenericException caught: " + e.getMessage());
			throw new InputStreamEosException();
		} finally {
			demuxerWriteLock.unlock();
		}
	}

}
