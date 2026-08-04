package org.tsitle.lib_dataprov.threads_demux;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_dataprov.ThreadDpBase;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeAac;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeH26x;
import org.tsitle.lib_ffmpeg.demux.FfmpegDemuxer;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSettingsDemux;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;

public final class ThreadDataProvDemux extends ThreadDpBase implements TdpDemuxReadNextAvPacketInterface {

	private static final int CACHE_SIZE_DEFAULT = 10;
	private static final int CACHE_SIZE_MAX = 500;

	private static class FfPktCacheEntry {
		final @NonNull FfmpegAvPktBasics ffPktObj = new FfmpegAvPktBasics();
		final @NonNull TimestampMonotonic ffPktTimestamp = TimestampMonotonic.ofEmpty();
	}

	private static class FfPktCacheCont {
		final boolean isVideo;
		final List<@NonNull FfPktCacheEntry> cacheList = new ArrayList<>(CACHE_SIZE_MAX);
		int count = 0;
		int ixRead = 0;
		int ixWrite = 0;
		final ReentrantLock lockCond = new ReentrantLock();
		/** Condition to signal that the queue is not empty */
		final Condition cacheNotEmpty = lockCond.newCondition();

		FfPktCacheCont(boolean isVideo) {
			this.isVideo = isVideo;
		}

		void clearCache() {
			count = 0;
			ixRead = 0;
			ixWrite = 0;
		}
	}

	private final @NonNull URI inputSourceDemuxMsUri;
	private final boolean isFromFile;

	private @Nullable FfmpegDemuxer ffDemuxerPtr = null;
	private final ReadWriteLock demuxerLock = new ReentrantReadWriteLock();
	private final Lock demuxerReadLock = demuxerLock.readLock();
	private final Lock demuxerWriteLock = demuxerLock.writeLock();

	private final AtomicBoolean eosReached = new AtomicBoolean(false);

	private final AtomicBoolean haveInputSi = new AtomicBoolean(false);
	private final AtomicBoolean haveInputVideo = new AtomicBoolean(false);
	private final AtomicBoolean haveInputAudio = new AtomicBoolean(false);
	private final FfPktCacheCont cacheVid = new FfPktCacheCont(true);
	private final FfPktCacheCont cacheAud = new FfPktCacheCont(false);

	private double durationSecs = -1.0;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param inputSourceDemuxMsUri URI of the Input Source
	 */
	public ThreadDataProvDemux(@NonNull LogMsgInterface logMsgInterface, @NonNull URI inputSourceDemuxMsUri) {
		super(logMsgInterface);

		//
		if (inputSourceDemuxMsUri.toString().isBlank()) {
			throw new IllegalArgumentException("Input URI must not be blank");
		}
		this.inputSourceDemuxMsUri = URI.create(inputSourceDemuxMsUri.toString());
		if (this.inputSourceDemuxMsUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI must have a protocol");
		}
		if (this.inputSourceDemuxMsUri.getPath() == null) {
			throw new IllegalArgumentException("Input URI must have a path");
		}

		//
		this.isFromFile = "file".equals(inputSourceDemuxMsUri.getScheme());
		if (! (isFromFile ||
				"http".equals(inputSourceDemuxMsUri.getScheme()) || "https".equals(inputSourceDemuxMsUri.getScheme()))) {
			throw new IllegalArgumentException("Input URI scheme must be 'file|http|https'");
		}

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
		String inputFilePath = inputSourceDemuxMsUri.toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");

		//
		FfmpegDmxSettingsDemux dmxSettingsDemux = new FfmpegDmxSettingsDemux();
		dmxSettingsDemux.cfgOutputModeH26x = FfmpegPktConvModeH26x.ANNEXB;
		dmxSettingsDemux.cfgOutputModeAac = FfmpegPktConvModeAac.WITH_ADTS;
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsDemux.cfgAllowedCodecsVideo.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsDemux.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);

