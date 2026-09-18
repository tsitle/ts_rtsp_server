package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdInputSourceNotFoundException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoIdEsSourceNotFoundException;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class RtspAvailableStreamsSvc implements RtspProtoAvailableStreamsInterface, AsCodecSettingsChangedFromMqInterface,
		AsCodecSettingsChangedFromDmxRtspInterface, AsCodecSettingsChangedFromDmxJbInterface, AsGetFileTagsInterface {

	private static class AsData {
		final Map<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> inputSourceMap = new HashMap<>();
		final Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> esSourceMap = new HashMap<>();
		final Map<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> eseiMap = new HashMap<>();

		void move(@NonNull AsData src) {
			this.inputSourceMap.clear();
			this.inputSourceMap.putAll(src.inputSourceMap);
			this.esSourceMap.clear();
			this.esSourceMap.putAll(src.esSourceMap);
			this.eseiMap.clear();
			this.eseiMap.putAll(src.eseiMap);
		}
	}

	private final AsData asDataStaged = new AsData();
	private final Set<@NonNull RtspProtoIdInputSource> stagedIsIdsToStopThreadsFor = new HashSet<>();
	private final Set<@NonNull RtspProtoIdEsSource> stagedEsIdsToStopThreadsFor = new HashSet<>();
	private final AsData asDataCurrent = new AsData();

	private final ReadWriteLock theLock = new ReentrantReadWriteLock();
	private final Lock theReadLock = theLock.readLock();
	private final Lock theWriteLock = theLock.writeLock();

	private final AtomicBoolean haveStreamsChanged = new AtomicBoolean(false);

	private final Map<@NonNull RtspProtoIdInputSource, @NonNull String> mapIsIdToFileTagValues = new HashMap<>();
	private final Map<@NonNull RtspProtoIdInputSource, @NonNull String> mapIsIdToFileTagHashes = new HashMap<>();

	public RtspAvailableStreamsSvc() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void updateAvailableStreamsFromConfig(@NonNull RtspAsSvcInputData asSvcInputData) {
		theWriteLock.lock();
		try {
			if (haveStreamsChanged.get()) {
				return;  // we have pending changes
			}

			//
			internalUpdateFromAsSvcInputData(asSvcInputData);

			//
			stagedIsIdsToStopThreadsFor.clear();
			copyIsIds(asSvcInputData.isIdsModified, stagedIsIdsToStopThreadsFor);
			copyIsIds(asSvcInputData.isIdsDeleted, stagedIsIdsToStopThreadsFor);

			stagedEsIdsToStopThreadsFor.clear();
			RtspAsDeltaEsHelper.findMqEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
					asDataStaged.inputSourceMap,
					asDataCurrent.eseiMap,
					asDataStaged.eseiMap,
					stagedEsIdsToStopThreadsFor
				);

			haveStreamsChanged.set(true);
		} finally {
			theWriteLock.unlock();
		}
	}

	public boolean haveStreamsChanged() {
		theReadLock.lock();
		try {
			return haveStreamsChanged.get();
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void getIdsForThreadsThatNeedToBeStopped(
				@NonNull Set<@NonNull RtspProtoIdInputSource> stopIsIds,
				@NonNull Set<@NonNull RtspProtoIdEsSource> stopMqEsIds
			) {
		theReadLock.lock();
		try {
			stopIsIds.clear();
			stopMqEsIds.clear();
			if (! haveStreamsChanged.get()) {
				return;
			}

			copyIsIds(stagedIsIdsToStopThreadsFor, stopIsIds);
			stagedIsIdsToStopThreadsFor.clear();

			copyEsIds(stagedEsIdsToStopThreadsFor, stopMqEsIds);
			stagedEsIdsToStopThreadsFor.clear();
		} finally {
			theReadLock.unlock();
		}
	}

	public void performStreamsUpdate() {
		theWriteLock.lock();
		try {
			asDataCurrent.move(asDataStaged);
			stagedIsIdsToStopThreadsFor.clear();
			stagedEsIdsToStopThreadsFor.clear();

			haveStreamsChanged.set(false);
		} finally {
			theWriteLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull Set<@NonNull RtspProtoIdEsSource> findRequiredMqEsSourcesForInputSources() {
		theReadLock.lock();
		try {
			Set<RtspProtoIdEsSource> resSet = new HashSet<>();
			RtspAsDeltaEsHelper.findMqEsThatAreInUse(
					asDataCurrent.inputSourceMap,
					asDataCurrent.eseiMap,
					resSet
				);
			return resSet;
		} finally {
			theReadLock.unlock();
		}
	}

	@Override
	public void onCodecSettingsChangedFromMq(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings) {
		theWriteLock.lock();
		try {
			RtspProtoEsSourceExpandedInfo eseiOld = getElementaryStreamSourceExpInfo(idEsSource);
			FrameRateEnum tmpFr;
			if (codecSettings.videoFps != null && codecSettings.videoFps != FrameRateEnum.UNKNOWN) {
				tmpFr = codecSettings.videoFps;
			} else {
				tmpFr = eseiOld.videoFps();
			}
			SampleRateEnum tmpSr;
			if (codecSettings.audioSamplerate != null && codecSettings.audioSamplerate != SampleRateEnum.UNKNOWN) {
				tmpSr = codecSettings.audioSamplerate;
			} else {
				tmpSr = eseiOld.audioSampleRate();
			}
			RtspProtoEsSourceExpandedInfo eseiNew = new RtspProtoEsSourceExpandedInfo(
					eseiOld.demuxerSubStreamIx(),
					codecSettings.codec != null ? codecSettings.getAsRtpPacketType() : eseiOld.codec(),
					eseiOld.esSourceType(),
					eseiOld.inputUri().clone(),
					eseiOld.credentials().clone(),
					eseiOld.durationSecs(),
					codecSettings.audioChannels != null ? codecSettings.audioChannels : eseiOld.audioChannelCount(),
					tmpSr,
					codecSettings.audioSamplesPerFrame != null ? codecSettings.audioSamplesPerFrame : eseiOld.audioSamplesPerFrame(),
					true,  // when reading from a MQ, the PCM audio data is expected to be big-endian
					ExtradataContainerHex.ofEmpty(),  // this will be populated later
					tmpFr,
					ExtradataContainerSdp.ofEmpty(),  // this will be populated later,
					eseiOld.tcSettingsAudio()
				);

			//
			asDataCurrent.eseiMap.put(idEsSource, eseiNew);
		} catch (RtspProtoIdEsSourceNotFoundException e) {
			// fail silently
		} finally {
			theWriteLock.unlock();
		}
	}

	@Override
	public void onCodecMetadataFromMq(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex) {
		theWriteLock.lock();
		try {
			RtspProtoEsSourceExpandedInfo eseiOld = getElementaryStreamSourceExpInfo(idEsSource);

			ExtradataContainerHex ech;
			ExtradataContainerSdp ecs = RtspAsEdSdpHelper.buildExtradataForSdp(eseiOld.codec(), metadataHex);
			switch (eseiOld.codec()) {
				case A_AAC -> ech = ExtradataContainerHex.ofAac(metadataHex);
				case V_H264 -> ech = ExtradataContainerHex.ofH264_annexB(metadataHex);
				case V_H265 -> ech = ExtradataContainerHex.ofH265_annexB(metadataHex);
				default -> ech = ExtradataContainerHex.ofEmpty();
			}

			RtspProtoEsSourceExpandedInfo eseiNew = new RtspProtoEsSourceExpandedInfo(
					eseiOld.demuxerSubStreamIx(),
					eseiOld.codec(),
					eseiOld.esSourceType(),
					eseiOld.inputUri().clone(),
					eseiOld.credentials().clone(),
					eseiOld.durationSecs(),
					eseiOld.audioChannelCount(),
					eseiOld.audioSampleRate(),
					eseiOld.audioSamplesPerFrame(),
					eseiOld.isAudioPcmBigEndian(),
					ech,
					eseiOld.videoFps(),
					ecs,
					eseiOld.tcSettingsAudio()
				);

			//
			asDataCurrent.eseiMap.put(idEsSource, eseiNew);
		} catch (RtspProtoIdEsSourceNotFoundException e) {
			// fail silently
		} finally {
			theWriteLock.unlock();
		}
	}

	public @NonNull Set<@NonNull RtspProtoIdInputSource> findRequiredDmxRtspInputSources() {
		return findRequiredDmxXxxInputSources(RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_RTSP);
	}

	@Override
	public void onCodecSettingsChangedFromDmxRtsp(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings) {
		onCodecSettingsChangedFromMq(idEsSource, codecSettings);
	}

	@Override
	public void onCodecMetadataFromDmxRtsp(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex) {
		onCodecMetadataFromMq(idEsSource, metadataHex);
	}

	public @NonNull Set<@NonNull RtspProtoIdInputSource> findRequiredDmxJbInputSources() {
		return findRequiredDmxXxxInputSources(RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_JB);
	}

	@Override
	public void onCodecSettingsChangedFromDmxJb(@NonNull RtspProtoIdEsSource idEsSource, @NonNull MqCodecSettings codecSettings) {
		onCodecSettingsChangedFromMq(idEsSource, codecSettings);
	}

	@Override
	public void onCodecMetadataFromDmxJb(@NonNull RtspProtoIdEsSource idEsSource, @NonNull String metadataHex) {
		onCodecMetadataFromMq(idEsSource, metadataHex);
	}

	// ------------------------------------------------------

	@Override
	public void onFileTagsChangedFromDmxJb(@NonNull RtspProtoIdInputSource idInputSource, @NonNull String fileTags) {
		theWriteLock.lock();
		try {
			if (! existsInputSourceId(idInputSource)) {
				return;
			}
			mapIsIdToFileTagValues.put(idInputSource.clone(), fileTags);
			mapIsIdToFileTagHashes.put(idInputSource.clone(), HashMd5Helper.hashOfString(fileTags, false));
		} finally {
			theWriteLock.unlock();
		}
	}

	@Override
	public Optional<String> getFileTagsValue(@NonNull RtspProtoIdInputSource idInputSource) {
		theReadLock.lock();
		try {
			return Optional.ofNullable(mapIsIdToFileTagValues.get(idInputSource));
		} finally {
			theReadLock.unlock();
		}
	}

	@Override
	public Optional<String> getFileTagsHash(@NonNull RtspProtoIdInputSource idInputSource) {
		theReadLock.lock();
		try {
			return Optional.ofNullable(mapIsIdToFileTagHashes.get(idInputSource));
		} finally {
			theReadLock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean existsInputSourceId(@NonNull RtspProtoIdInputSource idInputSource) {
		theReadLock.lock();
		try {
			return asDataCurrent.inputSourceMap.containsKey(idInputSource);
		} finally {
			theReadLock.unlock();
		}
	}

	@Override
	public @NonNull RtspProtoInputSource getInputSourceObj(@NonNull RtspProtoIdInputSource idInputSource)
			throws RtspProtoIdInputSourceNotFoundException {
		theReadLock.lock();
		try {
			if (! asDataCurrent.inputSourceMap.containsKey(idInputSource)) {
				throw new RtspProtoIdInputSourceNotFoundException(idInputSource.toString());
			}
			return asDataCurrent.inputSourceMap.get(idInputSource);
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
		if (! asDataCurrent.eseiMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		return asDataCurrent.eseiMap.get(idEsSource).clone();
	}

	@Override
	public double computeElementaryStreamSource_virtualFps(@NonNull RtspProtoIdEsSource idEsSource)
			throws RtspProtoIdEsSourceNotFoundException {
		if (! asDataCurrent.eseiMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		RtspProtoEsSourceExpandedInfo esei = asDataCurrent.eseiMap.get(idEsSource);
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
		if (! asDataCurrent.eseiMap.containsKey(idEsSource)) {
			throw new RtspProtoIdEsSourceNotFoundException(getEsSourceIdForExcMsg(idEsSource));
		}
		RtspProtoEsSourceExpandedInfo esei = asDataCurrent.eseiMap.get(idEsSource);
		return esei.audioSamplesPerFrame();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalUpdateFromAsSvcInputData(@NonNull RtspAsSvcInputData asSvcInputData) {
		for (RtspProtoIdInputSource idIs : asSvcInputData.isIdsDeleted) {
			asDataStaged.inputSourceMap.remove(idIs);
		}

		// clone Input Source objects
		for (Map.Entry<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> entry
				: asSvcInputData.mapIsIdToIsObj.entrySet()) {
			asDataStaged.inputSourceMap.put(
					RtspProtoIdInputSource.of(entry.getKey().getIdStr().orElseThrow()),
					entry.getValue().clone()
				);
		}

		// clone ES Source objects
		for (Map.Entry<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoElementaryStreamSource> entry
				: asSvcInputData.mapEsIdToEsObj.entrySet()) {
			asDataStaged.esSourceMap.put(
					RtspProtoIdEsSource.of(entry.getKey().getIdStr().orElseThrow()),
					entry.getValue().clone()
				);
		}

		// clone ESEI objects
		asDataStaged.eseiMap.clear();
		for (Map.Entry<@NonNull RtspProtoIdEsSource, @NonNull RtspProtoEsSourceExpandedInfo> entry
				: asSvcInputData.mapEsIdToEseiObj.entrySet()) {
			asDataStaged.eseiMap.put(
					RtspProtoIdEsSource.of(entry.getKey().getIdStr().orElseThrow()),
					entry.getValue().clone()
				);
		}

		// find all ES Source IDs that are in use by an Input Source
		Set<RtspProtoIdEsSource> esSourceIdsInUse = new HashSet<>();
		for (Map.Entry<@NonNull RtspProtoIdInputSource, @NonNull RtspProtoInputSource> entry
				: asDataStaged.inputSourceMap.entrySet()) {
			esSourceIdsInUse.addAll(entry.getValue().getEsSourceIds());
		}

		// delete all ES Source objects that are not in use by an Input Source
		Set<RtspProtoIdEsSource> esSourceIdsToDelete = new HashSet<>();
		for (RtspProtoIdEsSource esSourceId : asDataStaged.esSourceMap.keySet()) {
			if (! esSourceIdsInUse.contains(esSourceId)) {
				esSourceIdsToDelete.add(esSourceId);
			}
		}
		for (RtspProtoIdEsSource esSourceId : esSourceIdsToDelete) {
			asDataStaged.esSourceMap.remove(esSourceId);
		}

		// delete all ESEI objects that are not in use by an Input Source
		esSourceIdsToDelete.clear();
		for (RtspProtoIdEsSource esSourceId : asDataStaged.eseiMap.keySet()) {
			if (! esSourceIdsInUse.contains(esSourceId)) {
				esSourceIdsToDelete.add(esSourceId);
			}
		}
		for (RtspProtoIdEsSource esSourceId : esSourceIdsToDelete) {
			asDataStaged.eseiMap.remove(esSourceId);
		}

		// copy ESEI objects for MQs/DmxRtsp/DmxJb from 'current' to 'staged' if they have not changed
		Set<@NonNull RtspProtoIdEsSource> tmpIrrelevantEsIdsMq = new HashSet<>();
		RtspAsDeltaEsHelper.findMqEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				asDataStaged.inputSourceMap,
				asDataCurrent.eseiMap,
				asDataStaged.eseiMap,
				tmpIrrelevantEsIdsMq
			);
		Set<@NonNull RtspProtoIdEsSource> irrelevantEsIds = new HashSet<>(tmpIrrelevantEsIdsMq);
		Set<@NonNull RtspProtoIdEsSource> tmpIrrelevantEsIdsDmxFcOrRtsp = new HashSet<>();
		RtspAsDeltaEsHelper.findDmxFcOrRtspEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				asDataStaged.inputSourceMap,
				asDataCurrent.eseiMap,
				asDataStaged.eseiMap,
				tmpIrrelevantEsIdsDmxFcOrRtsp
			);
		irrelevantEsIds.addAll(tmpIrrelevantEsIdsDmxFcOrRtsp);
		Set<@NonNull RtspProtoIdEsSource> tmpIrrelevantEsIdsDmxJb = new HashSet<>();
		RtspAsDeltaEsHelper.findDmxJbEsIdsThatHaveBeenDeletedOrModifiedOrNotInUse(
				asDataStaged.inputSourceMap,
				asDataCurrent.eseiMap,
				asDataStaged.eseiMap,
				tmpIrrelevantEsIdsDmxJb
			);
		irrelevantEsIds.addAll(tmpIrrelevantEsIdsDmxJb);
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> entryEsei : asDataStaged.eseiMap.entrySet()) {
			/*System.out.println("Xstag ESEI " + entryEsei.getKey() + ": uri=" + entryEsei.getValue().inputUri() + ", " +
					"codec=" + entryEsei.getValue().codec() + " // " +
					"irrelevantEsIds.contains=" + (irrelevantEsIds.contains(entryEsei.getKey())) + " // " +
					"asDataCurrent.eseiMap.contains=" + (asDataCurrent.eseiMap.containsKey(entryEsei.getKey())) + " // " +
					"entryEsei.getValue().esSourceType()=" + entryEsei.getValue().esSourceType());*/
			if (! irrelevantEsIds.contains(entryEsei.getKey()) &&
					asDataCurrent.eseiMap.containsKey(entryEsei.getKey()) &&
					(entryEsei.getValue().esSourceType() == RtspProtoEsSourceType.ST_ES_MQ ||
						entryEsei.getValue().esSourceType() == RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_RTSP ||
						entryEsei.getValue().esSourceType() == RtspProtoEsSourceType.ST_DMX_VIRTUAL_ES_MQ_FROM_JB)) {
				asDataStaged.eseiMap.put(
						entryEsei.getKey().clone(),
						asDataCurrent.eseiMap.get(entryEsei.getKey()).clone()
					);
			}
		}

		//
		/*
		System.out.println("##########################################################################################");
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> entryEsei : asDataStaged.eseiMap.entrySet()) {
			System.out.println("stage ESEI " + entryEsei.getKey() + ": uri=" + entryEsei.getValue().inputUri() + ", codec=" + entryEsei.getValue().codec());
		}
		System.out.println("ppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp");
		for (Map.Entry<RtspProtoIdEsSource, RtspProtoEsSourceExpandedInfo> entryEsei : asDataCurrent.eseiMap.entrySet()) {
			System.out.println("curre ESEI " + entryEsei.getKey() + ": uri=" + entryEsei.getValue().inputUri() + ", codec=" + entryEsei.getValue().codec());
		}
		System.out.println("##########################################################################################");
		*/
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull Set<@NonNull RtspProtoIdInputSource> findRequiredDmxXxxInputSources(
				@NonNull RtspProtoEsSourceType esSourceType
			) {
		theReadLock.lock();
		try {
			Set<RtspProtoIdInputSource> resSet = new HashSet<>();
			for (RtspProtoInputSource tmpIsObj : asDataCurrent.inputSourceMap.values()) {
				if (! tmpIsObj.getEnabled()) {
					continue;
				}
				for (RtspProtoIdEsSource tmpIdEs : tmpIsObj.getEsSourceIds()) {
					if (asDataCurrent.eseiMap.containsKey(tmpIdEs) &&
							asDataCurrent.eseiMap.get(tmpIdEs).esSourceType() == esSourceType) {
						resSet.add(tmpIsObj.getIdInputSource());
						break;
					}
				}
			}
			return resSet;
		} finally {
			theReadLock.unlock();
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
			if (! asDataCurrent.esSourceMap.containsKey(tmpEsId)) {
				continue;  // this should never happen
			}
			if (! asDataCurrent.esSourceMap.get(tmpEsId).getEnabled()) {
				continue;
			}
			if (! asDataCurrent.eseiMap.containsKey(tmpEsId)) {
				continue;
			}
			RtspProtoEsSourceExpandedInfo esei = asDataCurrent.eseiMap.get(tmpEsId);
			/*System.out.println("KKKKKKKKKKKKKKKKKKKKKKKKKKKKKK IS " + idInputSource + " KKKKKKKKKKKKKKKKKKKKKKKKKKKKKK " +
					"found ES " + tmpEsId + " uri=" + esei.inputUri() + ", codec=" + esei.codec());*/
			if ((isVideo && esei.codec().isVideo()) || (! isVideo && esei.codec().isAudio())) {
				return Optional.of(asDataCurrent.esSourceMap.get(tmpEsId).clone());
			}
		}
		return Optional.empty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void copyIsIds(
				@NonNull Set<@NonNull RtspProtoIdInputSource> srcIsIds,
				@NonNull Set<@NonNull RtspProtoIdInputSource> dstIsIds
			) {
		for (RtspProtoIdInputSource idInputSource : srcIsIds) {
			dstIsIds.add(idInputSource.clone());
		}
	}

	private static void copyEsIds(
				@NonNull Set<@NonNull RtspProtoIdEsSource> srcEsIds,
				@NonNull Set<@NonNull RtspProtoIdEsSource> dstEsIds
			) {
		for (RtspProtoIdEsSource idEsSource : srcEsIds) {
			dstEsIds.add(idEsSource.clone());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String getEsSourceIdForExcMsg(@NonNull RtspProtoIdEsSource idEsSource) {
		return "esSrc='" + idEsSource.getIdStr().orElse("-unset-") + "'";
	}

}
