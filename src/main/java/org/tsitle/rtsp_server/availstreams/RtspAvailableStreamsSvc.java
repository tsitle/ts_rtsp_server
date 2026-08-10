package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoElementaryStreamSource;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class RtspAvailableStreamsSvc implements RtspProtoAvailableStreamsInterface {

	private final @NonNull Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap = new HashMap<>();
	private final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> esSourceMap = new HashMap<>();
	private final @NonNull Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMap = new HashMap<>();

	private final ReadWriteLock theLock = new ReentrantReadWriteLock();
	private final Lock theReadLock = theLock.readLock();
	private final Lock theWriteLock = theLock.writeLock();

	public RtspAvailableStreamsSvc() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void updateAvailableStreams(@NonNull RtspAsSvcInputData asSvcInputData) {
		/* @TODO
		 * - main: stop all RTSP TCP + PLAY threads for changed ISs
		 * - main: stop all changed MQs
		 * - main: wait until all stopped
		 */

		//
		theWriteLock.lock();
		try {
			copyFromAsSvcInputData(asSvcInputData);
		} finally {
			theWriteLock.unlock();
		}

		/* @TODO
		 * - main: re-adjust MQ pool and start new thread per MQ
		 */
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean existsInputSourceId(@NonNull RtspProtoIdInputSource idInputSource) {
		theReadLock.lock();
		try {
			return inputSourceMap.containsKey(idInputSource);
		} finally {
			theReadLock.unlock();
		}
	}

	@Override
	public @NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource)
			throws RtspProtoIdInputSourceNotFoundException {
		theReadLock.lock();
		try {
			if (! inputSourceMap.containsKey(idInputSource)) {
				throw new RtspProtoIdInputSourceNotFoundException(idInputSource.toString());
			}
			return inputSourceMap.get(idInputSource);
		} finally {
			theReadLock.unlock();
		}
	}

	@Override
	public Optional<RtspProtoElementaryStreamSource> getFirstVideoEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		theReadLock.lock();
		try {
			return internalGetFirstOfKindEsSourceObj(idInputSource, true);
		} finally {
			theReadLock.unlock();
		}
	}

	@Override
	public Optional<RtspProtoElementaryStreamSource> getFirstAudioEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource
			) {
		theReadLock.lock();
		try {
			return internalGetFirstOfKindEsSourceObj(idInputSource, false);
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspProtoEsSourceExpandedInfo getElementaryStreamSourceExpInfo(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		if (! eseiMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		return eseiMap.get(idEsSource).clone();
	}

	@Override
	public double computeElementaryStreamSource_virtualFps(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		if (! eseiMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		RtspProtoEsSourceExpandedInfo esei = eseiMap.get(idEsSource);
		if (esei.audioSampleRate() == SampleRateEnum.UNKNOWN) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource) +
					": audioSamplerateHz must be valid");
		}
		final int tmpSpf = esei.audioSamplesPerFrame();
		if (tmpSpf < 1) {
			return -1.0;
		}
		/*
		 * 25 fps ^= 1 frame each 40 ms
		 * 8000 samples/sec ^= 1 sample each 0.125 ms
		 * 40 ms / 0.125 ms == 320 samples per frame
		 *
		 * 15 fps ^= 1 frame each 66.7 ms
		 * 8000 samples/sec ^= 1 sample each 0.125 ms
		 * 66.7 ms / 0.125 ms == 533 samples per frame
		 *
		 * SampleIntv = 1 / SpS
		 * FrameIntv = SpF * SampleIntv --- SpF = FrameIntv / SampleIntv
		 *
		 * FpS = 1 / FrameIntv
		 */
		double frameIntv = (double)tmpSpf / (double)esei.audioSampleRate().getSrHz();
		return (1.0 / frameIntv);
	}

	@Override
	public int getElementaryStreamSource_samplesPerFrame(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		if (! eseiMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		RtspProtoEsSourceExpandedInfo esei = eseiMap.get(idEsSource);
		return esei.audioSamplesPerFrame();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void copyFromAsSvcInputData(@NonNull RtspAsSvcInputData asSvcInputData) {
		inputSourceMap.clear();
		for (Map.Entry<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> entry
				: asSvcInputData.mapIsIdToIsObj.entrySet()) {
			inputSourceMap.put(
					RtspProtoIdInputSource.of(entry.getKey().getIdStr().orElseThrow()),
					entry.getValue().clone()
				);
		}

		//
		esSourceMap.clear();
		for (Map.Entry<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> entry
				: asSvcInputData.mapEsIdToEsObj.entrySet()) {
			esSourceMap.put(
					RtspProtoIdEsSource.of(entry.getKey().getIdStr().orElseThrow()),
					entry.getValue().clone()
				);
		}

		//
		eseiMap.clear();
		for (Map.Entry<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> entry
				: asSvcInputData.mapEsIdToEseiObj.entrySet()) {
			eseiMap.put(
					RtspProtoIdEsSource.of(entry.getKey().getIdStr().orElseThrow()),
					entry.getValue().clone()
				);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the first enabled audio or video Elementary-Stream Source for the Input Source.
	 * @param idInputSource Input Source ID
	 * @param isVideo Get video source if true, audio source otherwise
	 * @return Elementary-Stream Source
	 */
	private Optional<RtspProtoElementaryStreamSource> internalGetFirstOfKindEsSourceObj(
				@NonNull RtspProtoIdInputSource idInputSource,
				boolean isVideo
			) {
		RtspProtoInputSource tmpInputSource;
		try {
			tmpInputSource = getInputSourceObj(idInputSource);
		} catch (RtspProtoIdInputSourceNotFoundException e) {
			return Optional.empty();
		}
		for (RtspProtoIdEsSource tmpEsId : tmpInputSource.getEsSourceIds()) {
			if (! esSourceMap.containsKey(tmpEsId)) {
				continue;  // this should never happen
			}
			if (! esSourceMap.get(tmpEsId).getEnabled()) {
				continue;
			}
			if (! eseiMap.containsKey(tmpEsId)) {
				continue;
			}
			RtspProtoEsSourceExpandedInfo esei = eseiMap.get(tmpEsId);
			if ((isVideo && esei.codec().isVideo()) || (! isVideo && esei.codec().isAudio())) {
				return Optional.of(esSourceMap.get(tmpEsId));
			}
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String getEsSourceIdForExcMsg(@NonNull RtspProtoIdEsSource idEsSource) {
		return "esSrc='" + idEsSource.getIdStr().orElse("-unset-") + "'";
	}

}