		try (FfmpegDemuxer ffDemuxer = FfmpegDemuxer.createForDemuxingOnly(
					logMsgInterface,
					inputFilePath,
					dmxSettingsDemux
				)) {
			demuxerWriteLock.lock();
			try { ffDemuxerPtr = ffDemuxer; } finally { demuxerWriteLock.unlock(); }
			//
			while (! doStop.get()) {
				if (! mainLoop()) {
					break;
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
			isRunning.set(false);
			//
			demuxerWriteLock.lock();
			try { ffDemuxerPtr = null; } finally { demuxerWriteLock.unlock(); }
			//
			signalCacheNotEmpty(cacheVid);
			signalCacheNotEmpty(cacheAud);
			//
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void readNextPacketVideo(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException {
		internalPollNextPacketFromCache(cacheVid, buf, stTimestamp);
	}

	@Override
	public void readNextPacketAudio(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamEosException, InputStreamThreadEndedException {
		internalPollNextPacketFromCache(cacheAud, buf, stTimestamp);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean seekStream(double targetTimestamp) {
		final String FNC_NAME = getClass().getSimpleName() + ".seekStream()";

		if (! isFromFile || targetTimestamp < 0.0 || eosReached.get() ||
				durationSecs < 0.001 || targetTimestamp > durationSecs + 0.1) {
			return false;
		}
		demuxerWriteLock.lock();
		try {
			if (ffDemuxerPtr == null) {
				return false;
			}
			cacheVid.lockCond.lock();
			try {
				cacheAud.lockCond.lock();
				try {
					cacheVid.clearCache();
					cacheAud.clearCache();
					//
					try {
						ffDemuxerPtr.seekToTimestamp(targetTimestamp);
					} catch (FfmpegGenericException e) {
						logError(FNC_NAME, "seeking failed: " + e.getMessage());
						return false;
					}
				} finally {
					cacheAud.lockCond.unlock();
				}
			} finally {
				cacheVid.lockCond.unlock();
			}
		} finally {
			demuxerWriteLock.unlock();
		}
		return true;
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

	private boolean mainLoop() throws InterruptedException, InputStreamEosException {
		boolean readNextPkt;

		demuxerReadLock.lock();
		try {
			if (eosReached.get() && cacheVid.count == 0 && cacheAud.count == 0) {
				return false;
			}
			readNextPkt = (
					! eosReached.get() &&
					(
						(haveInputVideo.get() && cacheVid.count < CACHE_SIZE_DEFAULT) ||
						(haveInputAudio.get() && cacheAud.count < CACHE_SIZE_DEFAULT)
					)
				);
		} finally {
			demuxerReadLock.unlock();
		}
		//
		if (! haveInputSi.get() || readNextPkt) {
			internalReadNextAvPacket();
		} else {
			checkCacheNotEmpty(haveInputVideo.get(), cacheVid);
			checkCacheNotEmpty(haveInputAudio.get(), cacheAud);
			//
			Thread.sleep(1);
		}

		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void checkCacheNotEmpty(boolean haveAv, @NonNull FfPktCacheCont cacheCont) {
		if (! haveAv) {
			return;
		}
		boolean tmpDoSignal;
		demuxerReadLock.lock();
		try {
			tmpDoSignal = (cacheCont.count > 0);
		} finally {
			demuxerReadLock.unlock();
		}
		if (tmpDoSignal) {
			signalCacheNotEmpty(cacheCont);
		}
	}

	private static void signalCacheNotEmpty(@NonNull FfPktCacheCont cacheCont) {
		cacheCont.lockCond.lock();
		try {
			cacheCont.cacheNotEmpty.signalAll();
		} finally {
			cacheCont.lockCond.unlock();
		}
	}

	private void addToCache(@NonNull FfPktCacheCont cacheCont, @NonNull FfPktCacheEntry cacheEntry) {
		FfPktCacheEntry tmpEntry = cacheCont.cacheList.get(cacheCont.ixWrite);
		tmpEntry.ffPktObj.pktBe.copyOf(cacheEntry.ffPktObj.pktBe);
		tmpEntry.ffPktTimestamp.copyFrom(cacheEntry.ffPktTimestamp);

		cacheCont.ixWrite = (cacheCont.ixWrite + 1) % cacheCont.cacheList.size();
		++cacheCont.count;
	}

	private void copyFromCache(
				@NonNull FfPktCacheCont cacheCont,
				@NonNull BufferExt buf,
				@NonNull TimestampMonotonic stTimestamp
			) throws InputStreamEosException, InputStreamThreadEndedException {
		if (! isRunning.get()) {
			throw new InputStreamThreadEndedException();
		}
		if (cacheCont.count == 0) {
			throw new InputStreamEosException();
		}
		FfPktCacheEntry cacheEntry = cacheCont.cacheList.get(cacheCont.ixRead);
		buf.copyOf(cacheEntry.ffPktObj.pktBe);
		stTimestamp.copyFrom(cacheEntry.ffPktTimestamp);

		cacheCont.ixRead = (cacheCont.ixRead + 1) % cacheCont.cacheList.size();
		--cacheCont.count;
	}

	private void internalPollNextPacketFromCache(
				@NonNull FfPktCacheCont cacheCont,
				@NonNull BufferExt buf,
				@NonNull TimestampMonotonic stTimestamp
			) throws InputStreamEosException, InputStreamThreadEndedException {
		if (doStop.get() || ! isRunning.get()) {
			throw new InputStreamThreadEndedException();
		}
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}

		// wait until we have read the stream info in the main loop
		int loopCnt = 0;
		while (++loopCnt <= 1000 && ! haveInputSi.get()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
				throw new InputStreamEosException();
			}
		}
		if (! haveInputSi.get() ||
				(cacheCont.isVideo && ! haveInputVideo.get()) ||
				(! cacheCont.isVideo && ! haveInputAudio.get())) {
			throw new InputStreamEosException();
		}

		//
		cacheCont.lockCond.lock();
		try {
			cacheCont.cacheNotEmpty.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		} finally {
			cacheCont.lockCond.unlock();
		}

		//
		if (doStop.get() || ! isRunning.get()) {
			throw new InputStreamThreadEndedException();
		}
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}

		//
		demuxerWriteLock.lock();
		try {
			copyFromCache(cacheCont, buf, stTimestamp);
		} finally {
			demuxerWriteLock.unlock();
		}
	}

	private void internalReadNextAvPacket() throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadNextAvPacket()";

		demuxerWriteLock.lock();
		try {
			if (eosReached.get()) {
				throw new InputStreamEosException();
			}
			if (ffDemuxerPtr == null) {
				logError(FNC_NAME, "ffDemuxerPtr is null");
				throw new InputStreamEosException();
			}
			if ((haveInputVideo.get() && cacheVid.count == CACHE_SIZE_MAX) ||
					(haveInputAudio.get() && cacheAud.count == CACHE_SIZE_MAX)) {
				logError(FNC_NAME, "cache is full");
				throw new InputStreamEosException();  // something went wrong, so we abort here
			}

			//
			FfPktCacheEntry tmpCacheEntry = new FfPktCacheEntry();

			FfmpegDemuxer.ReadResult tmpRr = ffDemuxerPtr.readNextAvPacket(tmpCacheEntry.ffPktObj);
			if (tmpRr == FfmpegDemuxer.ReadResult.RR_EOF) {
				eosReached.set(true);
				return;
			}

			//
			if (! haveInputSi.get()) {
				haveInputVideo.set(ffDemuxerPtr.getFfAvSubStreamIxVideo().isPresent());
				haveInputAudio.set(ffDemuxerPtr.getFfAvSubStreamIxAudio().isPresent());
				haveInputSi.set(true);  // set this only after [haveInputVideo] and [haveInputAudio]
				//
				durationSecs = ffDemuxerPtr.getDurationSecs().orElse(-1.0);
			}

			//
			TimestampMonotonic tmpTs = TimestampMonotonic.ofNsUnsigned64bit(
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
