package org.tsitle.rtsp_server.threads.dataprovider_demux;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.demux.FfmpegDemuxer;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSettingsDemux;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.exceptions.InputStreamThreadEndedException;
import org.tsitle.rtsp_server.threads.ThreadBase;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;

public final class ThreadDataProvDemux extends ThreadBase implements TdpDemuxReadNextAvPacketInterface {

	private static final int CACHE_SIZE_DEFAULT = 10;
	private static final int CACHE_SIZE_MAX = 500;

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

	private boolean haveInputSi = false;
	private boolean haveInputVideo = false;
	private boolean haveInputAudio = false;
	private final FfPktCacheCont cacheVid = new FfPktCacheCont();
	private final FfPktCacheCont cacheAud = new FfPktCacheCont();

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
		dmxSettingsDemux.cfgOutputH26xAsAnnexB = true;
		dmxSettingsDemux.cfgOutputAacWithAdts = true;
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsDemux.cfgAllowedCodecsVideo.addAll(RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsDemux.cfgAllowedCodecsAudio.addAll(RtpConstants.RTP_FFMPEG_ALLOWED_CODECS_AUDIO);

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
			demuxerWriteLock.lock();
			try { ffDemuxerPtr = null; } finally { demuxerWriteLock.unlock(); }
			//
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
			cacheVid.lock.lock();
			try {
				cacheAud.lock.lock();
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
					cacheAud.lock.unlock();
				}
			} finally {
				cacheVid.lock.unlock();
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
						(haveInputVideo && cacheVid.count < CACHE_SIZE_DEFAULT) ||
						(haveInputAudio && cacheAud.count < CACHE_SIZE_DEFAULT)
					)
				);
		} finally {
			demuxerReadLock.unlock();
		}
		//
		if (! haveInputSi || readNextPkt) {
			internalReadNextAvPacket();
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
			Thread.sleep(1);
		}

		return true;
	}

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

	private void internalReadNextAvPacket() throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".internalReadNextAvPacket()";

		demuxerWriteLock.lock();
		try {
			if (ffDemuxerPtr == null) {
				logError(FNC_NAME, "ffDemuxerPtr is null");
				throw new InputStreamEosException();
			}
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

			FfmpegDemuxer.ReadResult tmpRr = ffDemuxerPtr.readNextAvPacket(tmpCacheEntry.ffPktObj);
			if (tmpRr == FfmpegDemuxer.ReadResult.RR_EOF) {
				eosReached.set(true);
				return;
			}

			//
			if (! haveInputSi) {
				haveInputVideo = ffDemuxerPtr.getFfAvStreamIxVideo().isPresent();
				haveInputAudio = ffDemuxerPtr.getFfAvStreamIxAudio().isPresent();
				haveInputSi = true;
				//
				durationSecs = ffDemuxerPtr.getDurationSecs().orElse(-1.0);
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
